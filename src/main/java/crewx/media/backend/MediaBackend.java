package crewx.media.backend;

import crewx.media.MediaAction;
import crewx.media.MediaInfo;

import java.util.function.Consumer;

/** One OS-specific way of reading the "now playing" session of the system. */
public interface MediaBackend {
    /** {@code sink} receives null when nothing is playing; {@code status} receives short diagnostics. */
    void start(Consumer<MediaInfo> sink, Consumer<String> status);

    void stop();

    void send(MediaAction action, long argument);

    void refresh();

    /** Maps an app id / player name to a short label ("Spotify", "Chrome", ...). */
    static String friendlyName(String app) {
        if (app == null || app.isEmpty()) return "System";
        String lower = app.toLowerCase(java.util.Locale.ROOT);
        String[][] known = {
                {"spotify", "Spotify"}, {"chrome", "Chrome"}, {"msedge", "Edge"}, {"edge", "Edge"},
                {"firefox", "Firefox"}, {"opera", "Opera"}, {"brave", "Brave"}, {"vivaldi", "Vivaldi"},
                {"applemusic", "Apple Music"}, {"itunes", "iTunes"}, {"vlc", "VLC"}, {"musicbee", "MusicBee"},
                {"foobar", "foobar2000"}, {"tidal", "TIDAL"}, {"deezer", "Deezer"}, {"youtube", "YouTube"}
        };
        for (String[] entry : known) if (lower.contains(entry[0])) return entry[1];
        String cleaned = app.replaceAll("(?i)\\.exe$", "");
        int bang = cleaned.indexOf('!');
        if (bang > 0) cleaned = cleaned.substring(0, bang);
        return cleaned;
    }
}
