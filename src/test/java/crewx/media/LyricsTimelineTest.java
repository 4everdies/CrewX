package crewx.media;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LyricsTimelineTest {
    private static Lyrics synced(String lrc) {
        return new Lyrics("k", Lyrics.State.FOUND, LrcParser.parseSynced(lrc), true);
    }

    private static Lyrics plain(String text) {
        return new Lyrics("k", Lyrics.State.FOUND, LrcParser.parsePlain(text), false);
    }

    @Test
    public void syncedShowsExactlyTheLineForTheCurrentMoment() {
        LyricsTimeline t = LyricsTimeline.of(synced("[00:10.00] first\n[00:14.50] second\n[00:20.00] third"), 60000L);
        assertEquals(-1, t.indexAt(9000L));                // intro: nothing yet
        assertEquals("first", t.lines().get(t.indexAt(10000L)).text);
        assertEquals("first", t.lines().get(t.indexAt(14499L)).text);
        assertEquals("second", t.lines().get(t.indexAt(14500L)).text);
        assertEquals("third", t.lines().get(t.indexAt(21000L)).text);
        assertFalse(t.isEstimated());
    }

    @Test
    public void longInstrumentalGapHidesTheFinishedLine() {
        LyricsTimeline t = LyricsTimeline.of(synced("[00:10.00] hi\n[01:00.00] back"), 120000L);
        assertEquals(0, t.indexAt(12000L));
        assertEquals(-1, t.indexAt(30000L));                // long break: no stale line on screen
        assertEquals(1, t.indexAt(60000L));
    }

    @Test
    public void syncedRepeatsKeepTheirTimestampsAndAreRecognisedAsSameText() {
        LyricsTimeline t = LyricsTimeline.of(synced("[00:10.00] la la\n[00:30.00] La  la\n[00:40.00] end"), 60000L);
        assertEquals(3, t.lines().size());
        assertEquals(1, t.indexAt(31000L));                 // the repeat is current while it is sung
        assertTrue(t.sameText(0, 1));                       // so the card does not animate a "new" line
        assertFalse(t.sameText(1, 2));
    }

    @Test
    public void sweepProgressRunsFromZeroToOne() {
        LyricsTimeline t = LyricsTimeline.of(synced("[00:10.00] abc\n[00:20.00] next"), 60000L);
        assertEquals(0.0F, t.progress(0, 10000L, 60000L), 0.001F);
        assertTrue(t.progress(0, 10400L, 60000L) > 0.0F && t.progress(0, 10400L, 60000L) < 1.0F);
        assertEquals(1.0F, t.progress(0, 19000L, 60000L), 0.001F);
    }

    @Test
    public void plainLyricsAreShownOneLineAtATimeAcrossTheSong() {
        LyricsTimeline t = LyricsTimeline.of(plain("one\ntwo\nthree\nfour"), 100000L);
        assertTrue(t.isEstimated());
        assertEquals(-1, t.indexAt(1000L));                 // intro
        int last = -1;
        for (long ms = 8000L; ms <= 92000L; ms += 500L) {
            int index = t.indexAt(ms);
            assertTrue("never jumps back or skips", index == last || index == last + 1);
            last = index;
        }
        assertEquals(3, last);                              // every line was shown, the last one near the end
        assertEquals(1.0F, t.progress(2, 50000L, 100000L), 0.0F);
    }

    @Test
    public void plainConsecutiveDuplicatesAreMerged() {
        List<LyricLine> lines = LyricsTimeline.of(plain("Yeah\nyeah\nOh\nYeah"), 60000L).lines();
        assertEquals(Arrays.asList("Yeah", "Oh", "Yeah"), Arrays.asList(lines.get(0).text, lines.get(1).text, lines.get(2).text));
    }

    @Test
    public void plainWithoutDurationStillAdvances() {
        LyricsTimeline t = LyricsTimeline.of(plain("a\nb"), 0L);
        assertEquals(0, t.indexAt(0L));
        assertEquals(1, t.indexAt(60000L));
    }

    @Test
    public void notFoundOrEmptyGivesNothing() {
        assertTrue(LyricsTimeline.of(Lyrics.NONE, 1000L).lines().isEmpty());
        assertEquals(-1, LyricsTimeline.of(Lyrics.NONE, 1000L).indexAt(500L));
    }

    @Test
    public void placeholderLyricsFromLrclibAreRejected() {
        assertFalse(LyricsService.plausible(1, 214.0D));   // "[00:00.00]probe" on a 3.5 min song
        assertFalse(LyricsService.plausible(0, 30.0D));
        assertTrue(LyricsService.plausible(1, 45.0D));     // a short jingle may really have one line
        assertTrue(LyricsService.plausible(40, 214.0D));
    }
}
