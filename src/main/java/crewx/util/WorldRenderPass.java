package crewx.util;

/**
 * Makes the 3D render event (ESP boxes, NameTags projection, Tracers...) run exactly once per frame, at the first
 * point of the world pass where the GL matrices are still the world camera.
 *
 * The event is hooked on the read of EntityRenderer.renderHand inside renderWorldPass. Vanilla/Forge read that field
 * once, but OptiFine-based clients (Lunar, Badlion, OptiFine on Forge...) read it more than once: the later reads come
 * after the first-person hand was drawn, and drawing the hand loads its own projection and model-view matrices
 * (different FOV, hand bobbing, no camera rotation). Running the 3D modules there again captured those matrices for
 * the projection and drew the boxes with them, which is what made ESP and NameTags shake and jump around in those
 * clients. Only the first call of each frame is accepted now.
 *
 * It also fixes the partial tick of the frame: the value the client passed to renderWorldPass (the one the camera and
 * the world were drawn with) is used for every entity interpolation, instead of re-reading Minecraft's timer, which
 * some clients do not keep equal to it.
 */
public final class WorldRenderPass {
    private static boolean fired;
    private static long firedAt;
    private static float partialTicks = Float.NaN;
    private static long skipped;

    private WorldRenderPass() {
    }

    /** A new frame starts (beginning of updateCameraAndRender, or once the HUD of the frame was drawn). */
    public static void frameBegin() {
        fired = false;
    }

    /**
     * Called by the world-pass hook; returns false when the 3D event already ran in this frame. A frame marker that
     * never arrives (a client without the hooked methods) only lowers the 3D rate to 4 per second instead of
     * stopping it.
     */
    public static boolean begin(float framePartialTicks) {
        long now = System.nanoTime();
        if (fired && now - firedAt < 250_000_000L) {
            skipped++;
            return false;
        }
        fired = true;
        firedAt = now;
        partialTicks = framePartialTicks;
        return true;
    }

    /** Partial tick the world of this frame is drawn with (Minecraft's timer until the first world pass). */
    public static float partialTicks() {
        float value = partialTicks;
        if (value == value && value >= 0.0F && value <= 1.0F) return value;
        try {
            return ((crewx.mixin.IAccessorMinecraft) net.minecraft.client.Minecraft.getMinecraft()).getTimer().renderPartialTicks;
        } catch (Throwable failure) {
            return 1.0F;
        }
    }

    /** Extra world-pass calls ignored so far (shows up in the injector log). */
    public static long skipped() {
        return skipped;
    }
}
