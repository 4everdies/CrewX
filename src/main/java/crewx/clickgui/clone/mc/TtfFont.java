package crewx.clickgui.clone.mc;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

/**
 * Fonte TTF rasterizada pelo Java2D num atlas (textura OpenGL) e desenhada com quads.
 * Cobre os caracteres 32..255. Precisa ser criada na thread de render (contexto GL ativo).
 */
final class TtfFont {
    private static final int FIRST = 32, LAST = 255, PAD = 2, ATLAS_W = 512;

    final float capHeight;
    private final float[] adv = new float[LAST + 1];
    private final int[] gx = new int[LAST + 1], gy = new int[LAST + 1];
    private final int[] gw = new int[LAST + 1], gh = new int[LAST + 1];
    private final int[] ox = new int[LAST + 1], oy = new int[LAST + 1];
    private final int atlasH;
    private final int texture;

    TtfFont(Font font) {
        FontRenderContext frc = new FontRenderContext(null, true, true);
        capHeight = (float) font.createGlyphVector(frc, "H").getVisualBounds().getHeight();

        // 1) medir e empacotar (prateleiras)
        int x = 1, y = 1, rowH = 0;
        for (int c = FIRST; c <= LAST; c++) {
            String s = String.valueOf((char) c);
            adv[c] = (float) font.getStringBounds(s, frc).getWidth();
            Rectangle b = font.createGlyphVector(frc, s).getPixelBounds(frc, 0, 0);
            if (b.isEmpty()) continue;
            int w = b.width + PAD * 2, h = b.height + PAD * 2;
            if (x + w + 1 > ATLAS_W) {
                x = 1;
                y += rowH + 1;
                rowH = 0;
            }
            gx[c] = x;
            gy[c] = y;
            gw[c] = w;
            gh[c] = h;
            ox[c] = b.x - PAD;
            oy[c] = b.y - PAD;
            x += w + 1;
            rowH = Math.max(rowH, h);
        }
        int hh = 64;
        while (hh < y + rowH + 1) hh <<= 1;
        atlasH = hh;

        // 2) rasterizar em branco com alpha
        BufferedImage img = new BufferedImage(ATLAS_W, atlasH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setFont(font);
        g.setColor(Color.WHITE);
        for (int c = FIRST; c <= LAST; c++) {
            if (gw[c] == 0) continue;
            g.drawString(String.valueOf((char) c), gx[c] - ox[c], gy[c] - oy[c]);
        }
        g.dispose();

        // 3) subir para a GPU
        int[] pix = img.getRGB(0, 0, ATLAS_W, atlasH, null, 0, ATLAS_W);
        ByteBuffer buf = BufferUtils.createByteBuffer(ATLAS_W * atlasH * 4);
        for (int p : pix) {
            buf.put((byte) ((p >> 16) & 255));
            buf.put((byte) ((p >> 8) & 255));
            buf.put((byte) (p & 255));
            buf.put((byte) ((p >>> 24) & 255));
        }
        buf.flip();
        texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, ATLAS_W, atlasH, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
    }

    float width(String s) {
        float w = 0;
        for (int i = 0; i < s.length(); i++) w += adv[index(s.charAt(i))];
        return w;
    }

    /** @param baseline y da linha de base; x é a origem da caneta do primeiro caractere. */
    void draw(String s, float x, float baseline, int argb) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glColor4f(((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f,
                (argb & 255) / 255f, ((argb >>> 24) & 255) / 255f);
        GL11.glBegin(GL11.GL_QUADS);
        float pen = x;
        for (int i = 0; i < s.length(); i++) {
            int c = index(s.charAt(i));
            if (gw[c] > 0) {
                float x0 = pen + ox[c], y0 = baseline + oy[c];
                float x1 = x0 + gw[c], y1 = y0 + gh[c];
                float u0 = gx[c] / (float) ATLAS_W, v0 = gy[c] / (float) atlasH;
                float u1 = (gx[c] + gw[c]) / (float) ATLAS_W, v1 = (gy[c] + gh[c]) / (float) atlasH;
                GL11.glTexCoord2f(u0, v0);
                GL11.glVertex2f(x0, y0);
                GL11.glTexCoord2f(u0, v1);
                GL11.glVertex2f(x0, y1);
                GL11.glTexCoord2f(u1, v1);
                GL11.glVertex2f(x1, y1);
                GL11.glTexCoord2f(u1, v0);
                GL11.glVertex2f(x1, y0);
            }
            pen += adv[c];
        }
        GL11.glEnd();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }

    /** Desenha o primeiro caractere centralizado no ponto (usado nos ícones). */
    void drawCentered(String s, float cx, float cy, int argb) {
        if (s.isEmpty()) return;
        int c = index(s.charAt(0));
        if (gw[c] == 0) return;
        float pen = Math.round(cx - (ox[c] + gw[c] / 2f));
        float base = Math.round(cy - (oy[c] + gh[c] / 2f));
        draw(s.substring(0, 1), pen, base, argb);
    }

    private static int index(char ch) {
        return (ch < FIRST || ch > LAST) ? '?' : ch;
    }
}
