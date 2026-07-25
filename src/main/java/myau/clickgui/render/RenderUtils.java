package myau.clickgui.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class RenderUtils {

    public static void drawCircle(double x, double y, double radius, int color) {
        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;
        float a = (float) (color >> 24 & 255) / 255.0F;

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        for (int i = 0; i <= 360; i++) {
            GL11.glVertex2d(x + Math.sin(i * Math.PI / 180.0) * radius, y + Math.cos(i * Math.PI / 180.0) * radius);
        }
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    public static void drawGradientRect(double x, double y, double x1, double y1, int colorLeftT, int colorRightT, int colorRightB, int colorLeftB) {
        if (x > x1) { double j = x; x = x1; x1 = j; }
        if (y > y1) { double j = y; y = y1; y1 = j; }

        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableAlpha();
        GlStateManager.disableDepth();

        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glBegin(GL11.GL_QUADS);
        color(colorLeftT); GL11.glVertex2d(x, y);
        color(colorRightT); GL11.glVertex2d(x1, y);
        color(colorRightB); GL11.glVertex2d(x1, y1);
        color(colorLeftB); GL11.glVertex2d(x, y1);
        GL11.glEnd();

        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    public static void drawImage(double x, double y, double width, double height, ResourceLocation image, int color) {
        GlStateManager.enableBlend();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableAlpha();
        color(color);
        Minecraft.getMinecraft().getTextureManager().bindTexture(image);
        Gui.drawModalRectWithCustomSizedTexture((int) x, (int) y, 0, 0, (int) width, (int) height, (int) width, (int) height);
        GlStateManager.resetColor();
        GlStateManager.disableBlend();
    }

    private static void color(int color) {
        float f3 = (float) (color >> 24 & 255) / 255.0F;
        float f = (float) (color >> 16 & 255) / 255.0F;
        float f1 = (float) (color >> 8 & 255) / 255.0F;
        float f2 = (float) (color & 255) / 255.0F;
        GlStateManager.color(f, f1, f2, f3);
    }
}