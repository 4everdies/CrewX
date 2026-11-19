package crewx.clickgui.clone.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * ClickGUI em modo imediato: {@link #render} desenha e, ao mesmo tempo, registra as
 * áreas clicáveis daquele frame; os eventos de mouse usam essas áreas.
 *
 * Controles:
 *  - botão esquerdo no módulo: liga/desliga
 *  - botão direito no módulo: abre/fecha as settings
 *  - botão do meio: define a tecla (próxima tecla = bind, ESC remove); SHIFT + meio: esconde/mostra
 *  - CTRL segurado: mostra os binds ao lado dos módulos
 *  - arrastar o cabeçalho: move o painel; roda do mouse: rola a tela
 */
public final class ClickGui {

    private enum Kind { HEADER, MODULE, SLIDER, BOOL, MODE, MODE_OPTION, TEXT, THEMES, CONFIG }

    private static final class Hit {
        Kind kind;
        float x, y, w, h;
        Panel panel;
        ClickModule mod;
        Setting set;
        float tx, tw; // trilho do slider
        int optionIndex = -1;

        boolean contains(float mx, float my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private static final class Panel {
        final ClickCategory cat;
        float x, y, h;

        Panel(ClickCategory cat, float x, float y) {
            this.cat = cat;
            this.x = x;
            this.y = y;
        }
    }

    public final Theme theme;
    /** Linhas de ajuda no canto inferior esquerdo. */
    public final List<String> hintLines = new ArrayList<String>();
    public String themesLabel = "<- Themes";
    public String configLabel = "-> Config Menu";
    public Runnable onThemes = new Runnable() { public void run() { } };
    public Runnable onConfig = new Runnable() { public void run() { } };

    private final List<Panel> panels = new ArrayList<Panel>();
    private final List<Hit> hits = new ArrayList<Hit>();

    private float sw = 1280f, sh = 720f;
    private float scroll, scrollTarget, maxScroll;
    private boolean laidOut, ctrl, shift;
    /** Se o usuário já arrastou algum painel, não recentraliza. */
    private boolean userDragged;

    private IntFunction<String> keyNamer = new IntFunction<String>() {
        public String apply(int k) { return "KEY" + k; }
    };
    private ClickModule binding;

    private Panel dragPanel;
    private float dragDX, dragDY;
    private NumberSetting dragSlider;
    private float dragTx, dragTw;
    private float lastMx = -1, lastMy = -1;

    public ClickGui(Theme theme, List<ClickCategory> categories) {
        this.theme = theme;
        for (ClickCategory c : categories) panels.add(new Panel(c, 0, 0));
        hintLines.add("[MOUSE3] - Bind Module");
        hintLines.add("[CTRL] - See Module Binds");
        hintLines.add("[SHIFT + MOUSE3] - Hide/Unhide Module");
        resetLayout();
    }

    // ------------------------------------------------------------------ configuração

    public void setScreenSize(float w, float h) {
        boolean changed = (Math.abs(this.sw - w) > 0.5f) || (Math.abs(this.sh - h) > 0.5f);
        this.sw = w;
        this.sh = h;
        // Re-centraliza na primeira vez ou quando a resolução muda e o usuário ainda não arrastou.
        if (!userDragged && (changed || !laidOut)) {
            centerLayout();
        }
    }

    public void setModifiers(boolean ctrl, boolean shift) {
        this.ctrl = ctrl;
        this.shift = shift;
    }

    /** Converte o código de tecla do backend em texto (ex.: Keyboard::getKeyName no MC). */
    public void setKeyNamer(IntFunction<String> namer) {
        this.keyNamer = namer;
    }

    public void resetLayout() {
        userDragged = false;
        centerLayout();
        scroll = scrollTarget = 0;
        laidOut = true;
    }

    /** Centraliza a fileira de painéis na tela (ou empilha em 2 linhas se não couber). */
    public void centerLayout() {
        int n = panels.size();
        if (n == 0) return;
        float gap = theme.panelGap;
        float pw = theme.panelW;
        float totalOneRow = n * pw + Math.max(0, n - 1) * gap;
        float usable = Math.max(100f, sw - theme.marginX * 2f);

        int cols = n;
        if (totalOneRow > usable) {
            // tenta 2 linhas
            cols = (n + 1) / 2;
            if (cols * pw + Math.max(0, cols - 1) * gap > usable) {
                cols = Math.max(1, (int) ((usable + gap) / (pw + gap)));
            }
        }
        int rows = (n + cols - 1) / cols;
        float rowW = cols * pw + Math.max(0, cols - 1) * gap;
        float startX = (sw - rowW) * 0.5f;
        float startY = Math.max(theme.marginY, sh * 0.12f);

        // altura estimada de uma coluna para espaçar linhas
        float rowGap = 14f;
        float estH = theme.headerH + theme.rowH * 8f;

        for (int i = 0; i < n; i++) {
            int r = i / cols;
            int c = i % cols;
            Panel p = panels.get(i);
            p.x = startX + c * (pw + gap);
            p.y = startY + r * (estH + rowGap);
        }
    }

    public boolean isListening() {
        return binding != null;
    }

    public boolean isDraggingSlider() {
        return dragSlider != null;
    }

    private TextSetting editingText;

    public boolean beginTextEdit(TextSetting setting) {
        if (setting == null) return false;
        if (editingText != null && editingText != setting) editingText.editing = false;
        editingText = setting;
        setting.editing = true;
        return true;
    }

    public boolean isEditingText() {
        return editingText != null;
    }

    public boolean keyTyped(char typedChar, int keyCode, boolean repeat) {
        if (editingText == null) return false;
        if (typedChar == '\n' || typedChar == '\r' || keyCode == 28 || keyCode == 1) {
            editingText.editing = false;
            editingText = null;
            return true;
        }
        if (keyCode == 14 || typedChar == '\b') {
            String cur = editingText.get();
            if (cur.length() > 0) editingText.set(cur.substring(0, cur.length() - 1));
            return true;
        }
        if (typedChar >= 32 && typedChar < 127) {
            editingText.set(editingText.get() + typedChar);
            return true;
        }
        return true;
    }

    // ------------------------------------------------------------------ desenho

    public void render(Gfx g, float mx, float my, float dt) {
        lastMx = mx;
        lastMy = my;
        scroll += (scrollTarget - scroll) * Math.min(1f, dt * 14f);
        if (Math.abs(scrollTarget - scroll) < 0.05f) scroll = scrollTarget;

        if (dragSlider != null) applySlider(mx);
        if (dragPanel != null) {
            dragPanel.x = Math.max(-theme.panelW + 30, Math.min(sw - 30, mx - dragDX));
            float py = Math.max(0, Math.min(sh - theme.headerH, my - dragDY));
            dragPanel.y = py - scroll;
        }

        Panel hover = null;
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel p = panels.get(i);
            float py = p.y + scroll;
            if (mx >= p.x && mx < p.x + theme.panelW && my >= py && my < py + p.h) {
                hover = p;
                break;
            }
        }

        hits.clear();
        float bottom = 0;
        for (Panel p : panels) {
            renderPanel(g, p, hover, mx, my, dt);
            bottom = Math.max(bottom, p.y + p.h);
        }
        maxScroll = Math.max(0, bottom + theme.marginY - sh);
        scrollTarget = Math.max(-maxScroll, Math.min(0, scrollTarget));

        renderHints(g, mx, my);
    }

    private void renderPanel(Gfx g, Panel p, Panel hover, float mx, float my, float dt) {
        Theme t = theme;
        float w = t.panelW, px = p.x, py = p.y + scroll;

        // animação de expansão e altura total
        float content = 0;
        for (ClickModule m : p.cat.modules) {
            float target = (m.expanded && !m.settings.isEmpty()) ? 1f : 0f;
            m.anim += (target - m.anim) * Math.min(1f, dt * 16f);
            if (Math.abs(target - m.anim) < 0.003f) m.anim = target;
            content += t.rowH + settingsHeight(m) * m.anim;
        }
        p.h = t.headerH + content;

        // fundo: blur + cabeçalho + corpo (sem sobreposição, para a translucidez ficar uniforme)
        float r = t.radius;
        if (t.backdropBlur) g.backdropBlur(px, py, w, p.h, r, r, r, r, t.blur);
        boolean hasBody = content > 0.5f;
        g.roundRect(px, py, w, t.headerH, r, r, hasBody ? 0 : r, hasBody ? 0 : r, t.header);
        if (hasBody) g.roundRect(px, py + t.headerH, w, content, 0, 0, r, r, t.body);

        // cabeçalho
        float hcy = py + t.headerH / 2f;
        g.text(FontId.HEADER, p.cat.name, px + t.textPadX, hcy, t.text);
        g.glyph(FontId.ICON, p.cat.glyph, px + w - t.iconRight, hcy, t.text);
        addHit(Kind.HEADER, px, py, w, t.headerH, p, null, null);

        // módulos
        float y = py + t.headerH;
        for (ClickModule m : p.cat.modules) {
            boolean hov = hover == p && dragPanel == null && dragSlider == null
                    && mx >= px && mx < px + w && my >= y && my < y + t.rowH;
            if (hov) g.roundRect(px + 3, y + 1, w - 6, t.rowH - 2, 4, t.hover);

            // Cinza só se NÃO tiver nenhuma setting. Com config: branco (off) / rosa (on) full.
            int col;
            if (m.settings.isEmpty()) {
                col = m.isEnabled()
                        ? Theme.withAlpha(t.textEnabled, 0.55f)
                        : t.textMuted;
            } else {
                col = m.isEnabled() ? t.textEnabled : t.text;
            }
            if (m.hidden) col = Theme.withAlpha(col, 0.55f);
            float cy = y + t.rowH / 2f + t.textDy;
            String extra = null;
            if (binding == m) extra = "press a key...";
            else if (ctrl && m.key >= 0) extra = "[" + keyNamer.apply(m.key).toUpperCase() + "]";
            float rightPad = t.padX;
            float extraW = 0f;
            if (extra != null) {
                extraW = g.textWidth(FontId.SMALL, extra) + 8f;
            }
            float nameMax = Math.max(8f, w - t.textPadX - rightPad - extraW);
            g.text(FontId.MODULE, fit(g, FontId.MODULE, m.name, nameMax), px + t.textPadX, cy, col);
            if (extra != null) {
                g.textRight(FontId.SMALL, extra, px + w - t.padX, cy, t.textMuted);
            }
            addHit(Kind.MODULE, px, y, w, t.rowH, p, m, null);
            y += t.rowH;

            float shown = settingsHeight(m) * m.anim;
            if (shown > 0.5f) {
                boolean live = m.expanded || m.anim >= 0.35f;
                g.pushClip(px, y, w, shown);
                float sy = y;
                for (Setting s : m.settings) {
                    if (!s.visible()) continue;
                    if (s instanceof NumberSetting) {
                        renderSlider(g, p, m, (NumberSetting) s, px, sy, dt, live);
                    } else if (s instanceof BooleanSetting) {
                        renderBool(g, p, m, (BooleanSetting) s, px, sy, dt, live);
                    } else if (s instanceof ModeSetting) {
                        renderMode(g, p, m, (ModeSetting) s, px, sy, dt, live);
                    } else if (s instanceof TextSetting) {
                        renderText(g, p, m, (TextSetting) s, px, sy, dt, live);
                    }
                    sy += s.height(t);
                }
                g.popClip();
                y += shown;
            }
        }
    }

    private float settingsHeight(ClickModule m) {
        if (m.settings.isEmpty()) return 0;
        float h = theme.settingsPadBottom;
        for (Setting s : m.settings) {
            if (!s.visible()) continue;
            h += s.height(theme);
        }
        return h;
    }


    /** Corta o texto com reticências para caber em maxW. */
    private String fit(Gfx g, FontId font, String s, float maxW) {
        if (s == null) return "";
        if (maxW <= 4f) return "";
        if (g.textWidth(font, s) <= maxW) return s;
        final String ell = "...";
        float ew = g.textWidth(font, ell);
        if (ew > maxW) return ell;
        // binary search
        int lo = 0, hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (g.textWidth(font, s.substring(0, mid)) + ew <= maxW) lo = mid;
            else hi = mid - 1;
        }
        return lo <= 0 ? ell : s.substring(0, lo) + ell;
    }

    private void renderSlider(Gfx g, Panel p, ClickModule m, NumberSetting n,
                              float px, float y, float dt, boolean live) {
        Theme t = theme;
        float x0 = px + t.padX, tw = t.panelW - 2 * t.padX;
        float target = (float) n.progress();
        if (n.shown < 0) n.shown = target;
        n.shown += (target - n.shown) * Math.min(1f, dt * 20f);
        if (Math.abs(target - n.shown) < 0.001f) n.shown = target;

        String val = n.display();
        float valW = g.textWidth(FontId.LABEL, val);
        float nameMax = Math.max(8f, tw - valW - 8f);
        g.text(FontId.LABEL, fit(g, FontId.LABEL, n.name, nameMax), x0 - 0.5f, y + t.labelCy + t.textDy, t.text);
        g.textRight(FontId.LABEL, val, x0 + tw - 1f, y + t.labelCy + t.textDy, t.textMuted);

        float cy = y + t.trackCy, th = t.trackH;
        g.roundRect(x0, cy - th / 2f, tw, th, th / 2f, t.track);
        float fillW = tw * n.shown;
        if (fillW > 0.5f) {
            g.roundRectH(x0, cy - th / 2f, Math.max(fillW, th), th, th / 2f, t.accentA, t.accentB);
        }
        g.circle(x0 + fillW - 0.5f, cy, t.knobR, t.knob);

        if (live) {
            Hit h = addHit(Kind.SLIDER, x0 - 5, y, tw + 10, t.sliderH, p, m, n);
            h.tx = x0;
            h.tw = tw;
        }
    }

    private void renderBool(Gfx g, Panel p, ClickModule m, BooleanSetting b,
                            float px, float y, float dt, boolean live) {
        Theme t = theme;
        float x0 = px + t.padX, x1 = px + t.panelW - t.padX, cy = y + t.boolH / 2f;
        float ty = cy + t.textDy;
        float target = b.get() ? 1f : 0f;
        if (b.anim < 0) b.anim = target;
        b.anim += (target - b.anim) * Math.min(1f, dt * 18f);
        if (Math.abs(target - b.anim) < 0.01f) b.anim = target;

        float swW = t.switchW, swH = t.switchH, sx = x1 - swW, sy = cy - swH / 2f;
        float nameMax = Math.max(8f, (sx - 8f) - x0);
        g.text(FontId.LABEL, fit(g, FontId.LABEL, b.name, nameMax), x0 - 0.5f, ty, t.text);
        g.roundRect(sx, sy, swW, swH, swH / 2f, 0x24FFFFFF);
        if (b.anim > 0.02f) {
            g.roundRectH(sx, sy, swW, swH, swH / 2f,
                    Theme.withAlpha(t.accentA, b.anim), Theme.withAlpha(t.accentB, b.anim));
        }
        g.circle(sx + swH / 2f + (swW - swH) * b.anim, cy, swH * 0.38f, t.knob);
        if (live) addHit(Kind.BOOL, px, y, t.panelW, t.boolH, p, m, b);
    }

    private void renderMode(Gfx g, Panel p, ClickModule m, ModeSetting s,
                            float px, float y, float dt, boolean live) {
        Theme t = theme;
        float target = s.expanded ? 1f : 0f;
        s.anim += (target - s.anim) * Math.min(1f, dt * 16f);
        if (Math.abs(target - s.anim) < 0.003f) s.anim = target;
        float x0 = px + t.padX, x1 = px + t.panelW - t.padX, cy = y + t.modeH / 2f;
        String modeVal = s.get();
        float valW = g.textWidth(FontId.LABEL, modeVal);
        float nameMax = Math.max(8f, (x1 - x0) - valW - 10f);
        g.text(FontId.LABEL, fit(g, FontId.LABEL, s.name, nameMax), x0 - 0.5f, cy + t.textDy, t.text);
        int valCol = s.expanded ? t.textEnabled : t.textMuted;
        g.textRight(FontId.LABEL, fit(g, FontId.LABEL, modeVal, t.panelW * 0.55f), x1, cy + t.textDy, valCol);
        if (live) addHit(Kind.MODE, px, y, t.panelW, t.modeH, p, m, s);

        if (s.anim > 0.01f) {
            float optY = y + t.modeH;
            float boxH = (s.modes.length * t.modeOptionH + 4f) * s.anim;
            g.pushClip(px, optY, t.panelW, boxH);
            float iy = optY + 2f;
            for (int i = 0; i < s.modes.length; i++) {
                boolean sel = i == s.getIndex();
                int col = sel ? t.textEnabled : t.textMuted;
                if (sel) {
                    g.roundRect(px + 6, iy + 1, t.panelW - 12, t.modeOptionH - 2, 3, 0x28FF58D7);
                }
                g.text(FontId.SMALL, s.modes[i], x0, iy + t.modeOptionH / 2f + t.textDy, col);
                if (live && (s.expanded || s.anim > 0.05f)) {
                    Hit h = addHit(Kind.MODE_OPTION, px, iy, t.panelW, t.modeOptionH, p, m, s);
                    h.optionIndex = i;
                }
                iy += t.modeOptionH;
            }
            g.popClip();
        }
    }


    private void renderText(Gfx g, Panel p, ClickModule m, TextSetting s,
                            float px, float y, float dt, boolean live) {
        Theme t = theme;
        float panelW = t.panelW;
        float x0 = px + t.padX;
        float x1 = px + panelW - t.padX;
        float cy = y + t.textH / 2f;
        boolean editing = editingText == s;
        String val = s.get() == null ? "" : s.get();
        if (editing) val = val + "|";
        float valW = g.textWidth(FontId.LABEL, val.isEmpty() ? " " : val);
        float nameMax = Math.max(8f, (x1 - x0) - Math.min(valW, panelW * 0.55f) - 12f);
        g.text(FontId.LABEL, fit(g, FontId.LABEL, s.name, nameMax), x0 - 0.5f, cy + t.textDy, t.text);
        int valCol = editing ? t.textEnabled : t.textMuted;
        float valMax = Math.max(8f, (x1 - x0) * 0.55f);
        g.textRight(FontId.LABEL, fit(g, FontId.LABEL, val.isEmpty() ? "..." : val, valMax), x1, cy + t.textDy, valCol);
        if (live) addHit(Kind.TEXT, px, y, panelW, t.textH, p, m, s);
    }

    private void renderHints(Gfx g, float mx, float my) {
        Theme t = theme;
        float base = sh - 36f;
        for (int i = 0; i < hintLines.size(); i++) {
            g.text(FontId.SMALL, hintLines.get(i), 3f, base + i * 13f, t.hint);
        }
        float cy = sh / 2f + 1.5f;
        float tw = g.textWidth(FontId.SMALL, themesLabel);
        g.text(FontId.SMALL, themesLabel, 3f, cy, t.hint);
        addHit(Kind.THEMES, 0, cy - 8, tw + 6, 16, null, null, null);
        float cw = g.textWidth(FontId.SMALL, configLabel);
        g.textRight(FontId.SMALL, configLabel, sw - 6f, cy, t.hint);
        addHit(Kind.CONFIG, sw - cw - 12, cy - 8, cw + 12, 16, null, null, null);
    }


    private Hit hitAt(float mx, float my) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.contains(mx, my)) return h;
        }
        return null;
    }

    private Hit addHit(Kind k, float x, float y, float w, float h, Panel p, ClickModule m, Setting s) {
        Hit hit = new Hit();
        hit.kind = k;
        hit.x = x;
        hit.y = y;
        hit.w = w;
        hit.h = h;
        hit.panel = p;
        hit.mod = m;
        hit.set = s;
        hits.add(hit);
        return hit;
    }

    // ------------------------------------------------------------------ entrada

    /** @return true se o clique foi consumido pela GUI. */
    public boolean mouseClicked(float mx, float my, int button) {
        if (binding != null && button != 2) {
            binding = null; // qualquer clique cancela o bind
            return true;
        }
        // Clicar fora de um TEXT fecha o editor
        if (editingText != null && button == 0) {
            Hit th = hitAt(mx, my);
            if (th == null || th.kind != Kind.TEXT || th.set != editingText) {
                editingText.editing = false;
                editingText = null;
            }
        }
        binding = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (!h.contains(mx, my)) continue;
            switch (h.kind) {
                case HEADER:
                    if (button == 0) {
                        panels.remove(h.panel);
                        panels.add(h.panel); // traz para frente
                        dragPanel = h.panel; userDragged = true;
                        dragDX = mx - h.panel.x;
                        dragDY = my - (h.panel.y + scroll);
                    }
                    return true;
                case MODULE:
                    if (button == 0) h.mod.toggle();
                    else if (button == 1) h.mod.setExpanded(!h.mod.expanded, false);
                    else if (button == 2) {
                        if (shift) h.mod.hidden = !h.mod.hidden;
                        else binding = h.mod;
                    }
                    return true;
                case SLIDER:
                    if (button == 0) {
                        dragSlider = (NumberSetting) h.set;
                        dragTx = h.tx;
                        dragTw = h.tw;
                        applySlider(mx);
                    }
                    return true;
                case BOOL:
                    if (button == 0) ((BooleanSetting) h.set).toggle();
                    return true;
                case MODE:
                    if (button == 0 || button == 1) {
                        ModeSetting ms = (ModeSetting) h.set;
                        // close other mode dropdowns in same module
                        for (Setting other : h.mod.settings) {
                            if (other instanceof ModeSetting && other != ms) {
                                ((ModeSetting) other).setExpanded(false, false);
                            }
                        }
                        ms.setExpanded(!ms.expanded, false);
                    }
                    return true;
                case MODE_OPTION:
                    if (button == 0 && h.optionIndex >= 0) {
                        ModeSetting ms = (ModeSetting) h.set;
                        ms.setIndex(h.optionIndex);
                        ms.setExpanded(false, false);
                    }
                    return true;
                case TEXT:
                    if (button == 0 && h.set instanceof TextSetting) {
                        beginTextEdit((TextSetting) h.set);
                    }
                    return true;
                case THEMES:
                    if (button == 0) onThemes.run();
                    return true;
                case CONFIG:
                    if (button == 0) onConfig.run();
                    return true;
            }
        }
        return false;
    }

    public void mouseReleased(float mx, float my, int button) {
        if (button == 0) {
            dragPanel = null;
            dragSlider = null;
        }
    }

    /** Opcional: o arrasto também é tratado a cada frame em {@link #render}. */
    public void mouseDragged(float mx, float my) {
        lastMx = mx;
        lastMy = my;
    }

    /** @param dir +1 = roda para cima, -1 = para baixo. */
    public void mouseWheel(int dir) {
        // Nudge slider under cursor instead of scrolling the page
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.kind == Kind.SLIDER && h.contains(lastMx, lastMy) && h.set instanceof NumberSetting) {
                NumberSetting n = (NumberSetting) h.set;
                double step = n.step > 0 ? n.step : (n.max - n.min) / 100.0;
                n.set(n.get() + dir * step);
                return;
            }
        }
        scrollTarget = Math.max(-maxScroll, Math.min(0, scrollTarget + dir * 40f));
    }

    /**
     * Teclas enquanto a GUI está aguardando um bind.
     * @return true se a tecla foi consumida.
     */
    public boolean keyPressed(int keyCode, boolean escape) {
        if (binding == null) return false;
        binding.setKeyBind(escape ? -1 : keyCode);
        binding = null;
        return true;
    }

    private void applySlider(float mx) {
        double p = Math.max(0, Math.min(1, (mx - dragTx) / dragTw));
        dragSlider.set(dragSlider.min + p * (dragSlider.max - dragSlider.min));
    }
}
