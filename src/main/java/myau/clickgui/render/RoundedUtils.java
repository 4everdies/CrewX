package myau.clickgui.render;

import myau.clickgui.util.PositionUtils;
import myau.util.shader.Shader;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

public class RoundedUtils {

    private static final Shader roundedRect = new Shader("/assets/myau/shaders/roundedRect.frag", true);
    private static final Shader roundedRectCorners = new Shader("/assets/myau/shaders/roundedRectWithCorners.frag", true);
    private static final Shader roundedOutlinedRect = new Shader("/assets/myau/shaders/roundedOutlinedRect.frag", true);

    public static void drawRoundedRect(PositionUtils position, int color, float rounding) {
        drawRoundedRect(position.getX(), position.getY(), position.getWidth(), position.getHeight(), color, rounding);
    }

    public static void drawRoundedRect(PositionUtils position, int color, float rLT, float rRT, float rRB, float rLB) {
        drawRoundedRect(position.getX(), position.getY(), position.getWidth(), position.getHeight(), color, rLT, rRT, rRB, rLB);
    }

    public static void drawRoundedRect(double x, double y, double width, double height, int color, float rounding) {
        float f3 = (float) (color >> 24 & 255) / 255.0F;
        float f = (float) (color >> 16 & 255) / 255.0F;
        float f1 = (float) (color >> 8 & 255) / 255.0F;
        float f2 = (float) (color & 255) / 255.0F;

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

    public static void drawRoundedRect(double x, double y, double width, double height, int color, float rLT, float rRT, float rRB, float rLB) {
        float f3 = (float) (color >> 24 & 255) / 255.0F;
        float f = (float) (color >> 16 & 255) / 255.0F;
        float f1 = (float) (color >> 8 & 255) / 255.0F;
        float f2 = (float) (color & 255) / 255.0F;

        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.disableAlpha();
        roundedRectCorners.startProgram();

        roundedRectCorners.uniform4f("radius", rLB, rLT, rRB, rRT);
        roundedRectCorners.uniform2f("size", (float) width, (float) height);
        roundedRectCorners.uniform4f("color", f, f1, f2, f3);

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
}
