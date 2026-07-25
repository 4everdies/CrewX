package myau.gui;

import myau.Myau;
import myau.module.Module;
import myau.module.modules.GuiModule;
import myau.property.Property;
import myau.property.properties.*;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * ClickGUI do CrewX - port fiel da ClickInterface do Simp.
 *
 * Detalhe importante: o desenho usa o drawRect do Gui (que configura o estado
 * do OpenGL). A versao anterior chamava RenderUtil.drawRect, que desenha o
 * poligono com a textura da fonte ainda ligada - por isso a GUI inteira
 * aparecia transparente.
 */
public class ClickGui extends GuiScreen {

    private final List<CategoryPanel> panels = new ArrayList<CategoryPanel>();
    private Module listeningModule = null;
    private SettingComponent dragging = null;
    private int dragMode = 0; // 0 = slider, 1 = quadrado de cor, 2 = barra de matiz
    private SettingComponent focusedText = null;

// esquema de cores — ACCENT, BG, PANEL vêm do GuiModule; o resto é fixo
    private static final Color TEXT_COLOR = new Color(200, 200, 200);

    private static Color accentColor() {
        return GuiModule.getAccent();
    }

    private static Color bgColor() {
        return new Color(20, 20, 20, GuiModule.getBackgroundAlpha());
    }

    private static Color panelBg() {
        return new Color(25, 25, 25, Math.min(255, GuiModule.getBackgroundAlpha() + 20));
    }

    private static final Color HOVER_COLOR = new Color(35, 35, 35);
    private static final Color OPTION_BG = new Color(30, 30, 30, 220);
    private static final Color TRACK_COLOR = new Color(60, 60, 60);
    private static final Color ARROW_COLOR = new Color(150, 150, 150);
    private static final Color KEY_COLOR = new Color(150, 150, 150, 180);

    private static final int PANEL_WIDTH = 110;
    private static final int PANEL_SPACING = 10;
    private static final int TOP_MARGIN = 50;
    private static final int PICKER_HEIGHT = 40;

    private static final String[] CATEGORIES = {"Combat", "Movement", "Render", "Player", "Misc", "Script"};

    private static Field modesField;

    // ==================================================================

