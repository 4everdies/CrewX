package crewx.util;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * Keeps {@link GlStateManager}'s cache honest after {@code glPopAttrib}.
 *
 * GlStateManager skips any call whose value matches what it last set (bindTexture, enableTexture2D, enableBlend...).
 * glPopAttrib puts the driver back to the pushed state behind its back, so when vanilla code ran between push and
 * pop (rendering an entity binds its skin, toggles the lightmap, culling, alpha...) the cache keeps the values from
 * inside the block. Later calls are then skipped although the driver is in another state: a GUI icon is drawn with
 * whatever texture is really bound (the font atlas), "untextured" rectangles sample a texture, and so on - the
 * random garbled ClickGUI images. {@link #afterPopAttrib()} re-applies the real driver state through
 * GlStateManager, which either finds it already cached or updates its cache to match.
 */
public final class GlStateSync {
    private static final FloatBuffer FLOATS = BufferUtils.createFloatBuffer(16);
    private static final ByteBuffer BYTES = BufferUtils.createByteBuffer(16);

    private GlStateSync() {
    }

    // ------------------------------------------------------------------ snapshot of GlStateManager's cache

    /** Every non-final primitive field of GlStateManager's state, with the object that holds it (null = static). */
    private static Object[] holders;
    private static java.lang.reflect.Field[] fields;
    private static char[] kinds;   // 'Z' boolean, 'I' int, 'F' float, 'D' double
    private static boolean snapshotUnavailable;
    /** GlStateManager's vanilla state fields in MCP, SRG and 1.8.9 notch names. */
    private static final java.util.Set<String> STATE_FIELDS = new java.util.HashSet<String>(java.util.Arrays.asList(
            "alphaState", "field_179160_a", "lightingState", "field_179158_b", "lightState", "field_179159_c",
            "colorMaterialState", "field_179156_d", "blendState", "field_179157_e", "depthState", "field_179154_f",
            "fogState", "field_179155_g", "cullState", "field_179167_h", "polygonOffsetState", "field_179168_i",
            "colorLogicState", "field_179165_j", "texGenState", "field_179166_k", "clearState", "field_179163_l",
            "stencilState", "field_179164_m", "normalizeState", "field_179161_n", "activeTextureUnit", "field_179162_o",
            "textureState", "field_179174_p", "activeShadeModel", "field_179173_q", "rescaleNormalState", "field_179172_r",
            "colorMaskState", "field_179171_s", "colorState", "field_179170_t",
            "a", "b", "c", "d", "e", "f", "g", "h", "i", "j",
            "k", "l", "m", "n", "o", "p", "q", "r", "s", "t"));

    /**
     * Copies GlStateManager's cached state. Call right before a {@code glPushAttrib(GL_ALL_ATTRIB_BITS)} and hand
     * the result to {@link #afterPopAttrib(double[])}.
     *
     * After the pop the driver is back to exactly the state it had at the push - which is what GlStateManager's
     * cache said at that moment - so putting the copy back makes the cache right again without asking the driver
     * anything. The old way (still the fallback) read ~24 values back with glGet/glIsEnabled; every such query
     * makes the CPU wait for the GPU to finish its queued work, which showed up as short freezes every time the
     * GPU was busy (walking into new chunks) while ESP outline, Backtrack's box or a script drew.
     */
    public static double[] snapshot() {
        if (!resolve()) return null;
        double[] values = new double[fields.length];
        try {
            for (int i = 0; i < fields.length; i++) {
                java.lang.reflect.Field field = fields[i];
                Object holder = holders[i];
                switch (kinds[i]) {
                    case 'Z': values[i] = field.getBoolean(holder) ? 1.0 : 0.0; break;
                    case 'I': values[i] = field.getInt(holder); break;
                    case 'F': values[i] = field.getFloat(holder); break;
                    default: values[i] = field.getDouble(holder); break;
                }
            }
            return values;
        } catch (Throwable failure) {
            snapshotUnavailable = true;
            return null;
        }
    }

    /** Call right after the glPopAttrib(GL_ALL_ATTRIB_BITS) matching {@link #snapshot()}; null falls back to querying. */
    public static void afterPopAttrib(double[] snapshot) {
        if (snapshot == null || fields == null || snapshot.length != fields.length) {
            afterPopAttrib();
            return;
        }
        try {
            for (int i = 0; i < fields.length; i++) {
                java.lang.reflect.Field field = fields[i];
                Object holder = holders[i];
                switch (kinds[i]) {
                    case 'Z': field.setBoolean(holder, snapshot[i] != 0.0); break;
                    case 'I': field.setInt(holder, (int) snapshot[i]); break;
                    case 'F': field.setFloat(holder, (float) snapshot[i]); break;
                    default: field.setDouble(holder, snapshot[i]); break;
                }
            }
        } catch (Throwable failure) {
            snapshotUnavailable = true;
            afterPopAttrib();
        }
    }

    private static boolean resolve() {
        if (fields != null) return true;
        if (snapshotUnavailable) return false;
        try {
            java.util.List<Object> holderList = new java.util.ArrayList<Object>();
            java.util.List<java.lang.reflect.Field> fieldList = new java.util.ArrayList<java.lang.reflect.Field>();
            StringBuilder kindList = new StringBuilder();
            for (java.lang.reflect.Field field : GlStateManager.class.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                if (!STATE_FIELDS.contains(field.getName())) continue;   // vanilla state only (OptiFine adds its own)
                field.setAccessible(true);
                collect(null, field, holderList, fieldList, kindList, 0);
            }
            if (fieldList.isEmpty()) throw new IllegalStateException("no GlStateManager state found");
            holders = holderList.toArray();
            kinds = kindList.toString().toCharArray();
            fields = fieldList.toArray(new java.lang.reflect.Field[0]);
            return true;
        } catch (Throwable failure) {
            snapshotUnavailable = true;
            return false;
        }
    }

    /** Primitive state fields reachable from {@code field} of {@code holder}: GlStateManager's nested state objects and arrays. */
    private static void collect(Object holder, java.lang.reflect.Field field, java.util.List<Object> holderList,
                                java.util.List<java.lang.reflect.Field> fieldList, StringBuilder kindList, int depth) throws Exception {
        Class<?> type = field.getType();
        if (type.isPrimitive()) {
            if (java.lang.reflect.Modifier.isFinal(field.getModifiers())) return;
            char kind = type == boolean.class ? 'Z' : type == int.class ? 'I' : type == float.class ? 'F' : type == double.class ? 'D' : 0;
            if (kind == 0) return;
            holderList.add(holder);
            fieldList.add(field);
            kindList.append(kind);
            return;
        }
        if (depth > 3) return;
        Object value = field.get(holder);
        if (value == null) return;
        if (value.getClass().isArray()) {
            if (value.getClass().getComponentType().isPrimitive()) return;   // e.g. buffers: not state
            for (int i = 0; i < java.lang.reflect.Array.getLength(value); i++) {
                collectObject(java.lang.reflect.Array.get(value, i), holderList, fieldList, kindList, depth + 1);
            }
        } else {
            collectObject(value, holderList, fieldList, kindList, depth + 1);
        }
    }

    private static void collectObject(Object object, java.util.List<Object> holderList,
                                      java.util.List<java.lang.reflect.Field> fieldList, StringBuilder kindList, int depth) throws Exception {
        if (object == null) return;
        Class<?> type = object.getClass();
        // only GlStateManager's own state holders (AlphaState, BooleanState, TextureState...), never buffers etc.
        if (type.getEnclosingClass() != GlStateManager.class) return;
        for (java.lang.reflect.Field inner : type.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(inner.getModifiers())) continue;
            inner.setAccessible(true);
            collect(object, inner, holderList, fieldList, kindList, depth);
        }
    }

    // ------------------------------------------------------------------ fallback: read the state back

    /** Fallback when the cache cannot be copied: re-applies the real driver state (slow: ~24 queries). */
    public static void afterPopAttrib() {
        int activeUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        // texture units 1 (lightmap) and 0: enable state and binding
        for (int unit = 1; unit >= 0; unit--) {
            selectUnit(GL13.GL_TEXTURE0 + unit);
            if (GL11.glIsEnabled(GL11.GL_TEXTURE_2D)) GlStateManager.enableTexture2D();
            else GlStateManager.disableTexture2D();
            GlStateManager.bindTexture(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
        }
        selectUnit(activeUnit);

        if (GL11.glIsEnabled(GL11.GL_BLEND)) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
        if (GL11.glIsEnabled(GL11.GL_ALPHA_TEST)) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
        if (GL11.glIsEnabled(GL11.GL_DEPTH_TEST)) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
        if (GL11.glIsEnabled(GL11.GL_CULL_FACE)) GlStateManager.enableCull(); else GlStateManager.disableCull();
        if (GL11.glIsEnabled(GL11.GL_LIGHTING)) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
        for (int light = 0; light < 2; light++) {
            if (GL11.glIsEnabled(GL11.GL_LIGHT0 + light)) GlStateManager.enableLight(light);
            else GlStateManager.disableLight(light);
        }
        if (GL11.glIsEnabled(GL11.GL_COLOR_MATERIAL)) GlStateManager.enableColorMaterial(); else GlStateManager.disableColorMaterial();
        if (GL11.glIsEnabled(GL11.GL_FOG)) GlStateManager.enableFog(); else GlStateManager.disableFog();
        if (GL11.glIsEnabled(GL11.GL_NORMALIZE)) GlStateManager.enableNormalize(); else GlStateManager.disableNormalize();
        if (GL11.glIsEnabled(0x803A /* GL_RESCALE_NORMAL */)) GlStateManager.enableRescaleNormal();
        else GlStateManager.disableRescaleNormal();
        if (GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL)) GlStateManager.enablePolygonOffset();
        else GlStateManager.disablePolygonOffset();
        if (GL11.glIsEnabled(GL11.GL_COLOR_LOGIC_OP)) GlStateManager.enableColorLogic(); else GlStateManager.disableColorLogic();

        GlStateManager.depthMask(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
        GlStateManager.depthFunc(GL11.glGetInteger(GL11.GL_DEPTH_FUNC));
        GlStateManager.alphaFunc(GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC), GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF));
        GlStateManager.tryBlendFuncSeparate(GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA));
        GlStateManager.shadeModel(GL11.glGetInteger(GL11.GL_SHADE_MODEL));
        GlStateManager.cullFace(GL11.glGetInteger(GL11.GL_CULL_FACE_MODE));

        FLOATS.clear();
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, FLOATS);
        GlStateManager.color(FLOATS.get(0), FLOATS.get(1), FLOATS.get(2), FLOATS.get(3));
        BYTES.clear();
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, BYTES);
        GlStateManager.colorMask(BYTES.get(0) != 0, BYTES.get(1) != 0, BYTES.get(2) != 0, BYTES.get(3) != 0);
    }

    /** Makes both the driver and GlStateManager agree on the active texture unit. */
    private static void selectUnit(int unit) {
        GL13.glActiveTexture(unit);
        GlStateManager.setActiveTexture(unit);
    }
}
