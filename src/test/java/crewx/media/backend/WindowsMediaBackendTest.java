package crewx.media.backend;

import com.google.gson.JsonObject;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WindowsMediaBackendTest {
    @Test
    public void usesTheVersionedCoverFileReportedByTheHelper() throws Exception {
        File cover = File.createTempFile("cover", ".img.2");
        cover.deleteOnExit();
        JsonObject json = new JsonObject();
        json.addProperty("art", 2);
        json.addProperty("artFile", cover.getAbsolutePath());
        assertEquals("file:" + cover.getAbsolutePath(), WindowsMediaBackend.artworkUrl(json, new File("missing")));
    }

    @Test
    public void noCoverWhenTheReportedFileIsGoneOrMissing() {
        JsonObject json = new JsonObject();
        json.addProperty("art", 0);
        json.addProperty("artFile", "");
        assertEquals("", WindowsMediaBackend.artworkUrl(json, new File("missing")));
        json.addProperty("artFile", new File("does-not-exist.img.9").getAbsolutePath());
        assertEquals("", WindowsMediaBackend.artworkUrl(json, new File("missing")));
    }

    private static crewx.media.MediaInfo.Builder untimed(String title, boolean playing) {
        crewx.media.MediaInfo.Builder info = crewx.media.MediaInfo.builder();
        info.title = title;
        info.key = "chrome|" + title;
        info.playing = playing;
        return info;
    }

    @Test
    public void countsTimeItselfForPlayersWithoutATimeline() throws Exception {
        WindowsMediaBackend backend = new WindowsMediaBackend(new File("unused"));
        crewx.media.MediaInfo.Builder first = untimed("Song", true);
        backend.applyOwnClock(first);
        assertTrue(first.positionMs < 50L);
        Thread.sleep(300L);
        crewx.media.MediaInfo.Builder later = untimed("Song", true);
        backend.applyOwnClock(later);
        assertTrue("position advanced: " + later.positionMs, later.positionMs >= 280L && later.positionMs < 600L);
        // paused: the clock stops
        crewx.media.MediaInfo.Builder paused = untimed("Song", false);
        backend.applyOwnClock(paused);
        long at = paused.positionMs;
        Thread.sleep(200L);
        crewx.media.MediaInfo.Builder stillPaused = untimed("Song", false);
        backend.applyOwnClock(stillPaused);
        assertTrue(Math.abs(stillPaused.positionMs - at) < 30L);
        // another song starts from zero
        crewx.media.MediaInfo.Builder next = untimed("Other", true);
        backend.applyOwnClock(next);
        assertTrue(next.positionMs < 50L);
    }

    @Test
    public void leavesRealTimelinesAlone() {
        WindowsMediaBackend backend = new WindowsMediaBackend(new File("unused"));
        crewx.media.MediaInfo.Builder info = untimed("Song", true);
        info.durationMs = 200000L;
        info.positionMs = 42000L;
        info.capturedAt = 123L;
        backend.applyOwnClock(info);
        assertEquals(42000L, info.positionMs);
        assertEquals(123L, info.capturedAt);
    }
}
