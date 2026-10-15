package crewx.media.backend;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import crewx.media.MediaAction;
import crewx.media.MediaInfo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Windows: a hidden, long-lived PowerShell process reads GlobalSystemMediaTransportControls
 * (Spotify, SoundCloud/YouTube/anything in Chrome/Edge/Firefox, VLC, ...) - no account or login needed.
 */
public final class WindowsMediaBackend implements MediaBackend {
    private static final String SCRIPT_RESOURCE = "/assets/crewx/media/smtc.ps1";
    private static final int MAX_RESTARTS = 5;
    /** Optional override of the PowerShell executable (e.g. a pwsh.exe path). */
    private static final String EXECUTABLE_PROPERTY = "crewx.media.powershell";

    private final File directory;
    private final JsonParser parser = new JsonParser();
    private volatile boolean running;
    /** Bumped on every start/stop so a helper loop from an earlier start can never keep running. */
    private volatile int generation;
    private volatile Process process;
    private volatile BufferedWriter stdin;
    private volatile Consumer<MediaInfo> sink;
    private volatile Consumer<String> status;
    private Thread readerThread;
    /** Own clock for players without a timeline (reader thread only). */
    private String clockKey = "";
    private long clockBase;
    private long clockAnchor;
    private boolean clockPlaying;

    public WindowsMediaBackend(File directory) {
        this.directory = directory;
    }

    @Override
    public synchronized void start(Consumer<MediaInfo> sink, Consumer<String> status) {
        this.sink = sink;
        this.status = status;
        if (this.running) return;
        this.running = true;
        final int myGeneration = ++this.generation;
        this.readerThread = new Thread(() -> runLoop(myGeneration), "CrewX media (Windows)");
        this.readerThread.setDaemon(true);
        this.readerThread.start();
    }

    @Override
    public synchronized void stop() {
        this.running = false;
        this.generation++;
        Process current = this.process;
        this.process = null;
        this.stdin = null;
        if (current != null) current.destroyForcibly();
        Thread thread = this.readerThread;
        if (thread != null) thread.interrupt();
        this.readerThread = null;
    }

    @Override
    public void send(MediaAction action, long argument) {
        String command;
        switch (action) {
            case PLAY: command = "play"; break;
            case PAUSE: command = "pause"; break;
            case TOGGLE: command = "toggle"; break;
            case NEXT: command = "next"; break;
            case PREVIOUS: command = "prev"; break;
            case SEEK: command = "seek " + Math.max(0L, argument); break;
            default: return;
        }
        write(command);
    }

    @Override
    public void refresh() {
        write("noop");
    }

    private synchronized void write(String line) {
        BufferedWriter writer = this.stdin;
        if (writer == null) return;
        try {
            writer.write(line);
            writer.write('\n');
            writer.flush();
        } catch (IOException ignored) {
        }
    }

    private boolean isCurrent(int myGeneration) {
        return this.running && this.generation == myGeneration;
    }

