package crewx.gui.menu;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class MainMenuLayoutTest {
    private static final float FONT = 9.0F;
    /** Scaled GUI sizes: 1080p@2, 720p@2, 1080p@3, 4:3, 16:10, ultrawide, a tiny window, 4K@4. */
    private static final float[][] SCREENS = {
            {960, 540}, {640, 360}, {640, 360}, {640, 480}, {840, 525}, {1720, 720}, {427, 240}, {960, 540}, {320, 240}
    };

    @Test
    public void nothingOverlapsOrLeavesTheScreen() {
        for (float[] s : SCREENS) {
            MainMenuLayout l = new MainMenuLayout(s[0], s[1], FONT);
            String at = (int) s[0] + "x" + (int) s[1];
            float clockBottom = l.clockTop + l.clockCap;
            float profileTop = l.profileCenterY - l.avatarSize / 2.0F;
            float profileBottom = l.profileCenterY + l.avatarSize / 2.0F;
            float buttonsTop = l.buttonCenterY - l.buttonRadius;
            assertTrue("clock above the profile " + at, clockBottom + 6.0F <= profileTop);
            assertTrue("profile above the buttons " + at, profileBottom + 6.0F <= buttonsTop);
            assertTrue("labels under the buttons " + at, l.buttonCenterY + l.buttonRadius <= l.labelY);
            assertTrue("labels inside the screen " + at, l.labelY + FONT <= s[1] - 4.0F);
            assertTrue("clock inside the screen " + at, l.clockTop >= 4.0F && l.clockCap >= 14.0F);
            float left = l.buttonCenterX(0, 3) - l.buttonRadius - 30.0F;
            float right = l.buttonCenterX(2, 3) + l.buttonRadius + 30.0F;
            assertTrue("button row (with labels) fits " + at, left >= 0.0F && right <= s[0]);
            assertTrue("small buttons inside " + at, l.smallCenterX[1] - l.smallRadius >= 0.0F
                    && l.smallCenterX[0] + l.smallRadius <= s[0] && l.smallCenterY - l.smallRadius >= 0.0F);
            assertTrue("small buttons do not touch the clock " + at,
                    l.smallCenterY + l.smallRadius < l.clockTop || l.smallCenterX[1] - l.smallRadius > s[0] / 2.0F + 120.0F);
        }
    }

    @Test
    public void proportionsFollowTheReferenceOnAFullHdScreen() {
        MainMenuLayout l = new MainMenuLayout(960, 540, FONT);
        assertTrue(l.clockCap > 70.0F && l.clockCap < 90.0F);          // big clock (~15 % of the height)
        assertTrue(l.clockTop > 60.0F && l.clockTop < 80.0F);          // high up
        assertTrue(l.buttonCenterY > 540 * 0.80F);                     // buttons near the bottom
        assertTrue(l.buttonSpacing > l.buttonRadius * 3.0F);           // well spaced
    }

    @Test
    public void buttonLabelsNeverTouch() {
        // label widths of "Singleplayer", "Multiplayer", "Alts" at the menu's text scale
        float widest = 56.0F;
        for (float[] s : SCREENS) {
            MainMenuLayout l = new MainMenuLayout(s[0], s[1], FONT, widest + 14.0F);
            assertTrue("labels apart at " + (int) s[0] + "x" + (int) s[1], l.buttonSpacing >= widest + 14.0F);
            float left = l.buttonCenterX(0, 3) - widest / 2.0F;
            float right = l.buttonCenterX(2, 3) + widest / 2.0F;
            assertTrue("row fits at " + (int) s[0] + "x" + (int) s[1], left >= 0.0F && right <= s[0]);
        }
    }
}
