package myau.clickgui.font;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

public class Fonts {

    public static void drawString(String text, double x, double y, int color, String fontType) {
        drawString(text, x, y, color, fontType, 1);
    }

    public static void drawString(String text, double x, double y, int color, String fontType, double scale) {
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        Minecraft.getMinecraft().fontRendererObj.drawString(text, (int) x, (int) y, color);
    }

    public static void drawStringCentered(String text, double x, double y, int color, String fontType) {
        drawString(text, x - getWidth(text, fontType) / 2, y - getHeight(fontType) / 2, color, fontType);
    }

    public static void drawStringCentered(String text, double x, double y, int color, String fontType, double scale) {
        drawString(text, x - getWidth(text, fontType, scale) / 2, y - getHeight(fontType, scale) / 2, color, fontType, scale);
    }

    public static double getWidth(String text, String fontType, double scale) {
        return (double) Minecraft.getMinecraft().fontRendererObj.getStringWidth(text) * scale;
    }

    public static double getWidth(String text, String fontType) {
        return getWidth(text, fontType, 1);
    }

    public static double getHeight(String fontType, double scale) {
        return (double) Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT * scale;
    }

    public static double getHeight(String fontType) {
        return getHeight(fontType, 1);
    }
}
