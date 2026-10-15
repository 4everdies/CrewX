package crewx.media;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns messy player metadata ("Artist - Topic", "Song (feat. X) [Official Video]", "Artist - Song") into
 * clean search candidates. Pure Java, unit-testable.
 */
public final class LyricsQuery {
    public final String title;
    public final String artist;
    public final String album;
    public final long durationSeconds;

    public LyricsQuery(String title, String artist, String album, long durationSeconds) {
        this.title = title == null ? "" : title;
        this.artist = artist == null ? "" : artist;
        this.album = album == null ? "" : album;
        this.durationSeconds = Math.max(0L, durationSeconds);
    }

    private static final Pattern NOISE_BRACKETS = Pattern.compile(
            "\\s*[\\(\\[][^\\)\\]]*(feat|ft\\.|with |prod|official|video|audio|lyric|visuali[sz]er|remaster|live|"
                    + "version|edit|mix|explicit|clip|\\bhd\\b|\\bhq\\b|4k|mv|soundtrack|from )[^\\)\\]]*[\\)\\]]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_VERSION = Pattern.compile(
            "\\s+-\\s+(\\d{4}\\s+)?(remaster(ed)?|single version|radio edit|live|mono|stereo|bonus track|deluxe|"
                    + "album version|explicit|official).*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FEAT_TAIL = Pattern.compile("\\s+(feat\\.?|ft\\.?|featuring)\\s+.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PIPE_TAIL = Pattern.compile("\\s*[|｜].*$");
    private static final Pattern ARTIST_NOISE = Pattern.compile("\\s*-\\s*topic$|\\s*vevo$|\\s*official$|\\s*oficial$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ARTIST_SPLIT = Pattern.compile("\\s*(,|&|\\bx\\b|\\bfeat\\.?\\b|\\bft\\.?\\b|\\band\\b|;)\\s*",
            Pattern.CASE_INSENSITIVE);

    public static String cleanTitle(String raw) {
        String text = raw == null ? "" : raw;
        text = NOISE_BRACKETS.matcher(text).replaceAll("");
        text = PIPE_TAIL.matcher(text).replaceAll("");
        text = TRAILING_VERSION.matcher(text).replaceAll("");
        text = FEAT_TAIL.matcher(text).replaceAll("");
        return text.replaceAll("\\s+", " ").trim();
    }

    /** Artist without YouTube channel noise, but with every credited name. */
    public static String cleanArtistFull(String raw) {
        String text = raw == null ? "" : raw.trim();
        text = ARTIST_NOISE.matcher(text).replaceAll("");
        return text.replaceAll("\\s+", " ").trim();
    }

    public static String firstArtist(String raw) {
        String full = cleanArtistFull(raw);
        String[] parts = ARTIST_SPLIT.split(full, 2);
        return parts.length == 0 ? full : parts[0].trim();
    }

    /** Lower-case, accent-free, punctuation-free form used for comparing names. */
    public static String normalize(String text) {
        if (text == null) return "";
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return decomposed.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    /** Stable identity for the disk cache, shared between Spotify and system sources. */
    public String cacheKey() {
        return normalize(cleanArtistFull(this.artist)) + "|" + normalize(cleanTitle(this.title)) + "|" + this.durationSeconds / 2L;
    }

    /** Ordered (title, artist, withAlbum) combinations for the exact-match endpoint. */
    public List<String[]> exactCandidates() {
        Set<String> seen = new LinkedHashSet<>();
        List<String[]> result = new ArrayList<>();
        String cleanTitle = cleanTitle(this.title);
        String artistFull = cleanArtistFull(this.artist);
        String artistFirst = firstArtist(this.artist);
        add(result, seen, this.title, artistFull, this.album);
        add(result, seen, cleanTitle, artistFull, "");
        add(result, seen, cleanTitle, artistFirst, "");
        for (String[] pair : splitHyphenTitle(cleanTitle)) add(result, seen, pair[1], pair[0], "");
        return result;
    }

    /** "Artist - Song" titles (typical for YouTube/SoundCloud uploads) as {artist, song} pairs. */
    public static List<String[]> splitHyphenTitle(String title) {
        List<String[]> pairs = new ArrayList<>();
        int index = title.indexOf(" - ");
        if (index > 0 && index < title.length() - 3) {
            String left = title.substring(0, index).trim();
            String right = title.substring(index + 3).trim();
            if (!left.isEmpty() && !right.isEmpty()) pairs.add(new String[]{left, right});
        }
        return pairs;
    }

    private static void add(List<String[]> out, Set<String> seen, String title, String artist, String album) {
        if (title == null || title.trim().isEmpty()) return;
        String key = normalize(title) + "|" + normalize(artist) + "|" + normalize(album);
        if (seen.add(key)) out.add(new String[]{title.trim(), artist == null ? "" : artist.trim(), album == null ? "" : album.trim()});
    }

    /** Score a search hit: higher is better, below 0 means "not the same song". */
    public int score(String hitTitle, String hitArtist, long hitDurationSeconds, boolean hasSynced) {
        String wantedTitle = normalize(cleanTitle(this.title));
        String gotTitle = normalize(cleanTitle(hitTitle));
        int titleScore = similarity(wantedTitle, gotTitle);
        if (titleScore < 2) {
            // "Artist - Song" uploads: the real song name is the right side
            for (String[] pair : splitHyphenTitle(cleanTitle(this.title))) {
                titleScore = Math.max(titleScore, similarity(normalize(pair[1]), gotTitle));
            }
        }
        if (titleScore < 2) return -100;
        int artistScore = 0;
        String wantedArtist = normalize(cleanArtistFull(this.artist));
        String wantedFirst = normalize(firstArtist(this.artist));
        String gotArtist = normalize(hitArtist);
        if (!wantedArtist.isEmpty()) {
            artistScore = Math.max(similarity(wantedArtist, gotArtist), similarity(wantedFirst, gotArtist));
            if (artistScore < 2) {
                for (String[] pair : splitHyphenTitle(cleanTitle(this.title))) {
                    artistScore = Math.max(artistScore, similarity(normalize(pair[0]), gotArtist));
                }
            }
            if (artistScore < 2) return -100;
        }
        int score = titleScore * 4 + artistScore * 2;
        if (this.durationSeconds > 0L && hitDurationSeconds > 0L) {
            long diff = Math.abs(this.durationSeconds - hitDurationSeconds);
            if (diff <= 2L) score += 6;
            else if (diff <= 5L) score += 2;
            else if (diff > 12L) score -= 6;
        }
        if (hasSynced) score += 5;
        return score;
    }

    private static int similarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        if (a.equals(b)) return 3;
        if (a.contains(b) || b.contains(a)) return 2;
        return 0;
    }
}