    @Override
    public void initGui() {
        panels.clear();

        int x = 20;
        int y = TOP_MARGIN;

        for (int i = 0; i < CATEGORIES.length; i++) {
            // quebra de linha apenas quando a fileira nao cabe na tela
            if (x > 20 && x + PANEL_WIDTH > width - 10) {
                x = 20;
                y += 50;
            }
            panels.add(new CategoryPanel(i, x, y));
            x += PANEL_WIDTH + PANEL_SPACING;
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        if (dragging != null) {
            dragging.updateDrag(mouseX, mouseY);
        }

        for (int i = 0; i < panels.size(); i++) {
            panels.get(i).render(mouseX, mouseY);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        for (int i = 0; i < panels.size(); i++) {
            if (panels.get(i).mouseClicked(mouseX, mouseY, mouseButton)) {
                return;
            }
        }
        focusedText = null;
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        dragging = null;
        for (int i = 0; i < panels.size(); i++) {
            panels.get(i).mouseReleased();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;

        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;

        for (int i = 0; i < panels.size(); i++) {
            CategoryPanel panel = panels.get(i);
            if (mouseX >= panel.x && mouseX <= panel.x + PANEL_WIDTH && mouseY >= panel.y + panel.headerHeight) {
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
                focusedText = null;
            } else {
                focusedText.typeChar(typedChar, keyCode);
            }
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    // ==================================================================
    //  utilitarios
    // ==================================================================

    private void scissorOn(int x, int y, int w, int h) {
        if (w < 0) w = 0;
        if (h < 0) h = 0;
        ScaledResolution sr = new ScaledResolution(mc);
        int f = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * f, (sr.getScaledHeight() - (y + h)) * f, w * f, h * f);
    }

    private void scissorOff() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
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

    // ==================================================================
    //  painel de categoria
    // ==================================================================

    private class CategoryPanel {

        private final int category;
        private int x, y;
        private final int headerHeight = 16;
        private boolean dragPanel = false;
        private int dragX, dragY;
        private float scrollOffset = 0;
        private float targetScroll = 0;

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

        void render(int mouseX, int mouseY) {
            if (dragPanel) {
                x = mouseX - dragX;
                y = mouseY - dragY;
            }

            scrollOffset += (targetScroll - scrollOffset) * 0.2f;

            int totalHeight = 0;
            for (int i = 0; i < modules.size(); i++) {
                totalHeight += modules.get(i).getTotalHeight();
            }

            int maxVisibleHeight = height - y - headerHeight - 20;
            if (maxVisibleHeight < 0) maxVisibleHeight = 0;
            int maxScroll = Math.max(0, totalHeight - maxVisibleHeight);
            targetScroll = Math.max(0, Math.min(targetScroll, maxScroll));

            // cabecalho
            drawRect(x, y, x + PANEL_WIDTH, y + headerHeight, panelBg().getRGB());
            mc.fontRendererObj.drawString(CATEGORIES[category], x + 4, y + 4, TEXT_COLOR.getRGB());

            // corpo
            int bodyHeight = Math.min(totalHeight, maxVisibleHeight);
            drawRect(x, y + headerHeight, x + PANEL_WIDTH, y + headerHeight + bodyHeight, bgColor().getRGB());

            if (bodyHeight > 0) {
                scissorOn(x, y + headerHeight, PANEL_WIDTH, bodyHeight);

                int moduleY = y + headerHeight - (int) scrollOffset;
                for (int i = 0; i < modules.size(); i++) {
                    ModuleButton button = modules.get(i);
                    int buttonHeight = button.getTotalHeight();

                    if (moduleY + buttonHeight > y + headerHeight && moduleY < y + headerHeight + bodyHeight) {
                        button.render(x, moduleY, PANEL_WIDTH, mouseX, mouseY);
                    }
                    moduleY += buttonHeight;
                }

                scissorOff();
            }

            if (totalHeight > maxVisibleHeight) {
                drawScrollbar(y + headerHeight, bodyHeight, totalHeight, maxScroll);
            }
        }

        private void drawScrollbar(int startY, int visibleHeight, int totalHeight, int maxScroll) {
            int scrollbarX = x + PANEL_WIDTH - 2;

            drawRect(scrollbarX, startY, scrollbarX + 2, startY + visibleHeight,
                    new Color(40, 40, 40, 180).getRGB());

            float thumbSize = Math.max(20, (float) visibleHeight / totalHeight * visibleHeight);
            float thumbPos = maxScroll > 0 ? (scrollOffset / maxScroll) * (visibleHeight - thumbSize) : 0;

            drawRect(scrollbarX, (int) (startY + thumbPos),
                    scrollbarX + 2, (int) (startY + thumbPos + thumbSize),
                    accentColor().getRGB());
        }

        boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
            if (mouseX >= x && mouseX <= x + PANEL_WIDTH && mouseY >= y && mouseY <= y + headerHeight) {
                if (mouseButton == 0) {
                    dragPanel = true;
                    dragX = mouseX - x;
                    dragY = mouseY - y;
                    return true;
                }
            }

            int maxVisibleHeight = height - y - headerHeight - 20;
            if (maxVisibleHeight < 0) maxVisibleHeight = 0;
            if (mouseY < y + headerHeight || mouseY > y + headerHeight + maxVisibleHeight) return false;
            if (mouseX < x || mouseX > x + PANEL_WIDTH) return false;

            int moduleY = y + headerHeight - (int) scrollOffset;
            for (int i = 0; i < modules.size(); i++) {
                ModuleButton button = modules.get(i);
                int buttonHeight = button.getTotalHeight();

                if (moduleY + buttonHeight > y + headerHeight && moduleY < y + headerHeight + maxVisibleHeight) {
                    if (button.mouseClicked(x, moduleY, PANEL_WIDTH, mouseX, mouseY, mouseButton)) {
                        return true;
                    }
                }
                moduleY += buttonHeight;
            }
            return false;
        }

        void mouseReleased() {
            dragPanel = false;
        }
    }

    // ==================================================================
    //  botao de modulo
    // ==================================================================

    private class ModuleButton {

        private final Module module;
        private boolean expanded = false;
        private final List<SettingComponent> settings = new ArrayList<SettingComponent>();

        ModuleButton(Module module) {
            this.module = module;

            List<Property<?>> properties = getProperties(module);
            if (properties != null) {
                for (int i = 0; i < properties.size(); i++) {
                    settings.add(new SettingComponent(properties.get(i)));
                }
            }
        }

        int getTotalHeight() {
            int total = 16;
            if (expanded) {
                for (int i = 0; i < settings.size(); i++) {
                    SettingComponent setting = settings.get(i);
                    if (setting.property.isVisible()) total += setting.getHeight();
                }
            }
            return total;
        }

        void render(int x, int y, int width, int mouseX, int mouseY) {
            boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 16;

            Color bgColor = module.isEnabled() ? accentColor() : (hovered ? HOVER_COLOR : bgColor());
            drawRect(x, y, x + width, y + 16, bgColor.getRGB());

            String name = module == listeningModule ? "Listening..." : module.getName();
            int textColor = module.isEnabled() ? Color.WHITE.getRGB() : TEXT_COLOR.getRGB();
            mc.fontRendererObj.drawString(name, x + 4, y + 4, textColor);

            if (module.getKey() != 0 && module != listeningModule) {
                String keyName = Keyboard.getKeyName(module.getKey());
                if (keyName != null) {
                    int keyWidth = mc.fontRendererObj.getStringWidth(keyName);
                    int keyX = x + width - keyWidth - (settings.isEmpty() ? 4 : 14);
                    mc.fontRendererObj.drawString(keyName, keyX, y + 4, KEY_COLOR.getRGB());
                }
            }

            if (!settings.isEmpty()) {
                String arrow = expanded ? "\u25bc" : "\u25b6";
                mc.fontRendererObj.drawString(arrow, x + width - 10, y + 4, ARROW_COLOR.getRGB());
            }

            if (expanded) {
                int settingY = y + 16;
                for (int i = 0; i < settings.size(); i++) {
                    SettingComponent setting = settings.get(i);
                    if (!setting.property.isVisible()) continue;
                    setting.render(x, settingY, width, mouseX, mouseY);
                    settingY += setting.getHeight();
                }
            }
        }

        boolean mouseClicked(int x, int y, int width, int mouseX, int mouseY, int mouseButton) {
            boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 16;

            if (hovered) {
                if (mouseButton == 0) {
                    module.toggle();
                } else if (mouseButton == 1) {
                    if (!settings.isEmpty()) expanded = !expanded;
                } else if (mouseButton == 2) {
                    listeningModule = module;
                }
                return true;
            }

            if (expanded) {
                int settingY = y + 16;
                for (int i = 0; i < settings.size(); i++) {
                    SettingComponent setting = settings.get(i);
                    if (!setting.property.isVisible()) continue;
                    if (setting.mouseClicked(x, settingY, width, mouseX, mouseY, mouseButton)) return true;
                    settingY += setting.getHeight();
                }
            }
            return false;
        }
    }

    // ==================================================================
    //  componente de configuracao
    // ==================================================================

    private class SettingComponent {

        private final Property<?> property;
        private boolean dropdownOpen = false;
        private boolean pickerOpen = false;

        private int componentX = 0;
        private int componentWidth = 0;

        private int svX, svY, svW, svH;
        private int hueX, hueY, hueH;
        private float hue, saturation, brightness;

        private String editBuffer = null;

        SettingComponent(Property<?> property) {
            this.property = property;
        }

        int getHeight() {
            if (property instanceof ModeProperty && dropdownOpen) {
                return 16 + getModeValues((ModeProperty) property).length * 12;
            }
            if (property instanceof ColorProperty && pickerOpen) {
                return 17 + PICKER_HEIGHT + 6;
            }
            return 17;
        }

        // ------------------------------------------------------ desenho

        void render(int x, int y, int width, int mouseX, int mouseY) {
            boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 16;

            Color bgColor = hovered ? OPTION_BG : panelBg();
            drawRect(x, y, x + width, y + 16, bgColor.getRGB());

            if (property instanceof ButtonProperty) {
                renderButton(x, y, width, hovered);
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

        private void renderBoolean(int x, int y, int width) {
            boolean value = Boolean.TRUE.equals(property.getValue());

            int switchWidth = 18;
            int switchHeight = 8;
            int switchX = x + width - switchWidth - 4;
            int switchY = y + 4;

            Color bgColor = value ? accentColor().darker() : TRACK_COLOR;
            drawRect(switchX, switchY, switchX + switchWidth, switchY + switchHeight, bgColor.getRGB());

            int knobSize = 6;
            int knobX = value ? switchX + switchWidth - knobSize - 1 : switchX + 1;
            Color knobColor = value ? accentColor().brighter() : new Color(120, 120, 120);
            drawRect(knobX, switchY + 1, knobX + knobSize, switchY + switchHeight - 1, knobColor.getRGB());

            mc.fontRendererObj.drawString(property.getName(), x + 4, y + 4, TEXT_COLOR.getRGB());
        }

        private void renderNumber(int x, int y, int width, int mouseX, int mouseY) {
            double value = getNumber();
            double min = getMinimum();
            double max = getMaximum();
            double percent = max - min == 0 ? 0 : (value - min) / (max - min);
            if (percent < 0) percent = 0;
            if (percent > 1) percent = 1;

            String valueStr = property instanceof PercentProperty
                    ? formatNumber(value) + "%"
                    : formatNumber(value);

            mc.fontRendererObj.drawString(property.getName(), x + 4, y + 4, TEXT_COLOR.getRGB());

            int valueWidth = mc.fontRendererObj.getStringWidth(valueStr);
            mc.fontRendererObj.drawString(valueStr, x + width - valueWidth - 4, y + 4, accentColor().getRGB());

            int sliderY = y + 13;
            int sliderHeight = 2;
            int padding = 4;

            drawRect(x + padding, sliderY, x + width - padding, sliderY + sliderHeight, TRACK_COLOR.getRGB());

            int filledWidth = (int) ((width - padding * 2) * percent);
            drawRect(x + padding, sliderY, x + padding + filledWidth, sliderY + sliderHeight, accentColor().getRGB());

            boolean sliderHovered = mouseX >= x + padding && mouseX <= x + width - padding
                    && mouseY >= y && mouseY <= y + 16;
            if (dragging == this || sliderHovered) {
                int thumbX = x + padding + filledWidth;
                Color thumbColor = dragging == this ? accentColor().brighter() : accentColor();
                drawRect(thumbX - 2, sliderY - 2, thumbX + 2, sliderY + sliderHeight + 2, thumbColor.getRGB());
            }

            componentX = x;
            componentWidth = width;
        }

        private void renderMode(int x, int y, int width, int mouseX, int mouseY, ModeProperty modeProperty) {
            String displayText = property.getName() + ": " + modeProperty.getModeString();
            mc.fontRendererObj.drawString(displayText, x + 4, y + 4, TEXT_COLOR.getRGB());

            String arrow = dropdownOpen ? "\u25b2" : "\u25bc";
            mc.fontRendererObj.drawString(arrow, x + width - 10, y + 4, ARROW_COLOR.getRGB());

            if (!dropdownOpen) return;

            String[] values = getModeValues(modeProperty);
            int optionY = y + 16;
            int selected = modeProperty.getValue().intValue();

            for (int i = 0; i < values.length; i++) {
                boolean isSelected = i == selected;
                boolean hovered = mouseX >= x + 2 && mouseX <= x + width - 2
                        && mouseY >= optionY && mouseY <= optionY + 12;

                Color color;
                if (isSelected) {
                    color = accentColor();
                } else if (hovered) {
                    color = HOVER_COLOR.brighter();
                } else {
                    color = OPTION_BG;
                }

                drawRect(x + 2, optionY, x + width - 2, optionY + 12, color.getRGB());
                mc.fontRendererObj.drawString(values[i], x + 6, optionY + 2,
                        isSelected ? Color.WHITE.getRGB() : TEXT_COLOR.getRGB());
                optionY += 12;
            }
        }

        private void renderColor(int x, int y, int width, ColorProperty colorProperty) {
            int rgb = colorProperty.getValue().intValue() & 0xFFFFFF;

            int swatchWidth = 18;
            int swatchHeight = 8;
            int swatchX = x + width - swatchWidth - 4;
            int swatchY = y + 4;

            drawRect(swatchX - 1, swatchY - 1, swatchX + swatchWidth + 1, swatchY + swatchHeight + 1,
                    TRACK_COLOR.getRGB());
            drawRect(swatchX, swatchY, swatchX + swatchWidth, swatchY + swatchHeight, 0xFF000000 | rgb);

            mc.fontRendererObj.drawString(property.getName(), x + 4, y + 4, TEXT_COLOR.getRGB());

            if (!pickerOpen) return;

            svX = x + 4;
            svY = y + 18;
            svW = width - 8 - 12;
            svH = PICKER_HEIGHT;
            hueX = x + width - 12;
            hueY = y + 18;
            hueH = PICKER_HEIGHT;

            int pure = Color.HSBtoRGB(hue, 1f, 1f) & 0xFFFFFF;
            for (int i = 0; i < svW; i++) {
                int top = lerpColor(0xFFFFFF, pure, (float) i / svW);
                drawGradientRect(svX + i, svY, svX + i + 1, svY + svH, top, 0xFF000000);
            }
            for (int i = 0; i < hueH; i++) {
                int c = Color.HSBtoRGB((float) i / hueH, 1f, 1f) & 0xFFFFFF;
                drawRect(hueX, hueY + i, hueX + 8, hueY + i + 1, 0xFF000000 | c);
            }

            int markX = svX + (int) (saturation * svW);
            int markY = svY + (int) ((1f - brightness) * svH);
            drawRect(markX - 2, markY, markX + 3, markY + 1, 0xFFFFFFFF);
            drawRect(markX, markY - 2, markX + 1, markY + 3, 0xFFFFFFFF);

            int hueMark = hueY + (int) (hue * hueH);
            drawRect(hueX - 1, hueMark - 1, hueX + 9, hueMark + 1, 0xFFFFFFFF);
        }

        private void renderText(int x, int y, int width, TextProperty textProperty) {
            mc.fontRendererObj.drawString(property.getName(), x + 4, y + 4, TEXT_COLOR.getRGB());

            boolean editing = focusedText == this;
            String value = editing
                    ? (editBuffer == null ? "" : editBuffer)
                    : (textProperty.getValue() == null ? "" : textProperty.getValue());
            String shown = editing ? value + "_" : (value.isEmpty() ? "-" : value);

            int maxWidth = width - mc.fontRendererObj.getStringWidth(property.getName()) - 12;
            while (shown.length() > 1 && mc.fontRendererObj.getStringWidth(shown) > maxWidth) {
                shown = shown.substring(1);
            }

            int textWidth = mc.fontRendererObj.getStringWidth(shown);
            mc.fontRendererObj.drawString(shown, x + width - textWidth - 4, y + 4,
                    editing ? Color.WHITE.getRGB() : accentColor().getRGB());
        }

        private void renderButton(int x, int y, int width, boolean hovered) {
            Color color = hovered ? accentColor().darker() : new Color(45, 45, 45);
            drawRect(x + 4, y + 2, x + width - 4, y + 14, color.getRGB());

            String name = property.getName();
            int nameWidth = mc.fontRendererObj.getStringWidth(name);
            mc.fontRendererObj.drawString(name, x + (width - nameWidth) / 2, y + 4,
                    hovered ? Color.WHITE.getRGB() : TEXT_COLOR.getRGB());
        }

        // ------------------------------------------------------ clique

        boolean mouseClicked(int x, int y, int width, int mouseX, int mouseY, int mouseButton) {
            boolean onRow = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 16;

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

            if (isSlider(property)) {
                int sliderY = y + 11;
                if (mouseX >= x + 4 && mouseX <= x + width - 4 && mouseY >= sliderY && mouseY <= sliderY + 6) {
                    dragging = this;
                    dragMode = 0;
                    componentX = x;
                    componentWidth = width;
                    updateSlider(mouseX);
                    return true;
                }
                return false;
            }

            if (property instanceof ModeProperty) {
                ModeProperty modeProperty = (ModeProperty) property;

                if (dropdownOpen) {
                    String[] values = getModeValues(modeProperty);
                    int optionY = y + 16;
                    for (int i = 0; i < values.length; i++) {
                        if (mouseX >= x + 2 && mouseX <= x + width - 2
                                && mouseY >= optionY && mouseY <= optionY + 12) {
                            modeProperty.setValue(Integer.valueOf(i));
                            dropdownOpen = false;
                            return true;
                        }
                        optionY += 12;
                    }
                }

                if (onRow) {
                    dropdownOpen = !dropdownOpen;
                    return true;
                }
                return false;
            }

            if (property instanceof ColorProperty) {
                ColorProperty colorProperty = (ColorProperty) property;

                if (pickerOpen) {
                    if (mouseX >= svX && mouseX <= svX + svW && mouseY >= svY && mouseY <= svY + svH) {
                        dragging = this;
                        dragMode = 1;
                        updateSaturation(mouseX, mouseY, colorProperty);
                        return true;
                    }
                    if (mouseX >= hueX - 2 && mouseX <= hueX + 10 && mouseY >= hueY && mouseY <= hueY + hueH) {
                        dragging = this;
                        dragMode = 2;
                        updateHue(mouseY, colorProperty);
                        return true;
                    }
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

            if (property instanceof TextProperty) {
                if (onRow) {
                    TextProperty textProperty = (TextProperty) property;
                    if (mouseButton == 1) {
                        textProperty.setValue("");
                        editBuffer = "";
                    } else {
                        focusedText = this;
                        editBuffer = textProperty.getValue() == null ? "" : textProperty.getValue();
                    }
                    return true;
                }
                return false;
            }

            return false;
        }

        // ------------------------------------------------------ arrasto

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
            if (componentWidth <= 8) return;

            double percent = Math.max(0, Math.min(1, (mouseX - componentX - 4.0) / (componentWidth - 8.0)));
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
            saturation = clamp01((float) (mouseX - svX) / svW);
            brightness = clamp01(1f - (float) (mouseY - svY) / svH);
            applyColor(property);
        }

        private void updateHue(int mouseY, ColorProperty property) {
            if (hueH <= 0) return;
            hue = clamp01((float) (mouseY - hueY) / hueH);
            applyColor(property);
        }

        private void applyColor(ColorProperty property) {
            property.setValue(Integer.valueOf(Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF));
        }

        void typeChar(char typedChar, int keyCode) {
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

        // ------------------------------------------------------ numeros

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
