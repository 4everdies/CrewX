package crewx.module.modules.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TargetHudMathTest {
    @Test
    public void verdictComparesHealth() {
        assertEquals('W', TargetHudMath.verdict(20.0F, 10.0F));
        assertEquals('L', TargetHudMath.verdict(6.0F, 18.0F));
        assertEquals('D', TargetHudMath.verdict(20.0F, 20.0F));
        assertEquals('D', TargetHudMath.verdict(15.0F, 14.5F));
    }

    @Test
    public void healthBarDrainsSmoothlyInsteadOfJumping() {
        float shown = 20.0F;
        float previous = shown;
        int frames = 0;
        while (shown > 10.01F && frames < 600) {
            shown = TargetHudMath.animateHealth(shown, 10.0F, 1.0F / 60.0F);
            assertTrue("never goes up or past the target", shown <= previous && shown >= 10.0F);
            previous = shown;
            frames++;
        }
        assertTrue("takes several frames (visible animation), got " + frames, frames > 20);
        assertTrue("but finishes within ~1.5 s, got " + frames, frames < 90);
        assertEquals(12.0F, TargetHudMath.animateHealth(Float.NaN, 12.0F, 0.016F), 0.0F);  // new target: no drain
    }

    @Test
    public void fractionIsClamped() {
        assertEquals(0.5F, TargetHudMath.fraction(10.0F, 20.0F), 0.0001F);
        assertEquals(1.0F, TargetHudMath.fraction(30.0F, 20.0F), 0.0F);
        assertEquals(0.0F, TargetHudMath.fraction(-1.0F, 20.0F), 0.0F);
        assertEquals(0.0F, TargetHudMath.fraction(5.0F, 0.0F), 0.0F);
    }
}
