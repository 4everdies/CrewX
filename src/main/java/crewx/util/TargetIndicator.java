package crewx.util;

import crewx.mixin.IAccessorMinecraft;
import crewx.mixin.IAccessorRenderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;

/**
 * Jello-style target indicator: a dark ring around the target that glides up and down its body, leaving a
 * soft shadow trail behind it (the trail is longest mid-way and vanishes at the turning points).
 */
public final class TargetIndicator {
    /** One full up-and-down trip. */
    static final double PERIOD_MS = 2000.0D;
    private static final int SEGMENTS = 64;

    private TargetIndicator() {
    }

    /** 0 (feet) .. 1 (top of the head), eased so it slows down at both ends. */
    public static double heightFraction(long timeMs) {
        double phase = (timeMs % (long) PERIOD_MS) / PERIOD_MS;
        return (1.0D - Math.cos(phase * Math.PI * 2.0D)) / 2.0D;
    }

    /** Signed vertical speed in fractions/period: positive going up, negative going down. */
    public static double verticalSpeed(long timeMs) {
        double phase = (timeMs % (long) PERIOD_MS) / PERIOD_MS;
        return Math.sin(phase * Math.PI * 2.0D);
    }

    /**
     * @param alpha overall opacity 0..1 (used to fade the indicator in and out)
     * @param rgb   colour without alpha (0x000000 = the dark Jello look)
     */
    public static void draw(Entity entity, float partialTicks, float alpha, int rgb) {
        if (entity == null || alpha <= 0.01F) return;
        Minecraft mc = Minecraft.getMinecraft();
        IAccessorRenderManager rm = (IAccessorRenderManager) mc.getRenderManager();
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks - rm.getRenderPosX();
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks - rm.getRenderPosY();
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks - rm.getRenderPosZ();

        long now = System.currentTimeMillis();
        double height = entity.height + 0.1D;
        double ringY = y + heightFraction(now) * height;
        double speed = verticalSpeed(now);
        // the trail points where the ring came from
        double trail = -Math.signum(speed) * Math.min(0.5D, Math.abs(speed) * 0.5D);
        double radius = entity.width * 0.8D + 0.05D;

        float r = (rgb >> 16 & 255) / 255.0F;
        float g = (rgb >> 8 & 255) / 255.0F;
        float b = (rgb & 255) / 255.0F;

        GlStateManager.pushMatrix();
        RenderUtil.enableRenderState();
        GlStateManager.depthMask(false);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();

        if (Math.abs(trail) > 0.01D) {
            wr.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormats.POSITION_COLOR);
            for (int i = 0; i <= SEGMENTS; i++) {
                double angle = i * Math.PI * 2.0D / SEGMENTS;
                double cx = x + Math.cos(angle) * radius;
                double cz = z + Math.sin(angle) * radius;
                wr.pos(cx, ringY, cz).color(r, g, b, 0.45F * alpha).endVertex();
                wr.pos(cx, ringY + trail, cz).color(r, g, b, 0.0F).endVertex();
            }
            tessellator.draw();
        }

        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0F);
        wr.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= SEGMENTS; i++) {
            double angle = i * Math.PI * 2.0D / SEGMENTS;
            wr.pos(x + Math.cos(angle) * radius, ringY, z + Math.sin(angle) * radius).color(r, g, b, 0.75F * alpha).endVertex();
        }
        tessellator.draw();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1.0F);

        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.depthMask(true);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        RenderUtil.disableRenderState();
        GlStateManager.popMatrix();
    }
}
