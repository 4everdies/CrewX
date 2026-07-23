package myau.script;

import myau.mixin.IAccessorMinecraft;
import myau.mixin.IAccessorRenderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Safe, state-contained 3D primitive renderer for Lua scripts.
 *
 * World coordinates are converted relative to the current render camera. A
 * guarded matrix stack and forceFinish() keep one broken script from poisoning
 * the rest of Minecraft's render pipeline.
 */
public final class Render3D {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static boolean active;
    private static boolean rootMatrixPushed;
    private static boolean attribPushed;
    private static int scriptMatrixDepth;

    private Render3D() {
    }

    private static double camX() {
        return ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
    }

    private static double camY() {
        return ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
    }

    private static double camZ() {
        return ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();
    }

    public static float partialTicks() {
        return ((IAccessorMinecraft) mc).getTimer().renderPartialTicks;
    }

    public static boolean isActive() {
        return active;
    }

    /** Starts a depth-tested world render batch. */
    public static boolean begin() {
        return begin(false);
    }

    /**
     * Starts a world render batch.
     *
     * @param throughWalls when true, depth testing and depth writes are
     *                     disabled for this batch
     * @return false when Minecraft is not currently rendering a world
     */
    public static boolean begin(boolean throughWalls) {
        if (active) {
            forceFinish();
        }
        if (mc.theWorld == null || mc.getRenderManager() == null) {
            return false;
        }

        try {
            GlStateManager.pushMatrix();
            rootMatrixPushed = true;
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            attribPushed = true;
            active = true;
            scriptMatrixDepth = 0;

            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GlStateManager.enableAlpha();
            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.disableFog();
            GlStateManager.disableCull();
            if (throughWalls) {
                GlStateManager.disableDepth();
                GlStateManager.depthMask(false);
            } else {
                GlStateManager.enableDepth();
                GlStateManager.depthMask(true);
            }
            GlStateManager.shadeModel(GL11.GL_SMOOTH);
            GL11.glEnable(GL11.GL_NORMALIZE);
            return true;
        } catch (Throwable throwable) {
            forceFinish();
            return false;
        }
    }

    public static void finish() {
        if (!active && !rootMatrixPushed && !attribPushed) {
            return;
        }
        try {
            while (scriptMatrixDepth > 0 && rootMatrixPushed) {
                GlStateManager.popMatrix();
                scriptMatrixDepth--;
            }
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glLineWidth(1.0F);
            if (attribPushed) {
                GL11.glPopAttrib();
                attribPushed = false;
            }
            if (rootMatrixPushed) {
                GlStateManager.popMatrix();
                rootMatrixPushed = false;
            }
        } finally {
            active = false;
            scriptMatrixDepth = 0;
        }
    }

    /** Emergency cleanup called after every script render callback. */
    public static void forceFinish() {
        try {
            finish();
        } catch (Throwable ignored) {
            active = false;
            scriptMatrixDepth = 0;
            attribPushed = false;
            rootMatrixPushed = false;
            try {
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                GL11.glLineWidth(1.0F);
            } catch (Throwable ignoredAgain) {
            }
        }
    }

    private static boolean ready() {
        return active;
    }

