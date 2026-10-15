package crewx.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Finds synchronized lyrics on LRCLIB. Strategy: exact-match endpoint with progressively cleaner
 * metadata, then the search endpoint ranked by title/artist/duration. Results are cached in memory and
 * on disk, lookups never block the render thread, and stale results for a previous track are dropped.
 */
public final class LyricsService {
    private static final String BASE = "https://lrclib.net/api";
    private static final int TIMEOUT = 6000;
    private static final long NEGATIVE_TTL_MS = 3L * 60L * 1000L;
    private static final int MAX_MEMORY = 48;

    private final File cacheDir;
    private final JsonParser parser = new JsonParser();
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "CrewX Lyrics");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Lyrics> memory = new LinkedHashMap<String, Lyrics>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Lyrics> eldest) {
            return size() > MAX_MEMORY;
        }
    };
    private final Map<String, Long> negativeUntil = new LinkedHashMap<>();
    private volatile Lyrics current = Lyrics.NONE;
    private volatile String wantedKey = "";

    public LyricsService(File cacheDir) {
        this.cacheDir = cacheDir;
    }

    public Lyrics getCurrent() {
        return this.current;
    }

    public void shutdown() {
        this.executor.shutdownNow();
    }

    /** Starts (or reuses) the lookup for the given track. */
    public void request(MediaInfo info) {
        if (info == null) {
            this.wantedKey = "";
            this.current = Lyrics.NONE;
            return;
        }
        final String key = info.key;
        this.wantedKey = key;
        final LyricsQuery query = new LyricsQuery(info.title, info.artist, info.album, info.durationMs / 1000L);
        final String cacheKey = query.cacheKey();
        synchronized (this.memory) {
            Lyrics cached = this.memory.get(cacheKey);
            if (cached != null) {
                this.current = new Lyrics(key, cached.state, cached.lines, cached.synced);
                return;
            }
            Long until = this.negativeUntil.get(cacheKey);
            if (until != null && System.currentTimeMillis() < until) {
                this.current = new Lyrics(key, Lyrics.State.NOT_FOUND, Collections.<LyricLine>emptyList(), false);
                return;
            }
        }
        this.current = Lyrics.loading(key);
        this.executor.execute(() -> {
            Lyrics result = resolve(key, cacheKey, query);
            synchronized (this.memory) {
                if (result.state == Lyrics.State.FOUND || result.state == Lyrics.State.INSTRUMENTAL) {
                    this.memory.put(cacheKey, result);
                } else {
                    this.negativeUntil.put(cacheKey, System.currentTimeMillis()
                            + (result.state == Lyrics.State.ERROR ? 20000L : NEGATIVE_TTL_MS));
                    if (this.negativeUntil.size() > 200) this.negativeUntil.clear();
                }
            }
            if (key.equals(this.wantedKey)) this.current = result;
        });
    }

    private Lyrics resolve(String key, String cacheKey, LyricsQuery query) {
        Lyrics disk = readDisk(key, cacheKey);
        if (disk != null) return disk;
        boolean networkError = false;
        Lyrics plainFallback = null;
        try {
            for (String[] candidate : query.exactCandidates()) {
                if (!key.equals(this.wantedKey)) return new Lyrics(key, Lyrics.State.NOT_FOUND, Collections.<LyricLine>emptyList(), false);
                StringBuilder url = new StringBuilder(BASE).append("/get?track_name=").append(HttpUtil.urlEncode(candidate[0]))
                        .append("&artist_name=").append(HttpUtil.urlEncode(candidate[1]));
                if (!candidate[2].isEmpty()) url.append("&album_name=").append(HttpUtil.urlEncode(candidate[2]));
                if (query.durationSeconds > 0L) url.append("&duration=").append(query.durationSeconds);
                JsonObject hit = fetchObject(url.toString());
                if (hit == null) continue;
                Lyrics lyrics = fromJson(key, hit);
                if (lyrics != null && lyrics.synced) return store(cacheKey, hit, lyrics);
                if (lyrics != null && lyrics.state == Lyrics.State.INSTRUMENTAL) return store(cacheKey, hit, lyrics);
                if (lyrics != null && plainFallback == null) plainFallback = lyrics;
            }
            for (String searchUrl : searchUrls(query)) {
                if (!key.equals(this.wantedKey)) break;
                JsonArray results = fetchArray(searchUrl);
                if (results == null) continue;
                JsonObject best = null;
                int bestScore = 0;
                for (JsonElement element : results) {
                    if (!element.isJsonObject()) continue;
                    JsonObject item = element.getAsJsonObject();
                    boolean synced = !string(item, "syncedLyrics").isEmpty();
                    boolean plain = !string(item, "plainLyrics").isEmpty();
                    boolean instrumental = bool(item, "instrumental");
                    if (!synced && !plain && !instrumental) continue;
                    if (!instrumental && fromJson(key, item) == null) continue;   // placeholder submission
                    int score = query.score(string(item, "trackName"), string(item, "artistName"),
                            Math.round(number(item, "duration")), synced);
                    if (score > bestScore) {
                        bestScore = score;
                        best = item;
                    }
                }
                if (best != null) {
                    Lyrics lyrics = fromJson(key, best);
                    if (lyrics != null && (lyrics.synced || lyrics.state == Lyrics.State.INSTRUMENTAL)) return store(cacheKey, best, lyrics);
                    if (lyrics != null && plainFallback == null) plainFallback = lyrics;
                }
            }
        } catch (IOException exception) {
            networkError = true;
        } catch (RuntimeException exception) {
            networkError = true;
        }
        if (plainFallback != null) return plainFallback;
        return new Lyrics(key, networkError ? Lyrics.State.ERROR : Lyrics.State.NOT_FOUND,
                Collections.<LyricLine>emptyList(), false);
    }

    private static List<String> searchUrls(LyricsQuery query) {
        String title = LyricsQuery.cleanTitle(query.title);
        String artist = LyricsQuery.cleanArtistFull(query.artist);
        List<String> urls = new java.util.ArrayList<>();
        urls.add(BASE + "/search?track_name=" + HttpUtil.urlEncode(title) + "&artist_name=" + HttpUtil.urlEncode(artist));
        for (String[] pair : LyricsQuery.splitHyphenTitle(title)) {
            urls.add(BASE + "/search?track_name=" + HttpUtil.urlEncode(pair[1]) + "&artist_name=" + HttpUtil.urlEncode(pair[0]));
        }
        urls.add(BASE + "/search?q=" + HttpUtil.urlEncode((artist + " " + title).trim()));
        return urls;
    }

    /** One retry on transient failures; 404 means "not found" (null). */
    private JsonObject fetchObject(String url) throws IOException {
        String body = fetchBody(url);
        return body == null ? null : this.parser.parse(body).getAsJsonObject();
    }

    private JsonArray fetchArray(String url) throws IOException {
        String body = fetchBody(url);
        return body == null ? null : this.parser.parse(body).getAsJsonArray();
    }

    private String fetchBody(String url) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                HttpUtil.Result result = HttpUtil.get(url, TIMEOUT);
                if (result.code == 404) return null;
                if (result.code == 429) {
                    sleep(Math.min(3000L, Math.max(500L, result.retryAfterSeconds * 1000L)));
                    continue;
                }
                if (result.code >= 200 && result.code < 300 && !result.body.isEmpty()) return result.body;
                if (result.code >= 500) {
                    sleep(400L);
                    continue;
                }
                return null;
            } catch (IOException exception) {
                last = exception;
                sleep(300L);
            }
        }
        if (last != null) throw last;
        return null;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private Lyrics fromJson(String key, JsonObject json) {
        if (bool(json, "instrumental")) {
            return new Lyrics(key, Lyrics.State.INSTRUMENTAL, Collections.<LyricLine>emptyList(), false);
        }
        double duration = number(json, "duration");
        List<LyricLine> lines = LrcParser.parseSynced(string(json, "syncedLyrics"));
        if (plausible(lines.size(), duration)) return new Lyrics(key, Lyrics.State.FOUND, Collections.unmodifiableList(lines), true);
        lines = LrcParser.parsePlain(string(json, "plainLyrics"));
        if (plausible(lines.size(), duration)) return new Lyrics(key, Lyrics.State.FOUND, Collections.unmodifiableList(lines), false);
        return null;
    }

    /**
     * LRCLIB is crowd-sourced and has placeholder submissions (e.g. "[00:00.00]probe" as the exact match for
     * well-known songs). A song longer than a minute with fewer than 3 lines is treated as one of those, so
     * the lookup moves on to the next candidate instead of showing it.
     */
    static boolean plausible(int lineCount, double durationSeconds) {
        if (lineCount <= 0) return false;
        return lineCount >= 3 || durationSeconds <= 60.0D;
    }

    // ---- disk cache -------------------------------------------------------------------------

    private File cacheFile(String cacheKey) {
        return new File(this.cacheDir, HttpUtil.sha1(cacheKey) + ".json");
    }

    private Lyrics readDisk(String key, String cacheKey) {
        File file = cacheFile(cacheKey);
        if (!file.isFile()) return null;
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            return fromJson(key, this.parser.parse(text).getAsJsonObject());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Lyrics store(String cacheKey, JsonObject json, Lyrics lyrics) {
        try {
            if (!this.cacheDir.isDirectory() && !this.cacheDir.mkdirs()) return lyrics;
            JsonObject slim = new JsonObject();
            slim.addProperty("syncedLyrics", string(json, "syncedLyrics"));
            slim.addProperty("plainLyrics", string(json, "plainLyrics"));
            slim.addProperty("instrumental", bool(json, "instrumental"));
            Files.write(cacheFile(cacheKey).toPath(), slim.toString().getBytes(StandardCharsets.UTF_8));
            File[] files = this.cacheDir.listFiles();
            if (files != null && files.length > 400) {
                java.util.Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
                for (int i = 0; i < files.length - 300; i++) files[i].delete();
            }
        } catch (Exception ignored) {
        }
        return lyrics;
    }

    private static String string(JsonObject json, String key) {
        try {
            JsonElement element = json.get(key);
            return element == null || element.isJsonNull() ? "" : element.getAsString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static double number(JsonObject json, String key) {
        try {
            JsonElement element = json.get(key);
            return element == null || element.isJsonNull() ? 0.0D : element.getAsDouble();
        } catch (Exception ignored) {
            return 0.0D;
        }
    }

    private static boolean bool(JsonObject json, String key) {
        try {
            JsonElement element = json.get(key);
            return element != null && !element.isJsonNull() && element.getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }
}
