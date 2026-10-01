package crewx.gui;

import crewx.CrewX;
import crewx.config.Config;
import crewx.clickgui.render.RoundedUtils;
import crewx.module.Module;
import crewx.module.ModuleCategory;
import crewx.module.modules.render.GuiModule;
import crewx.property.Property;
import crewx.property.properties.*;
import crewx.util.KeyBindUtil;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.io.File;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.Collections;
import java.util.Locale;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClickGui extends GuiScreen {
    private static final ModuleCategory[] CATEGORIES = ModuleCategory.values();
    private static final int CONFIG_ROW_HEIGHT = 34;
    private static final int DRAG_NONE = 0;
    private static final int DRAG_MODULES = 1;
    private static final int DRAG_SETTINGS = 2;
    private static final int DRAG_CONFIGS = 3;

    private enum ViewTab {
        MODULES,
        CONFIGS
    }

    private static final int HEADER_HEIGHT = 40;
    private static final int FOOTER_HEIGHT = 22;
    private static final int MODULE_ROW_HEIGHT = 31;
    private static final int SETTING_HEIGHT = 26;
    private static final int OPTION_HEIGHT = 18;
    private static final int PICKER_HEIGHT = 54;
    private static final Color SIDEBAR = new Color(5, 5, 5, 255);
    private static final Color PANEL = new Color(9, 9, 9, 255);
    private static final Color PANEL_ALT = new Color(12, 12, 12, 255);
    private static final Color ROW = new Color(20, 20, 20, 255);
    private static final Color ROW_HOVER = new Color(29, 29, 29, 255);
    private static final Color TEXT = new Color(220, 220, 220);
    private static final Color MUTED = new Color(132, 132, 132);
    private static final Color TRACK = new Color(49, 49, 49);
    private static final float WINDOW_RADIUS = 3.0F;
    private static final float OPEN_SPEED = 7.0F;
    private static final float CLOSE_SPEED = 10.0F;
    private static final String CREWX_LOGO = "/assets/crewx/icons/crewx_logo.png";
    private static final String COMBAT_ICON = "/assets/crewx/icons/gui/combat.png";
    private static final String MOVEMENT_ICON = "/assets/crewx/icons/gui/movement.png";
    private static final String RENDER_ICON = "/assets/crewx/icons/gui/render.png";
    private static final String PLAYER_ICON = "/assets/crewx/icons/gui/player.png";
    private static final String MISC_ICON = "/assets/crewx/icons/gui/misc.png";
    private static final String SCRIPT_ICON = "/assets/crewx/icons/gui/script.png";
    private static final String SEARCH_ICON = "/assets/crewx/icons/gui/search.png";
    private static final String CLEAR_ICON = "/assets/crewx/icons/gui/clear.png";
    private static final Map<String, ResourceLocation> GUI_TEXTURES = new HashMap<String, ResourceLocation>();
    private static final ClickGuiFontRenderer FONT = ClientFont.getRenderer();

    private static Field modesField;

    private ViewTab activeTab = ViewTab.MODULES;
    private ModuleCategory selectedCategory = ModuleCategory.COMBAT;
    private Module selectedModule;
    private Module listeningModule;
    private SettingComponent focusedText;
    private SettingComponent draggingSetting;
    private int dragMode;
    private final List<SettingComponent> settingComponents = new ArrayList<SettingComponent>();
    private final List<File> savedConfigs = new ArrayList<File>();
    private File pendingConfigClick;
    private String selectedConfigName = "";
    private Module pendingModuleClick;
    private boolean pendingModuleSettingsHit;
    private int scrollDragTarget = DRAG_NONE;
    private boolean scrollDragActive;
    private float scrollDragOriginY;
    private float lastScrollDragY;
    private long lastConfigRefresh;
    private final Map<Module, Float> hoverAnimations = new HashMap<Module, Float>();
    private final Map<Module, Float> enabledAnimations = new HashMap<Module, Float>();
    private final Deque<float[]> scissorStack = new ArrayDeque<float[]>();
    private ModuleCategory visibleCacheCategory;
    private String visibleCacheSearch;
    private List<Module> visibleModulesCache;

    private String searchText = "";
    private boolean searchFocused;
    private boolean closing;
    private boolean draggingWindow;
    private float dragMouseOffsetX;
    private float dragMouseOffsetY;
    private float windowOffsetX;
    private float windowOffsetY;
    private float windowX;
    private float windowY;
    private float windowWidth;
    private float windowHeight;
    private float sidebarWidth;
    private float modulePaneWidth;
    private float settingsX;
    private float bodyY;
    private float bodyHeight;
    private float searchY;
    private float moduleListY;
    private float settingsListY;
    private float settingsListHeight;
    private float configListY;
    private float configListHeight;
    private float moduleScroll;
    private float targetModuleScroll;
    private float settingsScroll;
    private float targetSettingsScroll;
    private float configScroll;
    private float targetConfigScroll;
    private float centerX;
    private float centerY;
    private float renderScale = 1.0F;
    private float renderOffsetY;
    private float alphaMultiplier = 1.0F;
    private float deltaTime = 0.016F;
    private float lastMouseX;
    private float lastMouseY;
    private long lastFrame;
    private final long openedAt = System.currentTimeMillis();

    public ClickGui() {
        this.selectFirstModule();
    }

    @Override
    public void initGui() {
        this.closing = false;
        BlurController.setClickGuiOpen(true);
        this.lastFrame = 0L;
        this.computeLayout();
        this.refreshConfigs();
        if (this.selectedModule == null) {
            this.selectFirstModule();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.nanoTime();
        float dt = this.lastFrame == 0L ? 0.016F : (now - this.lastFrame) / 1.0e9F;
        this.lastFrame = now;
        this.deltaTime = Math.max(0.0F, Math.min(0.1F, dt));
        this.animationStep();
        if (this.closing && this.animation <= 0.0F) {
            mc.displayGuiScreen(null);
            return;
        }

        this.computeLayout();
        float virtualMouseX = this.toVirtualX(mouseX);
        float virtualMouseY = this.toVirtualY(mouseY);
        this.lastMouseX = virtualMouseX;
        this.lastMouseY = virtualMouseY;

        if (this.draggingWindow) {
            this.windowOffsetX = virtualMouseX - this.dragMouseOffsetX - (this.width - this.windowWidth) / 2.0F;
            this.windowOffsetY = virtualMouseY - this.dragMouseOffsetY - (this.height - this.windowHeight) / 2.0F;
            this.computeLayout();
        }
        if (this.draggingSetting != null) {
            this.draggingSetting.updateDrag((int) virtualMouseX, (int) virtualMouseY);
        }
        this.updateScrollDrag(virtualMouseY);
        if (this.activeTab == ViewTab.CONFIGS && System.currentTimeMillis() - this.lastConfigRefresh > 1000L) {
            this.refreshConfigs();
        }

        this.updateScroll();
        this.updateAnimations();
        this.alphaMultiplier = this.closing ? this.animation * this.animation : this.animation;
        this.centerX = this.width / 2.0F;
        this.centerY = this.height / 2.0F;
        this.scissorStack.clear();
        this.drawGradientRect(0, 0, this.width, this.height, this.fade(0x1E000000), this.fade(0x1E000000));

        GlStateManager.pushMatrix();
        GlStateManager.translate(this.centerX, this.centerY, 0.0F);
        GlStateManager.scale(this.renderScale, this.renderScale, 1.0F);
        GlStateManager.translate(-this.centerX, -this.centerY + this.renderOffsetY, 0.0F);
        this.renderWindow(virtualMouseX, virtualMouseY);
        GlStateManager.popMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private float animation;

    private void animationStep() {
        this.animation += (this.closing ? -CLOSE_SPEED : OPEN_SPEED) * this.deltaTime;
        this.animation = clamp01(this.animation);
        this.renderScale = 0.985F + 0.015F * this.animation;
        this.renderOffsetY = (1.0F - this.animation) * 3.0F;
    }

    private void computeLayout() {
        this.centerX = this.width / 2.0F;
        this.centerY = this.height / 2.0F;
        float availableWidth = Math.max(1.0F, this.width - 12.0F);
        float availableHeight = Math.max(1.0F, this.height - 12.0F);
        float preferredWidth = Math.max(Math.min(280.0F, availableWidth), this.width * 0.58F);
        float preferredHeight = Math.max(Math.min(210.0F, availableHeight), this.height * 0.65F);
        this.windowWidth = Math.min(820.0F, Math.min(availableWidth, preferredWidth));
        this.windowHeight = Math.min(470.0F, Math.min(availableHeight, preferredHeight));
        this.windowX = (this.width - this.windowWidth) / 2.0F + this.windowOffsetX;
        this.windowY = (this.height - this.windowHeight) / 2.0F + this.windowOffsetY;
        this.windowX = Math.max(0.0F, Math.min(this.width - this.windowWidth, this.windowX));
        this.windowY = Math.max(0.0F, Math.min(this.height - this.windowHeight, this.windowY));
        this.sidebarWidth = Math.min(136.0F, Math.max(40.0F, this.windowWidth * 0.20F));
        this.modulePaneWidth = Math.min(230.0F, Math.max(62.0F, this.windowWidth * 0.31F));
        float minimumSettingsWidth = Math.min(112.0F, this.windowWidth * 0.34F);
        if (this.windowWidth - this.sidebarWidth - this.modulePaneWidth < minimumSettingsWidth) {
            this.sidebarWidth = this.windowWidth * 0.20F;
            this.modulePaneWidth = this.windowWidth * 0.35F;
        }
        this.settingsX = this.windowX + this.sidebarWidth + this.modulePaneWidth;
        this.bodyY = this.windowY + HEADER_HEIGHT;
        this.bodyHeight = Math.max(0.0F, this.windowHeight - HEADER_HEIGHT - FOOTER_HEIGHT);
        this.searchY = this.bodyY + 34.0F;
        this.moduleListY = this.searchY + 27.0F;
        this.settingsListY = this.bodyY + (this.windowHeight < 260.0F ? 58.0F : 67.0F);
        this.settingsListHeight = Math.max(0.0F, this.windowY + this.windowHeight - FOOTER_HEIGHT - this.settingsListY - 7.0F);
        this.configListY = this.bodyY + 40.0F;
        this.configListHeight = Math.max(0.0F, this.windowY + this.windowHeight - FOOTER_HEIGHT - this.configListY - 8.0F);
    }

    private void updateScroll() {
        List<Module> modules = this.getVisibleModules();
        float moduleContent = modules.size() * MODULE_ROW_HEIGHT;
        float moduleVisible = this.bodyY + this.bodyHeight - this.moduleListY - 8.0F;
        float maxModule = Math.max(0.0F, moduleContent - moduleVisible);
        this.targetModuleScroll = Math.max(0.0F, Math.min(this.targetModuleScroll, maxModule));
        this.moduleScroll += (this.targetModuleScroll - this.moduleScroll) * Math.min(1.0F, this.deltaTime * 14.0F);

        float settingsContent = 0.0F;
        for (SettingComponent setting : this.settingComponents) {
            if (setting.property.isVisible()) settingsContent += setting.getHeight();
        }
        float maxSettings = Math.max(0.0F, settingsContent - this.settingsListHeight + 5.0F);
        this.targetSettingsScroll = Math.max(0.0F, Math.min(this.targetSettingsScroll, maxSettings));
        this.settingsScroll += (this.targetSettingsScroll - this.settingsScroll) * Math.min(1.0F, this.deltaTime * 14.0F);

        float configContent = this.savedConfigs.size() * CONFIG_ROW_HEIGHT;
        float maxConfigs = Math.max(0.0F, configContent - this.configListHeight + 4.0F);
        this.targetConfigScroll = Math.max(0.0F, Math.min(this.targetConfigScroll, maxConfigs));
        this.configScroll += (this.targetConfigScroll - this.configScroll) * Math.min(1.0F, this.deltaTime * 14.0F);

    }

    private void updateAnimations() {
        for (SettingComponent setting : this.settingComponents) {
            setting.updateAnimations(this.deltaTime);
        }
        List<Module> visibleModules = this.getVisibleModules();
        int visibleCount = visibleModules.size();
        for (int index = 0; index < visibleCount; index++) {
            Module module = visibleModules.get(index);
            float hover = this.hoverAnimations.containsKey(module) ? this.hoverAnimations.get(module).floatValue() : 0.0F;
            boolean hovered = this.lastMouseX >= this.windowX + this.sidebarWidth + 8.0F
                    && this.lastMouseX <= this.settingsX - 8.0F
                    && this.lastMouseY >= this.moduleListY - this.moduleScroll
                    && this.lastMouseY <= this.moduleListY - this.moduleScroll + visibleCount * MODULE_ROW_HEIGHT;
            float rowTop = this.moduleListY + index * MODULE_ROW_HEIGHT - this.moduleScroll;
            hovered = hovered && this.lastMouseY >= rowTop && this.lastMouseY <= rowTop + MODULE_ROW_HEIGHT - 3;
            this.hoverAnimations.put(module, approach(hover, hovered ? 1.0F : 0.0F, this.deltaTime, 12.0F));
            float enabled = this.enabledAnimations.containsKey(module) ? this.enabledAnimations.get(module).floatValue() : 0.0F;
            this.enabledAnimations.put(module, approach(enabled, module.isEnabled() ? 1.0F : 0.0F, this.deltaTime, 13.0F));
        }
    }

    private void renderWindow(float mouseX, float mouseY) {
        this.round(this.windowX, this.windowY, this.windowWidth, this.windowHeight,
                new Color(0, 0, 0, GuiModule.getBackgroundAlpha()).getRGB(), WINDOW_RADIUS);
        this.round(this.windowX + 1.0F, this.windowY + 1.0F, this.windowWidth - 2.0F, HEADER_HEIGHT - 1.0F,
                new Color(6, 6, 6, 255).getRGB(), WINDOW_RADIUS);
        if (this.activeTab == ViewTab.MODULES) {
            this.round(this.windowX, this.bodyY, this.sidebarWidth, this.bodyHeight, SIDEBAR.getRGB(), 0.0F);
            this.round(this.windowX + this.sidebarWidth, this.bodyY, this.modulePaneWidth, this.bodyHeight, PANEL.getRGB(), 0.0F);
            this.round(this.settingsX, this.bodyY, this.windowWidth - this.sidebarWidth - this.modulePaneWidth,
                    this.bodyHeight, PANEL_ALT.getRGB(), 0.0F);
            this.round(this.windowX + this.sidebarWidth, this.bodyY, 1.0F, this.bodyHeight, new Color(36, 36, 36, 255).getRGB(), 0.0F);
            this.round(this.settingsX, this.bodyY, 1.0F, this.bodyHeight, new Color(36, 36, 36, 255).getRGB(), 0.0F);
        } else {
            this.round(this.windowX, this.bodyY, this.windowWidth, this.bodyHeight, PANEL.getRGB(), 0.0F);
        }
        this.round(this.windowX, this.windowY + this.windowHeight - FOOTER_HEIGHT,
                this.windowWidth, FOOTER_HEIGHT, new Color(5, 5, 5, 255).getRGB(), 0.0F);

        this.renderHeader();
        if (this.activeTab == ViewTab.CONFIGS) {
            this.renderConfigsPane(mouseX, mouseY);
        } else {
            this.renderSidebar(mouseX, mouseY);
            this.renderModulePane(mouseX, mouseY);
            this.renderSettingsPane(mouseX, mouseY);
        }
        this.renderFooter();
    }

    private void renderHeader() {
        boolean compactHeader = this.windowWidth < 200.0F;
        float logoX = this.windowX + (compactHeader ? 8.0F : 11.0F);
        float logoY = this.windowY + (compactHeader ? 12.0F : 7.0F);
        int logoSize = compactHeader ? 16 : 25;
        this.drawLogo(logoX, logoY, logoSize);
        if (!compactHeader) this.text("CREWX", logoX + 31.0F, this.windowY + 15.0F, TEXT.getRGB());

        String modulesLabel = this.headerLabel(ViewTab.MODULES);
        String configsLabel = this.headerLabel(ViewTab.CONFIGS);
        this.drawHeaderTab(modulesLabel, this.modulesTabX(), this.activeTab == ViewTab.MODULES);
        this.drawHeaderTab(configsLabel, this.configsTabX(), this.activeTab == ViewTab.CONFIGS);
        if (this.windowWidth >= 370.0F) {
            String closeHint = "ESC";
            this.text(closeHint, this.windowX + this.windowWidth - FONT.getStringWidth(closeHint) - 12.0F,
                    this.windowY + 15.0F, MUTED.getRGB());
        }
    }

    private float modulesTabX() {
        if (this.windowWidth < 200.0F) return this.windowX + 31.0F;
        return this.windowX + Math.max(82.0F, Math.min(94.0F, this.windowWidth * 0.34F));
    }

    private float configsTabX() {
        return this.modulesTabX() + FONT.getStringWidth(this.headerLabel(ViewTab.MODULES)) + 8.0F;
    }

    private String headerLabel(ViewTab tab) {
        if (this.windowWidth < 360.0F) {
            if (tab == ViewTab.MODULES) return "MOD";
            if (tab == ViewTab.CONFIGS) return "CFG";
            return "CFG";
        }
        if (tab == ViewTab.MODULES) return "MODULES";
        if (tab == ViewTab.CONFIGS) return "CONFIGS";
        return "CONFIGS";
    }

    private void drawHeaderTab(String label, float x, boolean selected) {
        this.text(label, x, this.windowY + 15.0F, selected ? TEXT.getRGB() : MUTED.getRGB());
        if (selected) {
            this.round(x, this.windowY + 30.0F, FONT.getStringWidth(label), 2.0F, accentColor().getRGB(), 0.0F);
        }
    }

    private void drawLogo(float x, float y, int size) {
        this.drawIcon(CREWX_LOGO, x, y, size, 0xFFFFFFFF);
    }

    private void drawIcon(String resource, float x, float y, int size, int color) {
        int faded = this.fade(color);
        ResourceLocation texture = this.loadGuiTexture(resource);
        if (texture == null) return;
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        mc.getTextureManager().bindTexture(texture);
        GlStateManager.color((faded >> 16 & 255) / 255.0F, (faded >> 8 & 255) / 255.0F,
                (faded & 255) / 255.0F, (faded >>> 24) / 255.0F);
        Gui.drawModalRectWithCustomSizedTexture((int) x, (int) y, 0.0F, 0.0F, size, size,
                (float) size, (float) size);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
    }

    private ResourceLocation loadGuiTexture(String resource) {
        ResourceLocation cached = GUI_TEXTURES.get(resource);
        if (cached != null) return cached;
        try (InputStream stream = ClickGui.class.getResourceAsStream(resource)) {
            if (stream == null) return null;
            BufferedImage image = ImageIO.read(stream);
            if (image == null) return null;
            ResourceLocation texture = mc.getTextureManager().getDynamicTextureLocation(
                    "crewx_clickgui_" + Integer.toHexString(resource.hashCode()), new DynamicTexture(image));
            GUI_TEXTURES.put(resource, texture);
            return texture;
        } catch (Exception ignored) {
            return null;
        }
    }

    private float categoryNavStep() {
        float navHeight = this.bodyHeight < 140.0F ? this.bodyHeight - 34.0F : this.bodyHeight - 57.0F;
        return Math.max(10.0F, Math.min(29.0F, navHeight / CATEGORIES.length));
    }

    private String trimToWidth(String value, int maxWidth) {
        if (maxWidth <= 4) return "";
        String result = value;
        while (!result.isEmpty() && FONT.getStringWidth(result) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private void renderSidebar(float mouseX, float mouseY) {
        float x = this.windowX;
        float y = this.bodyY;
        this.text("CATEGORIES", x + 8.0F, y + 7.0F, MUTED.getRGB());
        float navY = y + (this.bodyHeight < 140.0F ? 18.0F : 26.0F);
        float navStep = this.categoryNavStep();
        for (ModuleCategory category : CATEGORIES) {
            boolean selected = category == this.selectedCategory;
            boolean hover = mouseX >= x + 5.0F && mouseX <= x + this.sidebarWidth - 5.0F
                    && mouseY >= navY && mouseY <= navY + navStep - 2.0F;
            if (selected || hover) {
                this.round(x + 6.0F, navY, this.sidebarWidth - 12.0F, navStep - 2.0F,
                        selected ? new Color(27, 27, 27, 255).getRGB() : new Color(17, 17, 17, 255).getRGB(), 2.0F);
            }
            if (selected) this.round(x + 6.0F, navY + 4.0F, 2.0F, Math.max(8.0F, navStep - 10.0F), accentColor().getRGB(), 0.0F);
            int iconColor = selected
                    ? (category == ModuleCategory.PLAYER ? Color.WHITE.getRGB() : accentColor().brighter().getRGB())
                    : MUTED.getRGB();
            float iconSize = Math.max(10.0F, Math.min(16.0F, navStep - 1.0F));
            this.drawCategoryIcon(category, x + Math.min(22.0F, this.sidebarWidth / 2.0F),
                    navY + (navStep - 2.0F) / 2.0F, iconColor, iconSize);
            if (this.sidebarWidth >= 88.0F) {
                this.text(category.getDisplayName(), x + 36.0F,
                        navY + navStep / 2.0F - 1.0F - FONT.getFontHeight() / 2.0F,
                        selected ? TEXT.getRGB() : MUTED.getRGB());
            }
            int activeCount = 0;
            for (Module module : this.getModules(category)) if (module.isEnabled()) activeCount++;
            if (activeCount > 0 && this.sidebarWidth >= 112.0F) {
                String count = String.valueOf(activeCount);
                int countWidth = FONT.getStringWidth(count);
                this.text(count, x + this.sidebarWidth - countWidth - 12.0F,
                        navY + navStep / 2.0F - 1.0F - FONT.getFontHeight() / 2.0F, MUTED.getRGB());
            }
            navY += navStep;
        }
        if (this.bodyHeight < 140.0F) return;
        float dividerY = this.windowY + this.windowHeight - FOOTER_HEIGHT - 31.0F;
        this.round(x + 11.0F, dividerY, this.sidebarWidth - 22.0F, 1.0F,
                new Color(37, 37, 37, 255).getRGB(), 0.0F);
        this.text("#CCW", x + 11.0F, dividerY + 10.0F, MUTED.getRGB());
    }

    private void renderModulePane(float mouseX, float mouseY) {
        float x = this.windowX + this.sidebarWidth;
        float width = this.modulePaneWidth;
        float contentX = x + 9.0F;
        List<Module> visible = this.getVisibleModules();
        String count = String.valueOf(visible.size());
        boolean showCount = width >= 74.0F;
        String heading = this.trimToWidth("MODULES", (int) (width - (showCount ? 36.0F : 20.0F)));
        this.text(heading, contentX + 2.0F, this.bodyY + 12.0F, MUTED.getRGB());
        if (showCount) {
            this.text(count, x + width - FONT.getStringWidth(count) - 12.0F, this.bodyY + 12.0F, MUTED.getRGB());
        }

        float searchWidth = width - 18.0F;
        boolean searchHover = mouseX >= contentX && mouseX <= contentX + searchWidth
                && mouseY >= this.searchY && mouseY <= this.searchY + 22.0F;
        this.round(contentX, this.searchY, searchWidth, 22.0F,
                this.searchFocused ? new Color(22, 22, 22, 255).getRGB() : new Color(15, 15, 15, 255).getRGB(), 2.0F);
        if (this.searchFocused) {
            this.round(contentX, this.searchY + 21.0F, searchWidth, 1.0F, accentColor().getRGB(), 0.0F);
        } else if (searchHover) {
            this.round(contentX, this.searchY + 21.0F, searchWidth, 1.0F, new Color(68, 68, 68, 255).getRGB(), 0.0F);
        }
        this.drawSearchIcon(contentX + 4.0F, this.searchY + 4.0F, MUTED.getRGB());
        String query = this.searchText.isEmpty() && !this.searchFocused ? "Search modules" : this.searchText + (this.searchFocused ? "_" : "");
        int queryWidth = Math.max(0, (int) searchWidth - (this.searchText.isEmpty() ? 34 : 47));
        this.text(this.trimToWidth(query, queryWidth), contentX + 22.0F,
                this.searchY + (22.0F - FONT.getFontHeight()) / 2.0F,
                this.searchText.isEmpty() && !this.searchFocused ? MUTED.getRGB() : TEXT.getRGB());
        if (!this.searchText.isEmpty()) this.drawIcon(CLEAR_ICON, contentX + searchWidth - 15.0F,
                this.searchY + 6.0F, 10, MUTED.getRGB());

        float visibleHeight = Math.max(0.0F, this.bodyY + this.bodyHeight - this.moduleListY - 5.0F);
        this.scissorOn(x + 5.0F, this.moduleListY, width - 10.0F, visibleHeight);
        float rowY = this.moduleListY - this.moduleScroll;
        for (Module module : visible) {
            this.renderModuleRow(module, x + 6.0F, rowY, width - 12.0F, mouseX, mouseY);
            rowY += MODULE_ROW_HEIGHT;
        }
        this.scissorOff();

        float contentHeight = visible.size() * MODULE_ROW_HEIGHT;
        if (contentHeight > visibleHeight) this.drawScrollbar(x + width - 3.0F, this.moduleListY, visibleHeight, contentHeight, this.moduleScroll);
        if (visible.isEmpty()) this.text("No modules found", contentX + 5.0F, this.moduleListY + 15.0F, MUTED.getRGB());
    }

    private void renderModuleRow(Module module, float x, float y, float width, float mouseX, float mouseY) {
        if (y + MODULE_ROW_HEIGHT <= this.moduleListY || y >= this.bodyY + this.bodyHeight) return;
        float hover = this.hoverAnimations.containsKey(module) ? this.hoverAnimations.get(module).floatValue() : 0.0F;
        float enabled = this.enabledAnimations.containsKey(module) ? this.enabledAnimations.get(module).floatValue() : (module.isEnabled() ? 1.0F : 0.0F);
        boolean selected = module == this.selectedModule;
        int bg = selected ? new Color(27, 27, 27, 255).getRGB() : lerpColor(PANEL.getRGB(), ROW_HOVER.getRGB(), hover);
        this.round(x, y + 1.0F, width, MODULE_ROW_HEIGHT - 3.0F, bg, 2.0F);
        if (selected) this.round(x, y + 5.0F, 2.0F, MODULE_ROW_HEIGHT - 11.0F, accentColor().getRGB(), 0.0F);

        String name = module == this.listeningModule ? "Press a key..." : module.getName().replace('-', ' ');
        int key = module.getKey();
        String keyName = key != 0 && module != this.listeningModule ? KeyBindUtil.getKeyName(key) : null;
        float nameMaxWidth = width - 39.0F;
        if (keyName != null && width >= 88.0F) {
            nameMaxWidth = Math.min(nameMaxWidth, width - 60.0F - FONT.getStringWidth(keyName));
            this.text(keyName, x + width - 52.0F - FONT.getStringWidth(keyName),
                    y + (MODULE_ROW_HEIGHT - FONT.getFontHeight()) / 2.0F, MUTED.getRGB());
        }
        this.text(this.trimToWidth(name, (int) Math.max(0.0F, nameMaxWidth)),
                x + 8.0F, y + (MODULE_ROW_HEIGHT - FONT.getFontHeight()) / 2.0F,
                enabled > 0.5F ? 0xFFFFFFFF : TEXT.getRGB());
        float switchX = x + width - 25.0F;
        float switchY = y + 10.0F;
        this.round(switchX, switchY, 18.0F, 9.0F,
                lerpColor(TRACK.getRGB(), accentColor().getRGB(), enabled), 4.0F);
        this.round(switchX + 1.0F + enabled * 8.0F, switchY + 1.0F, 7.0F, 7.0F, 0xFFFFFFFF, 3.5F);
    }

    private void renderSettingsPane(float mouseX, float mouseY) {
        float right = this.windowX + this.windowWidth;
        float width = right - this.settingsX;
        if (this.selectedModule == null) {
            this.text("Select a module", this.settingsX + 14.0F, this.bodyY + 20.0F, MUTED.getRGB());
            return;
        }

        float x = this.settingsX + 12.0F;
        float actionWidth = Math.min(48.0F, Math.max(28.0F, (width - 40.0F) / 2.0F));
        float bindX = right - 12.0F - actionWidth;
        float toggleX = bindX - 5.0F - actionWidth;
        float controlY = this.bodyY + 18.0F;
        if (width >= 220.0F) {
            this.text("MODULE SETTINGS", x, this.bodyY + 9.0F, MUTED.getRGB());
            String moduleName = this.trimToWidth(this.selectedModule.getName(), (int) Math.max(0.0F, toggleX - x - 7.0F));
            this.text(moduleName, x, this.bodyY + 29.0F, TEXT.getRGB());
        }

        this.round(toggleX, controlY, actionWidth, 20.0F,
                this.selectedModule.isEnabled() ? new Color(38, 38, 38, 255).getRGB() : ROW.getRGB(), 2.0F);
        String toggleLabel = this.selectedModule.isEnabled() ? "ON" : "OFF";
        float toggleLabelX = actionWidth >= 46.0F ? toggleX + 6.0F
                : toggleX + (actionWidth - FONT.getStringWidth(toggleLabel)) / 2.0F;
        this.text(toggleLabel, toggleLabelX, controlY + (20.0F - FONT.getFontHeight()) / 2.0F,
                this.selectedModule.isEnabled() ? accentColor().brighter().getRGB() : MUTED.getRGB());
        if (actionWidth >= 46.0F) {
            this.drawMiniSwitch(toggleX + actionWidth - 20.0F, controlY + 5.0F,
                    this.selectedModule.isEnabled() ? 1.0F : 0.0F);
        }
        String bindText = this.listeningModule == this.selectedModule ? "PRESS" : "KEY";
        this.round(bindX, controlY, actionWidth, 20.0F, ROW.getRGB(), 2.0F);
        bindText = this.trimToWidth(bindText, (int) Math.max(0.0F, actionWidth - 8.0F));
        this.text(bindText, bindX + (actionWidth - FONT.getStringWidth(bindText)) / 2.0F,
                controlY + (20.0F - FONT.getFontHeight()) / 2.0F,
                this.listeningModule == this.selectedModule ? accentColor().brighter().getRGB() : MUTED.getRGB());

        this.round(x, this.bodyY + 53.0F, width - 24.0F, 1.0F, new Color(38, 38, 38, 255).getRGB(), 0.0F);
        List<Property<?>> properties = this.getProperties(this.selectedModule);
        int visibleProperties = 0;
        if (properties != null) for (Property<?> property : properties) if (property.isVisible()) visibleProperties++;
        String propertiesLabel = width >= 112.0F ? "PROPERTIES" : "PROPS";
        this.text(propertiesLabel, x, this.settingsListY - 13.0F, MUTED.getRGB());
        String count = String.valueOf(visibleProperties);
        if (width >= 112.0F) {
            this.text(count, right - 12.0F - FONT.getStringWidth(count), this.settingsListY - 13.0F, MUTED.getRGB());
        }

        this.scissorOn(this.settingsX + 6.0F, this.settingsListY, width - 12.0F, this.settingsListHeight);
        float settingY = this.settingsListY - this.settingsScroll;
        for (SettingComponent setting : this.settingComponents) {
            if (!setting.property.isVisible()) continue;
            float height = setting.getHeight();
            if (settingY + height > this.settingsListY && settingY < this.settingsListY + this.settingsListHeight) {
                setting.render(this.settingsX + 9.0F, settingY, (int) (width - 18.0F), mouseX, mouseY);
            }
            settingY += height;
        }
        this.scissorOff();

        float settingsContent = 0.0F;
        for (SettingComponent setting : this.settingComponents) if (setting.property.isVisible()) settingsContent += setting.getHeight();
        if (settingsContent > this.settingsListHeight) {
            this.drawScrollbar(right - 3.0F, this.settingsListY, this.settingsListHeight, settingsContent, this.settingsScroll);
        }
        if (visibleProperties == 0) this.text("No settings", x + 3.0F, this.settingsListY + 12.0F, MUTED.getRGB());
    }

    private void renderConfigsPane(float mouseX, float mouseY) {
        float x = this.windowX + 14.0F;
        float width = Math.max(0.0F, this.windowWidth - 28.0F);
        String heading = width >= 100.0F ? "SAVED CONFIGS" : "CONFIGS";
        heading = this.trimToWidth(heading, (int) Math.max(0.0F, width - 24.0F));
        this.text(heading, x + 2.0F, this.bodyY + 13.0F, MUTED.getRGB());
        String count = String.valueOf(this.savedConfigs.size());
        if (width >= 130.0F) {
            this.text(count, this.windowX + this.windowWidth - FONT.getStringWidth(count) - 18.0F,
                    this.bodyY + 13.0F, MUTED.getRGB());
        }

        if (this.savedConfigs.isEmpty()) {
            float messageY = this.configListY + 16.0F;
            this.text(this.trimToWidth("Você não tem nenhuma configuração salva.", (int) (width - 12.0F)),
                    x + 7.0F, messageY, TEXT.getRGB());
            this.text(this.trimToWidth("Use .config save <nome> para salvar.", (int) (width - 12.0F)),
                    x + 7.0F, messageY + 17.0F, MUTED.getRGB());
            this.text(this.trimToWidth("As configs salvas aparecerão aqui.", (int) (width - 12.0F)),
                    x + 7.0F, messageY + 34.0F, MUTED.getRGB());
            return;
        }

        this.scissorOn(x, this.configListY, width, this.configListHeight);
        float rowY = this.configListY - this.configScroll;
        for (File file : this.savedConfigs) {
            String name = this.configName(file);
            if (rowY + CONFIG_ROW_HEIGHT > this.configListY
                    && rowY < this.configListY + this.configListHeight) {
                boolean hover = mouseX >= x && mouseX <= x + width
                        && mouseY >= rowY && mouseY <= rowY + CONFIG_ROW_HEIGHT - 3.0F;
                boolean selected = name.equals(this.selectedConfigName);
                int background = selected ? new Color(30, 30, 30, 255).getRGB()
                        : hover ? ROW_HOVER.getRGB() : ROW.getRGB();
                this.round(x + 1.0F, rowY + 1.0F, width - 7.0F, CONFIG_ROW_HEIGHT - 4.0F, background, 2.0F);
                if (selected) this.round(x + 1.0F, rowY + 6.0F, 2.0F, CONFIG_ROW_HEIGHT - 14.0F,
                        accentColor().getRGB(), 0.0F);
                String shown = this.trimToWidth(name, (int) (width - 74.0F));
                float rowTextY = rowY + 1.0F + (CONFIG_ROW_HEIGHT - 4.0F - FONT.getFontHeight()) / 2.0F;
                this.text(shown, x + 10.0F, rowTextY, TEXT.getRGB());
                if (width > 125.0F) {
                    String status = selected ? "LOADED" : "LOAD";
                    this.text(status, x + width - FONT.getStringWidth(status) - 17.0F,
                            rowTextY, selected ? accentColor().brighter().getRGB() : MUTED.getRGB());
                }
            }
            rowY += CONFIG_ROW_HEIGHT;
        }
        this.scissorOff();
        float contentHeight = this.savedConfigs.size() * CONFIG_ROW_HEIGHT;
        if (contentHeight > this.configListHeight) {
            this.drawScrollbar(x + width - 3.0F, this.configListY, this.configListHeight,
                    contentHeight, this.configScroll);
        }
    }

    private void refreshConfigs() {
        File directory = new File("./config/CrewX/");
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
        this.savedConfigs.clear();
        if (files != null) {
            for (File file : files) if (file.isFile()) this.savedConfigs.add(file);
        }
        Collections.sort(this.savedConfigs, (first, second) -> Long.compare(second.lastModified(), first.lastModified()));
        if (!this.selectedConfigName.isEmpty()) {
            boolean found = false;
            for (File file : this.savedConfigs) {
                if (this.configName(file).equals(this.selectedConfigName)) {
                    found = true;
                    break;
                }
            }
            if (!found) this.selectedConfigName = "";
        }
        this.lastConfigRefresh = System.currentTimeMillis();
    }

    private String configName(File file) {
        String name = file.getName();
        return name.toLowerCase(Locale.ROOT).endsWith(".json") ? name.substring(0, name.length() - 5) : name;
    }

    private File getConfigAt(float mouseX, float mouseY) {
        if (mouseX < this.windowX + 14.0F || mouseX > this.windowX + this.windowWidth - 14.0F
                || mouseY < this.configListY || mouseY > this.configListY + this.configListHeight) return null;
        int index = (int) ((mouseY - this.configListY + this.configScroll) / CONFIG_ROW_HEIGHT);
        return index >= 0 && index < this.savedConfigs.size() ? this.savedConfigs.get(index) : null;
    }

    private void loadConfig(File file) {
        if (file == null) return;
        String name = this.configName(file);
        new Config(name, false).load();
        this.selectedConfigName = name;
        this.refreshConfigs();
    }

    private void setActiveTab(ViewTab tab) {
        if (this.activeTab == tab) return;
        this.setFocus(null);
        this.activeTab = tab;
        this.clearScrollDrag();
        if (tab == ViewTab.CONFIGS) {
            this.refreshConfigs();
            this.configScroll = 0.0F;
            this.targetConfigScroll = 0.0F;
        }
    }

    private void beginScrollDrag(int target, float mouseY) {
        this.scrollDragTarget = target;
        this.scrollDragActive = false;
        this.scrollDragOriginY = mouseY;
        this.lastScrollDragY = mouseY;
        this.pendingModuleClick = null;
        this.pendingConfigClick = null;
        this.pendingModuleSettingsHit = false;
    }

    private void updateScrollDrag(float mouseY) {
        if (this.scrollDragTarget == DRAG_NONE || !Mouse.isButtonDown(0)) return;
        if (!this.scrollDragActive && Math.abs(mouseY - this.scrollDragOriginY) >= 3.0F) {
            this.scrollDragActive = true;
            this.pendingModuleClick = null;
            this.pendingConfigClick = null;
        }
        if (this.scrollDragActive) {
            float delta = mouseY - this.lastScrollDragY;
            if (this.scrollDragTarget == DRAG_MODULES) this.targetModuleScroll -= delta;
            else if (this.scrollDragTarget == DRAG_SETTINGS) this.targetSettingsScroll -= delta;
            else if (this.scrollDragTarget == DRAG_CONFIGS) this.targetConfigScroll -= delta;
        }
        this.lastScrollDragY = mouseY;
    }

    private void clearScrollDrag() {
        this.scrollDragTarget = DRAG_NONE;
        this.scrollDragActive = false;
        this.pendingModuleClick = null;
        this.pendingConfigClick = null;
        this.pendingModuleSettingsHit = false;
    }

    private void renderFooter() {
        float y = this.windowY + this.windowHeight - FOOTER_HEIGHT;
        this.round(this.windowX, y, this.windowWidth, 1.0F, new Color(35, 35, 35, 255).getRGB(), 0.0F);
        String hint = this.activeTab == ViewTab.CONFIGS ? "Click to load    Drag to scroll"
                : "L toggle    R settings    M bind";
        float x = this.windowX + 12.0F;
        float textY = y + (FOOTER_HEIGHT - FONT.getFontHeight()) / 2.0F;
        this.text(this.trimToWidth(hint, (int) (this.windowWidth - 60.0F)), x, textY, MUTED.getRGB());
        this.text("ESC", this.windowX + this.windowWidth - FONT.getStringWidth("ESC") - 12.0F,
                textY, TEXT.getRGB());
    }

    private void drawMiniSwitch(float x, float y, float value) {
        this.round(x, y, 16.0F, 10.0F, value > 0.5F ? new Color(255, 255, 255, 95).getRGB() : new Color(255, 255, 255, 55).getRGB(), 5.0F);
        this.round(x + (value > 0.5F ? 7.0F : 1.0F), y + 1.0F, 8.0F, 8.0F, 0xFFFFFFFF, 4.0F);
    }

    private void drawSearchIcon(float x, float y, int color) {
        this.drawIcon(SEARCH_ICON, x, y, 14, color);
    }

    private void drawScrollbar(float x, float y, float visible, float total, float scroll) {
        if (visible <= 8.0F || total <= visible) return;
        float track = visible - 8.0F;
        this.round(x, y + 4.0F, 2.0F, track, new Color(53, 61, 77, 100).getRGB(), 1.0F);
        float thumb = Math.max(18.0F, track * visible / total);
        float maxScroll = total - visible;
        float position = maxScroll <= 0.0F ? 0.0F : (track - thumb) * clamp01(scroll / maxScroll);
        this.round(x, y + 4.0F + position, 2.0F, thumb, withAlpha(accentColor().getRGB(), 0.85F), 1.0F);
    }

    private List<Module> getModules(ModuleCategory category) {
        return CrewX.moduleManager.getModules(category);
    }

    private List<Module> getVisibleModules() {
        String query = this.searchText.trim().toLowerCase(Locale.ROOT);
        if (this.visibleModulesCache != null && this.visibleCacheCategory == this.selectedCategory
                && query.equals(this.visibleCacheSearch)) {
            return this.visibleModulesCache;
        }
        List<Module> result = new ArrayList<Module>();
        for (Module module : this.getModules(this.selectedCategory)) {
            if (query.isEmpty() || module.getName().toLowerCase(java.util.Locale.ROOT).contains(query)) {
                result.add(module);
            }
        }
        this.visibleCacheCategory = this.selectedCategory;
        this.visibleCacheSearch = query;
        this.visibleModulesCache = result;
        return this.visibleModulesCache;
    }

    private List<Property<?>> getProperties(Module module) {
        if (CrewX.propertyManager == null || module == null) return null;
        return CrewX.propertyManager.properties.get(module);
    }

    private void selectCategory(ModuleCategory category) {
        if (category == this.selectedCategory) return;
        this.selectedCategory = category;
        this.moduleScroll = 0.0F;
        this.targetModuleScroll = 0.0F;
        this.selectFirstModule();
    }

    private void selectFirstModule() {
        List<Module> modules = this.getModules(this.selectedCategory);
        this.selectModule(modules.isEmpty() ? null : modules.get(0));
    }

    private void selectModule(Module module) {
        if (this.selectedModule == module) return;
        this.setFocus(null);
        this.selectedModule = module;
        this.settingComponents.clear();
        this.settingsScroll = 0.0F;
        this.targetSettingsScroll = 0.0F;
        List<Property<?>> properties = this.getProperties(module);
        if (properties != null) {
            for (Property<?> property : properties) {
                this.settingComponents.add(new SettingComponent(property));
            }
        }
    }

    private void setFocus(SettingComponent component) {
        if (this.focusedText != null && this.focusedText != component) {
            this.focusedText.finishEditing();
        }
        this.focusedText = component;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (this.closing) return;
        float mx = this.toVirtualX(mouseX);
        float my = this.toVirtualY(mouseY);
        this.lastMouseX = mx;
        this.lastMouseY = my;

        if (this.listeningModule != null) {
            this.listeningModule.setKey(mouseButton - 100);
            this.listeningModule = null;
            return;
        }
        if (this.searchFocused && !this.insideSearch(mx, my)) this.searchFocused = false;

        if (mx >= this.windowX && mx <= this.windowX + this.windowWidth
                && my >= this.windowY && my <= this.windowY + HEADER_HEIGHT) {
            if (mouseButton == 0) {
                float modulesX = this.modulesTabX();
                float configsX = this.configsTabX();
                String modulesLabel = this.headerLabel(ViewTab.MODULES);
                String configsLabel = this.headerLabel(ViewTab.CONFIGS);
                if (mx >= modulesX - 5.0F && mx <= modulesX + FONT.getStringWidth(modulesLabel) + 6.0F) {
                    this.setActiveTab(ViewTab.MODULES);
                    return;
                }
                if (mx >= configsX - 5.0F && mx <= configsX + FONT.getStringWidth(configsLabel) + 6.0F) {
                    this.setActiveTab(ViewTab.CONFIGS);
                    return;
                }
            }
            this.draggingWindow = mouseButton == 0;
            this.dragMouseOffsetX = mx - this.windowX;
            this.dragMouseOffsetY = my - this.windowY;
            return;
        }

        if (this.activeTab == ViewTab.CONFIGS) {
            if (mouseButton == 0 && my >= this.configListY && my <= this.configListY + this.configListHeight) {
                this.beginScrollDrag(DRAG_CONFIGS, my);
                this.pendingConfigClick = this.getConfigAt(mx, my);
            }
            this.setFocus(null);
            return;
        }

        float navY = this.bodyY + (this.bodyHeight < 140.0F ? 18.0F : 26.0F);
        float navStep = this.categoryNavStep();
        for (ModuleCategory category : CATEGORIES) {
            if (mx >= this.windowX + 5.0F && mx <= this.windowX + this.sidebarWidth - 5.0F
                    && my >= navY && my <= navY + navStep - 2.0F) {
                this.selectCategory(category);
                this.setFocus(null);
                return;
            }
            navY += navStep;
        }

        float searchX = this.windowX + this.sidebarWidth + 9.0F;
        float searchWidth = this.modulePaneWidth - 18.0F;
        if (mx >= searchX && mx <= searchX + searchWidth
                && my >= this.searchY && my <= this.searchY + 22.0F) {
            if (!this.searchText.isEmpty() && mx >= searchX + searchWidth - 19.0F) {
                this.searchText = "";
                this.targetModuleScroll = 0.0F;
            }
            this.searchFocused = true;
            this.setFocus(null);
            return;
        }

        if (mx >= this.windowX + this.sidebarWidth + 6.0F && mx < this.settingsX
                && my >= this.moduleListY && my <= this.bodyY + this.bodyHeight) {
            if (mouseButton == 0) this.beginScrollDrag(DRAG_MODULES, my);
            List<Module> visible = this.getVisibleModules();
            int index = (int) ((my - this.moduleListY + this.moduleScroll) / MODULE_ROW_HEIGHT);
            if (index >= 0 && index < visible.size()) {
                Module module = visible.get(index);
                float rowX = this.windowX + this.sidebarWidth + 7.0F;
                float rowWidth = this.modulePaneWidth - 14.0F;
                boolean settingsHit = mx >= rowX + rowWidth - 39.0F;
                if (mouseButton == 0) {
                    this.pendingModuleClick = module;
                    this.pendingModuleSettingsHit = settingsHit;
                } else if (mouseButton == 2) {
                    this.listeningModule = module;
                } else if (mouseButton == 1 || settingsHit) {
                    this.selectModule(module);
                }
                return;
            }
            if (mouseButton == 0) return;
        }

        if (this.selectedModule != null && mx >= this.settingsX && my >= this.bodyY) {
            float right = this.windowX + this.windowWidth;
            float paneWidth = right - this.settingsX;
            float actionWidth = Math.min(48.0F, Math.max(28.0F, (paneWidth - 40.0F) / 2.0F));
            float bindX = right - 12.0F - actionWidth;
            float toggleX = bindX - 5.0F - actionWidth;
            float controlY = this.bodyY + 18.0F;
            if (my >= controlY && my <= controlY + 20.0F) {
                if (mx >= toggleX && mx <= toggleX + actionWidth && mouseButton == 0) {
                    this.selectedModule.toggle();
                    return;
                }
                if (mx >= bindX && mx <= bindX + actionWidth && (mouseButton == 0 || mouseButton == 2)) {
                    this.listeningModule = this.selectedModule;
                    return;
                }
            }
            if (mx >= this.settingsX + 8.0F && my >= this.settingsListY
                    && my <= this.settingsListY + this.settingsListHeight) {
                boolean consumed = this.dispatchSettingClick(mx, my, mouseButton, true);
                if (mouseButton == 0 && !consumed) this.beginScrollDrag(DRAG_SETTINGS, my);
                if (consumed || mouseButton == 0 || mouseButton == 1) return;
                return;
            }
        }

        this.setFocus(null);
    }

    private boolean insideSearch(float mouseX, float mouseY) {
        float x = this.windowX + this.sidebarWidth + 9.0F;
        return mouseX >= x && mouseX <= x + this.modulePaneWidth - 18.0F
                && mouseY >= this.searchY && mouseY <= this.searchY + 22.0F;
    }

    private boolean dispatchSettingClick(float mouseX, float mouseY, int mouseButton, boolean pressPhase) {
        float y = this.settingsListY - this.settingsScroll;
        float x = this.settingsX + 12.0F;
        float width = this.windowX + this.windowWidth - this.settingsX - 24.0F;
        for (SettingComponent setting : this.settingComponents) {
            if (!setting.property.isVisible()) continue;
            float height = setting.getHeight();
            if (mouseY >= y && mouseY <= y + height) {
                return setting.mouseClicked(x, y, (int) width, mouseX, mouseY, mouseButton, pressPhase);
            }
            y += height;
        }
        return false;
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        float mx = this.toVirtualX(mouseX);
        float my = this.toVirtualY(mouseY);
        if (state == 0 && !this.scrollDragActive) {
            if (this.scrollDragTarget == DRAG_MODULES && this.pendingModuleClick != null) {
                List<Module> visible = this.getVisibleModules();
                int index = (int) ((my - this.moduleListY + this.moduleScroll) / MODULE_ROW_HEIGHT);
                if (index >= 0 && index < visible.size() && visible.get(index) == this.pendingModuleClick) {
                    if (this.pendingModuleSettingsHit) this.selectModule(this.pendingModuleClick);
                    else this.pendingModuleClick.toggle();
                }
            } else if (this.scrollDragTarget == DRAG_CONFIGS && this.pendingConfigClick != null) {
                File releasedConfig = this.getConfigAt(mx, my);
                if (this.pendingConfigClick.equals(releasedConfig)) this.loadConfig(releasedConfig);

            }
        }

        boolean releaseSettings = this.draggingSetting != null
                || (this.scrollDragTarget == DRAG_SETTINGS && !this.scrollDragActive);
        if (this.activeTab == ViewTab.MODULES && state == 0 && releaseSettings
                && this.selectedModule != null && mx >= this.settingsX
                && my >= this.settingsListY && my <= this.settingsListY + this.settingsListHeight) {
            this.dispatchSettingClick(mx, my, state, false);
        }
        this.draggingWindow = false;
        this.draggingSetting = null;
        this.clearScrollDrag();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        float mouseX = this.toVirtualX(Mouse.getEventX() * this.width / mc.displayWidth);
        float mouseY = this.toVirtualY(this.height - Mouse.getEventY() * this.height / mc.displayHeight - 1);
        if (this.activeTab == ViewTab.CONFIGS) {
            if (mouseX >= this.windowX && mouseX <= this.windowX + this.windowWidth
                    && mouseY >= this.configListY && mouseY <= this.configListY + this.configListHeight) {
                this.targetConfigScroll -= wheel / 120.0F * CONFIG_ROW_HEIGHT * 2.0F;
            }
        } else if (mouseX >= this.windowX + this.sidebarWidth && mouseX <= this.settingsX
                && mouseY >= this.moduleListY && mouseY <= this.bodyY + this.bodyHeight) {
            this.targetModuleScroll -= wheel / 120.0F * MODULE_ROW_HEIGHT * 2.0F;
        } else if (mouseX >= this.settingsX && mouseX <= this.windowX + this.windowWidth
                && mouseY >= this.settingsListY && mouseY <= this.settingsListY + this.settingsListHeight) {
            this.targetSettingsScroll -= wheel / 120.0F * 32.0F;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.listeningModule != null) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_DELETE) {
                this.listeningModule.setKey(0);
            } else if (keyCode != Keyboard.KEY_NONE) {
                this.listeningModule.setKey(keyCode);
            }
            this.listeningModule = null;
            return;
        }
        if (this.searchFocused) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) {
                this.searchFocused = false;
            } else if (keyCode == Keyboard.KEY_BACK) {
                if (!this.searchText.isEmpty()) this.searchText = this.searchText.substring(0, this.searchText.length() - 1);
                this.targetModuleScroll = 0.0F;
            } else if (typedChar >= ' ' && typedChar != 127) {
                this.searchText += typedChar;
                this.targetModuleScroll = 0.0F;
            }
            return;
        }
        if (this.focusedText != null) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) {
                this.setFocus(null);
            } else {
                this.focusedText.typeChar(typedChar, keyCode);
            }
            return;
        }
        boolean guiKey = keyCode != 0 && keyCode == getGuiKey()
                && System.currentTimeMillis() - this.openedAt > 300L;
        if (keyCode == Keyboard.KEY_ESCAPE || guiKey) {
            this.closing = true;
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void onGuiClosed() {
        this.setFocus(null);
        this.searchFocused = false;
        this.draggingWindow = false;
        this.draggingSetting = null;
        this.clearScrollDrag();
        BlurController.setClickGuiOpen(false);
        super.onGuiClosed();
    }

    private static int getGuiKey() {
        Module module = CrewX.moduleManager.modules.get(GuiModule.class);
        return module == null ? 0 : module.getKey();
    }

    private void drawCategoryIcon(ModuleCategory category, float x, float y, int color, float iconSize) {
        String texture;
        switch (category) {
            case COMBAT: texture = COMBAT_ICON; break;
            case MOVEMENT: texture = MOVEMENT_ICON; break;
            case RENDER: texture = RENDER_ICON; break;
            case PLAYER: texture = PLAYER_ICON; break;
            case MISC: texture = MISC_ICON; break;
            case SCRIPT: texture = SCRIPT_ICON; break;
            default: texture = MISC_ICON;
        }
        int size = Math.max(8, Math.round(iconSize));
        this.drawIcon(texture, x - size / 2.0F, y - size / 2.0F, size, color);
    }

    private static String[] getModeValues(ModeProperty property) {
        try {
            if (modesField == null) {
                modesField = ModeProperty.class.getDeclaredField("modes");
                modesField.setAccessible(true);
            }
            return (String[]) modesField.get(property);
        } catch (Exception ignored) {
            return new String[0];
        }
    }

    private void scissorOn(float x, float y, float w, float h) {
        float[] rect = new float[]{x, y, x + Math.max(0.0F, w), y + Math.max(0.0F, h)};
        float[] parent = this.scissorStack.peek();
        if (parent != null) {
            rect[0] = Math.max(rect[0], parent[0]);
            rect[1] = Math.max(rect[1], parent[1]);
            rect[2] = Math.min(rect[2], parent[2]);
            rect[3] = Math.min(rect[3], parent[3]);
        }
        if (rect[2] < rect[0]) rect[2] = rect[0];
        if (rect[3] < rect[1]) rect[3] = rect[1];
        this.scissorStack.push(rect);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        this.applyScissor(rect);
    }

    private void scissorOff() {
        this.scissorStack.poll();
        float[] parent = this.scissorStack.peek();
        if (parent == null) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        } else {
            this.applyScissor(parent);
        }
    }

    private void applyScissor(float[] rect) {
        ScaledResolution sr = new ScaledResolution(mc);
        int factor = sr.getScaleFactor();
        float x1 = this.centerX + (rect[0] - this.centerX) * this.renderScale;
        float x2 = this.centerX + (rect[2] - this.centerX) * this.renderScale;
        float y1 = this.centerY + (rect[1] + this.renderOffsetY - this.centerY) * this.renderScale;
        float y2 = this.centerY + (rect[3] + this.renderOffsetY - this.centerY) * this.renderScale;
        int sx = (int) Math.floor(x1 * factor);
        int sw = (int) Math.ceil((x2 - x1) * factor);
        int sh = (int) Math.ceil((y2 - y1) * factor);
        int sy = (int) Math.floor((sr.getScaledHeight() - y2) * factor);
        GL11.glScissor(sx, sy, Math.max(0, sw), Math.max(0, sh));
    }

    private void round(float x, float y, float w, float h, int color, float radius) {
        if (w <= 0.0F || h <= 0.0F) return;
        int faded = this.fade(color);
        if ((faded >>> 24) < 3) return;
        RoundedUtils.drawRoundedRect(x, y, w, h, faded, Math.min(radius, Math.min(w, h) / 2.0F));
    }

    private void text(String value, float x, float y, int color) {
        int faded = this.fade(color);
        if ((faded >>> 24) < 8) return;
        FONT.drawString(value, x, y, faded);
    }

    private void circleOutline(float x, float y, float radius, int color) {
        int faded = this.fade(color);
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.color((faded >> 16 & 255) / 255.0F, (faded >> 8 & 255) / 255.0F,
                (faded & 255) / 255.0F, (faded >>> 24) / 255.0F);
        GL11.glLineWidth(1.2F);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < 20; i++) {
            double angle = i * Math.PI * 2.0 / 20.0;
            GL11.glVertex2d(x + Math.cos(angle) * radius, y + Math.sin(angle) * radius);
        }
        GL11.glEnd();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private void line(float x1, float y1, float x2, float y2, int color, float width) {
        int faded = this.fade(color);
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.color((faded >> 16 & 255) / 255.0F, (faded >> 8 & 255) / 255.0F,
                (faded & 255) / 255.0F, (faded >>> 24) / 255.0F);
        GL11.glLineWidth(width);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glEnd();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private int fade(int argb) {
        int alpha = argb >>> 24 & 255;
        if (alpha == 0) alpha = 255;
        alpha = (int) (alpha * this.alphaMultiplier);
        alpha = Math.max(0, Math.min(255, alpha));
        return alpha << 24 | argb & 0xFFFFFF;
    }

    private static int withAlpha(int argb, float factor) {
        int alpha = argb >>> 24 & 255;
        if (alpha == 0) alpha = 255;
        alpha = Math.max(0, Math.min(255, (int) (alpha * factor)));
        return alpha << 24 | argb & 0xFFFFFF;
    }

    private static Color accentColor() {
        return GuiModule.getAccent();
    }

    private static int lerpColor(int a, int b, float t) {
        t = clamp01(t);
        int ar = a >> 16 & 255;
        int ag = a >> 8 & 255;
        int ab = a & 255;
        int aa = a >>> 24 & 255;
        int br = b >> 16 & 255;
        int bg = b >> 8 & 255;
        int bb = b & 255;
        int ba = b >>> 24 & 255;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        int alpha = (int) (aa + (ba - aa) * t);
        return alpha << 24 | r << 16 | g << 8 | bl;
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        float p = t - 1.0F;
        return 1.0F + c3 * p * p * p + c1 * p * p;
    }

    private static float easeOutCubic(float t) {
        float p = 1.0F - t;
        return 1.0F - p * p * p;
    }

    private static float approach(float current, float target, float delta, float speed) {
        float step = Math.min(1.0F, delta * speed);
        float result = current + (target - current) * step;
        return Math.abs(target - result) < 0.001F ? target : result;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static String formatNumber(double value) {
        BigDecimal bd = BigDecimal.valueOf(value).setScale(value % 1.0 == 0.0 ? 0 : 2, RoundingMode.HALF_UP);
        return bd.stripTrailingZeros().toPlainString();
    }

    private static boolean isSlider(Property<?> property) {
        return property instanceof FloatProperty || property instanceof IntProperty || property instanceof PercentProperty;
    }

    private class SettingComponent {
        private final Property<?> property;
        private boolean dropdownOpen;
        private boolean pickerOpen;
        private float dropdownAnim;
        private float pickerAnim;
        private float toggleAnim;
        private float hoverAnim;
        private float componentX;
        private float componentWidth;
        private float svX;
        private float svY;
        private float svW;
        private float svH;
        private float hueX;
        private float hueY;
        private float hueH;
        private float hue;
        private float saturation;
        private float brightness;
        private String editBuffer;
        private boolean editingNumber;

        private SettingComponent(Property<?> property) {
            this.property = property;
            if (property instanceof BooleanProperty) {
                this.toggleAnim = Boolean.TRUE.equals(property.getValue()) ? 1.0F : 0.0F;
            }
        }

        private void updateAnimations(float delta) {
            this.dropdownAnim = approach(this.dropdownAnim, this.dropdownOpen ? 1.0F : 0.0F, delta, 13.0F);
            this.pickerAnim = approach(this.pickerAnim, this.pickerOpen ? 1.0F : 0.0F, delta, 13.0F);
            if (this.property instanceof BooleanProperty) {
                this.toggleAnim = approach(this.toggleAnim, Boolean.TRUE.equals(this.property.getValue()) ? 1.0F : 0.0F, delta, 16.0F);
            }
            this.hoverAnim = approach(this.hoverAnim,
                    lastMouseX >= this.componentX && lastMouseX <= this.componentX + this.componentWidth ? 1.0F : 0.0F,
                    delta, 12.0F);
        }

        private float getHeight() {
            if (this.property instanceof ModeProperty && this.dropdownAnim > 0.001F) {
                return SETTING_HEIGHT + getModeValues((ModeProperty) this.property).length * OPTION_HEIGHT * easeOutCubic(this.dropdownAnim);
            }
            if (this.property instanceof ColorProperty && this.pickerAnim > 0.001F) {
                return SETTING_HEIGHT + (PICKER_HEIGHT + 8.0F) * easeOutCubic(this.pickerAnim);
            }
            return SETTING_HEIGHT;
        }

        private void render(float x, float y, int width, float mouseX, float mouseY) {
            this.componentX = x;
            this.componentWidth = width;
            ClickGui.this.round(x, y + 1.0F, width, SETTING_HEIGHT - 3.0F,
                    lerpColor(PANEL_ALT.getRGB(), ROW_HOVER.getRGB(), this.hoverAnim * 0.55F), 6.0F);
            if (this.property instanceof ButtonProperty) {
                this.renderButton(x, y, width);
            } else if (this.property instanceof BooleanProperty) {
                this.renderBoolean(x, y, width);
            } else if (isSlider(this.property)) {
                this.renderNumber(x, y, width, mouseX, mouseY);
            } else if (this.property instanceof ModeProperty) {
                this.renderMode(x, y, width, mouseX, mouseY, (ModeProperty) this.property);
            } else if (this.property instanceof ColorProperty) {
                this.renderColor(x, y, width, (ColorProperty) this.property);
            } else if (this.property instanceof TextProperty) {
                this.renderText(x, y, width, (TextProperty) this.property);
            } else {
                String value = ClickGui.this.trimToWidth(this.property.getValuePrompt(), Math.max(0, width / 2 - 12));
                float valueX = x + width - FONT.getStringWidth(value) - 10.0F;
                String label = ClickGui.this.trimToWidth(this.property.getName().replace('-', ' '),
                        Math.max(0, (int) (valueX - x - 15.0F)));
                float rowTextY = y + (SETTING_HEIGHT - FONT.getFontHeight()) / 2.0F;
                ClickGui.this.text(label, x + 10.0F, rowTextY, TEXT.getRGB());
                ClickGui.this.text(value, valueX, rowTextY, MUTED.getRGB());
            }
        }

        private void renderBoolean(float x, float y, int width) {
            float switchX = x + width - 36.0F;
            float switchY = y + (SETTING_HEIGHT - 11.0F) / 2.0F;
            String label = ClickGui.this.trimToWidth(this.property.getName().replace('-', ' '),
                    (int) Math.max(0.0F, switchX - x - 15.0F));
            ClickGui.this.text(label, x + 10.0F, y + (SETTING_HEIGHT - FONT.getFontHeight()) / 2.0F, TEXT.getRGB());
            ClickGui.this.round(switchX, switchY, 25.0F, 11.0F,
                    lerpColor(TRACK.getRGB(), accentColor().getRGB(), this.toggleAnim), 6.0F);
            ClickGui.this.round(switchX + 1.5F + 12.0F * this.toggleAnim, switchY + 1.5F, 8.0F, 8.0F, 0xFFFFFFFF, 4.0F);
        }

        private void renderNumber(float x, float y, int width, float mouseX, float mouseY) {
            double value = this.getNumber();
            double min = this.getMinimum();
            double max = this.getMaximum();
            float percent = max == min ? 0.0F : clamp01((float) ((value - min) / (max - min)));
            String label = this.property.getName().replace('-', ' ');
            boolean editing = this.editingNumber && this == focusedText;
            String valueText = editing ? (this.editBuffer == null ? "" : this.editBuffer) + "_" : this.valueString();
            valueText = ClickGui.this.trimToWidth(valueText, (int) Math.max(12.0F, width * 0.42F));
            float boxWidth = this.valueBoxWidth(width, valueText);
            float boxX = x + width - boxWidth - 7.0F;
            String shownLabel = ClickGui.this.trimToWidth(label,
                    (int) Math.max(0.0F, boxX - x - 15.0F));
            ClickGui.this.text(shownLabel, x + 10.0F, y + 4.5F, TEXT.getRGB());
            boolean overValue = mouseX >= boxX && mouseX <= x + width - 7.0F && mouseY >= y + 2.0F && mouseY <= y + 19.0F;
            if (editing || overValue) {
                ClickGui.this.round(boxX, y + 2.0F, boxWidth, 17.0F,
                        editing ? withAlpha(accentColor().getRGB(), 0.34F) : ROW_HOVER.getRGB(), 5.0F);
            }
            ClickGui.this.text(valueText, x + width - FONT.getStringWidth(valueText) - 12.0F, y + 4.5F,
                    editing ? 0xFFFFFFFF : accentColor().brighter().getRGB());
            float trackX = x + 10.0F;
            float trackY = y + 22.0F;
            float trackWidth = width - 20.0F;
            ClickGui.this.round(trackX, trackY, trackWidth, 2.5F, TRACK.getRGB(), 2.0F);
            ClickGui.this.round(trackX, trackY, trackWidth * percent, 2.5F, accentColor().getRGB(), 2.0F);
            float knob = this == draggingSetting ? 7.0F : 5.5F;
            ClickGui.this.round(trackX + trackWidth * percent - knob / 2.0F, trackY - (knob - 2.5F) / 2.0F,
                    knob, knob, 0xFFFFFFFF, knob / 2.0F);
            this.componentX = x;
            this.componentWidth = width;
        }

        private void renderMode(float x, float y, int width, float mouseX, float mouseY, ModeProperty mode) {
            String current = mode.getModeString().replace('-', ' ');
            current = ClickGui.this.trimToWidth(current, (int) Math.max(12.0F, width * 0.44F));
            int currentWidth = FONT.getStringWidth(current);
            float currentX = x + width - currentWidth - 21.0F;
            String label = ClickGui.this.trimToWidth(this.property.getName().replace('-', ' '),
                    (int) Math.max(0.0F, currentX - x - 15.0F));
            float rowTextY = y + (SETTING_HEIGHT - FONT.getFontHeight()) / 2.0F;
            ClickGui.this.text(label, x + 10.0F, rowTextY, TEXT.getRGB());
            ClickGui.this.text(current, currentX, rowTextY, accentColor().brighter().getRGB());
            float cx = x + width - 12.0F;
            float cy = y + 13.0F;
            ClickGui.this.triangle(cx - 3.0F, cy - 1.0F, cx + 3.0F, cy - 1.0F, cx, cy + 2.0F,
                    this.dropdownOpen ? accentColor().getRGB() : MUTED.getRGB());
            if (this.dropdownAnim <= 0.001F) return;
            String[] values = getModeValues(mode);
            float optionY = y + SETTING_HEIGHT - 1.0F;
            float reveal = easeOutCubic(this.dropdownAnim);
            for (int i = 0; i < values.length; i++) {
                boolean selected = i == mode.getValue().intValue();
                boolean hovered = mouseX >= x + 6.0F && mouseX <= x + width - 6.0F
                        && mouseY >= optionY && mouseY <= optionY + OPTION_HEIGHT;
                float previous = alphaMultiplier;
                alphaMultiplier = previous * reveal;
                ClickGui.this.round(x + 6.0F, optionY, width - 12.0F, OPTION_HEIGHT - 2.0F,
                        selected ? withAlpha(accentColor().getRGB(), 0.75F) : (hovered ? ROW_HOVER.getRGB() : ROW.getRGB()), 5.0F);
                String option = ClickGui.this.trimToWidth(values[i].replace('-', ' '), width - 28);
                ClickGui.this.text(option, x + 12.0F,
                        optionY + (OPTION_HEIGHT - 2.0F - FONT.getFontHeight()) / 2.0F,
                        selected ? 0xFFFFFFFF : TEXT.getRGB());
                alphaMultiplier = previous;
                optionY += OPTION_HEIGHT;
            }
        }

        private void renderColor(float x, float y, int width, ColorProperty property) {
            int rgb = property.getValue().intValue() & 0xFFFFFF;
            String label = ClickGui.this.trimToWidth(property.getName().replace('-', ' '), Math.max(0, width - 58));
            ClickGui.this.text(label, x + 10.0F, y + (SETTING_HEIGHT - FONT.getFontHeight()) / 2.0F, TEXT.getRGB());
            ClickGui.this.round(x + width - 42.0F, y + 7.0F, 28.0F, 12.0F, TRACK.getRGB(), 5.0F);
            ClickGui.this.round(x + width - 40.0F, y + 9.0F, 24.0F, 8.0F, 0xFF000000 | rgb, 4.0F);
            if (this.pickerAnim <= 0.001F) return;
            float previous = alphaMultiplier;
            alphaMultiplier = previous * easeOutCubic(this.pickerAnim);
            this.svX = x + 10.0F;
            this.svY = y + SETTING_HEIGHT + 2.0F;
            this.svW = width - 34.0F;
            this.svH = PICKER_HEIGHT;
            this.hueX = x + width - 18.0F;
            this.hueY = this.svY;
            this.hueH = PICKER_HEIGHT;
            int pure = Color.HSBtoRGB(this.hue, 1.0F, 1.0F) & 0xFFFFFF;
            for (int i = 0; i < (int) this.svW; i++) {
                int top = lerpColor(0xFFFFFFFF, 0xFF000000 | pure, (float) i / this.svW);
                ClickGui.this.drawGradientRect((int) (this.svX + i), (int) this.svY, (int) (this.svX + i + 1),
                        (int) (this.svY + this.svH), ClickGui.this.fade(top), ClickGui.this.fade(0xFF000000));
            }
            for (int i = 0; i < (int) this.hueH; i++) {
                int color = Color.HSBtoRGB((float) i / this.hueH, 1.0F, 1.0F);
                ClickGui.this.round(this.hueX, this.hueY + i, 8.0F, 1.0F, color, 0.0F);
            }
            float markX = this.svX + this.saturation * this.svW;
            float markY = this.svY + (1.0F - this.brightness) * this.svH;
            ClickGui.this.round(markX - 3.0F, markY - 3.0F, 6.0F, 6.0F, 0xFFFFFFFF, 3.0F);
            ClickGui.this.round(markX - 1.5F, markY - 1.5F, 3.0F, 3.0F,
                    0xFF000000 | Color.HSBtoRGB(this.hue, this.saturation, this.brightness), 1.5F);
            float hueMark = this.hueY + this.hue * this.hueH;
            ClickGui.this.round(this.hueX - 1.0F, hueMark - 1.5F, 10.0F, 3.0F, 0xFFFFFFFF, 1.5F);
            alphaMultiplier = previous;
        }

        private void renderText(float x, float y, int width, TextProperty property) {
            boolean editing = focusedText == this;
            String value = editing ? (this.editBuffer == null ? "" : this.editBuffer)
                    : (property.getValue() == null ? "" : property.getValue());
            String shown = editing ? value + "_" : (value.isEmpty() ? "-" : value);
            int maxWidth = Math.max(8, width / 2 - 12);
            while (shown.length() > 1 && FONT.getStringWidth(shown) > maxWidth) {
                shown = shown.substring(1);
            }
            String label = ClickGui.this.trimToWidth(property.getName().replace('-', ' '),
                    Math.max(0, width - FONT.getStringWidth(shown) - 27));
            float rowTextY = y + (SETTING_HEIGHT - FONT.getFontHeight()) / 2.0F;
            ClickGui.this.text(label, x + 10.0F, rowTextY, TEXT.getRGB());
            ClickGui.this.text(shown, x + width - FONT.getStringWidth(shown) - 10.0F, rowTextY,
                    editing ? 0xFFFFFFFF : accentColor().brighter().getRGB());
        }

        private void renderButton(float x, float y, int width) {
            float buttonWidth = Math.max(0.0F, width - 16.0F);
            ClickGui.this.round(x + 8.0F, y + 4.0F, buttonWidth, 20.0F,
                    withAlpha(accentColor().getRGB(), 0.74F), 6.0F);
            String label = ClickGui.this.trimToWidth(this.property.getName().replace('-', ' '),
                    Math.max(0, (int) buttonWidth - 10));
            ClickGui.this.text(label, x + (width - FONT.getStringWidth(label)) / 2.0F,
                    y + 4.0F + (20.0F - FONT.getFontHeight()) / 2.0F, 0xFFFFFFFF);
        }

        private boolean mouseClicked(float x, float y, int width, float mouseX, float mouseY, int mouseButton, boolean pressPhase) {
            boolean onRow = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + SETTING_HEIGHT;
            if (isSlider(this.property)) {
                boolean onValue = this.valueBoxContains(x, y, width, mouseX, mouseY);
                if (pressPhase) {
                    if (onValue || !onRow) return false;
                    draggingSetting = this;
                    dragMode = 0;
                    this.componentX = x;
                    this.componentWidth = width;
                    this.updateSlider((int) mouseX);
                    return true;
                }
                if (onValue) {
                    setFocus(this);
                    this.editingNumber = true;
                    this.editBuffer = formatNumber(this.getNumber());
                    return true;
                }
                return false;
            }
            if (this.property instanceof ColorProperty) {
                ColorProperty colorProperty = (ColorProperty) this.property;
                if (pressPhase) {
                    if (!this.pickerOpen || this.pickerAnim <= 0.4F) return false;
                    if (mouseX >= this.svX && mouseX <= this.svX + this.svW && mouseY >= this.svY && mouseY <= this.svY + this.svH) {
                        draggingSetting = this;
                        dragMode = 1;
                        this.updateSaturation((int) mouseX, (int) mouseY, colorProperty);
                        return true;
                    }
                    if (mouseX >= this.hueX - 2.0F && mouseX <= this.hueX + 10.0F && mouseY >= this.hueY && mouseY <= this.hueY + this.hueH) {
                        draggingSetting = this;
                        dragMode = 2;
                        this.updateHue((int) mouseY, colorProperty);
                        return true;
                    }
                    return false;
                }
                if (onRow) {
                    this.pickerOpen = !this.pickerOpen;
                    if (this.pickerOpen) {
                        int rgb = colorProperty.getValue().intValue() & 0xFFFFFF;
                        float[] hsb = Color.RGBtoHSB(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, null);
                        this.hue = hsb[0];
                        this.saturation = hsb[1];
                        this.brightness = hsb[2];
                    }
                    return true;
                }
                return false;
            }
            if (pressPhase) return false;
            if (this.property instanceof ButtonProperty) {
                if (onRow) {
                    ((ButtonProperty) this.property).press();
                    return true;
                }
                return false;
            }
            if (this.property instanceof BooleanProperty) {
                if (onRow) {
                    this.property.setValue(!Boolean.TRUE.equals(this.property.getValue()));
                    return true;
                }
                return false;
            }
            if (this.property instanceof ModeProperty) {
                ModeProperty mode = (ModeProperty) this.property;
                if (this.dropdownOpen && this.dropdownAnim > 0.4F) {
                    String[] values = getModeValues(mode);
                    float optionY = y + SETTING_HEIGHT - 1.0F;
                    for (int i = 0; i < values.length; i++) {
                        if (mouseX >= x + 6.0F && mouseX <= x + width - 6.0F
                                && mouseY >= optionY && mouseY <= optionY + OPTION_HEIGHT) {
                            mode.setValue(Integer.valueOf(i));
                            this.dropdownOpen = false;
                            return true;
                        }
                        optionY += OPTION_HEIGHT;
                    }
                }
                if (onRow) {
                    this.dropdownOpen = !this.dropdownOpen;
                    return true;
                }
                return false;
            }
            if (this.property instanceof TextProperty && onRow) {
                TextProperty textProperty = (TextProperty) this.property;
                if (mouseButton == 1) {
                    textProperty.setValue("");
                    this.editBuffer = "";
                } else {
                    setFocus(this);
                    this.editingNumber = false;
                    this.editBuffer = textProperty.getValue() == null ? "" : textProperty.getValue();
                }
                return true;
            }
            return false;
        }

        private boolean valueBoxContains(float x, float y, int width, float mouseX, float mouseY) {
            if (!isSlider(this.property)) return false;
            float boxWidth = this.valueBoxWidth(width, this.valueString());
            float boxX = x + width - boxWidth - 7.0F;
            return mouseX >= boxX && mouseX <= x + width - 7.0F && mouseY >= y + 2.0F && mouseY <= y + 19.0F;
        }

        private float valueBoxWidth(int width, String value) {
            String shown = ClickGui.this.trimToWidth(value, (int) Math.max(12.0F, width * 0.42F));
            float available = Math.max(0.0F, width - 14.0F);
            return Math.min(available, Math.max(24.0F, FONT.getStringWidth(shown) + 12.0F));
        }

        private String valueString() {
            double value = this.getNumber();
            return this.property instanceof PercentProperty ? formatNumber(value) + "%" : formatNumber(value);
        }

        private void finishEditing() {
            if (!this.editingNumber) {
                this.editBuffer = null;
                return;
            }
            this.editingNumber = false;
            String typed = this.editBuffer == null ? "" : this.editBuffer.trim().replace("%", "").replace(',', '.');
            this.editBuffer = null;
            if (typed.isEmpty() || typed.equals("-") || typed.equals(".")) return;
            double parsed;
            try {
                parsed = Double.parseDouble(typed);
            } catch (NumberFormatException ignored) {
                return;
            }
            double clamped = Math.max(this.getMinimum(), Math.min(this.getMaximum(), parsed));
            if (this.property instanceof FloatProperty) {
                ((FloatProperty) this.property).setValue(Float.valueOf((float) (Math.round(clamped * 100.0) / 100.0)));
            } else if (this.property instanceof IntProperty) {
                ((IntProperty) this.property).setValue(Integer.valueOf((int) Math.round(clamped)));
            } else if (this.property instanceof PercentProperty) {
                ((PercentProperty) this.property).setValue(Integer.valueOf((int) Math.round(clamped)));
            }
        }

        private void updateDrag(int mouseX, int mouseY) {
            if (dragMode == 0) {
                this.updateSlider(mouseX);
            } else if (this.property instanceof ColorProperty) {
                ColorProperty colorProperty = (ColorProperty) this.property;
                if (dragMode == 1) this.updateSaturation(mouseX, mouseY, colorProperty);
                else this.updateHue(mouseY, colorProperty);
            }
        }

        private void updateSlider(int mouseX) {
            if (this.componentWidth <= 20.0F) return;
            double percent = Math.max(0.0D, Math.min(1.0D, (mouseX - this.componentX - 10.0D) / (this.componentWidth - 20.0D)));
            double min = this.getMinimum();
            double max = this.getMaximum();
            double raw = min + (max - min) * percent;
            if (this.property instanceof FloatProperty) {
                raw = Math.round(raw * 100.0D) / 100.0D;
                ((FloatProperty) this.property).setValue(Float.valueOf((float) Math.max(min, Math.min(max, raw))));
            } else if (this.property instanceof IntProperty) {
                ((IntProperty) this.property).setValue(Integer.valueOf((int) Math.max(min, Math.min(max, Math.round(raw)))));
            } else if (this.property instanceof PercentProperty) {
                ((PercentProperty) this.property).setValue(Integer.valueOf((int) Math.max(min, Math.min(max, Math.round(raw)))));
            }
        }

        private void updateSaturation(int mouseX, int mouseY, ColorProperty property) {
            if (this.svW <= 0.0F || this.svH <= 0.0F) return;
            this.saturation = clamp01((mouseX - this.svX) / this.svW);
            this.brightness = clamp01(1.0F - (mouseY - this.svY) / this.svH);
            this.applyColor(property);
        }

        private void updateHue(int mouseY, ColorProperty property) {
            if (this.hueH <= 0.0F) return;
            this.hue = clamp01((mouseY - this.hueY) / this.hueH);
            this.applyColor(property);
        }

        private void applyColor(ColorProperty property) {
            property.setValue(Integer.valueOf(Color.HSBtoRGB(this.hue, this.saturation, this.brightness) & 0xFFFFFF));
        }

        private void typeChar(char typedChar, int keyCode) {
            if (this.editingNumber) {
                if (this.editBuffer == null) this.editBuffer = "";
                if (keyCode == Keyboard.KEY_BACK) {
                    if (!this.editBuffer.isEmpty()) this.editBuffer = this.editBuffer.substring(0, this.editBuffer.length() - 1);
                    return;
                }
                boolean digit = typedChar >= '0' && typedChar <= '9';
                boolean dot = (typedChar == '.' || typedChar == ',') && this.editBuffer.indexOf('.') < 0
                        && !(this.property instanceof IntProperty) && !(this.property instanceof PercentProperty);
                boolean minus = typedChar == '-' && this.editBuffer.isEmpty() && this.getMinimum() < 0.0D;
                if (digit) this.editBuffer += typedChar;
                else if (dot) this.editBuffer += '.';
                else if (minus) this.editBuffer += '-';
                return;
            }
            if (!(this.property instanceof TextProperty)) return;
            TextProperty textProperty = (TextProperty) this.property;
            if (this.editBuffer == null) this.editBuffer = textProperty.getValue() == null ? "" : textProperty.getValue();
            if (keyCode == Keyboard.KEY_BACK) {
                if (!this.editBuffer.isEmpty()) this.editBuffer = this.editBuffer.substring(0, this.editBuffer.length() - 1);
            } else if (typedChar >= ' ' && typedChar != 127) {
                this.editBuffer += typedChar;
            } else {
                return;
            }
            textProperty.setValue(this.editBuffer);
        }

        private double getNumber() {
            if (this.property instanceof FloatProperty) return ((FloatProperty) this.property).getValue().doubleValue();
            if (this.property instanceof IntProperty) return ((IntProperty) this.property).getValue().doubleValue();
            if (this.property instanceof PercentProperty) return ((PercentProperty) this.property).getValue().doubleValue();
            return 0.0D;
        }

        private double getMinimum() {
            if (this.property instanceof FloatProperty) return ((FloatProperty) this.property).getMinimum().doubleValue();
            if (this.property instanceof IntProperty) return ((IntProperty) this.property).getMinimum().doubleValue();
            if (this.property instanceof PercentProperty) return ((PercentProperty) this.property).getMinimum().doubleValue();
            return 0.0D;
        }

        private double getMaximum() {
            if (this.property instanceof FloatProperty) return ((FloatProperty) this.property).getMaximum().doubleValue();
            if (this.property instanceof IntProperty) return ((IntProperty) this.property).getMaximum().doubleValue();
            if (this.property instanceof PercentProperty) return ((PercentProperty) this.property).getMaximum().doubleValue();
            return 1.0D;
        }
    }

    private void triangle(float x1, float y1, float x2, float y2, float x3, float y3, int color) {
        int faded = this.fade(color);
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.color((faded >> 16 & 255) / 255.0F, (faded >> 8 & 255) / 255.0F,
                (faded & 255) / 255.0F, (faded >>> 24) / 255.0F);
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x3, y3);
        GL11.glEnd();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private float toVirtualX(float screenX) {
        return (screenX - this.centerX) / this.renderScale + this.centerX;
    }

    private float toVirtualY(float screenY) {
        return (screenY - this.centerY) / this.renderScale + this.centerY - this.renderOffsetY;
    }
}
