package crewx.clickgui.clone.mc;

import crewx.clickgui.clone.mc.Fonts;
import crewx.clickgui.clone.core.FontId;
import crewx.clickgui.clone.core.Gfx;
import crewx.clickgui.clone.core.Theme;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;

/**
 * Backend OpenGL (LWJGL 2, modo imediato) para Minecraft 1.8.9.
 * Desenha em pixels reais da janela: use {@link #begin} / {@link #end} em volta do render.
 * Os cantos têm anti-aliasing por "feather" (anel de 1px com alpha 0).
 *
 * Limitação: {@link #backdropBlur} não está implementado (o padrão do {@link Gfx} é ignorar).
 * Para ter o blur do fundo, sobrescreva-o com o seu shader de blur (Kawase/Gaussiano).
 */
public final class McGfx implements Gfx {
    private final Map<FontId, TtfFont> fonts = new EnumMap<FontId, TtfFont>(FontId.class);
    private final Deque<int[]> clips = new ArrayDeque<int[]>();
    private int displayHeight;

    // contorno da forma atual: posição e normal de cada vértice
    private float[] px = new float[160], py = new float[160], nx = new float[160], ny = new float[160];
    private int count;

    /** Prepara o estado do OpenGL. Chame antes de {@code gui.render(...)}. */
    public void begin(int displayWidth, int displayHeight) {
        this.displayHeight = displayHeight;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, displayWidth, displayHeight, 0, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        clips.clear();
    }

    /** Restaura o estado do OpenGL. */
    public void end() {
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopAttrib();
    }

    // ------------------------------------------------------------------ formas

    @Override
    public void roundRect(float x, float y, float w, float h, float tl, float tr, float br, float bl, int argb) {
        shape(x, y, w, h, tl, tr, br, bl, argb, argb);
    }

    @Override
    public void roundRectH(float x, float y, float w, float h, float r, int left, int right) {
        shape(x, y, w, h, r, r, r, r, left, right);
    }

    private void shape(float x, float y, float w, float h, float tl, float tr, float br, float bl, int cL, int cR) {
        if (w <= 0 || h <= 0) return;
        float m = Math.min(w, h) / 2f;
        tl = Math.min(tl, m);
        tr = Math.min(tr, m);
        br = Math.min(br, m);
        bl = Math.min(bl, m);
        count = 0;
        corner(x + tl, y + tl, tl, 180, -1, -1);
        corner(x + w - tr, y + tr, tr, 270, 1, -1);
        corner(x + w - br, y + h - br, br, 0, 1, 1);
        corner(x + bl, y + h - bl, bl, 90, -1, 1);

        boolean grad = cL != cR;
        GL11.glDisable(GL11.GL_TEXTURE_2D);

        // miolo (encolhido 0.5px)
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        color(grad ? Theme.lerp(cL, cR, 0.5f) : cL);
        GL11.glVertex2f(x + w / 2f, y + h / 2f);
        for (int i = 0; i <= count; i++) {
            int k = i % count;
            float vx = px[k] - nx[k] * 0.5f, vy = py[k] - ny[k] * 0.5f;
            color(grad ? at(cL, cR, (vx - x) / w) : cL);
            GL11.glVertex2f(vx, vy);
        }
        GL11.glEnd();

        // borda suave (anti-aliasing): de alpha cheio até 0, 1px de largura
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        for (int i = 0; i <= count; i++) {
            int k = i % count;
            float ix = px[k] - nx[k] * 0.5f, iy = py[k] - ny[k] * 0.5f;
            float ex = px[k] + nx[k] * 0.5f, ey = py[k] + ny[k] * 0.5f;
            int c = grad ? at(cL, cR, (ix - x) / w) : cL;
            color(c);
            GL11.glVertex2f(ix, iy);
            color(c & 0x00FFFFFF);
            GL11.glVertex2f(ex, ey);
        }
        GL11.glEnd();
    }

    private void corner(float cx, float cy, float r, int startDeg, float sx, float sy) {
        if (r <= 0.01f) {
            add(cx, cy, sx * 0.70711f, sy * 0.70711f);
            return;
        }
        int seg = Math.max(3, (int) Math.ceil(r * 0.9f));
        for (int i = 0; i <= seg; i++) {
            double a = Math.toRadians(startDeg + 90.0 * i / seg);
            float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
            add(cx + cs * r, cy + sn * r, cs, sn);
        }
    }

    private void add(float x, float y, float nxv, float nyv) {
        if (count == px.length) {
            int n = count * 2;
            px = java.util.Arrays.copyOf(px, n);
            py = java.util.Arrays.copyOf(py, n);
            nx = java.util.Arrays.copyOf(nx, n);
            ny = java.util.Arrays.copyOf(ny, n);
        }
        px[count] = x;
        py[count] = y;
        nx[count] = nxv;
        ny[count] = nyv;
        count++;
    }

    private static int at(int a, int b, float t) {
        return Theme.lerp(a, b, t < 0 ? 0 : (t > 1 ? 1 : t));
    }

    private static void color(int argb) {
        GL11.glColor4f(((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f,
                (argb & 255) / 255f, ((argb >>> 24) & 255) / 255f);
    }

    // ------------------------------------------------------------------ texto

    private TtfFont font(FontId id) {
        TtfFont f = fonts.get(id);
        if (f == null) {
            f = new TtfFont(Fonts.get(id));
            fonts.put(id, f);
        }
        return f;
    }

    @Override
    public void text(FontId id, String s, float x, float cy, int argb) {
        TtfFont f = font(id);
        f.draw(s, Math.round(x), Math.round(cy + f.capHeight / 2f), argb);
    }

    @Override
    public void glyph(FontId id, String s, float cx, float cy, int argb) {
        font(id).drawCentered(s, cx, cy, argb);
    }

    @Override
    public float textWidth(FontId id, String s) {
        return font(id).width(s);
    }

    // ------------------------------------------------------------------ recorte

    @Override
    public void pushClip(float x, float y, float w, float h) {
        int x0 = Math.round(x), y0 = Math.round(y), x1 = Math.round(x + w), y1 = Math.round(y + h);
        if (!clips.isEmpty()) {
            int[] c = clips.peek();
            x0 = Math.max(x0, c[0]);
            y0 = Math.max(y0, c[1]);
            x1 = Math.min(x1, c[2]);
            y1 = Math.min(y1, c[3]);
        }
        int[] r = new int[]{x0, y0, x1, y1};
        clips.push(r);
        apply(r);
    }

    @Override
    public void popClip() {
        clips.pop();
        if (clips.isEmpty()) GL11.glDisable(GL11.GL_SCISSOR_TEST);
        else apply(clips.peek());
    }

    private void apply(int[] c) {
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(c[0], displayHeight - c[3], Math.max(0, c[2] - c[0]), Math.max(0, c[3] - c[1]));
    }
}
