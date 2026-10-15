package crewx.util.notifications;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ToggleAnimationTest {
    @Test
    public void enableMovesKnobFromLeftToRight() {
        assertEquals(0.0F, ToggleAnimation.knob(0L, true), 0.0F);
        float mid = ToggleAnimation.knob(ToggleAnimation.KNOB_DELAY_MS + ToggleAnimation.KNOB_MS / 2, true);
        assertTrue(mid > 0.2F && mid < 1.0F);
        assertEquals(1.0F, ToggleAnimation.knob(2000L, true), 0.0F);
    }

    @Test
    public void disableMovesKnobFromRightToLeft() {
        assertEquals(1.0F, ToggleAnimation.knob(0L, false), 0.0F);
        assertEquals(0.0F, ToggleAnimation.knob(2000L, false), 0.0F);
    }

    @Test
    public void cardSlidesInStaysAndSlidesOut() {
        long display = 2500L;
        assertEquals(0.0F, ToggleAnimation.appear(0L, display), 0.0F);
        assertEquals(1.0F, ToggleAnimation.appear(1000L, display), 0.0F);
        float leaving = ToggleAnimation.appear(display + ToggleAnimation.SLIDE_MS / 2, display);
        assertTrue(leaving > 0.0F && leaving < 1.0F);
        assertFalse(ToggleAnimation.finished(display, display));
        assertTrue(ToggleAnimation.finished(display + ToggleAnimation.SLIDE_MS, display));
    }

    @Test
    public void knobFinishesBeforeTheCardLeaves() {
        assertTrue(ToggleAnimation.KNOB_DELAY_MS + ToggleAnimation.KNOB_MS < 2500L);
    }
}
