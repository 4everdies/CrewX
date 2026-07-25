package myau.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * Helper de desenho 2D usado pela ClickGUI.
 *
 * <p>Por que este arquivo existe: {@code myau.util.RenderUtil#drawRect} desenha
 * o poligono sem configurar o estado do OpenGL - nao desliga GL_TEXTURE_2D e nao
 * liga o blend. Dentro de um GuiScreen a ultima textura ligada e o atlas da
 * fonte, entao todo retangulo saia amostrado da fonte (quase todo transparente)
 * e a GUI inteira ficava invisivel. Aqui cada primitiva liga o estado que
 * precisa e devolve o estado no fim.</p>
 */
public final class GuiRender {

    private GuiRender() {
    }

    // ------------------------------------------------------------------
    //  Estado
    // ------------------------------------------------------------------

    private static void begin() {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.disableAlpha();
        GlStateManager.disableCull();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
    }

    private static void end() {
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void glColor(int argb) {
        float a = (float) (argb >>> 24 & 0xFF) / 255.0f;
        float r = (float) (argb >> 16 & 0xFF) / 255.0f;
        float g = (float) (argb >> 8 & 0xFF) / 255.0f;
        float b = (float) (argb & 0xFF) / 255.0f;
        GlStateManager.color(r, g, b, a);
    }

    // ------------------------------------------------------------------
    //  Cores
    // ------------------------------------------------------------------

    public static int rgba(int rgb, int alpha) {
        return (alpha & 0xFF) << 24 | (rgb & 0xFFFFFF);
    }

    public static int alpha(int argb, float factor) {
        int a = (int) ((argb >>> 24 & 0xFF) * factor);
        if (a < 0) a = 0;
        if (a > 255) a = 255;
        return a << 24 | (argb & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        if (t < 0.0f) t = 0.0f;
        if (t > 1.0f) t = 1.0f;
        int aa = a >>> 24 & 0xFF, ar = a >> 16 & 0xFF, ag = a >> 8 & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24 & 0xFF, br = b >> 16 & 0xFF, bg = b >> 8 & 0xFF, bb = b & 0xFF;
        int ra = (int) (aa + (ba - aa) * t);
        int rr = (int) (ar + (br - ar) * t);
        int rg = (int) (ag + (bg - ag) * t);
        int rb = (int) (ab + (bb - ab) * t);
        return ra << 24 | rr << 16 | rg << 8 | rb;
    }

    // ------------------------------------------------------------------
    //  Caminho arredondado
    // ------------------------------------------------------------------

    private static void corner(double cx, double cy, double r, double a0, double a1,
                               boolean gradient, double y0, double h, int top, int bottom) {
        if (r <= 0.0) {
            if (gradient) glColor(mix(top, bottom, (float) ((cy - y0) / h)));
            GL11.glVertex2d(cx, cy);
            return;
        }
        int seg = (int) Math.ceil(r * 3.0);
        if (seg < 4) seg = 4;
        if (seg > 48) seg = 48;
        for (int i = 0; i <= seg; i++) {
            double ang = Math.toRadians(a0 + (a1 - a0) * i / seg);
            double vx = cx + Math.cos(ang) * r;
            double vy = cy + Math.sin(ang) * r;
            if (gradient) glColor(mix(top, bottom, (float) ((vy - y0) / h)));
            GL11.glVertex2d(vx, vy);
        }
    }

    private static void path(double x, double y, double w, double h,
                             double tl, double tr, double br, double bl,
                             boolean gradient, int top, int bottom) {
        double max = Math.min(w, h) / 2.0;
        if (tl > max) tl = max;
        if (tr > max) tr = max;
        if (br > max) br = max;
        if (bl > max) bl = max;
        if (tl < 0.0) tl = 0.0;
        if (tr < 0.0) tr = 0.0;
        if (br < 0.0) br = 0.0;
        if (bl < 0.0) bl = 0.0;
        double gh = h <= 0.0 ? 1.0 : h;
        corner(x + tl, y + tl, tl, 180.0, 270.0, gradient, y, gh, top, bottom);
        corner(x + w - tr, y + tr, tr, 270.0, 360.0, gradient, y, gh, top, bottom);
        corner(x + w - br, y + h - br, br, 0.0, 90.0, gradient, y, gh, top, bottom);
        corner(x + bl, y + h - bl, bl, 90.0, 180.0, gradient, y, gh, top, bottom);
    }

    // ------------------------------------------------------------------
    //  Primitivas
    // ------------------------------------------------------------------

    public static void rect(double x, double y, double w, double h, int color) {
        if (w <= 0.0 || h <= 0.0) return;
        begin();
        glColor(color);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2d(x, y);
        GL11.glVertex2d(x, y + h);
        GL11.glVertex2d(x + w, y + h);
        GL11.glVertex2d(x + w, y);
        GL11.glEnd();
        end();
    }

    public static void gradientV(double x, double y, double w, double h, int top, int bottom) {
        if (w <= 0.0 || h <= 0.0) return;
        begin();
        GL11.glBegin(GL11.GL_QUADS);
        glColor(top);
        GL11.glVertex2d(x, y);
        glColor(bottom);
        GL11.glVertex2d(x, y + h);
        glColor(bottom);
        GL11.glVertex2d(x + w, y + h);
        glColor(top);
        GL11.glVertex2d(x + w, y);
        GL11.glEnd();
        end();
    }

    public static void gradientH(double x, double y, double w, double h, int left, int right) {
        if (w <= 0.0 || h <= 0.0) return;
        begin();
        GL11.glBegin(GL11.GL_QUADS);
        glColor(left);
        GL11.glVertex2d(x, y);
        glColor(left);
        GL11.glVertex2d(x, y + h);
        glColor(right);
        GL11.glVertex2d(x + w, y + h);
        glColor(right);
        GL11.glVertex2d(x + w, y);
        GL11.glEnd();
        end();
    }

    public static void rounded(double x, double y, double w, double h,
                               double tl, double tr, double br, double bl, int color) {
        if (w <= 0.0 || h <= 0.0) return;
        begin();
        glColor(color);
        GL11.glBegin(GL11.GL_POLYGON);
        path(x, y, w, h, tl, tr, br, bl, false, color, color);
        GL11.glEnd();
        end();
    }

    public static void roundedRect(double x, double y, double w, double h, double radius, int color) {
        rounded(x, y, w, h, radius, radius, radius, radius, color);
    }

    public static void roundedGradient(double x, double y, double w, double h,
                                       double tl, double tr, double br, double bl,
                                       int top, int bottom) {
        if (w <= 0.0 || h <= 0.0) return;
        begin();
        GL11.glBegin(GL11.GL_POLYGON);
        path(x, y, w, h, tl, tr, br, bl, true, top, bottom);
        GL11.glEnd();
        end();
    }

    public static void roundedGradientV(double x, double y, double w, double h,
                                        double radius, int top, int bottom) {
        roundedGradient(x, y, w, h, radius, radius, radius, radius, top, bottom);
    }

    public static void roundedOutline(double x, double y, double w, double h,
                                      double radius, float thickness, int color) {
        if (w <= 0.0 || h <= 0.0) return;
        begin();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(thickness);
        glColor(color);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        path(x + 0.5, y + 0.5, w - 1.0, h - 1.0, radius, radius, radius, radius, false, color, color);
        GL11.glEnd();
        GL11.glLineWidth(1.0f);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        end();
    }

    public static void circle(double cx, double cy, double radius, int color) {
        if (radius <= 0.0) return;
        begin();
        glColor(color);
        int seg = (int) Math.ceil(radius * 6.0);
        if (seg < 12) seg = 12;
        if (seg > 90) seg = 90;
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2d(cx, cy);
        for (int i = 0; i <= seg; i++) {
            double ang = Math.PI * 2.0 * i / seg;
            GL11.glVertex2d(cx + Math.cos(ang) * radius, cy + Math.sin(ang) * radius);
        }
        GL11.glEnd();
        end();
    }

    public static void circleOutline(double cx, double cy, double radius, float thickness, int color) {
        if (radius <= 0.0) return;
        begin();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(thickness);
        glColor(color);
        int seg = (int) Math.ceil(radius * 6.0);
        if (seg < 12) seg = 12;
        if (seg > 90) seg = 90;
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < seg; i++) {
            double ang = Math.PI * 2.0 * i / seg;
            GL11.glVertex2d(cx + Math.cos(ang) * radius, cy + Math.sin(ang) * radius);
        }
        GL11.glEnd();
        GL11.glLineWidth(1.0f);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        end();
    }

    /** Sombra suave por tras de um painel. */
    public static void shadow(double x, double y, double w, double h, double radius) {
        for (int i = 5; i >= 1; i--) {
            int a = 6 + (5 - i) * 5;
            roundedRect(x - i, y - i + 1, w + i * 2, h + i * 2, radius + i, a << 24);
        }
    }

    // ------------------------------------------------------------------
    //  Scissor
    // ------------------------------------------------------------------

    public static void scissorStart(double x, double y, double w, double h) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        int f = sr.getScaleFactor();
        int sx = (int) Math.floor(x * f);
        int sy = (int) Math.floor((sr.getScaledHeight() - (y + h)) * f);
        int sw = (int) Math.ceil(w * f);
        int sh = (int) Math.ceil(h * f);
        if (sw < 0) sw = 0;
        if (sh < 0) sh = 0;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(sx, sy, sw, sh);
    }

    public static void scissorEnd() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    // ------------------------------------------------------------------
    //  Texto
    // ------------------------------------------------------------------

    public static void text(String s, double x, double y, int color) {
        Minecraft.getMinecraft().fontRendererObj.drawString(s, (float) x, (float) y, color, false);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void textShadow(String s, double x, double y, int color) {
        Minecraft.getMinecraft().fontRendererObj.drawString(s, (float) x, (float) y, color, true);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void textRight(String s, double right, double y, int color) {
        text(s, right - textWidth(s), y, color);
    }

    public static void textCentered(String s, double cx, double y, int color) {
        text(s, cx - textWidth(s) / 2.0, y, color);
    }

    public static int textWidth(String s) {
        return Minecraft.getMinecraft().fontRendererObj.getStringWidth(s);
    }

    /** Corta o texto para caber em {@code maxWidth} pixels, com reticencias. */
    public static String trim(String s, int maxWidth) {
        if (s == null) return "";
        if (maxWidth <= 0) return "";
        if (textWidth(s) <= maxWidth) return s;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (textWidth(sb.toString() + s.charAt(i) + "..") > maxWidth) break;
            sb.append(s.charAt(i));
        }
        return sb.append("..").toString();
    }

    /** Mantem apenas o final do texto (usado em campos de digitacao). */
    public static String trimEnd(String s, int maxWidth) {
        if (s == null) return "";
        if (textWidth(s) <= maxWidth) return s;
        String out = s;
        while (out.length() > 0 && textWidth(out) > maxWidth) {
            out = out.substring(1);
        }
        return out;
    }
}
