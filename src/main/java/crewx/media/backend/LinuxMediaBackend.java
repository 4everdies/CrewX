package crewx.media.backend;

import crewx.media.MediaAction;
import crewx.media.MediaInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Linux: any MPRIS player (Spotify, browsers, VLC...) through the "playerctl" command. All players are read
 * and the one that is actually playing wins, so a paused browser tab cannot hide Spotify (or the reverse).
 */
public final class LinuxMediaBackend extends PollingBackend {
    private static final String SEP = "\u001f";
    private static final String FORMAT = String.join(SEP, "{{status}}", "{{title}}", "{{artist}}", "{{album}}",
            "{{mpris:length}}", "{{position}}", "{{mpris:artUrl}}", "{{playerName}}");

    /** MPRIS name of the player shown on the card; controls go to it. */
    private volatile String activePlayer = "";

    public LinuxMediaBackend() {
        super(1000L);
    }

    @Override
    MediaInfo poll() throws Exception {
        Exec.Output output = Exec.run(Arrays.asList("playerctl", "--all-players", "metadata", "--format", FORMAT), 2500L);
        // exit code 1 + "No players found" just means nothing is open; a missing binary throws instead
        if (output.exitCode != 0 || output.text.trim().isEmpty()) {
            this.activePlayer = "";
            return null;
        }
        String line = pickLine(output.text);
        MediaInfo info = line == null ? null : parse(line, System.currentTimeMillis());
        this.activePlayer = line == null ? "" : line.split(SEP, -1)[7].trim();
        return info;
    }

    /** One line per player: the first one that is playing, otherwise the first paused one with a title. */
    static String pickLine(String output) {
        String fallback = null;
        for (String line : output.split("\n")) {
            String[] f = line.split(SEP, -1);
            if (f.length < 8 || f[1].trim().isEmpty()) continue;
            if ("Playing".equalsIgnoreCase(f[0].trim())) return line;
            if (fallback == null && "Paused".equalsIgnoreCase(f[0].trim())) fallback = line;
        }
        return fallback;
    }

    /** Exposed for tests. */
    static MediaInfo parse(String line, long now) {
        String[] f = line.split(SEP, -1);
        if (f.length < 8 || f[1].trim().isEmpty()) return null;
        MediaInfo.Builder info = MediaInfo.builder();
        info.playing = "Playing".equalsIgnoreCase(f[0].trim());
        info.title = f[1].trim();
        info.artist = f[2].trim();
        info.album = f[3].trim();
        info.durationMs = micros(f[4]) / 1000L;
        info.positionMs = micros(f[5]) / 1000L;
        info.artworkUrl = f[6].trim();
        info.source = MediaBackend.friendlyName(f[7].trim());
        info.key = f[7].trim() + "|" + info.title + "|" + info.artist;
        info.capturedAt = now;
        info.canSeek = info.durationMs > 0L;
        return info.build();
    }

    private static long micros(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    @Override
    void execute(MediaAction action, long argument) {
        List<String> command = new ArrayList<>();
        command.add("playerctl");
        String player = this.activePlayer;
        if (!player.isEmpty()) {
            command.add("--player");
            command.add(player);
        }
        switch (action) {
            case PLAY: command.add("play"); break;
            case PAUSE: command.add("pause"); break;
            case TOGGLE: command.add("play-pause"); break;
            case NEXT: command.add("next"); break;
            case PREVIOUS: command.add("previous"); break;
            case SEEK:
                command.add("position");
                command.add(String.format(java.util.Locale.ROOT, "%.3f", Math.max(0L, argument) / 1000.0D));
                break;
            default: return;
        }
        try {
            Exec.run(command, 2500L);
        } catch (Exception ignored) {
        }
    }

    @Override
    String unavailableMessage() {
        return "Install 'playerctl' to detect system media on Linux";
    }
}
