package crewx.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class ClickGuiFontRenderer {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final int ATLAS_WIDTH = 1024;
    private static final int ATLAS_HEIGHT = 256;
    private static final float SCALE = 0.42F;
    private final Glyph[] glyphs = new Glyph[256];
    private int ascent = 22;
    private float baselineOffset = 17.0F;
    private float visualHeight = 24.0F;
    private ResourceLocation texture;
    private boolean textureLoadAttempted;
    private boolean hasMetrics;

    public ClickGuiFontRenderer() {
        this.loadMetrics();
    }

    public int getStringWidth(String text) {
        return Math.round(this.getStringWidthFloat(text));
    }

    public float getStringWidthFloat(String text) {
        if (text == null || text.isEmpty()) return 0;
        if (!this.hasMetrics) return MC.fontRendererObj.getStringWidth(text);
        float width = 0.0F;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                i++;
                continue;
            }
            width += this.getGlyph(c).advance;
        }
        return width * SCALE;
    }

    public int getFontHeight() {
        return this.hasMetrics ? Math.max(1, Math.round(this.visualHeight * SCALE)) : MC.fontRendererObj.FONT_HEIGHT;
    }

    public void drawString(String text, float x, float y, int color) {
        if (text == null || text.isEmpty()) return;
        if (!this.hasMetrics) {
            MC.fontRendererObj.drawString(text, (int) x, (int) y, color);
            return;
        }
        ResourceLocation fontTexture = this.getTexture();
        if (fontTexture == null) {
            MC.fontRendererObj.drawString(text, (int) x, (int) y, color);
            return;
        }
        boolean hasVisibleGlyph = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                i++;
                continue;
            }
            Glyph glyph = this.getGlyph(c);
            if (glyph.width > 0 && glyph.height > 0) {
                hasVisibleGlyph = true;
                break;
            }
        }
        if (!hasVisibleGlyph) return;
        MC.getTextureManager().bindTexture(fontTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        if ((color & 0xFC000000) == 0) color |= 0xFF000000;
        int alpha = color >>> 24 & 255;
        int red = color >> 16 & 255;
        int green = color >> 8 & 255;
        int blue = color & 255;
        float cursor = x;
        float baseline = y + this.baselineOffset * SCALE;
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        renderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                i++;
                continue;
            }
            Glyph glyph = this.getGlyph(c);
            if (glyph.width > 0 && glyph.height > 0) {
                float left = cursor + glyph.bearingX * SCALE;
                float top = baseline + glyph.bearingY * SCALE;
                float right = left + glyph.width * SCALE;
                float bottom = top + glyph.height * SCALE;
                float u0 = glyph.x / (float) ATLAS_WIDTH;
                float v0 = glyph.y / (float) ATLAS_HEIGHT;
                float u1 = (glyph.x + glyph.width) / (float) ATLAS_WIDTH;
                float v1 = (glyph.y + glyph.height) / (float) ATLAS_HEIGHT;
                renderer.pos(left, top, 0.0D).tex(u0, v0).color(red, green, blue, alpha).endVertex();
                renderer.pos(left, bottom, 0.0D).tex(u0, v1).color(red, green, blue, alpha).endVertex();
                renderer.pos(right, bottom, 0.0D).tex(u1, v1).color(red, green, blue, alpha).endVertex();
                renderer.pos(right, top, 0.0D).tex(u1, v0).color(red, green, blue, alpha).endVertex();
            }
            cursor += glyph.advance * SCALE;
        }

        tessellator.draw();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
    }

    private Glyph getGlyph(char c) {
        if (c < this.glyphs.length && this.glyphs[c] != null) return this.glyphs[c];
        Glyph fallback = this.glyphs['?'];
        return fallback == null ? new Glyph(0, 0, 0, 0, 12.0F, 0, 0) : fallback;
    }

    private ResourceLocation getTexture() {
        if (this.texture != null) return this.texture;
        if (this.textureLoadAttempted) return null;
        this.textureLoadAttempted = true;
        try (InputStream stream = ClickGuiFontRenderer.class.getResourceAsStream(
                "/assets/crewx/fonts/clickgui-font.png")) {
            if (stream == null) return null;
            BufferedImage image = ImageIO.read(stream);
            if (image == null) return null;
            this.texture = MC.getTextureManager().getDynamicTextureLocation(
                    "crewx_clickgui_font_atlas", new DynamicTexture(image));
        } catch (Exception ignored) {
            return null;
        }
        return this.texture;
    }

    private void loadMetrics() {
        InputStream stream = ClickGuiFontRenderer.class.getResourceAsStream(
                "/assets/crewx/fonts/clickgui-font.metrics");
        if (stream == null) return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length == 2 && "ascent".equals(parts[0])) {
                    this.ascent = Integer.parseInt(parts[1]);
                } else if (parts.length == 8) {
                    int code = Integer.parseInt(parts[0]);
                    if (code >= 0 && code < this.glyphs.length) {
                        this.glyphs[code] = new Glyph(
                                Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                                Integer.parseInt(parts[3]), Integer.parseInt(parts[4]),
                                Float.parseFloat(parts[5]), Integer.parseInt(parts[6]), Integer.parseInt(parts[7]));
                    }
                }
            }
            Glyph cap = this.glyphs['H'];
            if (cap != null) this.baselineOffset = -cap.bearingY;
            float maxHeight = 0.0F;
            String layoutGlyphs = "HgjpqyABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
            for (int i = 0; i < layoutGlyphs.length(); i++) {
                Glyph glyph = this.getGlyph(layoutGlyphs.charAt(i));
                maxHeight = Math.max(maxHeight, glyph.height);
            }
            if (maxHeight > 0.0F) this.visualHeight = maxHeight;
            this.hasMetrics = this.glyphs['?'] != null;
        } catch (Exception ignored) {
            this.hasMetrics = false;
        }
    }

    private static final class Glyph {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final float advance;
        private final int bearingX;
        private final int bearingY;

        private Glyph(int x, int y, int width, int height, float advance, int bearingX, int bearingY) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.advance = advance;
            this.bearingX = bearingX;
            this.bearingY = bearingY;
        }
    }
}
