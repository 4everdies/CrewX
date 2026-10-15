package crewx.util;

import net.minecraft.client.renderer.ActiveRenderInfo;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * The world camera of the current frame, for projecting points onto the screen without asking the driver.
 *
 * Minecraft already reads the modelview, projection and viewport once per frame, right after setting up the world
 * camera (ActiveRenderInfo.updateRenderInfo). Reusing those copies avoids extra glGet calls, each of which makes the
 * CPU wait for the GPU to catch up - harmless when the GPU is idle, a visible hitch when it is busy.
 */
public final class CameraMatrices {
    private static FloatBuffer modelView;
    private static FloatBuffer projection;
    private static IntBuffer viewport;
    private static FloatBuffer modelRead;
    private static FloatBuffer projectionRead;
    private static IntBuffer viewportRead;
    private static boolean unavailable;
    private static final FloatBuffer RESULT = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer worldModel = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer worldProjection = BufferUtils.createFloatBuffer(16);
    private static final IntBuffer worldViewport = BufferUtils.createIntBuffer(16);
    private static final FloatBuffer worldModelRead = worldModel.duplicate();
    private static final FloatBuffer worldProjectionRead = worldProjection.duplicate();
    private static final IntBuffer worldViewportRead = worldViewport.duplicate();
    private static boolean worldCaptured;

    private CameraMatrices() {
    }

    /** Read the actual world camera once per world pass; Lunar can leave ActiveRenderInfo's copy a frame behind. */
    public static void captureWorldPass() {
        try {
            worldModel.clear();
            worldProjection.clear();
            worldViewport.clear();
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, worldModel);
            GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, worldProjection);
            GL11.glGetInteger(GL11.GL_VIEWPORT, worldViewport);
            worldCaptured = true;
        } catch (Throwable failure) {
            worldCaptured = false;
        }
    }

    private static boolean resolve() {
        if (modelView != null) return true;
        if (unavailable) return false;
        try {
            modelView = (FloatBuffer) field("MODELVIEW", "field_178812_b").get(null);
            projection = (FloatBuffer) field("PROJECTION", "field_178813_c").get(null);
            viewport = (IntBuffer) field("VIEWPORT", "field_178814_a").get(null);
            if (modelView == null || projection == null || viewport == null) return false;
            modelRead = modelView.duplicate();
            projectionRead = projection.duplicate();
            viewportRead = viewport.duplicate();
            return true;
        } catch (Throwable failure) {
            unavailable = true;
            modelView = null;
            crewx.util.ClientLog.log("CameraMatrices fields unavailable: " + failure);
            return false;
        }
    }

    private static Field field(String named, String srg) throws NoSuchFieldException {
        String owner = "net.minecraft.client.renderer.ActiveRenderInfo";
        for (String candidate : new String[]{named, srg}) {
            try {
                Field field = ActiveRenderInfo.class.getDeclaredField(candidate);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Try the next naming scheme used by the active client.
            }
        }
        throw new NoSuchFieldException(owner + "." + named);
    }

    /**
     * Projects a camera-relative point (world position minus the render position) to window pixels, y up; returns
     * {x, y, depth} in a reused array, or null when it is behind the camera or the matrices are not available.
     */
    public static float[] project(double x, double y, double z, float[] out) {
        FloatBuffer model;
        FloatBuffer projectionBuffer;
        IntBuffer viewportBuffer;
        if (worldCaptured) {
            model = worldModelRead;
            projectionBuffer = worldProjectionRead;
            viewportBuffer = worldViewportRead;
        } else {
            if (modelView == null && !resolve()) return null;
            model = modelRead;
            projectionBuffer = projectionRead;
            viewportBuffer = viewportRead;
        }
        model.clear();
        projectionBuffer.clear();
        viewportBuffer.clear();
        RESULT.clear();
        if (!GLU.gluProject((float) x, (float) y, (float) z,
                model, projectionBuffer, viewportBuffer, RESULT)) return null;
        float depth = RESULT.get(2);
        if (depth < 0.0F || depth >= 1.0F) return null;
        out[0] = RESULT.get(0);
        out[1] = RESULT.get(1);
        out[2] = depth;
        return out;
    }
}
