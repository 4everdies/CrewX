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
        // one pass (this used to re-measure every prefix: quadratic, and it runs every frame)
        float used = 0.0F;
        int end = 0;
        for (int i = 0; i < text.length(); i++) {
            used += RENDERER.getStringWidthFloat(String.valueOf(text.charAt(i)));
            if (Math.round(used) > width) break;
            end = i + 1;
        }
        return text.substring(0, end);
    }

    public static int drawString(String text, float x, float y, int color) {
        if (text == null || text.isEmpty()) return Math.round(x);
        long timed = crewx.util.HitchMonitor.start();
        try {
            return drawPlain(text, x, y, color);
        } finally {
            crewx.util.HitchMonitor.section("text", timed);
        }
    }

    private static int drawPlain(String text, float x, float y, int color) {
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
        long timed = crewx.util.HitchMonitor.start();
        try {
            return drawShadowed(text, x, y, color);
        } finally {
            crewx.util.HitchMonitor.section("text", timed);
        }
    }

    private static int drawShadowed(String text, float x, float y, int color) {
        if (hasFormatting(text)) return MC.fontRendererObj.drawStringWithShadow(text, x, y, color);
        int shadow = (color & 0xFF000000) | ((color & 0x00FCFCFC) >> 2);
        RENDERER.drawString(text, x + 1.0F, y + 1.0F, shadow);
        RENDERER.drawString(text, x, y, color);
        return Math.round(x) + RENDERER.getStringWidth(text);
    }

    /** Single-batch text with per-glyph colours, optional shadow and faux-bold (see ClickGuiFontRenderer). */
    public static void drawStringStyled(String text, float x, float y, ClickGuiFontRenderer.GlyphColor color,
                                        float boldOffset, int shadowColor, float shadowOffset) {
        if (text == null || text.isEmpty()) return;
        RENDERER.drawStringStyled(text, x, y, color, boldOffset, shadowColor, shadowOffset);
    }

    private static boolean hasFormatting(String text) {
        return text.indexOf('\u00a7') >= 0;
    }
}
