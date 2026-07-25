package myau.gui;

import myau.Myau;
import myau.clickgui.render.RoundedUtils;
import myau.module.Module;
import myau.module.modules.GuiModule;
import myau.property.Property;
import myau.property.properties.*;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class ClickGui extends GuiScreen {

    private final List<CategoryPanel> panels = new ArrayList<CategoryPanel>();
    private Module listeningModule = null;
    private SettingComponent dragging = null;
    private int dragMode = 0;
    private SettingComponent focusedText = null;

    private static final Color TEXT_COLOR = new Color(198, 198, 204);
    private static final Color HOVER_COLOR = new Color(48, 48, 54);
    private static final Color OPTION_BG = new Color(34, 34, 40, 235);
    private static final Color TRACK_COLOR = new Color(62, 62, 70);
    private static final Color KEY_COLOR = new Color(140, 140, 148, 190);
    private static final Color INDICATOR_COLOR = new Color(130, 130, 140);

    private static Color accentColor() {
        return GuiModule.getAccent();
    }

    private static Color bgColor() {
        return new Color(20, 20, 24, GuiModule.getBackgroundAlpha());
    }

    private static Color panelBg() {
        return new Color(27, 27, 32, Math.min(255, GuiModule.getBackgroundAlpha() + 24));
    }

    private static final int PANEL_WIDTH = 112;
    private static final int PANEL_SPACING = 10;
    private static final int TOP_MARGIN = 50;
    private static final int PICKER_HEIGHT = 40;
    private static final int HEADER_HEIGHT = 17;
    private static final int ROW_HEIGHT = 16;
    private static final int SETTING_HEIGHT = 17;
    private static final int OPTION_HEIGHT = 12;

    private static final float PANEL_ROUND = 6.0f;
    private static final float ROW_ROUND = 4.0f;
    private static final float SMALL_ROUND = 3.0f;
    private static final float DRAG_THRESHOLD = 4.0f;
    private static final String[] CATEGORIES = {"Combat", "Movement", "Render", "Player", "Misc", "Script"};
    private static Field modesField;
    private static final float OPEN_SPEED = 6.8f;
    private static final float CLOSE_SPEED = 9.5f;
    private float animation = 0.0f;
    private boolean closing = false;
    private long lastFrame = 0L;
    private final long openedAt = System.currentTimeMillis();
    private float renderScale = 1.0f;
    private float renderOffsetY = 0.0f;
    private float alphaMultiplier = 1.0f;
    private float centerX = 0.0f;
    private float centerY = 0.0f;
    private final Deque<float[]> scissorStack = new ArrayDeque<float[]>();
    @Override
    public void initGui() {
        boolean firstTime = panels.isEmpty();
        panels.clear();

        int x = 20;
        int y = TOP_MARGIN;

        for (int i = 0; i < CATEGORIES.length; i++) {
            if (x > 20 && x + PANEL_WIDTH > width - 10) {
                x = 20;
                y += 50;
            }
            panels.add(new CategoryPanel(i, x, y));
            x += PANEL_WIDTH + PANEL_SPACING;
        }
        if (firstTime) {
            animation = 0.0f;
            closing = false;
            lastFrame = 0L;
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
    private void requestClose() {
        closing = true;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.nanoTime();
        float delta = lastFrame == 0L ? 0.016f : (now - lastFrame) / 1.0e9f;
        if (delta > 0.1f) delta = 0.1f;
        if (delta < 0.0f) delta = 0.0f;
        lastFrame = now;

        animation += (closing ? -CLOSE_SPEED : OPEN_SPEED) * delta;
        if (animation > 1.0f) animation = 1.0f;
        if (animation <= 0.0f) {
            animation = 0.0f;
            if (closing) {
                mc.displayGuiScreen(null);
                return;
            }
        }
        float eased = closing ? easeInQuad(animation) : easeOutBack(animation);
        renderScale = 0.90f + 0.10f * eased;
        renderOffsetY = (1.0f - eased) * 12.0f;
        alphaMultiplier = closing ? animation * animation : animation;
        centerX = width / 2.0f;
        centerY = height / 2.0f;
        drawGradientRect(0, 0, width, height, fade(0xB4101014), fade(0xC8060608));
        float virtualMouseX = toVirtualX(mouseX);
        float virtualMouseY = toVirtualY(mouseY);
        for (int i = 0; i < panels.size(); i++) {
            panels.get(i).updateAnimations(delta);
        }
        if (dragging != null) {
            dragging.updateDrag((int) virtualMouseX, (int) virtualMouseY);
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, centerY, 0.0f);
        GlStateManager.scale(renderScale, renderScale, 1.0f);
        GlStateManager.translate(-centerX, -centerY + renderOffsetY, 0.0f);
        for (int i = 0; i < panels.size(); i++) {
            panels.get(i).render(virtualMouseX, virtualMouseY, i);
        }
        GlStateManager.popMatrix();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }
    private void setFocus(SettingComponent component) {
        if (focusedText != null && focusedText != component) {
            focusedText.finishEditing();
        }
        focusedText = component;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (closing) return;

        float mx = toVirtualX(mouseX);
        float my = toVirtualY(mouseY);

        for (int i = 0; i < panels.size(); i++) {
            if (panels.get(i).mouseClicked(mx, my, mouseButton)) {
                return;
            }
        }
        setFocus(null);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        dragging = null;

        float mx = toVirtualX(mouseX);
        float my = toVirtualY(mouseY);

        for (int i = 0; i < panels.size(); i++) {
            panels.get(i).mouseReleased(mx, my);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;

        float mouseX = toVirtualX(Mouse.getEventX() * width / mc.displayWidth);
        float mouseY = toVirtualY(height - Mouse.getEventY() * height / mc.displayHeight - 1);

        for (int i = 0; i < panels.size(); i++) {
            CategoryPanel panel = panels.get(i);
            if (mouseX >= panel.x && mouseX <= panel.x + PANEL_WIDTH && mouseY >= panel.y + HEADER_HEIGHT) {
                panel.targetScroll -= wheel / 120f * 20;
                return;
            }
        }

        int amount = wheel > 0 ? 15 : -15;
        for (int i = 0; i < panels.size(); i++) {
            panels.get(i).x += amount;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (listeningModule != null) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_DELETE) {
                listeningModule.setKey(0);
            } else {
                listeningModule.setKey(keyCode);
            }
            listeningModule = null;
            return;
        }

        if (focusedText != null) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) {
                setFocus(null);
            } else {
                focusedText.typeChar(typedChar, keyCode);
            }
            return;
        }
        boolean guiKey = keyCode != 0 && keyCode == getGuiKey()
                && System.currentTimeMillis() - openedAt > 300L;
        if (keyCode == Keyboard.KEY_ESCAPE || guiKey) {
            requestClose();
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    private static int getGuiKey() {
        Module module = Myau.moduleManager.modules.get(GuiModule.class);
        return module == null ? 0 : module.getKey();
    }
    private float toVirtualX(float screenX) {
        return (screenX - centerX) / renderScale + centerX;
    }

    private float toVirtualY(float screenY) {
        return (screenY - centerY) / renderScale + centerY - renderOffsetY;
    }

    private int fade(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        if (alpha == 0) alpha = 255;
        alpha = (int) (alpha * alphaMultiplier);
        if (alpha < 0) alpha = 0;
        if (alpha > 255) alpha = 255;
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    private static int withAlpha(int argb, float factor) {
        int alpha = (argb >>> 24) & 0xFF;
        if (alpha == 0) alpha = 255;
        alpha = (int) (alpha * factor);
        if (alpha < 0) alpha = 0;
        if (alpha > 255) alpha = 255;
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    private void round(float x, float y, float w, float h, int color, float radius) {
        if (w <= 0.0f || h <= 0.0f) return;
        int faded = fade(color);
        if ((faded >>> 24) < 3) return;
        float r = Math.min(radius, Math.min(w, h) / 2.0f);
        RoundedUtils.drawRoundedRect(x, y, w, h, faded, r);
    }

    private void round(float x, float y, float w, float h, int color,
                       float topLeft, float topRight, float bottomLeft, float bottomRight) {
        if (w <= 0.0f || h <= 0.0f) return;
        int faded = fade(color);
        if ((faded >>> 24) < 3) return;
        float max = Math.min(w, h) / 2.0f;
        RoundedUtils.drawRoundedRect(x, y, w, h, faded,
                Math.min(topLeft, max), Math.min(topRight, max),
                Math.min(bottomLeft, max), Math.min(bottomRight, max));
    }

    private void text(String string, float x, float y, int color) {
        int faded = fade(color);
        if ((faded >>> 24) < 8) return;
        mc.fontRendererObj.drawString(string, (int) x, (int) y, faded);
    }

    private void triangle(float x1, float y1, float x2, float y2, float x3, float y3, int color) {
        int faded = fade(color);
        if ((faded >>> 24) < 3) return;

        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableAlpha();
        GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
        GlStateManager.color((faded >> 16 & 0xFF) / 255.0f, (faded >> 8 & 0xFF) / 255.0f,
                (faded & 0xFF) / 255.0f, (faded >>> 24) / 255.0f);

        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x3, y3);
        GL11.glEnd();

        GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }
    private void renderSettingsIndicator(float cx, float cy, float open, int color) {
        float radius = 1.0f;
        float spread = 3.4f * (1.0f - open);
        int sideColor = withAlpha(color, 0.35f + 0.65f * (1.0f - open));

        round(cx - spread - radius, cy - radius, radius * 2, radius * 2, sideColor, radius);
        round(cx + spread - radius, cy - radius, radius * 2, radius * 2, sideColor, radius);

        float barWidth = 2.0f + 6.0f * open;
        round(cx - barWidth / 2.0f, cy - radius, barWidth, radius * 2, color, radius);
    }

    private void renderCaret(float cx, float cy, float open, int color) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy, 0.0f);
        GlStateManager.rotate(180.0f * open, 0.0f, 0.0f, 1.0f);
        triangle(-3.0f, -1.6f, 3.0f, -1.6f, 0.0f, 2.2f, color);
        GlStateManager.popMatrix();
    }

    private void scissorOn(float x, float y, float w, float h) {
        if (w < 0) w = 0;
        if (h < 0) h = 0;

        float[] rect = new float[]{x, y, x + w, y + h};
        float[] parent = scissorStack.peek();
        if (parent != null) {
            rect[0] = Math.max(rect[0], parent[0]);
            rect[1] = Math.max(rect[1], parent[1]);
            rect[2] = Math.min(rect[2], parent[2]);
            rect[3] = Math.min(rect[3], parent[3]);
        }
        if (rect[2] < rect[0]) rect[2] = rect[0];
        if (rect[3] < rect[1]) rect[3] = rect[1];

        scissorStack.push(rect);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        applyScissor(rect);
    }

    private void scissorOff() {
        scissorStack.poll();
        float[] parent = scissorStack.peek();
        if (parent == null) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        } else {
            applyScissor(parent);
        }
    }

    private void applyScissor(float[] rect) {
        ScaledResolution sr = new ScaledResolution(mc);
        int factor = sr.getScaleFactor();

        float x1 = centerX + (rect[0] - centerX) * renderScale;
        float x2 = centerX + (rect[2] - centerX) * renderScale;
        float y1 = centerY + (rect[1] + renderOffsetY - centerY) * renderScale;
        float y2 = centerY + (rect[3] + renderOffsetY - centerY) * renderScale;

        int sx = (int) Math.floor(x1 * factor);
        int sw = (int) Math.ceil((x2 - x1) * factor);
        int sh = (int) Math.ceil((y2 - y1) * factor);
        int sy = (int) Math.floor((sr.getScaledHeight() - y2) * factor);

        GL11.glScissor(sx, sy, Math.max(0, sw), Math.max(0, sh));
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        float p = t - 1.0f;
        return 1.0f + c3 * p * p * p + c1 * p * p;
    }

    private static float easeInQuad(float t) {
        return t * t;
    }

    private static float easeOutCubic(float t) {
        float p = 1.0f - t;
        return 1.0f - p * p * p;
    }

    private static float approach(float current, float target, float delta, float speed) {
        float step = delta * speed;
        if (step > 1.0f) step = 1.0f;
        float result = current + (target - current) * step;
        if (Math.abs(target - result) < 0.001f) return target;
        return result;
    }

    private List<Module> getModules(int category) {
        List<Module> list = new ArrayList<Module>();
        for (Module module : Myau.moduleManager.modules.values()) {
            if (Myau.getCategoryForModule(module) == category) list.add(module);
        }
        return list;
    }

    private List<Property<?>> getProperties(Module module) {
        return Myau.propertyManager.properties.get(module);
    }

    private static String[] getModeValues(ModeProperty property) {
        try {
            if (modesField == null) {
                modesField = ModeProperty.class.getDeclaredField("modes");
                modesField.setAccessible(true);
            }
            return (String[]) modesField.get(property);
        } catch (Exception e) {
            return new String[0];
        }
    }

    private static String formatNumber(double value) {
        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(value % 1 == 0 ? 0 : 2, RoundingMode.HALF_UP);
        return bd.stripTrailingZeros().toPlainString();
    }

    private static boolean isSlider(Property<?> property) {
        return property instanceof FloatProperty
                || property instanceof IntProperty
                || property instanceof PercentProperty;
    }

    private static int lerpColor(int a, int b, float t) {
        int ar = a >> 16 & 0xFF, ag = a >> 8 & 0xFF, ab = a & 0xFF;
        int br = b >> 16 & 0xFF, bg = b >> 8 & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    private class CategoryPanel {
        private final int category;
        private int x, y;
        private boolean dragPanel = false;
        private int dragX, dragY;
        private float scrollOffset = 0;
        private float targetScroll = 0;
        private boolean pressingBody = false;
        private boolean scrollDragging = false;
        private float pressY = 0;
        private float pressScroll = 0;

        private final List<ModuleButton> modules = new ArrayList<ModuleButton>();

        CategoryPanel(int category, int x, int y) {
            this.category = category;
            this.x = x;
            this.y = y;
            List<Module> list = getModules(category);
            for (int i = 0; i < list.size(); i++) {
                modules.add(new ModuleButton(list.get(i)));
            }
        }
        void updateAnimations(float delta) {
            scrollOffset += (targetScroll - scrollOffset) * Math.min(1.0f, delta * 14.0f);
            for (int i = 0; i < modules.size(); i++) {
                modules.get(i).updateAnimations(delta);
            }
        }
        private float contentHeight() {
            float total = 0;
            for (int i = 0; i < modules.size(); i++) {
                total += modules.get(i).getTotalHeight();
            }
            return total;
        }

        private float visibleHeight() {
            float max = height - y - HEADER_HEIGHT - 20;
            return max < 0 ? 0 : max;
        }

        void render(float mouseX, float mouseY, int index) {
            if (dragPanel) {
                x = (int) mouseX - dragX;
                y = (int) mouseY - dragY;
            }
            float delay = Math.min(0.45f, index * 0.06f);
            float panelAlpha = clamp01((animation - delay) / Math.max(0.15f, 1.0f - delay));
            float previousAlpha = alphaMultiplier;
            alphaMultiplier = previousAlpha * (closing ? 1.0f : easeOutCubic(panelAlpha));
            float totalHeight = contentHeight();
            float maxVisible = visibleHeight();
            float maxScroll = Math.max(0, totalHeight - maxVisible);
            if (pressingBody) {
                float travel = mouseY - pressY;
                if (!scrollDragging && Math.abs(travel) > DRAG_THRESHOLD) {
                    scrollDragging = true;
                }
                if (scrollDragging) {
                    targetScroll = pressScroll - travel;
                }
            }
            targetScroll = Math.max(0, Math.min(targetScroll, maxScroll));
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            float bodyHeight = Math.min(totalHeight, maxVisible);
            round(x, y, PANEL_WIDTH, HEADER_HEIGHT + bodyHeight, bgColor().getRGB(),
                    PANEL_ROUND, PANEL_ROUND, PANEL_ROUND, PANEL_ROUND);
            round(x, y, PANEL_WIDTH, HEADER_HEIGHT, panelBg().getRGB(),
                    PANEL_ROUND, PANEL_ROUND, 0.0f, 0.0f);
            round(x + 5, y + HEADER_HEIGHT - 1.5f, PANEL_WIDTH - 10, 1.5f,
                    withAlpha(accentColor().getRGB(), 0.85f), 0.75f);
            String title = CATEGORIES[category];
            int titleWidth = mc.fontRendererObj.getStringWidth(title);
            text(title, x + (PANEL_WIDTH - titleWidth) / 2.0f, y + 5, 0xFFFFFFFF);
            if (bodyHeight > 0) {
                scissorOn(x, y + HEADER_HEIGHT, PANEL_WIDTH, bodyHeight);
                float moduleY = y + HEADER_HEIGHT - scrollOffset;
                for (int i = 0; i < modules.size(); i++) {
                    ModuleButton button = modules.get(i);
                    float buttonHeight = button.getTotalHeight();
                    if (moduleY + buttonHeight > y + HEADER_HEIGHT && moduleY < y + HEADER_HEIGHT + bodyHeight) {
                        button.render(x, moduleY, PANEL_WIDTH, mouseX, mouseY);
                    }
                    moduleY += buttonHeight;
                }
                scissorOff();
            }

            if (totalHeight > maxVisible) {
                drawScrollbar(y + HEADER_HEIGHT, bodyHeight, totalHeight, maxScroll);
            }
            alphaMultiplier = previousAlpha;
        }

        private void drawScrollbar(float startY, float visible, float total, float maxScroll) {
            float barX = x + PANEL_WIDTH - 3.5f;
            float track = visible - 4.0f;
            if (track <= 2.0f || total <= 0.0f) return;
            round(barX, startY + 2, 2.0f, track, new Color(45, 45, 52, 170).getRGB(), 1.0f);
            float thumbSize = Math.min(track, Math.max(14.0f, visible / total * track));
            float percent = maxScroll > 0 ? clamp01(scrollOffset / maxScroll) : 0.0f;
            float thumbPos = (track - thumbSize) * percent;

            round(barX, startY + 2 + thumbPos, 2.0f, thumbSize, accentColor().getRGB(), 1.0f);
        }

        boolean mouseClicked(float mouseX, float mouseY, int mouseButton) {
            if (mouseX >= x && mouseX <= x + PANEL_WIDTH && mouseY >= y && mouseY <= y + HEADER_HEIGHT) {
                if (mouseButton == 0) {
                    dragPanel = true;
                    dragX = (int) mouseX - x;
                    dragY = (int) mouseY - y;
                    return true;
                }
            }

            if (!insideBody(mouseX, mouseY)) return false;

            if (mouseButton == 0) {
                if (dispatchBody(mouseX, mouseY, 0, true)) return true;

                pressingBody = true;
                scrollDragging = false;
                pressY = mouseY;
                pressScroll = targetScroll;
                return true;
            }

            return dispatchBody(mouseX, mouseY, mouseButton, false);
        }

        private float bodyHeight() {
            return Math.min(contentHeight(), visibleHeight());
        }

        private boolean insideBody(float mouseX, float mouseY) {
            float body = bodyHeight();
            if (mouseY < y + HEADER_HEIGHT || mouseY > y + HEADER_HEIGHT + body) return false;
            return mouseX >= x && mouseX <= x + PANEL_WIDTH;
        }

        private boolean dispatchBody(float mouseX, float mouseY, int mouseButton, boolean pressPhase) {
            float maxVisible = bodyHeight();
            float moduleY = y + HEADER_HEIGHT - scrollOffset;

            for (int i = 0; i < modules.size(); i++) {
                ModuleButton button = modules.get(i);
                float buttonHeight = button.getTotalHeight();

                if (moduleY + buttonHeight > y + HEADER_HEIGHT && moduleY < y + HEADER_HEIGHT + maxVisible) {
                    if (button.mouseClicked(x, moduleY, PANEL_WIDTH, mouseX, mouseY, mouseButton, pressPhase)) {
                        return true;
                    }
                }
                moduleY += buttonHeight;
            }
            return false;
        }

        void mouseReleased(float mouseX, float mouseY) {
            dragPanel = false;

            if (!pressingBody) return;
            boolean wasScrolling = scrollDragging;
            pressingBody = false;
            scrollDragging = false;
            if (!wasScrolling && insideBody(mouseX, mouseY)) {
                dispatchBody(mouseX, mouseY, 0, false);
            }
        }
    }
    private class ModuleButton {

        private final Module module;
        private boolean expanded = false;
        private final List<SettingComponent> settings = new ArrayList<SettingComponent>();
        private float expandAnim = 0.0f;
        private float hoverAnim = 0.0f;
        private float enabledAnim = 0.0f;

        ModuleButton(Module module) {
            this.module = module;
            List<Property<?>> properties = getProperties(module);
            if (properties != null) {
                for (int i = 0; i < properties.size(); i++) {
                    settings.add(new SettingComponent(properties.get(i)));
                }
            }
            this.enabledAnim = module.isEnabled() ? 1.0f : 0.0f;
        }

        void updateAnimations(float delta) {
            expandAnim = approach(expandAnim, expanded ? 1.0f : 0.0f, delta, 13.0f);
            enabledAnim = approach(enabledAnim, module.isEnabled() ? 1.0f : 0.0f, delta, 15.0f);
            for (int i = 0; i < settings.size(); i++) {
                settings.get(i).updateAnimations(delta);
            }
        }

        private float settingsHeight() {
            float total = 0;
            for (int i = 0; i < settings.size(); i++) {
                SettingComponent setting = settings.get(i);
                if (setting.property.isVisible()) total += setting.getHeight();
            }
            return total;
        }

        float getTotalHeight() {
            if (expandAnim <= 0.001f) return ROW_HEIGHT;
            return ROW_HEIGHT + settingsHeight() * easeOutCubic(expandAnim);
        }

        void render(float x, float y, int width, float mouseX, float mouseY) {
            boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + ROW_HEIGHT;
            hoverAnim = approach(hoverAnim, hovered ? 1.0f : 0.0f, 0.016f, 12.0f);

            float rowX = x + 3;
            float rowW = width - 6;
            float rowY = y + 1;
            float rowH = ROW_HEIGHT - 2;

            if (hoverAnim > 0.01f) {
                round(rowX, rowY, rowW, rowH, withAlpha(HOVER_COLOR.getRGB(), hoverAnim * 0.9f), ROW_ROUND);
            }
            if (enabledAnim > 0.01f) {
                round(rowX, rowY, rowW, rowH, withAlpha(accentColor().getRGB(), enabledAnim), ROW_ROUND);
            }

            String name = module == listeningModule ? "Listening..." : module.getName();
            int textColor = lerpColor(TEXT_COLOR.getRGB(), 0xFFFFFF, enabledAnim);
            text(name, x + 8, y + 4, textColor);

            float indicatorX = x + width - 10;

            if (module.getKey() != 0 && module != listeningModule) {
                String keyName = Keyboard.getKeyName(module.getKey());
                if (keyName != null) {
                    int keyWidth = mc.fontRendererObj.getStringWidth(keyName);
                    float keyX = x + width - keyWidth - (settings.isEmpty() ? 8 : 18);
                    text(keyName, keyX, y + 4, KEY_COLOR.getRGB());
                }
            }

            if (!settings.isEmpty()) {
                int indicatorColor = expandAnim > 0.02f
                        ? lerpColor(INDICATOR_COLOR.getRGB(), accentColor().brighter().getRGB(), expandAnim)
                        : INDICATOR_COLOR.getRGB();
                if (module.isEnabled()) indicatorColor = 0xFFFFFFFF;
                renderSettingsIndicator(indicatorX, y + ROW_HEIGHT / 2.0f, easeOutCubic(expandAnim),
                        withAlpha(indicatorColor, 0.45f + 0.55f * Math.max(expandAnim, hoverAnim)));
            }

            if (expandAnim <= 0.001f) return;

            float openHeight = settingsHeight() * easeOutCubic(expandAnim);
            scissorOn(x, y + ROW_HEIGHT, width, openHeight);
            float slide = (1.0f - easeOutCubic(expandAnim)) * 6.0f;
            float settingY = y + ROW_HEIGHT - slide;
            for (int i = 0; i < settings.size(); i++) {
                SettingComponent setting = settings.get(i);
                if (!setting.property.isVisible()) continue;
                setting.render(x, settingY, width, mouseX, mouseY);
                settingY += setting.getHeight();
            }

            scissorOff();
        }

        boolean mouseClicked(float x, float y, int width, float mouseX, float mouseY, int mouseButton,
                             boolean pressPhase) {
            boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + ROW_HEIGHT;

            if (hovered) {
                if (pressPhase) return false;

                if (mouseButton == 0) {
                    module.toggle();
                } else if (mouseButton == 1) {
                    if (!settings.isEmpty()) expanded = !expanded;
                } else if (mouseButton == 2) {
                    listeningModule = module;
                }
                return true;
            }

            if (expanded && expandAnim > 0.4f) {
                float settingY = y + ROW_HEIGHT;
                for (int i = 0; i < settings.size(); i++) {
                    SettingComponent setting = settings.get(i);
                    if (!setting.property.isVisible()) continue;
                    if (setting.mouseClicked(x, settingY, width, mouseX, mouseY, mouseButton, pressPhase)) return true;
                    settingY += setting.getHeight();
                }
            }
            return false;
        }
    }

    private class SettingComponent {

        private final Property<?> property;
        private boolean dropdownOpen = false;
        private boolean pickerOpen = false;

        private float dropdownAnim = 0.0f;
        private float pickerAnim = 0.0f;
        private float toggleAnim = 0.0f;
        private float hoverAnim = 0.0f;

        private float componentX = 0;
        private float componentWidth = 0;

        private float svX, svY, svW, svH;
        private float hueX, hueY, hueH;
        private float hue, saturation, brightness;

        private String editBuffer = null;
        private boolean editingNumber = false;

        SettingComponent(Property<?> property) {
            this.property = property;
            if (property instanceof BooleanProperty) {
                this.toggleAnim = Boolean.TRUE.equals(property.getValue()) ? 1.0f : 0.0f;
            }
        }

        void updateAnimations(float delta) {
            dropdownAnim = approach(dropdownAnim, dropdownOpen ? 1.0f : 0.0f, delta, 13.0f);
            pickerAnim = approach(pickerAnim, pickerOpen ? 1.0f : 0.0f, delta, 13.0f);
            if (property instanceof BooleanProperty) {
                boolean value = Boolean.TRUE.equals(property.getValue());
                toggleAnim = approach(toggleAnim, value ? 1.0f : 0.0f, delta, 16.0f);
            }
        }

        float getHeight() {
            if (property instanceof ModeProperty && dropdownAnim > 0.001f) {
                float extra = getModeValues((ModeProperty) property).length * OPTION_HEIGHT;
                return SETTING_HEIGHT + extra * easeOutCubic(dropdownAnim);
            }
            if (property instanceof ColorProperty && pickerAnim > 0.001f) {
                return SETTING_HEIGHT + (PICKER_HEIGHT + 6) * easeOutCubic(pickerAnim);
            }
            return SETTING_HEIGHT;
        }

        void render(float x, float y, int width, float mouseX, float mouseY) {
            boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + ROW_HEIGHT;
            hoverAnim = approach(hoverAnim, hovered ? 1.0f : 0.0f, 0.016f, 12.0f);

            round(x + 3, y + 0.5f, width - 6, ROW_HEIGHT - 1,
                    lerpColor(panelBg().getRGB(), OPTION_BG.getRGB(), hoverAnim), SMALL_ROUND);

            if (property instanceof ButtonProperty) {
                renderButton(x, y, width, hoverAnim);
            } else if (property instanceof BooleanProperty) {
                renderBoolean(x, y, width);
            } else if (isSlider(property)) {
                renderNumber(x, y, width, mouseX, mouseY);
            } else if (property instanceof ModeProperty) {
                renderMode(x, y, width, mouseX, mouseY, (ModeProperty) property);
            } else if (property instanceof ColorProperty) {
                renderColor(x, y, width, (ColorProperty) property);
            } else if (property instanceof TextProperty) {
                renderText(x, y, width, (TextProperty) property);
            }
        }

        private void renderBoolean(float x, float y, int width) {
            float switchWidth = 18;
            float switchHeight = 9;
            float switchX = x + width - switchWidth - 6;
            float switchY = y + 3.5f;

            int track = lerpColor(TRACK_COLOR.getRGB(), accentColor().getRGB(), toggleAnim);
            round(switchX, switchY, switchWidth, switchHeight, track, switchHeight / 2.0f);

            float knobSize = 7;
            float knobX = switchX + 1 + (switchWidth - knobSize - 2) * toggleAnim;
            int knob = lerpColor(new Color(140, 140, 148).getRGB(), 0xFFFFFF, toggleAnim);
            round(knobX, switchY + 1, knobSize, knobSize, knob, knobSize / 2.0f);

            text(property.getName(), x + 8, y + 4, TEXT_COLOR.getRGB());
        }

        private void renderNumber(float x, float y, int width, float mouseX, float mouseY) {
            double value = getNumber();
            double min = getMinimum();
            double max = getMaximum();
            double percent = max - min == 0 ? 0 : (value - min) / (max - min);
            if (percent < 0) percent = 0;
            if (percent > 1) percent = 1;

            boolean editing = editingNumber && this == focusedText;
            String valueStr = editing ? (editBuffer == null ? "" : editBuffer) + "_" : valueString();

            text(property.getName(), x + 8, y + 3, TEXT_COLOR.getRGB());

            float boxWidth = valueWidth() + 8;
            float boxX = x + width - boxWidth - 4;
            boolean overValue = mouseX >= boxX && mouseX <= x + width - 4 && mouseY >= y + 1 && mouseY <= y + 12;

            if (editing) {
                round(boxX, y + 1.5f, boxWidth, 10, withAlpha(accentColor().getRGB(), 0.30f), SMALL_ROUND);
            } else if (overValue) {
                round(boxX, y + 1.5f, boxWidth, 10, new Color(58, 58, 66, 200).getRGB(), SMALL_ROUND);
            }

            int valueTextWidth = mc.fontRendererObj.getStringWidth(valueStr);
            text(valueStr, x + width - valueTextWidth - 8, y + 3,
                    editing ? 0xFFFFFFFF : accentColor().getRGB());

            float sliderY = y + 12.5f;
            float sliderHeight = 2.5f;
            float padding = 8;
            float trackWidth = width - padding * 2;

            round(x + padding, sliderY, trackWidth, sliderHeight, TRACK_COLOR.getRGB(), sliderHeight / 2.0f);

            float filledWidth = (float) (trackWidth * percent);
            round(x + padding, sliderY, filledWidth, sliderHeight, accentColor().getRGB(), sliderHeight / 2.0f);

            boolean sliderHovered = mouseX >= x + padding && mouseX <= x + width - padding
                    && mouseY >= y && mouseY <= y + ROW_HEIGHT;
            float thumbSize = dragging == this ? 6.5f : (sliderHovered ? 5.5f : 4.5f);
            float thumbX = x + padding + filledWidth;
            round(thumbX - thumbSize / 2.0f, sliderY + sliderHeight / 2.0f - thumbSize / 2.0f,
                    thumbSize, thumbSize, 0xFFFFFFFF, thumbSize / 2.0f);

            componentX = x;
            componentWidth = width;
        }

        private void renderMode(float x, float y, int width, float mouseX, float mouseY, ModeProperty modeProperty) {
            String displayText = property.getName() + ": " + modeProperty.getModeString();
            text(displayText, x + 8, y + 4, TEXT_COLOR.getRGB());

            renderCaret(x + width - 10, y + ROW_HEIGHT / 2.0f, easeOutCubic(dropdownAnim),
                    dropdownAnim > 0.5f ? accentColor().getRGB() : INDICATOR_COLOR.getRGB());

            if (dropdownAnim <= 0.001f) return;

            String[] values = getModeValues(modeProperty);
            float optionY = y + SETTING_HEIGHT - 1;
            int selected = modeProperty.getValue().intValue();
            float reveal = easeOutCubic(dropdownAnim);

            for (int i = 0; i < values.length; i++) {
                boolean isSelected = i == selected;
                boolean hovered = mouseX >= x + 6 && mouseX <= x + width - 6
                        && mouseY >= optionY && mouseY <= optionY + OPTION_HEIGHT;

                int color;
                if (isSelected) {
                    color = accentColor().getRGB();
                } else if (hovered) {
                    color = HOVER_COLOR.brighter().getRGB();
                } else {
                    color = OPTION_BG.getRGB();
                }

                float previous = alphaMultiplier;
                alphaMultiplier = previous * reveal;

                round(x + 6, optionY, width - 12, OPTION_HEIGHT - 1.5f, color, SMALL_ROUND);
                text(values[i], x + 10, optionY + 2, isSelected ? 0xFFFFFFFF : TEXT_COLOR.getRGB());

                alphaMultiplier = previous;
                optionY += OPTION_HEIGHT;
            }
        }

        private void renderColor(float x, float y, int width, ColorProperty colorProperty) {
            int rgb = colorProperty.getValue().intValue() & 0xFFFFFF;

            float swatchWidth = 18;
            float swatchHeight = 9;
            float swatchX = x + width - swatchWidth - 6;
            float swatchY = y + 3.5f;

            round(swatchX - 1, swatchY - 1, swatchWidth + 2, swatchHeight + 2, TRACK_COLOR.getRGB(), 5.0f);
            round(swatchX, swatchY, swatchWidth, swatchHeight, 0xFF000000 | rgb, 4.5f);

            text(property.getName(), x + 8, y + 4, TEXT_COLOR.getRGB());

            if (pickerAnim <= 0.001f) return;

            float reveal = easeOutCubic(pickerAnim);
            float previous = alphaMultiplier;
            alphaMultiplier = previous * reveal;

            svX = x + 8;
            svY = y + SETTING_HEIGHT + 1;
            svW = width - 16 - 12;
            svH = PICKER_HEIGHT;
            hueX = x + width - 16;
            hueY = svY;
            hueH = PICKER_HEIGHT;

            int pure = Color.HSBtoRGB(hue, 1f, 1f) & 0xFFFFFF;
            for (int i = 0; i < svW; i++) {
                int top = lerpColor(0xFFFFFF, pure, (float) i / svW);
                drawGradientRect((int) (svX + i), (int) svY, (int) (svX + i + 1), (int) (svY + svH),
                        fade(top), fade(0xFF000000));
            }
            for (int i = 0; i < hueH; i++) {
                int c = Color.HSBtoRGB((float) i / hueH, 1f, 1f) & 0xFFFFFF;
                round(hueX, hueY + i, 8, 1, 0xFF000000 | c, 0.0f);
            }

            float markX = svX + saturation * svW;
            float markY = svY + (1f - brightness) * svH;
            round(markX - 2.5f, markY - 2.5f, 5, 5, 0xFFFFFFFF, 2.5f);
            round(markX - 1.5f, markY - 1.5f, 3, 3, 0xFF000000 | Color.HSBtoRGB(hue, saturation, brightness), 1.5f);

            float hueMark = hueY + hue * hueH;
            round(hueX - 1, hueMark - 1.5f, 10, 3, 0xFFFFFFFF, 1.5f);

            alphaMultiplier = previous;
        }

        private void renderText(float x, float y, int width, TextProperty textProperty) {
            text(property.getName(), x + 8, y + 4, TEXT_COLOR.getRGB());

            boolean editing = focusedText == this;
            String value = editing
                    ? (editBuffer == null ? "" : editBuffer)
                    : (textProperty.getValue() == null ? "" : textProperty.getValue());
            String shown = editing ? value + "_" : (value.isEmpty() ? "-" : value);

            int maxWidth = width - mc.fontRendererObj.getStringWidth(property.getName()) - 20;
            while (shown.length() > 1 && mc.fontRendererObj.getStringWidth(shown) > maxWidth) {
                shown = shown.substring(1);
            }

            int textWidth = mc.fontRendererObj.getStringWidth(shown);
            text(shown, x + width - textWidth - 8, y + 4, editing ? 0xFFFFFFFF : accentColor().getRGB());
        }

        private void renderButton(float x, float y, int width, float hover) {
            int color = lerpColor(new Color(48, 48, 55).getRGB(), accentColor().getRGB(), hover);
            round(x + 6, y + 2, width - 12, ROW_HEIGHT - 4, color, ROW_ROUND);

            String name = property.getName();
            int nameWidth = mc.fontRendererObj.getStringWidth(name);
            text(name, x + (width - nameWidth) / 2.0f, y + 4,
                    lerpColor(TEXT_COLOR.getRGB(), 0xFFFFFF, hover));
        }


        boolean mouseClicked(float x, float y, int width, float mouseX, float mouseY, int mouseButton,
                             boolean pressPhase) {
            boolean onRow = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + ROW_HEIGHT;

            if (isSlider(property)) {
                boolean onValue = valueBoxContains(x, y, width, mouseX, mouseY);

                if (pressPhase) {
                    if (onValue || !onRow) return false;
                    dragging = this;
                    dragMode = 0;
                    componentX = x;
                    componentWidth = width;
                    updateSlider((int) mouseX);
                    return true;
                }

                if (onValue) {
                    setFocus(this);
                    this.editingNumber = true;
                    this.editBuffer = formatNumber(getNumber());
                    return true;
                }
                return false;
            }

            if (property instanceof ColorProperty) {
                ColorProperty colorProperty = (ColorProperty) property;

                if (pressPhase) {
                    if (!pickerOpen || pickerAnim <= 0.4f) return false;
                    if (mouseX >= svX && mouseX <= svX + svW && mouseY >= svY && mouseY <= svY + svH) {
                        dragging = this;
                        dragMode = 1;
                        updateSaturation((int) mouseX, (int) mouseY, colorProperty);
                        return true;
                    }
                    if (mouseX >= hueX - 2 && mouseX <= hueX + 10 && mouseY >= hueY && mouseY <= hueY + hueH) {
                        dragging = this;
                        dragMode = 2;
                        updateHue((int) mouseY, colorProperty);
                        return true;
                    }
                    return false;
                }

                if (onRow) {
                    pickerOpen = !pickerOpen;
                    if (pickerOpen) {
                        int rgb = colorProperty.getValue().intValue() & 0xFFFFFF;
                        float[] hsb = Color.RGBtoHSB(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, null);
                        hue = hsb[0];
                        saturation = hsb[1];
                        brightness = hsb[2];
                    }
                    return true;
                }
                return false;
            }
            if (pressPhase) return false;

            if (property instanceof ButtonProperty) {
                if (onRow) {
                    ((ButtonProperty) property).press();
                    return true;
                }
                return false;
            }

            if (property instanceof BooleanProperty) {
                if (onRow) {
                    property.setValue(!Boolean.TRUE.equals(property.getValue()));
                    return true;
                }
                return false;
            }

            if (property instanceof ModeProperty) {
                ModeProperty modeProperty = (ModeProperty) property;

                if (dropdownOpen && dropdownAnim > 0.4f) {
                    String[] values = getModeValues(modeProperty);
                    float optionY = y + SETTING_HEIGHT - 1;
                    for (int i = 0; i < values.length; i++) {
                        if (mouseX >= x + 6 && mouseX <= x + width - 6
                                && mouseY >= optionY && mouseY <= optionY + OPTION_HEIGHT) {
                            modeProperty.setValue(Integer.valueOf(i));
                            dropdownOpen = false;
                            return true;
                        }
                        optionY += OPTION_HEIGHT;
                    }
                }

                if (onRow) {
                    dropdownOpen = !dropdownOpen;
                    return true;
                }
                return false;
            }

            if (property instanceof TextProperty) {
                if (onRow) {
                    TextProperty textProperty = (TextProperty) property;
                    if (mouseButton == 1) {
                        textProperty.setValue("");
                        editBuffer = "";
                    } else {
                        setFocus(this);
                        editingNumber = false;
                        editBuffer = textProperty.getValue() == null ? "" : textProperty.getValue();
                    }
                    return true;
                }
                return false;
            }

            return false;
        }

        /** area do numero no canto direito da linha - e o que abre a digitacao. */
        private boolean valueBoxContains(float x, float y, int width, float mouseX, float mouseY) {
            if (!isSlider(property)) return false;
            float boxWidth = valueWidth() + 8;
            float boxX = x + width - boxWidth - 4;
            return mouseX >= boxX && mouseX <= x + width - 4 && mouseY >= y + 1 && mouseY <= y + 12;
        }

        private float valueWidth() {
            String shown = editingNumber && this == focusedText
                    ? (editBuffer == null ? "" : editBuffer) + "_"
                    : valueString();
            return Math.max(18, mc.fontRendererObj.getStringWidth(shown));
        }

        private String valueString() {
            double value = getNumber();
            return property instanceof PercentProperty ? formatNumber(value) + "%" : formatNumber(value);
        }

        /** grava o numero digitado, preso entre o minimo e o maximo da propriedade. */
        void finishEditing() {
            if (!editingNumber) {
                editBuffer = null;
                return;
            }
            editingNumber = false;

            String typed = editBuffer == null ? "" : editBuffer.trim().replace("%", "").replace(',', '.');
            editBuffer = null;
            if (typed.isEmpty() || typed.equals("-") || typed.equals(".")) return;

            double parsed;
            try {
                parsed = Double.parseDouble(typed);
            } catch (NumberFormatException e) {
                return;
            }

            double min = getMinimum();
            double max = getMaximum();
            double clamped = Math.max(min, Math.min(max, parsed));

            if (property instanceof FloatProperty) {
                ((FloatProperty) property).setValue(Float.valueOf((float) (Math.round(clamped * 100.0) / 100.0)));
            } else if (property instanceof IntProperty) {
                ((IntProperty) property).setValue(Integer.valueOf((int) Math.round(clamped)));
            } else if (property instanceof PercentProperty) {
                ((PercentProperty) property).setValue(Integer.valueOf((int) Math.round(clamped)));
            }
        }

        void updateDrag(int mouseX, int mouseY) {
            if (dragMode == 0) {
                updateSlider(mouseX);
            } else if (property instanceof ColorProperty) {
                ColorProperty colorProperty = (ColorProperty) property;
                if (dragMode == 1) {
                    updateSaturation(mouseX, mouseY, colorProperty);
                } else {
                    updateHue(mouseY, colorProperty);
                }
            }
        }

        private void updateSlider(int mouseX) {
            if (componentWidth <= 16) return;

            double percent = Math.max(0, Math.min(1, (mouseX - componentX - 8.0) / (componentWidth - 16.0)));
            double min = getMinimum();
            double max = getMaximum();
            double raw = min + (max - min) * percent;

            if (property instanceof FloatProperty) {
                raw = Math.round(raw * 100.0) / 100.0;
                ((FloatProperty) property).setValue(Float.valueOf((float) Math.max(min, Math.min(max, raw))));
            } else if (property instanceof IntProperty) {
                long rounded = Math.round(raw);
                ((IntProperty) property).setValue(Integer.valueOf((int) Math.max(min, Math.min(max, rounded))));
            } else if (property instanceof PercentProperty) {
                long rounded = Math.round(raw);
                ((PercentProperty) property).setValue(Integer.valueOf((int) Math.max(min, Math.min(max, rounded))));
            }
        }

        private void updateSaturation(int mouseX, int mouseY, ColorProperty property) {
            if (svW <= 0 || svH <= 0) return;
            saturation = clamp01((mouseX - svX) / svW);
            brightness = clamp01(1f - (mouseY - svY) / svH);
            applyColor(property);
        }

        private void updateHue(int mouseY, ColorProperty property) {
            if (hueH <= 0) return;
            hue = clamp01((mouseY - hueY) / hueH);
            applyColor(property);
        }

        private void applyColor(ColorProperty property) {
            property.setValue(Integer.valueOf(Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF));
        }

        void typeChar(char typedChar, int keyCode) {
            if (editingNumber) {
                if (editBuffer == null) editBuffer = "";

                if (keyCode == Keyboard.KEY_BACK) {
                    if (!editBuffer.isEmpty()) editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
                    return;
                }

                boolean digit = typedChar >= '0' && typedChar <= '9';
                boolean dot = (typedChar == '.' || typedChar == ',') && editBuffer.indexOf('.') < 0
                        && !(property instanceof IntProperty) && !(property instanceof PercentProperty);
                boolean minus = typedChar == '-' && editBuffer.isEmpty() && getMinimum() < 0;

                if (digit) {
                    editBuffer += typedChar;
                } else if (dot) {
                    editBuffer += '.';
                } else if (minus) {
                    editBuffer += '-';
                }
                return;
            }

            if (!(property instanceof TextProperty)) return;
            TextProperty textProperty = (TextProperty) property;

            if (editBuffer == null) {
                editBuffer = textProperty.getValue() == null ? "" : textProperty.getValue();
            }

            if (keyCode == Keyboard.KEY_BACK) {
                if (!editBuffer.isEmpty()) editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
            } else if (typedChar >= ' ' && typedChar != 127) {
                editBuffer += typedChar;
            } else {
                return;
            }
            textProperty.setValue(editBuffer);
        }

        private double getNumber() {
            if (property instanceof FloatProperty) return ((FloatProperty) property).getValue().doubleValue();
            if (property instanceof IntProperty) return ((IntProperty) property).getValue().doubleValue();
            if (property instanceof PercentProperty) return ((PercentProperty) property).getValue().doubleValue();
            return 0;
        }

        private double getMinimum() {
            if (property instanceof FloatProperty) return ((FloatProperty) property).getMinimum().doubleValue();
            if (property instanceof IntProperty) return ((IntProperty) property).getMinimum().doubleValue();
            if (property instanceof PercentProperty) return ((PercentProperty) property).getMinimum().doubleValue();
            return 0;
        }

        private double getMaximum() {
            if (property instanceof FloatProperty) return ((FloatProperty) property).getMaximum().doubleValue();
            if (property instanceof IntProperty) return ((IntProperty) property).getMaximum().doubleValue();
            if (property instanceof PercentProperty) return ((PercentProperty) property).getMaximum().doubleValue();
            return 1;
        }
    }
}