    private void runLoop(int myGeneration) {
        int failures = 0;
        while (isCurrent(myGeneration)) {
            long startedAt = System.currentTimeMillis();
            Process started = null;
            try {
                File script = extractScript();
                File thumb = new File(this.directory, "cover.img");
                List<String> command = new ArrayList<>();
                command.add(powershellExecutable());
                command.addAll(Arrays.asList("-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                        "-ExecutionPolicy", "Bypass", "-File", script.getAbsolutePath(), "-ThumbPath", thumb.getAbsolutePath()));
                started = new ProcessBuilder(command).start();
                synchronized (this) {
                    if (!isCurrent(myGeneration)) {
                        started.destroyForcibly();
                        return;
                    }
                    this.process = started;
                    this.stdin = new BufferedWriter(new OutputStreamWriter(started.getOutputStream(), StandardCharsets.UTF_8));
                }
                drain(started.getErrorStream());
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(started.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (isCurrent(myGeneration) && (line = reader.readLine()) != null) handleLine(line, thumb);
                }
            } catch (Exception exception) {
                if (isCurrent(myGeneration)) {
                    this.status.accept("System media unavailable (PowerShell: " + exception.getMessage() + ")");
                }
            }
            synchronized (this) {
                // only clear what this loop owns; a newer start() may already have its own helper running
                if (started != null && this.process == started) {
                    this.process = null;
                    this.stdin = null;
                }
            }
            if (started != null) started.destroyForcibly();
            if (!isCurrent(myGeneration)) return;
            this.sink.accept(null);
            if (System.currentTimeMillis() - startedAt > 60000L) failures = 0;
            failures++;
            if (failures > MAX_RESTARTS) {
                // the module stays "on" but idle; toggling it (stop + start) gives the helper a fresh set of retries
                synchronized (this) {
                    if (this.generation == myGeneration) this.running = false;
                }
                this.status.accept("System media helper keeps failing; use the Spotify login instead");
                return;
            }
            try {
                Thread.sleep(2000L * failures);
            } catch (InterruptedException interrupted) {
                return;
            }
        }
    }

    private void handleLine(String line, File thumb) {
        line = line.trim();
        if (line.isEmpty() || line.charAt(0) != '{') return;
        try {
            JsonObject json = this.parser.parse(line).getAsJsonObject();
            if (!bool(json, "ok", true)) {
                this.status.accept("System media: " + str(json, "error", "unavailable"));
                this.sink.accept(null);
                return;
            }
            if (bool(json, "none", false)) {
                this.sink.accept(null);
                return;
            }
            String app = str(json, "app", "");
            MediaInfo.Builder info = MediaInfo.builder();
            info.title = str(json, "title", "");
            info.artist = str(json, "artist", "");
            info.album = str(json, "album", "");
            info.source = MediaBackend.friendlyName(app);
            info.key = app + "|" + info.title + "|" + info.artist;
            info.durationMs = lng(json, "dur", 0L);
            info.positionMs = lng(json, "pos", 0L);
            info.capturedAt = System.currentTimeMillis();
            info.playing = "Playing".equalsIgnoreCase(str(json, "status", ""));
            info.canSeek = bool(json, "seek", false);
            applyOwnClock(info);
            info.artworkUrl = artworkUrl(json, thumb);
            String artError = str(json, "artErr", "");
            this.status.accept(info.source + (info.playing ? " playing" : " paused")
                    + (artError.isEmpty() ? "" : " · cover error: " + artError));
            this.sink.accept(info.build());
        } catch (Exception ignored) {
        }
    }

    /**
     * Some players (several sites in the browser) publish a song without a timeline: position and length stay 0.
     * The lyrics then never left their first line. For those, the time is counted here from when the song was
     * first seen, paused while it is paused.
     */
    void applyOwnClock(MediaInfo.Builder info) {
        if (info.durationMs > 0L || info.positionMs > 0L) {
            this.clockKey = "";
            return;
        }
        long now = System.currentTimeMillis();
        if (!info.key.equals(this.clockKey)) {
            this.clockKey = info.key;
            this.clockBase = 0L;
            this.clockAnchor = now;
            this.clockPlaying = info.playing;
        } else if (this.clockPlaying != info.playing) {
            if (this.clockPlaying) this.clockBase += now - this.clockAnchor;
            this.clockAnchor = now;
            this.clockPlaying = info.playing;
        }
        info.positionMs = this.clockBase + (this.clockPlaying ? now - this.clockAnchor : 0L);
        info.capturedAt = now;
    }

    /** powershell.exe from the Windows directory (works even when PATH is broken), or an explicit override. */
    private static String powershellExecutable() {
        String override = System.getProperty(EXECUTABLE_PROPERTY, "").trim();
        if (!override.isEmpty()) return override;
        String root = System.getenv("SystemRoot");
        if (root != null && !root.isEmpty()) {
            File candidate = new File(root, "System32\\WindowsPowerShell\\v1.0\\powershell.exe");
            if (candidate.isFile()) return candidate.getAbsolutePath();
        }
        return "powershell.exe";
    }

    /**
     * The helper writes every cover version to its own file (cover.img.1, cover.img.2, ...) and reports it in
     * "artFile", so the game never reads a half-written file. Older helpers only sent a version number.
     */
    static String artworkUrl(JsonObject json, File thumb) {
        String file = str(json, "artFile", "");
        if (!file.isEmpty()) return new File(file).isFile() ? "file:" + file : "";
        long version = lng(json, "art", 0L);
        return version > 0L && thumb.isFile() ? "file:" + thumb.getAbsolutePath() + "?v=" + version : "";
    }

    private File extractScript() throws IOException {
        if (!this.directory.isDirectory() && !this.directory.mkdirs()) throw new IOException("cannot create " + this.directory);
        File script = new File(this.directory, "smtc.ps1");
        try (InputStream in = WindowsMediaBackend.class.getResourceAsStream(SCRIPT_RESOURCE)) {
            if (in == null) throw new IOException("script resource missing");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) != -1) buffer.write(chunk, 0, read);
            byte[] bytes = buffer.toByteArray();
            // Windows PowerShell 5.1 reads BOM-less files as ANSI; add a UTF-8 BOM so any non-ASCII is safe.
            try (FileOutputStream out = new FileOutputStream(script)) {
                out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
                out.write(bytes);
            }
        }
        return script;
    }

    private static void drain(InputStream stream) {
        Thread thread = new Thread(() -> {
            byte[] sink = new byte[1024];
            try {
                while (stream.read(sink) != -1) {
                    // discard
                }
            } catch (IOException ignored) {
            }
        }, "CrewX media stderr");
        thread.setDaemon(true);
        thread.start();
    }

    private static String str(JsonObject json, String key, String fallback) {
        JsonElement element = json.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsString();
    }

    private static long lng(JsonObject json, String key, long fallback) {
        try {
            JsonElement element = json.get(key);
            return element == null || element.isJsonNull() ? fallback : element.getAsLong();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        try {
            JsonElement element = json.get(key);
            return element == null || element.isJsonNull() ? fallback : element.getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
