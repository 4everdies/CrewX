package crewx.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cover art from the iTunes Search API (free, no account), for players that do not hand a picture to the
 * system: some browser sites, players whose thumbnail cannot be read, or when reading it failed. Looked up once
 * per song in the background; the answer (a URL, or "none") is kept for the session.
 */
public final class OnlineCovers {
    private static final String NONE = "";
    private static final int MAX_ENTRIES = 64;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "CrewX Cover lookup");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, String> results = new LinkedHashMap<String, String>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MAX_ENTRIES;
        }
    };
    private final JsonParser parser = new JsonParser();

    /**
     * The cover URL found for this song, or "" while it is being looked up / when nothing matched.
     * {@code onFound} runs (on the lookup thread) once a URL was found.
     */
    public String coverFor(MediaInfo info, Runnable onFound) {
        if (info == null || info.title.isEmpty()) return NONE;
        final LyricsQuery query = new LyricsQuery(info.title, info.artist, info.album, info.durationMs / 1000L);
        final String key = query.cacheKey();
        synchronized (this.results) {
            String known = this.results.get(key);
            if (known != null) return known;
            this.results.put(key, NONE);   // looked up once, even when nothing is found
        }
        this.executor.execute(() -> {
            String url = lookup(query);
            if (url.isEmpty()) return;
            synchronized (this.results) {
                this.results.put(key, url);
            }
            onFound.run();
        });
        return NONE;
    }

    private String lookup(LyricsQuery query) {
        String title = LyricsQuery.cleanTitle(query.title);
        String artist = LyricsQuery.firstArtist(query.artist);
        if (artist.isEmpty()) {
            for (String[] pair : LyricsQuery.splitHyphenTitle(title)) {
                artist = pair[0];
                title = pair[1];
            }
        }
        String term = (artist + " " + title).trim();
        if (term.isEmpty()) return NONE;
        try {
            HttpUtil.Result result = HttpUtil.get("https://itunes.apple.com/search?media=music&entity=song&limit=10&term="
                    + HttpUtil.urlEncode(term), 6000);
            if (result.code != 200) return NONE;
            JsonElement root = this.parser.parse(result.body);
            if (!root.isJsonObject() || !root.getAsJsonObject().has("results")) return NONE;
            JsonArray hits = root.getAsJsonObject().getAsJsonArray("results");
            String best = NONE;
            int bestScore = 0;
            for (JsonElement element : hits) {
                if (!element.isJsonObject()) continue;
                JsonObject hit = element.getAsJsonObject();
                String art = string(hit, "artworkUrl100");
                if (art.isEmpty()) continue;
                long seconds = 0L;
                try {
                    if (hit.has("trackTimeMillis")) seconds = hit.get("trackTimeMillis").getAsLong() / 1000L;
                } catch (RuntimeException ignored) {
                }
                int score = query.score(string(hit, "trackName"), string(hit, "artistName"), seconds, false);
                if (score > bestScore) {
                    bestScore = score;
                    best = art;
                }
            }
            return bigger(best);
        } catch (Exception failure) {
            return NONE;
        }
    }

    /** iTunes returns a 100 px thumbnail; the same path serves any size. */
    static String bigger(String url) {
        if (url == null || url.isEmpty()) return NONE;
        return url.replaceAll("/\\d+x\\d+(bb)?\\.(jpg|png|webp)$", "/600x600bb.jpg");
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }
}
