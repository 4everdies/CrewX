package crewx.clickgui.render;

import crewx.util.shader.Shader;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

public class RoundedUtils {

    private static final Shader roundedRect = new Shader("/assets/crewx/shaders/roundedRect.frag", true);
    private static final Shader roundedRectCorners = new Shader("/assets/crewx/shaders/roundedRectWithCorners.frag", true);
    private static final Shader roundedOutlinedRect = new Shader("/assets/crewx/shaders/roundedOutlinedRect.frag", true);

    public static void drawRoundedRect(double x, double y, double width, double height, int color, float rounding) {
        long timed = crewx.util.HitchMonitor.start();
        try {
            drawRoundedRectImpl(x, y, width, height, color, rounding);
        } finally {
            crewx.util.HitchMonitor.section("rounded panels", timed);
        }
    }

    private static void drawRoundedRectImpl(double x, double y, double width, double height, int color, float rounding) {
        float f3 = (float) (color >> 24 & 255) / 255.0F;
        float f = (float) (color >> 16 & 255) / 255.0F;
        float f1 = (float) (color >> 8 & 255) / 255.0F;
        float f2 = (float) (color & 255) / 255.0F;

        if (!roundedRect.isValid()) {
            fallbackRect(x, y, width, height, f, f1, f2, f3);
            return;
        }
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.disableAlpha();
        roundedRect.startProgram();

        roundedRect.uniform1f("radius", rounding);
        roundedRect.uniform2f("size", (float) width, (float) height);
        roundedRect.uniform4f("color", f, f1, f2, f3);

        roundedRect.renderShader(x, y, width, height);

        roundedRect.stopProgram();
        GlStateManager.enableAlpha();
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }
    public static void drawRoundedRect(double x, double y, double width, double height, int color,
                                       float topLeft, float topRight, float bottomLeft, float bottomRight) {
        float a = (float) (color >> 24 & 255) / 255.0F;
        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;

        if (!roundedRectCorners.isValid()) {
            fallbackRect(x, y, width, height, r, g, b, a);
            return;
        }
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.disableAlpha();
        roundedRectCorners.startProgram();

        roundedRectCorners.uniform2f("size", (float) width, (float) height);
        roundedRectCorners.uniform4f("color", r, g, b, a);
        roundedRectCorners.uniform4f("radius", bottomLeft, topLeft, bottomRight, topRight);

        roundedRectCorners.renderShader(x, y, width, height);

        roundedRectCorners.stopProgram();
        GlStateManager.enableAlpha();
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    public static void drawRoundedOutlinedRect(double x, double y, double width, double height, int color, float rounding, float outlineSize) {
        float f3 = (float) (color >> 24 & 255) / 255.0F;
        float f = (float) (color >> 16 & 255) / 255.0F;
        float f1 = (float) (color >> 8 & 255) / 255.0F;
        float f2 = (float) (color & 255) / 255.0F;

        if (!roundedOutlinedRect.isValid()) return;   // an outline-only shape: nothing sensible to draw without the shader
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.disableAlpha();
        roundedOutlinedRect.startProgram();

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        net.minecraft.client.gui.ScaledResolution sr = new net.minecraft.client.gui.ScaledResolution(mc);

        roundedOutlinedRect.uniform1f("outlineThickness", outlineSize);
        roundedOutlinedRect.uniform2f("location", (float) x * sr.getScaleFactor(),
                (float) ((mc.displayHeight - (height * sr.getScaleFactor())) - (y * sr.getScaleFactor())));
        roundedOutlinedRect.uniform2f("rectSize", (float) width * sr.getScaleFactor(), (float) height * sr.getScaleFactor());
        roundedOutlinedRect.uniform1f("radius", rounding * 2);
        roundedOutlinedRect.uniform4f("outlineColor", f, f1, f2, f3);

        roundedOutlinedRect.renderShader(x - (1 + outlineSize), y - (1 + outlineSize), width + (2 + outlineSize), height + (2 + outlineSize));
        roundedOutlinedRect.stopProgram();
        GlStateManager.enableAlpha();
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    /**
     * Plain translucent quad for GPUs/drivers where the rounded-rect shader did not compile. Without it the quad
     * was drawn with whatever colour/program happened to be current, which showed up as flashing boxes.
     */
    private static void fallbackRect(double x, double y, double width, double height, float r, float g, float b, float a) {
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableAlpha();
        GlStateManager.color(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2d(x, y);
        GL11.glVertex2d(x, y + height);
        GL11.glVertex2d(x + width, y + height);
        GL11.glVertex2d(x + width, y);
        GL11.glEnd();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }
}
