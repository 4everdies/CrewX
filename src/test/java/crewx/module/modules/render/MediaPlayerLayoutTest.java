package crewx.module.modules.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MediaPlayerLayoutTest {
    private static final float EPS = 0.001F;

    @Test
    public void hundredPercentKeepsTheConfiguredWidth() {
        assertEquals(290.0F, MediaPlayerLayout.width(290, 100, 960, 540, true), EPS);
    }

    @Test
    public void sliderScalesGraduallyAndMonotonically() {
        float previous = 0.0F;
        for (int percent = 50; percent <= 200; percent++) {
            float width = MediaPlayerLayout.width(290, percent, 1920, 1080, true);
            assertTrue("width must never shrink when the slider grows", width >= previous);
            previous = width;
        }
        assertEquals(MediaPlayerLayout.MIN_WIDTH, MediaPlayerLayout.width(290, 50, 1920, 1080, true), EPS);
        assertEquals(580.0F, MediaPlayerLayout.width(290, 200, 1920, 1080, true), EPS);
        assertEquals(435.0F, MediaPlayerLayout.width(290, 150, 1920, 1080, true), EPS);
    }

    @Test
    public void neverSmallerThanTheReadableMinimum() {
        assertEquals(MediaPlayerLayout.MIN_WIDTH, MediaPlayerLayout.width(180, 50, 960, 540, true), EPS);
    }

    @Test
    public void staysInsideTheScreenAtTheMaximum() {
        float screenW = 640, screenH = 360; // 1920x1080 at GUI scale 3
        float width = MediaPlayerLayout.width(460, 200, screenW, screenH, true);
        float totalHeight = width * (MediaPlayerLayout.CARD_RATIO + MediaPlayerLayout.LYRICS_RATIO);
        assertTrue(width <= screenW * 0.7F + EPS);
        assertTrue(totalHeight <= screenH - 16.0F + EPS);
        // without lyrics the card may be wider, still within the screen
        float noLyrics = MediaPlayerLayout.width(460, 200, screenW, screenH, false);
        assertTrue(noLyrics >= width && noLyrics <= screenW * 0.7F + EPS);
    }

    @Test
    public void tinyScreenReportsThatTheCardCannotFit() {
        assertTrue(MediaPlayerLayout.width(290, 100, 180, 120, true) < MediaPlayerLayout.MIN_WIDTH);
    }

    @Test
    public void cardOnlyShowsWhileMusicPlays() {
        assertTrue(MediaPlayerLayout.shouldShow(true, true, 0L, false));
        assertTrue(!MediaPlayerLayout.shouldShow(false, false, 0L, false));       // nothing detected
        assertTrue(!MediaPlayerLayout.shouldShow(false, false, 0L, true));        // not even with chat open
        assertTrue(MediaPlayerLayout.shouldShow(true, false, 1000L, false));      // short grace after pause
        assertTrue(!MediaPlayerLayout.shouldShow(true, false, 10000L, false));    // paused for a while: hidden
        assertTrue(MediaPlayerLayout.shouldShow(true, false, 10000L, true));      // chat open: can resume/move
    }
}
