package crewx.media;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Hub that ties everything together: it runs the system source (always) and the Spotify API source
 * (only when it adds something), decides which one the card shows, and loads cover art + lyrics for
 * the current track in the background. The render thread only reads cheap volatile snapshots.
 */
public final class MediaManager implements MediaSource.Listener {
    public static final int MODE_AUTO = 0;
    public static final int MODE_SYSTEM = 1;
    public static final int MODE_SPOTIFY = 2;

    public interface Settings {
        int sourceMode();

        String clientIdOverride();

        boolean autoLogin();
    }

    private final Settings settings;
    private final SpotifyApiSource.Notifier notifier;
    private final SystemMediaSource system;
    private final SpotifyApiSource spotify;
    private final LyricsService lyrics;
    private final OnlineCovers onlineCovers = new OnlineCovers();
    /** When the current song was first seen: the player gets a moment to hand over its own cover first. */
    private volatile long trackSeenAt;
    private ScheduledExecutorService ticker;
    private ExecutorService artworkWorker;
    private final Map<String, BufferedImage> artworkCache = new LinkedHashMap<String, BufferedImage>(8, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
            return size() > 6;
        }
    };

    private volatile MediaInfo systemInfo;
    private volatile MediaInfo spotifyInfo;
    private volatile MediaInfo current;
    private volatile BufferedImage pendingArtwork;
    /** Set when the shown cover must go away (cover changed, nothing playing, restart). */
    private volatile boolean artworkCleared;
    private volatile String artworkRequested = "";
    private volatile String systemStatus = "";
    /** Why the current cover could not be shown ("" when fine); reported by {@link #getStatus()}. */
    private volatile String artworkError = "";
    private volatile boolean running;
    private volatile long seekHoldUntil;
    private volatile long seekHoldPosition;
    private String lastKey = "";
    private boolean autoLoginTried;

    public MediaManager(File configDir, Settings settings, SpotifyApiSource.Notifier notifier) {
        this.settings = settings;
        this.notifier = notifier;
        this.system = new SystemMediaSource(new File(configDir, "media"));
        this.spotify = new SpotifyApiSource(new File(configDir, "spotify-auth.json"), settings::clientIdOverride, notifier);
        this.lyrics = new LyricsService(new File(configDir, "lyrics"));
    }

    // ------------------------------------------------------------------ lifecycle

    public synchronized void start() {
        if (this.running) return;
        this.running = true;
        this.autoLoginTried = false;
        synchronized (this.artworkCache) {
            this.artworkCache.clear();
        }
        this.artworkWorker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "CrewX Cover");
            thread.setDaemon(true);
            return thread;
        });
        this.ticker = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "CrewX Media tick");
            thread.setDaemon(true);
            return thread;
        });
        this.system.start(this);
        this.spotify.start(this);
        this.ticker.scheduleWithFixedDelay(this::tick, 0L, 1000L, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (!this.running) return;
        this.running = false;
        if (this.ticker != null) this.ticker.shutdownNow();
        if (this.artworkWorker != null) this.artworkWorker.shutdownNow();
        this.system.stop();
        this.spotify.stop();
        this.systemInfo = null;
        this.spotifyInfo = null;
        this.current = null;
        this.pendingArtwork = null;
        this.artworkCleared = true;
        this.lastKey = "";
        this.artworkRequested = "";
        this.lyrics.request(null);
    }

    // ------------------------------------------------------------------ source callbacks

    @Override
    public void onMedia(MediaSource source, MediaInfo info) {
        if (source == this.system) this.systemInfo = info;
        else if (source == this.spotify) this.spotifyInfo = info;
        evaluate();
    }

    @Override
    public void onStatus(MediaSource source, String status) {
        if (source == this.system) this.systemStatus = status;
    }

    private void tick() {
        try {
            int mode = this.settings.sourceMode();
            MediaInfo sys = this.systemInfo;
            boolean wantSpotifyApi = this.spotify.isLinked() && !this.spotify.isBlocked()
                    && (mode == MODE_SPOTIFY || (mode == MODE_AUTO && (sys == null || !sys.playing)));
            this.spotify.setActive(wantSpotifyApi);
            if (!wantSpotifyApi) this.spotifyInfo = null;
            if (!this.autoLoginTried) {
                this.autoLoginTried = true;
                if (mode != MODE_SYSTEM && this.settings.autoLogin() && this.spotify.hasClientId()
                        && !this.spotify.isLinked() && !this.spotify.wasDeclined()) {
                    this.spotify.connect();
                }
            }
            evaluate();
        } catch (RuntimeException ignored) {
        }
    }

    private synchronized void evaluate() {
        MediaInfo chosen = choose();
        MediaInfo held = applySeekHold(chosen);
        this.current = held;
        String key = held == null ? "" : held.key;
        if (!key.equals(this.lastKey)) {
            this.lastKey = key;
            this.trackSeenAt = System.currentTimeMillis();
            this.lyrics.request(held);
        }
        String art = held == null ? "" : held.artworkUrl;
        if (held != null && art.isEmpty() && System.currentTimeMillis() - this.trackSeenAt > 2500L) {
            // the player gave no picture (or it could not be read): look the cover up online
            art = this.onlineCovers.coverFor(held, this::evaluate);
        }
        if (!art.equals(this.artworkRequested)) {
            this.artworkRequested = art;
            // drop the old cover right away so a new song never shows the previous song's art
            this.pendingArtwork = null;
            this.artworkCleared = true;
            this.artworkError = "";
            if (!art.isEmpty()) loadArtwork(art, held.source, 1);
        }
    }

    private MediaInfo choose() {
        int mode = this.settings.sourceMode();
        MediaInfo sys = this.systemInfo;
        MediaInfo spo = this.spotifyInfo;
        if (mode == MODE_SYSTEM) return sys;
        if (mode == MODE_SPOTIFY) return spo;
        if (sys != null && sys.playing) return sys;
        if (spo != null && spo.playing) return spo;
        if (sys == null) return spo;
        if (spo == null) return sys;
        return sys.capturedAt >= spo.capturedAt ? sys : spo;
    }

    /** After a seek, keep showing the requested position until the source catches up. */
    private MediaInfo applySeekHold(MediaInfo info) {
        if (info == null || System.currentTimeMillis() >= this.seekHoldUntil) return info;
        return info.toBuilder().playing(info.playing, this.seekHoldPosition).build();
    }

    private static final int ARTWORK_ATTEMPTS = 3;

    private void loadArtwork(String url, String source, int attempt) {
        ExecutorService worker = this.artworkWorker;
        if (worker == null) return;
        worker.execute(() -> {
            if (!url.equals(this.artworkRequested)) return;
            // Covers written by the Windows helper are numbered files (cover.img.1, .2, ...) and the numbering
            // starts over whenever the helper restarts, so a file name does not identify a picture: those are
            // always read again. Caching them showed an earlier song's cover.
            boolean cacheable = !url.startsWith("file:");
            BufferedImage built = null;
            if (cacheable) {
                synchronized (this.artworkCache) {
                    built = this.artworkCache.get(url);
                }
            }
            if (built == null) {
                try {
                    BufferedImage image = CoverBuilder.load(url);
                    if ("Spotify".equalsIgnoreCase(source) && url.startsWith("file:")) {
                        image = CoverBuilder.extractSpotifyArtwork(image);
                    }
                    built = CoverBuilder.buildRounded(image);
                } catch (Exception | OutOfMemoryError failure) {
                    if (!url.equals(this.artworkRequested)) return;
                    this.artworkError = "cover failed: " + failure.getClass().getSimpleName()
                            + (failure.getMessage() == null ? "" : " (" + failure.getMessage() + ")");
                    // network hiccups and covers that are still being written deserve another try
                    if (attempt < ARTWORK_ATTEMPTS) {
                        ScheduledExecutorService retry = this.ticker;
                        if (retry != null && !retry.isShutdown()) {
                            retry.schedule(() -> loadArtwork(url, source, attempt + 1), 1500L * attempt, TimeUnit.MILLISECONDS);
                        }
                    }
                    return;
                }
                if (cacheable) {
                    synchronized (this.artworkCache) {
                        this.artworkCache.put(url, built);
                    }
                }
            }
            if (url.equals(this.artworkRequested)) {
                this.artworkError = "";
                this.pendingArtwork = built;
            }
        });
    }

    // ------------------------------------------------------------------ queries (render thread)

    public MediaInfo getCurrent() {
        return this.current;
    }

    public Lyrics getLyrics() {
        Lyrics lyrics = this.lyrics.getCurrent();
        MediaInfo info = this.current;
        return info != null && info.key.equals(lyrics.key) ? lyrics : Lyrics.NONE;
    }

    /** Returns a freshly loaded rounded cover once, or null. */
    public BufferedImage takeArtwork() {
        BufferedImage image = this.pendingArtwork;
        this.pendingArtwork = null;
        return image;
    }

    /** True once after the current cover became invalid; the card should drop its texture. */
    public boolean takeArtworkCleared() {
        if (!this.artworkCleared) return false;
        this.artworkCleared = false;
        return true;
    }

    public String getStatus() {
        MediaInfo info = this.current;
        if (info != null) {
            String text = (info.playing ? "Playing" : "Paused") + " · " + info.source;
            if (info.artworkUrl.isEmpty()) {
                int cover = this.systemStatus.indexOf("cover error:");
                text += cover >= 0 && !info.key.startsWith("spotify:") ? " · " + this.systemStatus.substring(cover) : " · no cover from the player";
                if (!this.artworkRequested.isEmpty()) text += " (using an online cover)";
            } else if (!this.artworkError.isEmpty()) {
                text += " · " + this.artworkError;
            }
            return text;
        }
        int mode = this.settings.sourceMode();
        if (mode == MODE_SPOTIFY) return this.spotify.getStatus();
        String sysText = this.systemStatus.isEmpty() ? "Nothing playing" : this.systemStatus;
        return mode == MODE_SYSTEM ? sysText : sysText + " · Spotify: " + this.spotify.getStatus();
    }

    // ------------------------------------------------------------------ actions

    public void control(MediaAction action) {
        MediaInfo info = this.current;
        if (info == null) {
            refreshNow();
            return;
        }
        MediaSource target = info.key.startsWith("spotify:") ? this.spotify : this.system;
        target.control(action, 0L);
        if (action == MediaAction.TOGGLE && target == this.system) {
            // instant feedback; the real state arrives from the helper within ~0.2 s
            MediaInfo flipped = info.toBuilder().playing(!info.playing, info.positionNow()).build();
            this.systemInfo = flipped;
            evaluate();
        }
    }

    public void seek(long positionMs) {
        MediaInfo info = this.current;
        if (info == null || !info.canSeek) return;
        long clamped = Math.max(0L, info.durationMs > 0L ? Math.min(positionMs, info.durationMs - 300L) : positionMs);
        MediaSource target = info.key.startsWith("spotify:") ? this.spotify : this.system;
        target.control(MediaAction.SEEK, clamped);
        this.seekHoldPosition = clamped;
        this.seekHoldUntil = System.currentTimeMillis() + 1500L;
        evaluate();
    }

    public void refreshNow() {
        this.system.refreshNow();
        this.spotify.refreshNow();
    }

    public void connectSpotify() {
        if (!this.spotify.hasClientId()) {
            this.notifier.chat("&eSpotify login is optional: no Client ID is configured, but the Spotify app and "
                    + "browser players are detected automatically.&r");
            return;
        }
        this.spotify.connect();
    }

    public void logoutSpotify() {
        this.spotify.logout();
    }

    public SpotifyApiSource getSpotify() {
        return this.spotify;
    }
}
