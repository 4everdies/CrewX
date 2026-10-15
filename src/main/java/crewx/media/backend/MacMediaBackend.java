package crewx.media.backend;

import crewx.media.MediaAction;
import crewx.media.MediaInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * macOS: Spotify and Music apps through AppleScript (browsers are not exposed to scripts on macOS). The app
 * that is playing wins over a paused one. macOS asks once for "Automation" permission to control each app.
 */
public final class MacMediaBackend extends PollingBackend {
    private static final String SEP = "\u001f";
    private static final String[] READ_SCRIPT = {
            "set sep to ASCII character 31",
            "set spotifyLine to \"\"",
            "set spotifyPlaying to false",
            "if application \"Spotify\" is running then",
            "tell application \"Spotify\"",
            "if player state is not stopped then",
            "set spotifyPlaying to (player state is playing)",
            "set spotifyLine to \"Spotify\" & sep & (player state as string) & sep & (name of current track) & sep & (artist of current track) & sep & (album of current track) & sep & (duration of current track) & sep & (player position * 1000) & sep & (artwork url of current track)",
            "end if",
            "end tell",
            "end if",
            "if spotifyPlaying then return spotifyLine",
            "set musicLine to \"\"",
            "if application \"Music\" is running then",
            "tell application \"Music\"",
            "if player state is not stopped then",
            "set musicLine to \"Music\" & sep & (player state as string) & sep & (name of current track) & sep & (artist of current track) & sep & (album of current track) & sep & ((duration of current track) * 1000) & sep & ((player position) * 1000) & sep & \"\"",
            "if player state is playing then return musicLine",
            "end if",
            "end tell",
            "end if",
            // nothing playing: show whichever app is paused (Spotify first)
            "if spotifyLine is not \"\" then return spotifyLine",
            "return musicLine"
    };

    private volatile String activeApp = "Spotify";

    public MacMediaBackend() {
        super(1500L);
    }

    @Override
    MediaInfo poll() throws Exception {
        List<String> command = new ArrayList<>();
        command.add("osascript");
        for (String line : READ_SCRIPT) {
            command.add("-e");
            command.add(line);
        }
        Exec.Output output = Exec.run(command, 3000L);
        if (output.exitCode != 0) throw new IllegalStateException("osascript failed");
        String text = output.text.trim();
        if (text.isEmpty()) return null;
        MediaInfo info = parse(text, System.currentTimeMillis());
        if (info != null) this.activeApp = info.source;
        return info;
    }

    /** Exposed for tests. */
    static MediaInfo parse(String line, long now) {
        String[] f = line.split(SEP, -1);
        if (f.length < 8 || f[2].trim().isEmpty()) return null;
        MediaInfo.Builder info = MediaInfo.builder();
        info.source = f[0].trim();
        info.playing = f[1].toLowerCase(java.util.Locale.ROOT).contains("playing");
        info.title = f[2].trim();
        info.artist = f[3].trim();
        info.album = f[4].trim();
        info.durationMs = number(f[5]);
        info.positionMs = number(f[6]);
        info.artworkUrl = f[7].trim();
        info.key = info.source + "|" + info.title + "|" + info.artist;
        info.capturedAt = now;
        info.canSeek = info.durationMs > 0L;
        return info.build();
    }

    private static long number(String text) {
        try {
            return (long) Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    @Override
    void execute(MediaAction action, long argument) {
        String app = this.activeApp;
        String statement;
        switch (action) {
            case PLAY: statement = "play"; break;
            case PAUSE: statement = "pause"; break;
            case TOGGLE: statement = "playpause"; break;
            case NEXT: statement = "next track"; break;
            case PREVIOUS: statement = "previous track"; break;
            case SEEK: statement = "set player position to " + Math.max(0L, argument) / 1000.0D; break;
            default: return;
        }
        try {
            List<String> command = new ArrayList<>();
            command.add("osascript");
            command.add("-e");
            command.add("tell application \"" + app + "\" to " + statement.replace(',', '.'));
            Exec.run(command, 3000L);
        } catch (Exception ignored) {
        }
    }

    @Override
    String unavailableMessage() {
        return "System media on macOS needs the Spotify or Music app (allow automation if asked)";
    }
}
