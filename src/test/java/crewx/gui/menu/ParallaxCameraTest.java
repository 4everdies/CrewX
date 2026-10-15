package crewx.gui.menu;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ParallaxCameraTest {
    private static final float FRAME = 1.0F / 60.0F;

    @Test
    public void followsTheCursorGraduallyWithInertia() {
        ParallaxCamera camera = new ParallaxCamera();
        camera.update(1.0F, 0.0F, true, FRAME);
        assertTrue("first frame moves only a little", camera.x() > 0.0F && camera.x() < 0.1F);
        float previous = camera.x();
        for (int i = 0; i < 30; i++) {
            camera.update(1.0F, 0.0F, true, FRAME);
            assertTrue("never overshoots or jumps back", camera.x() >= previous && camera.x() <= 1.0F);
            previous = camera.x();
        }
        for (int i = 0; i < 600; i++) camera.update(1.0F, 0.0F, true, FRAME);
        assertEquals(1.0F, camera.x(), 1e-3F);
    }

    @Test
    public void frameRateDoesNotChangeTheMotion() {
        ParallaxCamera fast = new ParallaxCamera();
        ParallaxCamera slow = new ParallaxCamera();
        for (int i = 0; i < 144; i++) fast.update(0.8F, -0.5F, true, 1.0F / 144.0F);
        for (int i = 0; i < 30; i++) slow.update(0.8F, -0.5F, true, 1.0F / 30.0F);
        assertEquals(fast.x(), slow.x(), 1e-3F);
        assertEquals(fast.y(), slow.y(), 1e-3F);
    }

    @Test
    public void returnsToNeutralWhenTheCursorLeaves() {
        ParallaxCamera camera = new ParallaxCamera();
        for (int i = 0; i < 300; i++) camera.update(-1.0F, 1.0F, true, FRAME);
        for (int i = 0; i < 600; i++) camera.update(-1.0F, 1.0F, false, FRAME);
        assertEquals(0.0F, camera.x(), 1e-3F);
        assertEquals(0.0F, camera.y(), 1e-3F);
    }

    @Test
    public void zoomOnlyWhenLookingUpAndStaysSubtle() {
        ParallaxCamera camera = new ParallaxCamera();
        for (int i = 0; i < 600; i++) camera.update(0.0F, 1.0F, true, FRAME);
        assertEquals(1.0F, camera.zoom(), 1e-6F);
        for (int i = 0; i < 600; i++) camera.update(0.0F, -1.0F, true, FRAME);
        assertTrue(camera.zoom() > 1.02F && camera.zoom() <= 1.0F + ParallaxCamera.MAX_ZOOM + 1e-6F);
    }

    @Test
    public void layersAlwaysCoverTheScreen() {
        float[][] screens = {{960, 540}, {640, 480}, {1720, 720}, {427, 240}, {1280, 800}};
        float[] out = new float[4];
        for (float[] s : screens) {
            for (float cx = -1.0F; cx <= 1.0F; cx += 0.5F) {
                for (float cy = -1.0F; cy <= 1.0F; cy += 0.5F) {
                    ParallaxCamera camera = new ParallaxCamera();
                    for (int i = 0; i < 600; i++) camera.update(cx, cy, true, FRAME);
                    for (float depth : new float[]{0.28F, 0.62F, 1.0F}) {
                        camera.layer(s[0], s[1], 16.0F / 9.0F, depth, 1.0F, 0.5F, 0.47F, out);
                        assertTrue("left edge visible " + s[0] + "x" + s[1], out[0] <= 0.0F);
                        assertTrue("top edge visible", out[1] <= 0.0F);
                        assertTrue("right edge visible", out[0] + out[2] >= s[0]);
                        assertTrue("bottom edge visible", out[1] + out[3] >= s[1]);
                    }
                }
            }
        }
    }

    @Test
    public void nearLayersMoveMoreThanFarOnes() {
        ParallaxCamera camera = new ParallaxCamera();
        float[] center = new float[4];
        float[] near = new float[4];
        float[] far = new float[4];
        new ParallaxCamera().layer(960, 540, 16.0F / 9.0F, 1.0F, 0.0F, 0.5F, 0.5F, center);
        for (int i = 0; i < 600; i++) camera.update(1.0F, 0.0F, true, FRAME);
        camera.layer(960, 540, 16.0F / 9.0F, 1.0F, 0.0F, 0.5F, 0.5F, near);
        camera.layer(960, 540, 16.0F / 9.0F, 0.28F, 0.0F, 0.5F, 0.5F, far);
        float nearShift = Math.abs(near[0] - center[0]);
        float farShift = Math.abs(far[0] - center[0]);
        assertTrue(nearShift > farShift * 3.0F && farShift > 0.0F);
        assertTrue("moves against the cursor like a camera pan", near[0] < center[0]);
    }
}
