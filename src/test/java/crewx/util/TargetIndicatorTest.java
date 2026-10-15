package crewx.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TargetIndicatorTest {
    @Test
    public void ringTravelsFromFeetToHeadAndBack() {
        long period = (long) TargetIndicator.PERIOD_MS;
        assertEquals(0.0D, TargetIndicator.heightFraction(0L), 1e-9);
        assertEquals(1.0D, TargetIndicator.heightFraction(period / 2), 1e-9);
        assertEquals(0.0D, TargetIndicator.heightFraction(period), 1e-9);
        for (long t = 0; t < period * 3; t += 7) {
            double h = TargetIndicator.heightFraction(t);
            assertTrue(h >= 0.0D && h <= 1.0D);
        }
    }

    @Test
    public void goesUpThenDown() {
        long period = (long) TargetIndicator.PERIOD_MS;
        assertTrue(TargetIndicator.verticalSpeed(period / 4) > 0.0D);
        assertTrue(TargetIndicator.verticalSpeed(period * 3 / 4) < 0.0D);
    }
}
