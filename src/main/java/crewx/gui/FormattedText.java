package crewx.gui;

/**
 * A line of Minecraft-formatted text ({@code §} colour codes, as in server MOTDs and player lists) parsed once and
 * drawn in the client font as a single batch, each glyph in its run's colour. Formatting codes other than colours
 * and reset are ignored. Very dark colours are lifted so they stay readable on the dark panels.
 */
public final class FormattedText {
    private static final int[] PALETTE = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    /** The text without formatting codes. */
    public final String plain;
    /** Width of {@link #plain} in the client font. */
    public final float width;
    private final float[] charEnds;
    private final int[] charColors;
    private final int defaultColor;
    private float drawAlpha = 1.0F;
    private final ClickGuiFontRenderer.GlyphColor glyphColor = this::colorAtOffset;

    private FormattedText(String plain, float[] charEnds, int[] charColors, int defaultColor) {
        this.plain = plain;
        this.charEnds = charEnds;
        this.charColors = charColors;
        this.defaultColor = defaultColor;
        this.width = charEnds.length == 0 ? 0.0F : charEnds[charEnds.length - 1];
    }

    /** @param defaultColor RGB of text before any colour code (and after a reset) */
    public static FormattedText of(String raw, int defaultColor) {
        String text = raw == null ? "" : raw;
        StringBuilder plain = new StringBuilder(text.length());
        int[] colors = new int[text.length()];
        int color = defaultColor & 0xFFFFFF;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(++i));
                int index = "0123456789abcdef".indexOf(code);
                if (index >= 0) color = readable(PALETTE[index]);
                else if (code == 'r') color = defaultColor & 0xFFFFFF;
                continue;
            }
            colors[plain.length()] = color;
            plain.append(c);
        }
        String result = plain.toString();
        float[] ends = new float[result.length()];
        ClickGuiFontRenderer font = ClientFont.getRenderer();
        float x = 0.0F;
        for (int i = 0; i < result.length(); i++) {
            x += font.getStringWidthFloat(String.valueOf(result.charAt(i)));
            ends[i] = x;
        }
        int[] trimmed = new int[result.length()];
        System.arraycopy(colors, 0, trimmed, 0, result.length());
        return new FormattedText(result, ends, trimmed, defaultColor);
    }

    /** Strips colour and formatting codes. */
    public static String strip(String raw) {
        if (raw == null || raw.indexOf('§') < 0) return raw == null ? "" : raw;
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '§' && i + 1 < raw.length()) {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    /** Draws the line, cut with an ellipsis if it is wider than {@code maxWidth}. */
    public void draw(float x, float y, float maxWidth, float alpha) {
        if (this.plain.isEmpty() || alpha <= 0.01F) return;
        String text = this.plain;
        if (this.width > maxWidth) {
            float ellipsis = ClientFont.getStringWidthFloat("...");
            int end = 0;
            while (end < this.charEnds.length && this.charEnds[end] + ellipsis <= maxWidth) end++;
            text = this.plain.substring(0, end).trim() + "...";
        }
        this.drawAlpha = alpha;
        ClientFont.drawStringStyled(text, x, y, this.glyphColor, 0.0F, 0, 0.0F);
    }

    private int colorAtOffset(float offset) {
        int color = this.defaultColor & 0xFFFFFF;
        // first glyph whose right edge is past the offset
        int lo = 0;
        int hi = this.charEnds.length - 1;
        int found = this.charEnds.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (this.charEnds[mid] > offset + 0.01F) {
                found = mid;
                hi = mid - 1;
            } else {
                lo = mid + 1;
            }
        }
        if (found >= 0 && found < this.charColors.length) color = this.charColors[found];
        else if (this.charColors.length > 0) color = this.charColors[this.charColors.length - 1];
        int a = Math.round(255.0F * Math.max(0.0F, Math.min(1.0F, this.drawAlpha)));
        return a << 24 | color;
    }

    /** Lifts very dark colours (black, dark blue...) towards white so they read on a dark background. */
    static int readable(int rgb) {
        int r = rgb >> 16 & 255;
        int g = rgb >> 8 & 255;
        int b = rgb & 255;
        float luminance = (0.2126F * r + 0.7152F * g + 0.0722F * b) / 255.0F;
        if (luminance >= 0.22F) return rgb;
        float t = 0.42F;
        return Math.round(r + (255 - r) * t) << 16 | Math.round(g + (255 - g) * t) << 8 | Math.round(b + (255 - b) * t);
    }
}
