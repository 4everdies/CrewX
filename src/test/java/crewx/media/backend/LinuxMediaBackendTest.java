package crewx.media.backend;

import crewx.media.MediaInfo;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LinuxMediaBackendTest {
    private static final String S = "\u001f";

    private static String line(String status, String title, String artist, String player) {
        return String.join(S, status, title, artist, "Album", "180000000", "42000000", "https://i.scdn.co/x", player);
    }

    @Test
    public void parsesPlayerctlLine() {
        MediaInfo info = LinuxMediaBackend.parse(line("Playing", "Song", "Artist", "spotify"), 1000L);
        assertNotNull(info);
        assertTrue(info.playing);
        assertEquals("Song", info.title);
        assertEquals("Artist", info.artist);
        assertEquals(180000L, info.durationMs);
        assertEquals(42000L, info.positionMs);
        assertEquals("Spotify", info.source);
        assertEquals("https://i.scdn.co/x", info.artworkUrl);
        assertTrue(info.canSeek);
    }

    @Test
    public void playingPlayerWinsOverPausedOneListedFirst() {
        String output = line("Paused", "Old tab", "Someone", "chromium") + "\n"
                + line("Playing", "SoundCloud song", "Artist", "firefox") + "\n"
                + line("Paused", "Spotify song", "Other", "spotify");
        assertEquals("SoundCloud song", LinuxMediaBackend.parse(LinuxMediaBackend.pickLine(output), 0L).title);
    }

    @Test
    public void fallsBackToFirstPausedPlayer() {
        String output = line("Stopped", "", "", "vlc") + "\n" + line("Paused", "Paused song", "A", "spotify");
        MediaInfo info = LinuxMediaBackend.parse(LinuxMediaBackend.pickLine(output), 0L);
        assertEquals("Paused song", info.title);
        assertFalse(info.playing);
    }

    @Test
    public void stoppedOrEmptyPlayersMeanNothingPlaying() {
        assertNull(LinuxMediaBackend.pickLine(line("Stopped", "Song", "A", "spotify")));
        assertNull(LinuxMediaBackend.pickLine(line("Playing", "", "", "chromium")));
        assertNull(LinuxMediaBackend.pickLine(""));
    }
}
