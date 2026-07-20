package myau.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import myau.clickgui.bridge.BridgeClient;
import myau.clickgui.bridge.BridgeModule;
import myau.clickgui.font.Fonts;
import myau.clickgui.render.RenderUtils;
import myau.clickgui.render.RoundedUtils;
import myau.clickgui.value.Value;
import myau.clickgui.value.settings.BooleanValue;
import myau.clickgui.value.settings.ColorValue;
import myau.clickgui.value.settings.ModeValue;
import myau.clickgui.value.settings.NumberValue;
import myau.clickgui.value.settings.RangeValue;
import myau.clickgui.value.settings.StringValue;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class ModernClickGui extends GuiScreen {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File POS_FILE = new File("./config/CrewX/", "gui_position.json");
    private static final int SIDEBAR_WIDTH = 120;
    private static final int HEADER_HEIGHT = 32;
    private static final int MODULE_ITEM_HEIGHT = 28;

    private int windowWidth;
    private int windowHeight;
    private int windowX;
    private int windowY;
    private boolean dragging = false;
    private int dragOffX, dragOffY;
    private static int selectedCategory = 0;
    private static BridgeModule selectedModule = null;
    private static BridgeModule listeningModule = null;
    private static float moduleScroll;
    private static float moduleScrollTarget;
    private static float settingScroll;
    private static float settingScrollTarget;
    private DraggingSlider draggingSlider = null;
    private EditingString editingString = null;
    private String editingBuffer = "";
    private boolean dragScrolling = false;
    private int dragScrollLastY;
    private boolean draggingModuleScrollbar = false;
    private boolean draggingSettingScrollbar = false;
    private final Map<String, SettingState> settingStates = new HashMap<>();
    private static final int COLOR_WINDOW_BG = 0xFA111111;
    private static final int COLOR_HEADER_BG = 0xFF141414;
    private static final int COLOR_SIDEBAR_BG = 0xF5131313;
    private static final int COLOR_CONTENT_BG = 0xF0181818;
    private static final int COLOR_ACCENT = 0xFF7891FF;
    private static final int COLOR_TEXT = 0xFFD2D2D2;
    private static final int COLOR_TEXT_SECONDARY = 0xFF8C8C8C;
    private static final int COLOR_SEPARATOR = 0xFF232323;
    private static final int COLOR_HOVER = 0xFF202020;
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        if (dragging) {
            windowX = clamp(mouseX - dragOffX, 0, Math.max(0, width - windowWidth));
            windowY = clamp(mouseY - dragOffY, 0, Math.max(0, height - windowHeight));
        }

        if (draggingSlider != null) {
            draggingSlider.update(mouseX);
        }

        if (draggingModuleScrollbar) {
            int scrollbarY = windowY + HEADER_HEIGHT + 1 + 10;
            int scrollbarH = windowHeight - HEADER_HEIGHT - 1 - 20;
            List<BridgeModule> modules = getModulesForCategory(selectedCategory);
            int totalH = modules.size() * MODULE_ITEM_HEIGHT;
            float maxS = Math.max(0, totalH - scrollbarH);
            float thumbH = Math.max(16, (float) scrollbarH / totalH * scrollbarH);
            float clickY = mouseY - scrollbarY;
            float newThumbPos = clickY - thumbH / 2;
            newThumbPos = clamp(newThumbPos, 0, scrollbarH - thumbH);
            if (maxS > 0 && scrollbarH > thumbH) moduleScrollTarget = newThumbPos / (scrollbarH - thumbH) * maxS;
        } else if (draggingSettingScrollbar) {
            int scrollbarY = windowY + HEADER_HEIGHT + 1 + 38;
            int scrollbarH = windowHeight - HEADER_HEIGHT - 1 - 38;
            int totalH = 0;
            if (selectedModule != null) {
                for (Value v : selectedModule.getVisibleSettings()) {
                    totalH += getSettingHeight(v) + 6;
                }
            }
            float maxS = Math.max(0, totalH - scrollbarH);
            float thumbH = Math.max(16, (float) scrollbarH / totalH * scrollbarH);
            float clickY = mouseY - scrollbarY;
            float newThumbPos = clickY - thumbH / 2;
            newThumbPos = clamp(newThumbPos, 0, scrollbarH - thumbH);
            if (maxS > 0 && scrollbarH > thumbH) settingScrollTarget = newThumbPos / (scrollbarH - thumbH) * maxS;
        } else if (dragScrolling && Mouse.isButtonDown(0)) {
            int dy = mouseY - dragScrollLastY;
            if (dy != 0) {
                moduleScrollTarget += dy;
                dragScrollLastY = mouseY;
            }
        }

        moduleScroll = lerp(moduleScroll, moduleScrollTarget, 0.18f);
        settingScroll = lerp(settingScroll, settingScrollTarget, 0.18f);

        drawWindow(mouseX, mouseY);
    }

    private void drawWindow(int mouseX, int mouseY) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GlStateManager.disableDepth();
        GlStateManager.disableCull();
        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        RoundedUtils.drawRoundedRect(windowX - 3, windowY - 3, windowWidth + 6, windowHeight + 6, 0x50000000, 8);
        RoundedUtils.drawRoundedRect(windowX, windowY, windowWidth, windowHeight, COLOR_WINDOW_BG, 6);
        RoundedUtils.drawRoundedRect(windowX, windowY, windowWidth, HEADER_HEIGHT, COLOR_HEADER_BG, 6);
        RenderUtils.drawRect(windowX, windowY + HEADER_HEIGHT - 5, windowX + windowWidth, windowY + HEADER_HEIGHT, COLOR_HEADER_BG);

        Fonts.drawString("CrewX", windowX + 12, windowY + 10, COLOR_ACCENT, "");

        RenderUtils.drawRect(windowX, windowY + HEADER_HEIGHT, windowX + windowWidth, windowY + HEADER_HEIGHT + 1, COLOR_SEPARATOR);

        drawSidebar(mouseX, mouseY);

        int contentX = windowX + SIDEBAR_WIDTH;
        int contentY = windowY + HEADER_HEIGHT + 1;
        RenderUtils.drawRect(contentX, contentY, contentX + 1, contentY + windowHeight - HEADER_HEIGHT - 1, COLOR_SEPARATOR);

        int moduleWidth = (int) (windowWidth * 0.33);
        drawModuleList(contentX + 1, contentY, moduleWidth - 1, windowHeight - HEADER_HEIGHT - 1, mouseX, mouseY);

        int settingsX = contentX + moduleWidth;
        RenderUtils.drawRect(settingsX, contentY, settingsX + 1, contentY + windowHeight - HEADER_HEIGHT - 1, COLOR_SEPARATOR);
        drawSettings(settingsX + 1, contentY, windowWidth - SIDEBAR_WIDTH - moduleWidth - 1, windowHeight - HEADER_HEIGHT - 1, mouseX, mouseY);

        GL11.glPopAttrib();
    }

    private void drawSidebar(int mouseX, int mouseY) {
        int sx = windowX;
        int sy = windowY + HEADER_HEIGHT + 1;
        int sh = windowHeight - HEADER_HEIGHT - 1;
        RoundedUtils.drawRoundedRect(sx, sy, SIDEBAR_WIDTH, sh, COLOR_SIDEBAR_BG, 0);

        int cy = sy + 10;
        for (int i = 0; i < 5; i++) {
            boolean sel = selectedCategory == i;
            boolean hov = mouseX >= sx && mouseX <= sx + SIDEBAR_WIDTH && mouseY >= cy && mouseY <= cy + 26;
            if (sel || hov) {
                int bg = sel ? 0x287891FF : COLOR_HOVER;
                RoundedUtils.drawRoundedRect(sx + 6, cy, SIDEBAR_WIDTH - 12, 26, bg, 4);
            }
            if (sel) {
                RenderUtils.drawRect(sx + 4, cy + 6, sx + 6, cy + 20, COLOR_ACCENT);
            }
            int tc = sel ? COLOR_ACCENT : (hov ? COLOR_TEXT : COLOR_TEXT_SECONDARY);
            Fonts.drawString(getCategoryName(i), sx + 14, cy + 7, tc, "");
            cy += 30;
        }
    }

    private void drawModuleList(int x, int y, int w, int h, int mouseX, int mouseY) {
        RenderUtils.drawRect(x, y, x + w, y + h, COLOR_CONTENT_BG);

        List<BridgeModule> modules = getModulesForCategory(selectedCategory);
        int totalH = modules.size() * MODULE_ITEM_HEIGHT;
        int maxScroll = Math.max(0, totalH - (h - 20));
        moduleScrollTarget = clamp(moduleScrollTarget, 0, maxScroll);

        RenderUtils.enableScisor();
        ScaledResolution sr = new ScaledResolution(mc);
        RenderUtils.scissor(sr, x, y + 10, w, h - 20);

        int my = y + 10 - (int) moduleScroll;
        for (BridgeModule mod : modules) {
            boolean sel = mod == selectedModule;
            boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= my && mouseY <= my + MODULE_ITEM_HEIGHT;
            if (sel || hov) {
                int bg = sel ? 0x307891FF : COLOR_HOVER;
                RoundedUtils.drawRoundedRect(x + 6, my, w - 12, MODULE_ITEM_HEIGHT, bg, 4);
            }
            int dot = mod.isEnabled() ? COLOR_ACCENT : 0xFF3C3C3C;
            RenderUtils.drawCircle(x + 14, my + MODULE_ITEM_HEIGHT / 2, 4, dot);
            String label = mod == listeningModule ? "Press a key..." : mod.getName();
            int tc = mod.isEnabled() ? COLOR_TEXT : COLOR_TEXT_SECONDARY;
            Fonts.drawString(label, x + 24, my + 8, tc, "");

            if (mod.getKey() != 0 && mod != listeningModule) {
                String keyName = getKeyName(mod.getKey());
                int kw = (int) Fonts.getWidth(keyName, "");
                int bgW = kw + 10;
                int bgH = 18;
                int bgX = x + w - bgW - 8;
                int bgY = my + 5;
                RoundedUtils.drawRoundedRect(bgX, bgY, bgW, bgH, 0xFF282828, 3);
                Fonts.drawStringCentered(keyName, bgX + bgW / 2.0, bgY + bgH / 2.0, COLOR_TEXT_SECONDARY, "");
            }
            my += MODULE_ITEM_HEIGHT;
        }

        RenderUtils.disableScisor();
        if (totalH > h - 20) drawScrollbar(x + w - 8, y + 10, h - 20, totalH, moduleScroll);
    }

    private void drawSettings(int x, int y, int w, int h, int mouseX, int mouseY) {
        RenderUtils.drawRect(x, y, x + w, y + h, COLOR_CONTENT_BG);

        if (selectedModule == null) {
            String text = "Select a module";
            int tw = (int) Fonts.getWidth(text, "");
            Fonts.drawString(text, x + (w - tw) / 2f, y + h / 2f - 4, COLOR_TEXT_SECONDARY, "");
            return;
        }

        Fonts.drawString(selectedModule.getName(), x + 12, y + 12, COLOR_TEXT, "");
        RenderUtils.drawRect(x + 12, y + 30, x + w - 12, y + 31, COLOR_SEPARATOR);

        int settingsY = y + 38;
        int availH = h - (settingsY - y);

        List<Value> settings = selectedModule.getVisibleSettings();
        int totalH = 0;
        for (Value v : settings) {
            totalH += getSettingHeight(v) + 6;
        }

        int maxScroll = Math.max(0, totalH - availH);
        settingScrollTarget = clamp(settingScrollTarget, 0, maxScroll);

        RenderUtils.enableScisor();
        ScaledResolution sr = new ScaledResolution(mc);
        RenderUtils.scissor(sr, x, settingsY, w, availH);

        int currentY = settingsY - (int) settingScroll;
        for (Value v : settings) {
            drawSetting(v, x + 12, currentY, w - 24, mouseX, mouseY);
            currentY += getSettingHeight(v) + 6;
        }

        RenderUtils.disableScisor();
        if (totalH > availH) drawScrollbar(x + w - 8, settingsY, availH, totalH, settingScroll);
    }

    private int getSettingHeight(Value v) {
        if (v instanceof ModeValue) {
            SettingState ss = getState(v);
            if (ss.dropdownOpen) {
                return 30 + ((ModeValue) v).getModes().size() * 18;
            }
            return 30;
        }
        if (v instanceof ColorValue) {
            SettingState ss = getState(v);
            return ss.dropdownOpen ? 22 + PICKER_HEIGHT : 22;
        }
        if (v instanceof StringValue) return 40;
        if (v instanceof NumberValue || v instanceof RangeValue) return 34;
        return 22;
    }

    private static final int PICKER_SB_HEIGHT = 54;
    private static final int PICKER_HUE_HEIGHT = 10;
    private static final int PICKER_HEIGHT = PICKER_SB_HEIGHT + PICKER_HUE_HEIGHT + 12;
    private void drawColorPicker(ColorValue cv, int x, int y, int w) {
        SettingState ss = getState(cv);
        float[] hsb = ss.hsb(cv.getColor1());

        int sbY = y + 4;
        int pureHue = 0xFF000000 | Color.HSBtoRGB(hsb[0], 1f, 1f);
        RenderUtils.drawGradientRect(x, sbY, x + w, sbY + PICKER_SB_HEIGHT,
                0xFFFFFFFF, pureHue, pureHue, 0xFFFFFFFF);
        RenderUtils.drawGradientRect(x, sbY, x + w, sbY + PICKER_SB_HEIGHT,
                0x00000000, 0x00000000, 0xFF000000, 0xFF000000);

        int cursorX = (int) (x + hsb[1] * w);
        int cursorY = (int) (sbY + (1f - hsb[2]) * PICKER_SB_HEIGHT);
        RenderUtils.drawCircle(cursorX, cursorY, 4f, 0xFF000000);
        RenderUtils.drawCircle(cursorX, cursorY, 3f, 0xFFFFFFFF);

        int hueY = sbY + PICKER_SB_HEIGHT + 6;
        int steps = 12;
        for (int i = 0; i < steps; i++) {
            float h1 = (float) i / steps;
            float h2 = (float) (i + 1) / steps;
            int c1 = 0xFF000000 | Color.HSBtoRGB(h1, 1f, 1f);
            int c2 = 0xFF000000 | Color.HSBtoRGB(h2, 1f, 1f);
            double sx = x + (double) w * i / steps;
            double ex = x + (double) w * (i + 1) / steps;
            RenderUtils.drawGradientRect(sx, hueY, ex, hueY + PICKER_HUE_HEIGHT, c1, c2, c2, c1);
        }
        int hueX = (int) (x + hsb[0] * w);
        RoundedUtils.drawRoundedRect(hueX - 2, hueY - 2, 4, PICKER_HUE_HEIGHT + 4, 0xFFFFFFFF, 2);

        ss.componentX = x;
        ss.componentW = w;
        ss.pickerY = sbY;
    }
    private void updateColorPicker(ColorValue cv, int mouseX, int mouseY) {
        SettingState ss = getState(cv);
        if (ss.componentW <= 0) return;
        float[] hsb = ss.hsb(cv.getColor1());
        float pct = clamp((float) (mouseX - ss.componentX) / ss.componentW, 0f, 1f);

        if (ss.pickerDragHue) {
            hsb[0] = pct;
        } else {
            hsb[1] = pct;
            hsb[2] = 1f - clamp((float) (mouseY - ss.pickerY) / PICKER_SB_HEIGHT, 0f, 1f);
        }
        ss.storeHsb(hsb);
        cv.setColor1(Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]) & 0xFFFFFF);
    }


    private void drawSetting(Value v, int x, int y, int w, int mouseX, int mouseY) {
        Fonts.drawString(v.getName(), x, y + 3, COLOR_TEXT, "");

        if (v instanceof BooleanValue) {
            BooleanValue bv = (BooleanValue) v;
            int sw = 34, sh = 18;
            int sx = x + w - sw;
            int sy = y + 2;
            int bg = bv.isEnabled() ? 0xFF5A7FFF : 0xFF323232;
            RoundedUtils.drawRoundedRect(sx, sy, sw, sh, bg, 9);
            int knobSize = 14;
            int knobX = bv.isEnabled() ? sx + sw - knobSize - 2 : sx + 2;
            int knobColor = bv.isEnabled() ? 0xFFFFFFFF : 0xFF8C8C8C;
            RenderUtils.drawCircle(knobX + knobSize / 2f, sy + sh / 2f, knobSize / 2f, knobColor);
            return;
        }

        if (v instanceof NumberValue) {
            NumberValue nv = (NumberValue) v;
            String valStr = formatNum(nv.getValue());
            int vw = (int) Fonts.getWidth(valStr, "");
            Fonts.drawString(valStr, x + w - vw, y + 3, COLOR_ACCENT, "");

            int sliderY = y + 18;
            int sliderH = 5;
            double percent = (nv.getValue() - nv.getMin()) / (nv.getMax() - nv.getMin());
            RoundedUtils.drawRoundedRect(x, sliderY, w, sliderH, 0xFF282828, 3);
            RoundedUtils.drawRoundedRect(x, sliderY, (int) (w * percent), sliderH, COLOR_ACCENT, 3);

            int thumb = 10;
            int tx = (int) (x + w * percent - thumb / 2f);
            boolean thov = mouseX >= tx && mouseX <= tx + thumb && mouseY >= sliderY - 2 && mouseY <= sliderY + sliderH + 2;
            int tc = (draggingSlider != null && draggingSlider.value == v) || thov ? 0xFF9AB0FF : COLOR_ACCENT;
            RenderUtils.drawCircle(tx + thumb / 2f, sliderY + sliderH / 2f, thumb / 2f, tc);

            SettingState ss = getState(v);
            ss.componentX = x;
            ss.componentW = w;
            return;
        }

        if (v instanceof RangeValue) {
            RangeValue rv = (RangeValue) v;
            String minStr = formatNum(rv.getMinValue());
            String maxStr = formatNum(rv.getMaxValue());
            int mw = (int) Math.max(Fonts.getWidth(minStr, ""), Fonts.getWidth(maxStr, ""));
            Fonts.drawString(minStr, x + w - mw, y + 3, COLOR_ACCENT, "");
            Fonts.drawString(maxStr, x + w - mw, y + 14, 0xFFAAAAAA, "");

            int sliderY = y + 18;
            int sliderH = 5;
            double range = rv.getMax() - rv.getMin();
            double minP = range == 0 ? 0 : (rv.getMinValue() - rv.getMin()) / range;
            double maxP = range == 0 ? 0 : (rv.getMaxValue() - rv.getMin()) / range;

            RoundedUtils.drawRoundedRect(x, sliderY, w, sliderH, 0xFF282828, 3);
            RoundedUtils.drawRoundedRect(x + (int) (w * minP), sliderY, (int) (w * (maxP - minP)), sliderH, COLOR_ACCENT, 3);

            int thumb = 8;
            int tx1 = (int) (x + w * minP - thumb / 2f);
            int tx2 = (int) (x + w * maxP - thumb / 2f);
            RenderUtils.drawCircle(tx1 + thumb / 2f, sliderY + sliderH / 2f, thumb / 2f, COLOR_ACCENT);
            RenderUtils.drawCircle(tx2 + thumb / 2f, sliderY + sliderH / 2f, thumb / 2f, COLOR_ACCENT);

            SettingState ss = getState(v);
            ss.componentX = x;
            ss.componentW = w;
            return;
        }

        if (v instanceof ColorValue) {
            ColorValue cv = (ColorValue) v;
            SettingState cs = getState(v);
            int rgb = cv.getColor1();
            String hex = String.format("#%06X", rgb);

            int swW = 22, swH = 14;
            int swX = x + w - swW;
            int swY = y + 2;
            RoundedUtils.drawRoundedRect(swX - 1, swY - 1, swW + 2, swH + 2, 0xFF3C3C3C, 4);
            RoundedUtils.drawRoundedRect(swX, swY, swW, swH, 0xFF000000 | rgb, 3);

            int hw = (int) Fonts.getWidth(hex, "");
            Fonts.drawString(hex, swX - hw - 8, y + 3, COLOR_TEXT_SECONDARY, "");

            if (cs.dropdownOpen) {
                drawColorPicker(cv, x, y + 22, w);
            }
            return;
        }

        if (v instanceof StringValue) {
            StringValue sv = (StringValue) v;
            boolean editing = editingString != null && editingString.value == v;
            String shown = editing ? editingBuffer : sv.getValue();

            int boxY = y + 16;
            int boxH = 20;
            RoundedUtils.drawRoundedRect(x, boxY, w, boxH, editing ? 0xFF262626 : 0xFF1E1E1E, 4);
            if (editing) {
                RoundedUtils.drawRoundedOutlinedRect(x, boxY, w, boxH, COLOR_ACCENT, 4, 1);
            }

            String display = shown;
            while (Fonts.getWidth(display, "") > w - 14 && display.length() > 1) {
                display = display.substring(1);
            }
            if (display.isEmpty() && !editing) {
                Fonts.drawString("(empty)", x + 7, boxY + 6, 0xFF5A5A5A, "");
            } else {
                Fonts.drawString(editing ? display + "_" : display, x + 7, boxY + 6, COLOR_TEXT, "");
            }

            SettingState ts = getState(v);
            ts.componentX = x;
            ts.componentW = w;
            return;
        }

        if (v instanceof ModeValue) {
            ModeValue mv = (ModeValue) v;
            String val = mv.getMode();
            int vw = (int) Fonts.getWidth(val, "");
            SettingState ss = getState(v);
            String arrow = ss.dropdownOpen ? "\u25b2" : "\u25bc";
            Fonts.drawString(arrow, x + w - 8, y + 3, COLOR_TEXT_SECONDARY, "");
            Fonts.drawString(val, x + w - vw - 16, y + 3, COLOR_ACCENT, "");

            if (ss.dropdownOpen) {
                int dy = y + 24;
                for (Value option : mv.getModes()) {
                    boolean optSel = option.getName().equals(mv.getMode());
                    boolean optHov = mouseX >= x && mouseX <= x + w && mouseY >= dy && mouseY <= dy + 18;
                    int bg = optSel ? 0x507891FF : (optHov ? 0xFF282828 : 0xFF1E1E1E);
                    RoundedUtils.drawRoundedRect(x, dy, w, 18, bg, 3);
                    Fonts.drawString(option.getName(), x + 6, dy + 4, optSel ? COLOR_ACCENT : COLOR_TEXT, "");
                    dy += 18;
                }
            }
        }
    }

    private void drawScrollbar(int x, int y, int h, int totalH, float scroll) {
        RoundedUtils.drawRoundedRect(x, y, 4, h, 0x641E1E1E, 2);
        float maxS = Math.max(0, totalH - h);
        float thumbH = Math.max(16, (float) h / totalH * h);
        float thumbPos = maxS > 0 ? scroll / maxS * (h - thumbH) : 0;
        RoundedUtils.drawRoundedRect(x + 1, y + (int) thumbPos, 2, (int) thumbH, COLOR_ACCENT, 1);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (listeningModule != null) {
            listeningModule.setKey(mouseButton - 100);
            listeningModule = null;
            return;
        }

        clearTransient();

        int closeX = windowX + windowWidth - 24;
        int closeY = windowY + 7;
        if (mouseX >= closeX && mouseX <= closeX + 18 && mouseY >= closeY && mouseY <= closeY + 18) {
            mc.displayGuiScreen(null);
            return;
        }

        if (mouseX >= windowX && mouseX <= windowX + windowWidth && mouseY >= windowY && mouseY <= windowY + HEADER_HEIGHT) {
            dragging = true;
            dragOffX = mouseX - windowX;
            dragOffY = mouseY - windowY;
            return;
        }

        int sy = windowY + HEADER_HEIGHT + 1 + 10;
        for (int i = 0; i < 5; i++) {
            if (mouseX >= windowX && mouseX <= windowX + SIDEBAR_WIDTH && mouseY >= sy && mouseY <= sy + 26) {
                selectedCategory = i;
                selectedModule = null;
                listeningModule = null;
                moduleScrollTarget = 0;
                settingScrollTarget = 0;
                List<BridgeModule> mods = getModulesForCategory(i);
                if (!mods.isEmpty()) selectedModule = mods.get(0);
                return;
            }
            sy += 30;
        }

        int modScrollX = windowX + SIDEBAR_WIDTH + (int)(windowWidth * 0.33) - 8;
        int modScrollY = windowY + HEADER_HEIGHT + 1 + 10;
        int modScrollH = windowHeight - HEADER_HEIGHT - 1 - 20;

        if (mouseX >= modScrollX - 2 && mouseX <= modScrollX + 6 && mouseY >= modScrollY && mouseY <= modScrollY + modScrollH) {
            draggingModuleScrollbar = true;
            return;
        }

        int setScrollX = windowX + windowWidth - 8;
        int setScrollY = windowY + HEADER_HEIGHT + 1 + 38;
        int setScrollH = windowHeight - HEADER_HEIGHT - 1 - 38;

        if (mouseX >= setScrollX - 2 && mouseX <= setScrollX + 6 && mouseY >= setScrollY && mouseY <= setScrollY + setScrollH) {
            draggingSettingScrollbar = true;
            return;
        }

        int contentX = windowX + SIDEBAR_WIDTH + 1;
        int contentY = windowY + HEADER_HEIGHT + 1;
        int moduleW = (int) (windowWidth * 0.33) - 1;

        if (!handleModuleClick(contentX, contentY, moduleW, mouseX, mouseY, mouseButton)) {
            if (mouseButton == 0 && mouseX >= contentX && mouseX <= contentX + moduleW) {
                dragScrolling = true;
                dragScrollLastY = mouseY;
            }
        }
        handleSettingClick(contentX + moduleW, contentY, windowWidth - SIDEBAR_WIDTH - moduleW, windowHeight - HEADER_HEIGHT - 1, mouseX, mouseY);
    }

    private boolean handleModuleClick(int x, int y, int w, int mouseX, int mouseY, int btn) {
        if (mouseX < x || mouseX > x + w) return false;

        List<BridgeModule> mods = getModulesForCategory(selectedCategory);
        int my = y + 10 - (int) moduleScroll;
        for (BridgeModule mod : mods) {
            if (mouseY >= my && mouseY <= my + MODULE_ITEM_HEIGHT) {
                if (btn == 1) {
                    selectedModule = mod;
                    settingScrollTarget = 0;
                } else if (btn == 0) {
                    mod.toggle();
                } else if (btn == 2) {
                    listeningModule = mod;
                }
                return true;
            }
            my += MODULE_ITEM_HEIGHT;
        }
        return false;
    }

    private void handleSettingClick(int x, int y, int w, int h, int mouseX, int mouseY) {
        if (selectedModule == null) return;

        int settingsY = y + 38;
        int currentY = settingsY - (int) settingScroll;

        for (Value v : selectedModule.getVisibleSettings()) {
            int sh = getSettingHeight(v);

            if (v instanceof BooleanValue) {
                BooleanValue bv = (BooleanValue) v;
                int sx = x + 12 + w - 24 - 34;
                int sy = currentY + 2;
                if (mouseX >= sx && mouseX <= sx + 34 && mouseY >= sy && mouseY <= sy + 18) {
                    bv.setEnabled(!bv.isEnabled());
                    return;
                }
            } else if (v instanceof NumberValue) {
                NumberValue nv = (NumberValue) v;
                int sliderY = currentY + 18;
                if (mouseX >= x + 12 && mouseX <= x + 12 + w - 24 && mouseY >= sliderY - 2 && mouseY <= sliderY + 7) {
                    draggingSlider = new DraggingSlider(v);
                    updateSlider(nv, mouseX, x + 12, w - 24);
                    return;
                }
            } else if (v instanceof RangeValue) {
                RangeValue rv = (RangeValue) v;
                int sliderY = currentY + 18;
                if (mouseX >= x + 12 && mouseX <= x + 12 + w - 24 && mouseY >= sliderY - 2 && mouseY <= sliderY + 7) {
                    draggingSlider = new DraggingSlider(v);
                    updateRange(rv, mouseX, x + 12, w - 24);
                    return;
                }
            } else if (v instanceof ColorValue) {
                ColorValue cv = (ColorValue) v;
                SettingState cs = getState(v);
                if (cs.dropdownOpen) {
                    int sbY = currentY + 22 + 4;
                    int hueY = sbY + PICKER_SB_HEIGHT + 6;
                    if (mouseX >= x + 12 && mouseX <= x + 12 + w - 24) {
                        if (mouseY >= sbY && mouseY <= sbY + PICKER_SB_HEIGHT) {
                            cs.pickerY = sbY;
                            cs.pickerDragHue = false;
                            draggingSlider = new DraggingSlider(v);
                            updateColorPicker(cv, mouseX, mouseY);
                            return;
                        }
                        if (mouseY >= hueY - 2 && mouseY <= hueY + PICKER_HUE_HEIGHT + 2) {
                            cs.pickerY = sbY;
                            cs.pickerDragHue = true;
                            draggingSlider = new DraggingSlider(v);
                            updateColorPicker(cv, mouseX, mouseY);
                            return;
                        }
                    }
                }
                if (mouseX >= x + 12 && mouseX <= x + 12 + w - 24 && mouseY >= currentY && mouseY <= currentY + 20) {
                    cs.dropdownOpen = !cs.dropdownOpen;
                    return;
                }
            } else if (v instanceof StringValue) {
                int boxY = currentY + 16;
                if (mouseX >= x + 12 && mouseX <= x + 12 + w - 24 && mouseY >= boxY && mouseY <= boxY + 20) {
                    editingString = new EditingString(v);
                    editingBuffer = ((StringValue) v).getValue();
                    return;
                }
            } else if (v instanceof ModeValue) {
                ModeValue mv = (ModeValue) v;
                SettingState ss = getState(v);
                if (ss.dropdownOpen) {
                    int dy = currentY + 24;
                    for (Value opt : mv.getModes()) {
                        if (mouseX >= x + 12 && mouseX <= x + 12 + w - 24 && mouseY >= dy && mouseY <= dy + 18) {
                            mv.setMode(opt);
                            ss.dropdownOpen = false;
                            return;
                        }
                        dy += 18;
                    }
                }
                if (mouseX >= x + 12 && mouseX <= x + 12 + w - 24 && mouseY >= currentY && mouseY <= currentY + 22) {
                    ss.dropdownOpen = !ss.dropdownOpen;
                    return;
                }
            }

            currentY += sh + 6;
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        dragging = false;
        draggingSlider = null;
        dragScrolling = false;
        draggingModuleScrollbar = false;
        draggingSettingScrollbar = false;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int scroll = wheel > 0 ? -20 : 20;
            int mx = Mouse.getEventX() * width / mc.displayWidth;
            int contentX = windowX + SIDEBAR_WIDTH + 1;
            int moduleW = (int) (windowWidth * 0.33) - 1;
            if (mx >= contentX && mx <= contentX + moduleW) {
                moduleScrollTarget += scroll;
            } else {
                settingScrollTarget += scroll;
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (editingString != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                editingString = null;
                editingBuffer = "";
            } else if (keyCode == Keyboard.KEY_RETURN) {
                if (editingString.value instanceof StringValue) {
                    ((StringValue) editingString.value).setValue(editingBuffer);
                }
                editingString = null;
                editingBuffer = "";
            } else if (keyCode == Keyboard.KEY_BACK) {
                if (!editingBuffer.isEmpty()) editingBuffer = editingBuffer.substring(0, editingBuffer.length() - 1);
            } else if (isCtrlKeyDown()) {
                if (keyCode == Keyboard.KEY_V) {
                    String clip = GuiScreen.getClipboardString();
                    if (!clip.isEmpty()) {
                        StringBuilder sb = new StringBuilder(editingBuffer.length() + clip.length());
                        sb.append(editingBuffer);
                        for (char c : clip.toCharArray()) {
                            if (ChatAllowedCharacters.isAllowedCharacter(c)) sb.append(c);
                        }
                        editingBuffer = sb.toString();
                    }
                } else if (keyCode == Keyboard.KEY_A) {
                    editingBuffer = "";
                } else if (keyCode == Keyboard.KEY_C) {
                    GuiScreen.setClipboardString(editingBuffer);
                }
            } else if (ChatAllowedCharacters.isAllowedCharacter(typedChar)) {
                editingBuffer += typedChar;
            }
            return;
        }

        if (listeningModule != null) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_DELETE) {
                listeningModule.setKey(0);
            } else {
                listeningModule.setKey(keyCode);
            }
            listeningModule = null;
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void initGui() {
        windowWidth = Math.min(500, width - 20);
        windowHeight = Math.min(340, height - 20);
        windowX = (width - windowWidth) / 2;
        windowY = (height - windowHeight) / 2;
        loadPosition();
        if (selectedModule == null) {
            List<BridgeModule> mods = getModulesForCategory(selectedCategory);
            if (!mods.isEmpty()) selectedModule = mods.get(0);
        }
    }

    @Override
    public void onGuiClosed() {
        savePosition();
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    private void loadPosition() {
        if (!POS_FILE.exists()) return;
        try (BufferedReader r = new BufferedReader(new FileReader(POS_FILE))) {
            GuiPos p = GSON.fromJson(r, GuiPos.class);
            if (p != null) {
                windowX = clamp(p.x, 0, Math.max(0, width - windowWidth));
                windowY = clamp(p.y, 0, Math.max(0, height - windowHeight));
            }
        } catch (Exception ignored) {}
    }

    private void savePosition() {
        try {
            POS_FILE.getParentFile().mkdirs();
            GuiPos p = new GuiPos(windowX, windowY);
            try (PrintWriter w = new PrintWriter(POS_FILE)) {
                w.write(GSON.toJson(p));
            }
        } catch (Exception ignored) {}
    }

    private static class GuiPos {
        int x, y;
        GuiPos() {}
        GuiPos(int x, int y) { this.x = x; this.y = y; }
    }

    private void clearTransient() {
        dragging = false;
        draggingSlider = null;
        dragScrolling = false;
        draggingModuleScrollbar = false;
        draggingSettingScrollbar = false;
        if (editingString != null) {
            if (editingString.value instanceof StringValue) {
                ((StringValue) editingString.value).setValue(editingBuffer);
            }
            editingString = null;
            editingBuffer = "";
        }
    }

    private void updateSlider(NumberValue v, int mouseX, int sx, int sw) {
        if (sw <= 0) return;
        double pct = clamp((double) (mouseX - sx) / sw, 0.0, 1.0);
        double range = v.getMax() - v.getMin();
        double raw = range == 0 ? v.getMin() : v.getMin() + range * pct;
        double inc = v.getIncrement();
        if (inc > 0) raw = Math.round(raw / inc) * inc;
        v.setValue(clamp(raw, v.getMin(), v.getMax()));
    }

    private void updateRange(RangeValue v, int mouseX, int sx, int sw) {
        if (sw <= 0) return;
        double pct = clamp((double) (mouseX - sx) / sw, 0.0, 1.0);
        double range = v.getMax() - v.getMin();
        double raw = range == 0 ? v.getMin() : v.getMin() + range * pct;
        double inc = v.getIncrement();
        if (inc > 0) raw = Math.round(raw / inc) * inc;
        raw = clamp(raw, v.getMin(), v.getMax());

        double mid = (v.getMinValue() + v.getMaxValue()) / 2;
        if (raw < mid) {
            v.setMinValue(raw);
        } else {
            v.setMaxValue(raw);
        }
    }

    private SettingState getState(Value v) {
        return settingStates.computeIfAbsent(v.getId(), k -> new SettingState());
    }

    private String getKeyName(int key) {
        if (key < 0) {
            return "MB" + (key + 101);
        }
        String name = Keyboard.getKeyName(key);
        return name != null ? name : "Unknown";
    }

    private String formatNum(double d) {
        BigDecimal bd = new BigDecimal(d);
        bd = bd.setScale(d % 1 == 0 ? 0 : 1, RoundingMode.HALF_UP);
        return bd.stripTrailingZeros().toPlainString();
    }

    private String getCategoryName(int idx) {
        return BridgeModule.getCategoryName(idx);
    }

    private List<BridgeModule> getModulesForCategory(int cat) {
        List<BridgeModule> result = new ArrayList<>();
        for (BridgeModule bm : BridgeClient.getInstance().getModules()) {
            if (bm.getCategoryIndex() == cat) result.add(bm);
        }
        return result;
    }
    private static float lerp(float a, float b, float f) {
        return a + f * (b - a);
    }
    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
    private static class SettingState {
        boolean dropdownOpen = false;
        int componentX;
        int componentW;
        int pickerY;
        boolean pickerDragHue = false;
        private float[] hsbCache = null;
        private int hsbSource = -1;
        float[] hsb(int rgb) {
            if (hsbCache == null || hsbSource != rgb) {
                hsbCache = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
                hsbSource = rgb;
            }
            return new float[]{hsbCache[0], hsbCache[1], hsbCache[2]};
        }

        void storeHsb(float[] hsb) {
            hsbCache = new float[]{hsb[0], hsb[1], hsb[2]};
            hsbSource = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]) & 0xFFFFFF;
        }
    }

    private class DraggingSlider {
        final Value value;
        DraggingSlider(Value v) { this.value = v; }

        void update(int mouseX) {
            if (value instanceof ColorValue) {
                ScaledResolution sr = new ScaledResolution(mc);
                int my = sr.getScaledHeight() - Mouse.getY() * sr.getScaledHeight() / mc.displayHeight - 1;
                updateColorPicker((ColorValue) value, mouseX, my);
                return;
            }
            if (value instanceof NumberValue) {
                NumberValue nv = (NumberValue) value;
                SettingState ss = getState(nv);
                if (ss.componentW <= 0) return;
                double pct = clamp((double) (mouseX - ss.componentX) / ss.componentW, 0.0, 1.0);
                double range = nv.getMax() - nv.getMin();
                double raw = range == 0 ? nv.getMin() : nv.getMin() + range * pct;
                double inc = nv.getIncrement();
                if (inc > 0) raw = Math.round(raw / inc) * inc;
                nv.setValue(clamp(raw, nv.getMin(), nv.getMax()));
            } else if (value instanceof RangeValue) {
                RangeValue rv = (RangeValue) value;
                SettingState ss = getState(rv);
                if (ss.componentW <= 0) return;
                double pct = clamp((double) (mouseX - ss.componentX) / ss.componentW, 0.0, 1.0);
                double range = rv.getMax() - rv.getMin();
                double raw = range == 0 ? rv.getMin() : rv.getMin() + range * pct;
                double inc = rv.getIncrement();
                if (inc > 0) raw = Math.round(raw / inc) * inc;
                raw = clamp(raw, rv.getMin(), rv.getMax());
                double mid = (rv.getMinValue() + rv.getMaxValue()) / 2;
                if (raw < mid) rv.setMinValue(raw);
                else rv.setMaxValue(raw);
            }
        }
    }

    private static class EditingString {
        final Value value;
        EditingString(Value v) { this.value = v; }
    }
}