    private static void color(int argb) {
        float a = (argb >>> 24 & 0xFF) / 255.0F;
        float r = (argb >> 16 & 0xFF) / 255.0F;
        float g = (argb >> 8 & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        GL11.glColor4f(r, g, b, a);
    }

    public static void push() {
        if (!ready()) return;
        GlStateManager.pushMatrix();
        scriptMatrixDepth++;
    }

    public static void pop() {
        if (!ready() || scriptMatrixDepth <= 0) return;
        GlStateManager.popMatrix();
        scriptMatrixDepth--;
    }

    public static void translateWorld(double x, double y, double z) {
        if (!ready()) return;
        GlStateManager.translate(x - camX(), y - camY(), z - camZ());
    }

    /** Moves to an entity's interpolated render position. */
    public static void translateEntity(Entity entity, float partial, double yOffset) {
        if (!ready() || entity == null) return;
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partial;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partial + yOffset;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partial;
        translateWorld(x, y, z);
    }

    public static void translate(double x, double y, double z) {
        if (!ready()) return;
        GlStateManager.translate(x, y, z);
    }

    public static void rotate(float angle, float x, float y, float z) {
        if (!ready()) return;
        GlStateManager.rotate(angle, x, y, z);
    }

    public static void scale(float x, float y, float z) {
        if (!ready()) return;
        GlStateManager.scale(x, y, z);
    }

    public static void cube(float size, int argb) {
        box(-size / 2, -size / 2, -size / 2, size / 2, size / 2, size / 2, argb);
    }

    public static void box(float x1, float y1, float z1, float x2, float y2, float z2, int argb) {
        if (!ready()) return;
        color(argb);
        GL11.glBegin(GL11.GL_QUADS);

        vertex(x1, y1, z1); vertex(x2, y1, z1); vertex(x2, y1, z2); vertex(x1, y1, z2);
        vertex(x1, y2, z1); vertex(x1, y2, z2); vertex(x2, y2, z2); vertex(x2, y2, z1);
        vertex(x1, y1, z1); vertex(x1, y2, z1); vertex(x2, y2, z1); vertex(x2, y1, z1);
        vertex(x1, y1, z2); vertex(x2, y1, z2); vertex(x2, y2, z2); vertex(x1, y2, z2);
        vertex(x1, y1, z1); vertex(x1, y1, z2); vertex(x1, y2, z2); vertex(x1, y2, z1);
        vertex(x2, y1, z1); vertex(x2, y2, z1); vertex(x2, y2, z2); vertex(x2, y1, z2);

        GL11.glEnd();
    }

    public static void wireBox(float x1, float y1, float z1, float x2, float y2, float z2, int argb, float width) {
        if (!ready()) return;
        color(argb);
        GL11.glLineWidth(Math.max(1.0F, width));
        GL11.glBegin(GL11.GL_LINES);
        edge(x1,y1,z1, x2,y1,z1); edge(x2,y1,z1, x2,y1,z2); edge(x2,y1,z2, x1,y1,z2); edge(x1,y1,z2, x1,y1,z1);
        edge(x1,y2,z1, x2,y2,z1); edge(x2,y2,z1, x2,y2,z2); edge(x2,y2,z2, x1,y2,z2); edge(x1,y2,z2, x1,y2,z1);
        edge(x1,y1,z1, x1,y2,z1); edge(x2,y1,z1, x2,y2,z1); edge(x2,y1,z2, x2,y2,z2); edge(x1,y1,z2, x1,y2,z2);
        GL11.glEnd();
    }

    private static void vertex(double x, double y, double z) {
        GL11.glVertex3d(x, y, z);
    }

    private static void edge(double x1, double y1, double z1, double x2, double y2, double z2) {
        vertex(x1, y1, z1);
        vertex(x2, y2, z2);
    }

    public static void sphere(float radius, int rings, int sectors, int argb) {
        if (!ready()) return;
        color(argb);
        rings = Math.max(3, Math.min(64, rings));
        sectors = Math.max(3, Math.min(96, sectors));

        for (int r = 0; r < rings; r++) {
            double theta1 = Math.PI * r / rings;
            double theta2 = Math.PI * (r + 1) / rings;
            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for (int s = 0; s <= sectors; s++) {
                double phi = 2.0 * Math.PI * s / sectors;
                double sinPhi = Math.sin(phi);
                double cosPhi = Math.cos(phi);
                vertex(radius * Math.sin(theta1) * cosPhi, radius * Math.cos(theta1), radius * Math.sin(theta1) * sinPhi);
                vertex(radius * Math.sin(theta2) * cosPhi, radius * Math.cos(theta2), radius * Math.sin(theta2) * sinPhi);
            }
            GL11.glEnd();
        }
    }

    public static void cylinder(float radius, float height, int sectors, int argb) {
        if (!ready()) return;
        color(argb);
        sectors = Math.max(3, Math.min(96, sectors));

        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for (int s = 0; s <= sectors; s++) {
            double phi = 2.0 * Math.PI * s / sectors;
            double x = radius * Math.cos(phi);
            double z = radius * Math.sin(phi);
            vertex(x, 0, z);
            vertex(x, height, z);
        }
        GL11.glEnd();
        drawDisk(radius, 0, sectors, true);
        drawDisk(radius, height, sectors, false);
    }

    private static void drawDisk(float radius, float y, int sectors, boolean reverse) {
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        vertex(0, y, 0);
        for (int s = 0; s <= sectors; s++) {
            int index = reverse ? sectors - s : s;
            double phi = 2.0 * Math.PI * index / sectors;
            vertex(radius * Math.cos(phi), y, radius * Math.sin(phi));
        }
        GL11.glEnd();
    }

    public static void quad(double[] p1, double[] p2, double[] p3, double[] p4, int argb) {
        if (!ready()) return;
        color(argb);
        GL11.glBegin(GL11.GL_QUADS);
        vertex(p1[0], p1[1], p1[2]);
        vertex(p2[0], p2[1], p2[2]);
        vertex(p3[0], p3[1], p3[2]);
        vertex(p4[0], p4[1], p4[2]);
        GL11.glEnd();
    }

    public static void line(double x1, double y1, double z1, double x2, double y2, double z2, int argb, float width) {
        if (!ready()) return;
        color(argb);
        GL11.glLineWidth(Math.max(1.0F, width));
        GL11.glBegin(GL11.GL_LINES);
        vertex(x1, y1, z1);
        vertex(x2, y2, z2);
        GL11.glEnd();
    }

    /** Draws a cached remote image as a camera-facing quad, bottom-center anchored. */
    public static boolean billboardImage(ResourceLocation texture, float width, float height, int argb) {
        if (!ready() || texture == null) return false;
        push();
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        float view = mc.gameSettings.thirdPersonView == 2 ? -1.0F : 1.0F;
        GlStateManager.rotate(mc.getRenderManager().playerViewX, view, 0.0F, 0.0F);
        GlStateManager.enableTexture2D();
        mc.getTextureManager().bindTexture(texture);
        color(argb);

        float half = width / 2.0F;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, 1.0F); vertex(-half, 0, 0);
        GL11.glTexCoord2f(1.0F, 1.0F); vertex( half, 0, 0);
        GL11.glTexCoord2f(1.0F, 0.0F); vertex( half, height, 0);
        GL11.glTexCoord2f(0.0F, 0.0F); vertex(-half, height, 0);
        GL11.glEnd();

        GlStateManager.disableTexture2D();
        pop();
        return true;
    }
}
