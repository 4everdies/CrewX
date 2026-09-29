package crewx.gui;

import net.minecraft.client.Minecraft;

public final class ClientFont {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final ClickGuiFontRenderer RENDERER = new ClickGuiFontRenderer();

    private ClientFont() {
    }

    public static ClickGuiFontRenderer getRenderer() {
        return RENDERER;
    }

    public static int getHeight() {
        return RENDERER.getFontHeight();
    }

    public static int getStringWidth(String text) {
        if (text == null || text.isEmpty()) return 0;
        if (hasFormatting(text)) return MC.fontRendererObj.getStringWidth(text);
        return RENDERER.getStringWidth(text);
    }

    public static float getStringWidthFloat(String text) {
        if (text == null || text.isEmpty()) return 0.0F;
        if (hasFormatting(text)) return MC.fontRendererObj.getStringWidth(text);
        return RENDERER.getStringWidthFloat(text);
    }

    public static String trimStringToWidth(String text, int width) {
        if (text == null || text.isEmpty() || width <= 0) return "";
        if (hasFormatting(text)) return MC.fontRendererObj.trimStringToWidth(text, width);
        int end = 0;
        for (int i = 1; i <= text.length(); i++) {
            if (RENDERER.getStringWidth(text.substring(0, i)) > width) break;
            end = i;
        }
        return text.substring(0, end);
    }

    public static int drawString(String text, float x, float y, int color) {
        if (text == null || text.isEmpty()) return Math.round(x);
        if (hasFormatting(text)) return MC.fontRendererObj.drawString(text, x, y, color, false);
        RENDERER.drawString(text, x, y, color);
        return Math.round(x) + RENDERER.getStringWidth(text);
    }

    public static int drawString(String text, float x, float y, int color, boolean shadow) {
        if (shadow) return drawStringWithShadow(text, x, y, color);
        return drawString(text, x, y, color);
    }

    public static int drawStringWithShadow(String text, float x, float y, int color) {
        if (text == null || text.isEmpty()) return Math.round(x);
        if (hasFormatting(text)) return MC.fontRendererObj.drawStringWithShadow(text, x, y, color);
        int shadow = (color & 0xFF000000) | ((color & 0x00FCFCFC) >> 2);
        RENDERER.drawString(text, x + 1.0F, y + 1.0F, shadow);
        RENDERER.drawString(text, x, y, color);
        return Math.round(x) + RENDERER.getStringWidth(text);
    }

    private static boolean hasFormatting(String text) {
        return text.indexOf('\u00a7') >= 0;
    }
}
