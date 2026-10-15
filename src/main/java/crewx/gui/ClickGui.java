package crewx.gui;

import crewx.CrewX;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
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
import org.lwjgl.opengl.GL13;

import java.awt.Color;
import java.io.File;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.Reader;
import java.awt.EventQueue;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.filechooser.FileNameExtensionFilter;
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
import java.util.Set;

public class ClickGui extends GuiScreen {
    private static volatile long drawCount;

    public static long getDrawCount() {
        return drawCount;
    }
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

    private static final int HEADER_HEIGHT = 42;
    private static final int FOOTER_HEIGHT = 12;
    private static final int MODULE_ROW_HEIGHT = 28;
    private static final int SETTING_HEIGHT = 26;
    private static final int OPTION_HEIGHT = 18;
    private static final int PICKER_HEIGHT = 54;
    private static final Color SIDEBAR = new Color(13, 13, 13, 255);
    private static final Color PANEL = new Color(8, 8, 8, 255);
    private static final Color PANEL_ALT = new Color(11, 11, 11, 255);
    private static final Color ROW = new Color(19, 19, 19, 255);
    private static final Color ROW_HOVER = new Color(27, 27, 27, 255);
    private static final Color TEXT = new Color(220, 220, 220);
    private static final Color MUTED = new Color(132, 132, 132);
    private static final Color TRACK = new Color(42, 42, 42);
    private static final float WINDOW_RADIUS = 11.0F;
    private static final float OPEN_SPEED = 4.2F;
    private static final float CLOSE_SPEED = 6.6F;
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
    private static final Map<String, DynamicTexture> GUI_TEXTURE_OBJECTS = new HashMap<String, DynamicTexture>();

    private static final Map<String, int[]> GUI_TEXTURE_SIZES = new HashMap<String, int[]>();
    private static long lastTextureCheck;
    private static final ClickGuiFontRenderer FONT = ClientFont.getRenderer();

    private static Field modesField;

    private ViewTab activeTab = ViewTab.MODULES;
    private ModuleCategory selectedCategory = ModuleCategory.COMBAT;
    private Module selectedModule;
    private Module listeningModule;
    private SettingComponent listeningSettingBind;
    private SettingComponent focusedText;
    private SettingComponent draggingSetting;
    private int dragMode;
    private final List<SettingComponent> settingComponents = new ArrayList<SettingComponent>();
    private final List<File> savedConfigs = new ArrayList<File>();
    private File pendingConfigClick;
    private String selectedConfigName = "";
    private String configImportStatus = "";
    private long configImportStatusUntil;
    private volatile File pendingConfigImport;
    private volatile boolean configPickerFailed;
    private long unloadConfirmUntil;
    private Module pendingModuleClick;
    private boolean pendingModuleSettingsHit;
    private boolean pendingModuleToggleHit;
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
    private float uiScale = 1.0F;
    private float virtualWidth;
    private float virtualHeight;
    private float renderScale = 1.0F;
    private float renderOffsetY;
    private float alphaMultiplier = 1.0F;
    private float deltaTime = 0.016F;
    private float lastMouseX;
    private float lastMouseY;
    private long lastFrame;
    private final long openedAt = System.currentTimeMillis();

    public ClickGui() {
        this.selectedModule = null;
    }

    @Override
    public void initGui() {
        crewx.util.TextureGuard.requestCheck();
        this.closing = false;
        this.animation = 0.0F;
        this.animationStep();
        BlurController.setClickGuiOpen(true);
        this.lastFrame = 0L;
        this.computeLayout();
        this.refreshConfigs();

    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        ++drawCount;
        File selectedImport = this.pendingConfigImport;
        if (selectedImport != null) {
            this.pendingConfigImport = null;
            this.importConfigFile(selectedImport);
        }
        if (this.configPickerFailed) {
            this.configPickerFailed = false;
            this.setConfigImportStatus("Could not open the file picker.");
        }
        if (!this.firstFrameLogged) {
            this.firstFrameLogged = true;
            crewx.util.ClientLog.log("ClickGUI first frame");
        }
        long now = System.nanoTime();
        float dt = this.lastFrame == 0L ? 0.016F : (now - this.lastFrame) / 1.0e9F;
        this.lastFrame = now;
        BlurController.ensureBlur();
        verifyGuiTextures();
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
            this.windowOffsetX = virtualMouseX - this.dragMouseOffsetX - (this.virtualWidth - this.windowWidth) / 2.0F;
            this.windowOffsetY = virtualMouseY - this.dragMouseOffsetY - (this.virtualHeight - this.windowHeight) / 2.0F;
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
        float fadeProgress = easeOutCubic(this.animation);
        this.alphaMultiplier = this.closing ? this.animation * this.animation : Math.min(1.0F, fadeProgress * 1.15F);
        this.centerX = this.virtualWidth / 2.0F;
        this.centerY = this.virtualHeight / 2.0F;
        this.scissorStack.clear();
        this.drawGradientRect(0, 0, this.width, this.height, this.fade(0x26000000), this.fade(0x4A000000));

        GlStateManager.pushMatrix();
        GlStateManager.scale(this.uiScale, this.uiScale, 1.0F);
        GlStateManager.translate(this.centerX, this.centerY, 0.0F);
        GlStateManager.scale(this.renderScale, this.renderScale, 1.0F);
        GlStateManager.translate(-this.centerX, -this.centerY + this.renderOffsetY, 0.0F);
        this.renderWindow(virtualMouseX, virtualMouseY);
        GlStateManager.popMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private float animation;
    private boolean firstFrameLogged;

    private float tabLineX = -1.0F;
    private float tabLineWidth;
    private float categorySelectionY = -1.0F;
    private final float[] categoryHover = new float[CATEGORIES.length];

    private void animationStep() {
        this.animation += (this.closing ? -CLOSE_SPEED : OPEN_SPEED) * this.deltaTime;
        this.animation = clamp01(this.animation);
        if (this.closing) {
            float p = 1.0F - this.animation;
            float in = p * p * p;
            this.renderScale = 1.0F - 0.06F * in;
            this.renderOffsetY = 5.0F * in;
        } else {
            this.renderScale = 0.9F + 0.1F * easeOutBack(this.animation);
            this.renderOffsetY = (1.0F - easeOutCubic(this.animation)) * 8.0F;
        }
    }

    private void computeLayout() {
        this.uiScale = Math.min(1.0F, Math.min(this.width / 800.0F, this.height / 450.0F));
        this.virtualWidth = this.width / this.uiScale;
        this.virtualHeight = this.height / this.uiScale;
        this.centerX = this.virtualWidth / 2.0F;
        this.centerY = this.virtualHeight / 2.0F;
        float availableWidth = Math.max(1.0F, this.virtualWidth - 16.0F);
        float availableHeight = Math.max(1.0F, this.virtualHeight - 16.0F);
        float preferredWidth = Math.max(Math.min(350.0F, availableWidth), this.virtualWidth * 0.48F);
        float preferredHeight = Math.max(Math.min(250.0F, availableHeight), this.virtualHeight * 0.62F);
        this.windowWidth = Math.min(760.0F, Math.min(availableWidth, preferredWidth));
        this.windowHeight = Math.min(430.0F, Math.min(availableHeight, preferredHeight));
        this.windowX = (this.virtualWidth - this.windowWidth) / 2.0F + this.windowOffsetX;
        this.windowY = (this.virtualHeight - this.windowHeight) / 2.0F + this.windowOffsetY;
        this.windowX = Math.max(0.0F, Math.min(this.virtualWidth - this.windowWidth, this.windowX));
        this.windowY = Math.max(0.0F, Math.min(this.virtualHeight - this.windowHeight, this.windowY));
        if (this.windowWidth < 260.0F) {
            this.sidebarWidth = this.windowWidth * 0.40F;
        } else {
            this.sidebarWidth = Math.min(190.0F, Math.max(104.0F, this.windowWidth * 0.29F));
        }
        this.modulePaneWidth = Math.max(0.0F, this.windowWidth - this.sidebarWidth);
        this.settingsX = this.windowX + this.windowWidth;
        this.bodyY = this.windowY + HEADER_HEIGHT;
        this.bodyHeight = Math.max(0.0F, this.windowHeight - HEADER_HEIGHT - FOOTER_HEIGHT);
        this.searchY = this.bodyY + 31.0F;
        this.moduleListY = this.searchY + 28.0F;
        this.settingsListY = this.moduleListY;
        this.settingsListHeight = Math.max(0.0F, this.bodyY + this.bodyHeight - this.settingsListY - 6.0F);
        this.configListY = this.bodyY + 45.0F;
        this.configListHeight = Math.max(0.0F, this.windowY + this.windowHeight - FOOTER_HEIGHT - this.configListY - 7.0F);
    }

    private void updateScroll() {
        List<Module> modules = this.getVisibleModules();
        float moduleContent = modules.size() * MODULE_ROW_HEIGHT;
        if (this.selectedModule != null && modules.contains(this.selectedModule)) moduleContent += this.expandedSettingsHeight();
        float moduleVisible = this.bodyY + this.bodyHeight - this.moduleListY - 5.0F;
        float maxModule = Math.max(0.0F, moduleContent - moduleVisible);
        this.targetModuleScroll = Math.max(0.0F, Math.min(this.targetModuleScroll, maxModule));
        this.moduleScroll += (this.targetModuleScroll - this.moduleScroll) * Math.min(1.0F, this.deltaTime * 14.0F);
        float settingsContent = this.propertyContentHeight();
        this.settingsListHeight = settingsContent;
        this.settingsScroll = 0.0F;
        this.targetSettingsScroll = 0.0F;
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
            float rowTop = this.moduleRowTop(module);
            boolean hovered = this.lastMouseX >= this.windowX + this.sidebarWidth + 8.0F
                    && this.lastMouseX <= this.windowX + this.windowWidth - 8.0F
                    && this.lastMouseY >= rowTop && this.lastMouseY <= rowTop + MODULE_ROW_HEIGHT - 3;
            this.hoverAnimations.put(module, approach(hover, hovered ? 1.0F : 0.0F, this.deltaTime, 12.0F));
            float enabled = this.enabledAnimations.containsKey(module) ? this.enabledAnimations.get(module).floatValue() : 0.0F;
            this.enabledAnimations.put(module, approach(enabled, module.isEnabled() ? 1.0F : 0.0F, this.deltaTime, 13.0F));
        }
    }

    private void renderWindow(float mouseX, float mouseY) {

        for (int i = 4; i >= 1; i--) {
            float spread = i * 2.5F;
            this.round(this.windowX - spread, this.windowY - spread + 3.0F, this.windowWidth + spread * 2.0F,
                    this.windowHeight + spread * 2.0F, (0x0C + (4 - i) * 0x06) << 24, WINDOW_RADIUS + spread);
        }

        this.round(this.windowX, this.windowY, this.windowWidth, this.windowHeight,
                new Color(38, 38, 41, GuiModule.getBackgroundAlpha()).getRGB(), WINDOW_RADIUS);
        this.round(this.windowX + 1.0F, this.windowY + 1.0F, this.windowWidth - 2.0F, this.windowHeight - 2.0F,
                new Color(14, 14, 14, 255).getRGB(), WINDOW_RADIUS - 1.0F);
        this.round(this.windowX + 1.0F, this.windowY + 1.0F, this.windowWidth - 2.0F, HEADER_HEIGHT,
                new Color(17, 17, 18, 255).getRGB(), WINDOW_RADIUS - 1.0F);

        this.round(this.windowX + WINDOW_RADIUS, this.windowY + 1.0F, Math.max(0.0F, this.windowWidth - WINDOW_RADIUS * 2.0F), 1.0F,
                0x0FFFFFFF, 0.0F);
        if (this.activeTab == ViewTab.MODULES) {
            this.round(this.windowX + 1.0F, this.bodyY, this.sidebarWidth, this.bodyHeight,
                    new Color(13, 13, 13, 255).getRGB(), 0.0F);
            this.round(this.windowX + this.sidebarWidth, this.bodyY, this.modulePaneWidth, this.bodyHeight,
                    new Color(8, 8, 8, 255).getRGB(), 0.0F);
            this.round(this.windowX + this.sidebarWidth, this.bodyY + 8.0F, 1.0F,
                    Math.max(0.0F, this.bodyHeight - 16.0F), new Color(28, 28, 30, 255).getRGB(), 0.0F);
        } else {
            this.round(this.windowX + 1.0F, this.bodyY, this.windowWidth - 2.0F, this.bodyHeight,
                    new Color(8, 8, 8, 255).getRGB(), 0.0F);
        }
        this.round(this.windowX + 1.0F, this.bodyY, this.windowWidth - 2.0F, 1.0F, new Color(26, 26, 28, 255).getRGB(), 0.0F);
        this.renderHeader();
        if (this.activeTab == ViewTab.CONFIGS) {
            this.renderConfigsPane(mouseX, mouseY);
        } else {
            this.renderSidebar(mouseX, mouseY);
            this.renderModulePane(mouseX, mouseY);
        }
        this.renderFooter();
    }

    private void renderHeader() {
        boolean compactHeader = this.windowWidth < 200.0F;
        float logoX = this.windowX + (this.windowWidth < 110.0F ? 5.0F : compactHeader ? 8.0F : 11.0F);
        int logoSize = this.windowWidth < 110.0F ? 10 : compactHeader ? 16 : 25;
        float logoY = this.windowY + (HEADER_HEIGHT - logoSize) / 2.0F;
        this.drawLogo(logoX, logoY, logoSize);
        if (!compactHeader) this.text("CREWX", logoX + 31.0F, this.windowY + 15.0F, TEXT.getRGB());

        String modulesLabel = this.headerLabel(ViewTab.MODULES);
        String configsLabel = this.headerLabel(ViewTab.CONFIGS);
        this.drawHeaderTab(modulesLabel, this.modulesTabX(), this.activeTab == ViewTab.MODULES);
        this.drawHeaderTab(configsLabel, this.configsTabX(), this.activeTab == ViewTab.CONFIGS);

        float targetX = (this.activeTab == ViewTab.MODULES ? this.modulesTabX() : this.configsTabX()) - this.windowX;
        float targetWidth = FONT.getStringWidth(this.activeTab == ViewTab.MODULES ? modulesLabel : configsLabel);
        if (this.tabLineX < 0.0F) {
            this.tabLineX = targetX;
            this.tabLineWidth = targetWidth;
        }
        this.tabLineX = approach(this.tabLineX, targetX, this.deltaTime, 16.0F);
        this.tabLineWidth = approach(this.tabLineWidth, targetWidth, this.deltaTime, 16.0F);
        this.round(this.windowX + this.tabLineX, this.windowY + 30.0F, this.tabLineWidth, 2.0F, accentColor().getRGB(), 1.0F);
    }

    private boolean destroyConfirmationPending() {
        return System.currentTimeMillis() < this.unloadConfirmUntil;
    }

    private float destroyButtonWidth(String label) {
        return FONT.getStringWidth(label) + 20.0F;
    }

    private float destroyButtonX(String label) {
        return this.windowX + this.windowWidth - this.destroyButtonWidth(label) - 12.0F;
    }

    private boolean destroyButtonHit(String label, float x, float mx, float my) {
        return mx >= x && mx <= x + this.destroyButtonWidth(label)
                && my >= this.windowY + 10.0F && my <= this.windowY + 31.0F;
    }

    private void drawDestroyButton(String label, float x) {
        float width = this.destroyButtonWidth(label);
        float y = this.windowY + 10.0F;
        boolean hover = this.destroyButtonHit(label, x, this.lastMouseX, this.lastMouseY);
        int accent = accentColor().getRGB();
        this.round(x, y + 3.0F, width, 20.0F, 0x99000000, 5.0F);
        this.round(x, y, width, 21.0F,
                lerpColor(new Color(38, 38, 38).getRGB(), accent, 0.30F), 5.0F);
        this.round(x + 1.0F, y + 1.0F, width - 2.0F, 18.0F,
                lerpColor(PANEL_ALT.getRGB(), accent, hover ? 0.20F : 0.12F), 4.0F);
        this.round(x + 4.0F, y + 2.0F, width - 8.0F, 1.0F, 0x26FFFFFF, 0.0F);
        this.text(label, x + 10.0F, y + 5.0F, Color.WHITE.getRGB());
    }

    private float modulesTabX() {
        if (this.windowWidth < 110.0F) return this.windowX + 20.0F;
        if (this.windowWidth < 200.0F) return this.windowX + 31.0F;
        return this.windowX + Math.max(82.0F, Math.min(94.0F, this.windowWidth * 0.34F));
    }

    private float configsTabX() {
        float gap = this.windowWidth < 110.0F ? 4.0F : this.windowWidth < 200.0F ? 6.0F : 8.0F;
        return this.modulesTabX() + FONT.getStringWidth(this.headerLabel(ViewTab.MODULES)) + gap;
    }

    private String headerLabel(ViewTab tab) {
        if (this.windowWidth < 150.0F) {
            return tab == ViewTab.MODULES ? "M" : "C";
        }
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
        boolean hover = !selected && this.lastMouseX >= x - 5.0F && this.lastMouseX <= x + FONT.getStringWidth(label) + 6.0F
                && this.lastMouseY >= this.windowY && this.lastMouseY <= this.windowY + HEADER_HEIGHT;
        this.text(label, x, this.windowY + 15.0F, selected ? TEXT.getRGB() : hover ? 0xFFB4B4B8 : MUTED.getRGB());
    }

    private void drawLogo(float x, float y, int size) {
        this.drawIcon(CREWX_LOGO, x, y, size, 0xFFFFFFFF);
    }

    private void drawIcon(String resource, float x, float y, int size, int color) {
        int faded = this.fade(color);
        ResourceLocation texture = this.loadGuiTexture(resource);
        if (texture == null) return;
        DynamicTexture object = GUI_TEXTURE_OBJECTS.get(resource);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        if (object != null) {


            int id = object.getGlTextureId();
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            GlStateManager.bindTexture(id);
        } else {
            mc.getTextureManager().bindTexture(texture);
        }
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
            DynamicTexture object = new DynamicTexture(image);
            ResourceLocation texture = mc.getTextureManager().getDynamicTextureLocation(
                    "crewx_clickgui_" + Integer.toHexString(resource.hashCode()), object);
            GUI_TEXTURES.put(resource, texture);
            GUI_TEXTURE_OBJECTS.put(resource, object);
            GUI_TEXTURE_SIZES.put(resource, new int[]{image.getWidth(), image.getHeight()});
            return texture;
        } catch (Exception ignored) {
            return null;
        }
    }








    private static void verifyGuiTextures() {
        if (GUI_TEXTURE_OBJECTS.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - lastTextureCheck < 300L) return;
        lastTextureCheck = now;
        Set<Integer> foreign = new java.util.HashSet<Integer>();
        CategoryIconRenderer.collectTextureIds(foreign);
        int atlas = FONT.textureId();
        if (atlas > 0) foreign.add(atlas);
        Map<Integer, String> owners = new HashMap<Integer, String>();
        List<String> broken = null;
        int activeUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            for (Map.Entry<String, DynamicTexture> entry : GUI_TEXTURE_OBJECTS.entrySet()) {
                int id = entry.getValue().getGlTextureId();
                String other = owners.put(id, entry.getKey());
                int[] size = GUI_TEXTURE_SIZES.get(entry.getKey());
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
                int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
                int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
                boolean bad = other != null || foreign.contains(id)
                        || (size != null && (width != size[0] || height != size[1]));
                if (!bad) continue;
                if (broken == null) broken = new ArrayList<String>();
                if (!broken.contains(entry.getKey())) broken.add(entry.getKey());
                if (other != null && !broken.contains(other)) broken.add(other);
            }
        } finally {

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
            GL13.glActiveTexture(activeUnit);
        }
        if (broken == null) return;
        crewx.util.ClientLog.log("ClickGUI icon textures lost their GL names, reloading " + broken);
        for (String resource : broken) {

            GUI_TEXTURE_OBJECTS.remove(resource);
            GUI_TEXTURES.remove(resource);
            GUI_TEXTURE_SIZES.remove(resource);
        }
    }

    private float categoryNavStep() {
        float navTop = this.bodyY + 29.0F;
        float navBottom = this.windowY + this.windowHeight - FOOTER_HEIGHT - 30.0F;
        float navHeight = Math.max(0.0F, navBottom - navTop);
        return Math.min(35.0F, navHeight / Math.max(1, CATEGORIES.length));
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
        String categoriesLabel = this.windowWidth < 150.0F ? "CAT" : "CATEGORIES";
        this.text(this.trimToWidth(categoriesLabel, (int) Math.max(0.0F, this.sidebarWidth - 26.0F)),
                x + 14.0F, y + 10.0F, MUTED.getRGB());
        float navY = y + 29.0F;
        float navStep = this.categoryNavStep();
        float rowHeight = navStep <= 0.0F ? 0.0F : Math.max(1.0F, Math.min(navStep, navStep - 3.0F));

        int selectedIndex = Math.max(0, java.util.Arrays.asList(CATEGORIES).indexOf(this.selectedCategory));
        float selectedTarget = selectedIndex * navStep;
        if (this.categorySelectionY < 0.0F) this.categorySelectionY = selectedTarget;
        this.categorySelectionY = approach(this.categorySelectionY, selectedTarget, this.deltaTime, 15.0F);
        if (rowHeight > 0.0F) {
            float pillY = navY + this.categorySelectionY + (navStep - rowHeight) / 2.0F;
            this.round(x + 8.0F, pillY, Math.max(0.0F, this.sidebarWidth - 16.0F), rowHeight,
                    lerpColor(new Color(12, 12, 12, 255).getRGB(), accentColor().getRGB(), 0.22F),
                    Math.min(7.0F, rowHeight / 2.0F));
            if (rowHeight >= 4.0F) {
                float markerHeight = Math.max(1.0F, Math.min(rowHeight - 2.0F, navStep - 8.0F));
                this.round(x + 8.0F, navY + this.categorySelectionY + (navStep - markerHeight) / 2.0F, 2.0F, markerHeight,
                        accentColor().getRGB(), 1.0F);
            }
        }
        int categoryIndex = 0;
        for (ModuleCategory category : CATEGORIES) {
            boolean selected = category == this.selectedCategory;
            boolean hover = mouseX >= x + 7.0F && mouseX <= x + this.sidebarWidth - 7.0F
                    && mouseY >= navY && mouseY <= navY + navStep - 3.0F;
            float hoverAnim = this.categoryHover[categoryIndex] =
                    approach(this.categoryHover[categoryIndex], hover && !selected ? 1.0F : 0.0F, this.deltaTime, 12.0F);
            categoryIndex++;
            if (rowHeight <= 0.0F) continue;
            float rowY = navY + (navStep - rowHeight) / 2.0F;
            if (hoverAnim > 0.01F) {
                this.round(x + 8.0F, rowY, Math.max(0.0F, this.sidebarWidth - 16.0F), rowHeight,
                        withAlpha(new Color(22, 22, 24, 255).getRGB(), hoverAnim), Math.min(7.0F, rowHeight / 2.0F));
            }
            boolean compactRows = this.windowWidth < 360.0F || navStep < 18.0F;
            String categoryLabel = category.getDisplayName();
            if (this.windowWidth < 150.0F && categoryLabel.length() > 2) {
                categoryLabel = categoryLabel.substring(0, 2).toUpperCase(Locale.ROOT);
            }
            int activeCount = 0;
            if (!compactRows) {
                for (Module module : this.getModules(category)) if (module.isEnabled()) activeCount++;
            }
            if (!compactRows) {
                int iconColor = selected ? Color.WHITE.getRGB() : lerpColor(MUTED.getRGB(), 0xFFC8C8CC, hoverAnim);
                this.drawCategoryIcon(category, x + 25.0F, navY + navStep / 2.0F, iconColor, 15.0F);
            }
            float labelX = compactRows ? x + 18.0F : x + 43.0F;
            boolean showCount = !compactRows && this.sidebarWidth >= 220.0F && activeCount > 0;
            float labelRightInset = compactRows ? 8.0F : showCount ? 28.0F + FONT.getStringWidth(String.valueOf(activeCount)) : 14.0F;
            int labelWidth = (int) Math.max(0.0F, x + this.sidebarWidth - labelRightInset - labelX);
            this.text(this.trimToWidth(categoryLabel, labelWidth), labelX,
                    navY + Math.max(0.0F, (navStep - FONT.getFontHeight()) / 2.0F),
                    selected ? Color.WHITE.getRGB() : TEXT.getRGB());
            if (showCount) {
                String count = String.valueOf(activeCount);
                this.text(count, x + this.sidebarWidth - FONT.getStringWidth(count) - 17.0F,
                        navY + Math.max(0.0F, (navStep - FONT.getFontHeight()) / 2.0F),
                        selected ? Color.WHITE.getRGB() : MUTED.getRGB());
            }
            navY += navStep;
        }
        float dividerY = this.windowY + this.windowHeight - FOOTER_HEIGHT - 30.0F;
        this.round(x + 14.0F, dividerY, this.sidebarWidth - 28.0F, 1.0F,
                new Color(34, 34, 36, 255).getRGB(), 0.0F);
    }

    private void renderModulePane(float mouseX, float mouseY) {
        float x = this.windowX + this.sidebarWidth;
        float width = this.modulePaneWidth;
        float contentX = x + 13.0F;
        List<Module> visible = this.getVisibleModules();
        float headingWidth = width < 155.0F ? Math.max(0.0F, width - 20.0F)
                : width - FONT.getStringWidth(String.valueOf(visible.size()) + " MODULES") - 28.0F;
        String categoryHeading = this.trimToWidth(this.selectedCategory.getDisplayName().toUpperCase(Locale.ROOT),
                (int) Math.max(0.0F, headingWidth));
        this.text(categoryHeading, contentX + 1.0F, this.bodyY + 10.0F, TEXT.getRGB());
        String count = String.valueOf(visible.size());
        if (width >= 155.0F) {
            this.text(count + " MODULES", x + width - FONT.getStringWidth(count + " MODULES") - 15.0F,
                    this.bodyY + 10.0F, MUTED.getRGB());
        }
        float searchWidth = width - 26.0F;
        boolean searchHover = mouseX >= contentX && mouseX <= contentX + searchWidth
                && mouseY >= this.searchY && mouseY <= this.searchY + 23.0F;
        int searchEdge = this.searchFocused ? withAlpha(accentColor().getRGB(), 0.55F)
                : searchHover ? new Color(44, 44, 48, 255).getRGB() : new Color(28, 28, 31, 255).getRGB();
        this.round(contentX, this.searchY, searchWidth, 23.0F, searchEdge, 7.0F);
        this.round(contentX + 1.0F, this.searchY + 1.0F, searchWidth - 2.0F, 21.0F,
                this.searchFocused ? new Color(18, 18, 20, 255).getRGB() : new Color(13, 13, 14, 255).getRGB(), 6.0F);
        this.drawSearchIcon(contentX + 7.0F, this.searchY + 5.0F, MUTED.getRGB());
        String query = this.searchText.isEmpty() && !this.searchFocused ? "Search modules..." : this.searchText + (this.searchFocused ? "_" : "");
        this.text(this.trimToWidth(query, Math.max(0, (int) searchWidth - 43)), contentX + 27.0F,
                this.searchY + (23.0F - FONT.getFontHeight()) / 2.0F,
                this.searchText.isEmpty() && !this.searchFocused ? MUTED.getRGB() : TEXT.getRGB());
        if (!this.searchText.isEmpty()) this.drawIcon(CLEAR_ICON, contentX + searchWidth - 17.0F, this.searchY + 6.0F, 11, MUTED.getRGB());
        float visibleHeight = Math.max(0.0F, this.bodyY + this.bodyHeight - this.moduleListY - 5.0F);
        this.scissorOn(x + 6.0F, this.moduleListY, width - 12.0F, visibleHeight);
        float rowY = this.moduleListY - this.moduleScroll;
        for (Module module : visible) {
            this.renderModuleRow(module, x + 10.0F, rowY, width - 20.0F, mouseX, mouseY);
            rowY += MODULE_ROW_HEIGHT;
            if (module == this.selectedModule) {
                this.settingsListY = rowY + 25.0F;
                this.settingsListHeight = this.propertyContentHeight();
                this.renderInlineSettings(module, x + 10.0F, rowY, width - 20.0F, mouseX, mouseY);
                rowY += this.expandedSettingsHeight();
            }
        }
        this.scissorOff();
        float contentHeight = visible.size() * MODULE_ROW_HEIGHT;
        if (this.selectedModule != null && visible.contains(this.selectedModule)) contentHeight += this.expandedSettingsHeight();
        if (contentHeight > visibleHeight) this.drawScrollbar(x + width - 4.0F, this.moduleListY, visibleHeight, contentHeight, this.moduleScroll);
        if (visible.isEmpty()) this.text("No modules found", contentX + 5.0F, this.moduleListY + 15.0F, MUTED.getRGB());
    }

    private void renderModuleRow(Module module, float x, float y, float width, float mouseX, float mouseY) {
        if (y + MODULE_ROW_HEIGHT <= this.moduleListY || y >= this.bodyY + this.bodyHeight) return;
        float hover = this.hoverAnimations.containsKey(module) ? this.hoverAnimations.get(module).floatValue() : 0.0F;
        float enabled = this.enabledAnimations.containsKey(module) ? this.enabledAnimations.get(module).floatValue() : (module.isEnabled() ? 1.0F : 0.0F);
        boolean selected = module == this.selectedModule;
        int base = lerpColor(new Color(15, 15, 16, 255).getRGB(), accentColor().getRGB(), 0.035F * enabled);
        int bg = selected ? lerpColor(new Color(14, 14, 14, 255).getRGB(), accentColor().getRGB(), 0.16F) : lerpColor(base, new Color(25, 25, 27, 255).getRGB(), hover);
        int edge = lerpColor(bg, 0xFF34343A, selected ? 0.0F : hover * 0.85F);
        this.round(x, y + 1.0F, width, MODULE_ROW_HEIGHT - 2.0F, edge, 6.0F);
        this.round(x + 1.0F, y + 2.0F, width - 2.0F, MODULE_ROW_HEIGHT - 4.0F, bg, 5.0F);
        if (selected) this.round(x, y + 7.0F, 2.0F, MODULE_ROW_HEIGHT - 14.0F, accentColor().getRGB(), 1.0F);
        String name = module == this.listeningModule ? "Press a key..." : module.getName().replace('-', ' ');
        int key = module.getKey();
        String keyName = key != 0 && module != this.listeningModule ? KeyBindUtil.getKeyName(key) : null;
        float switchX = x + width - 25.0F;
        this.text(this.trimToWidth(name, (int) Math.max(0.0F, width - 67.0F)), x + 12.0F,
                y + (MODULE_ROW_HEIGHT - FONT.getFontHeight()) / 2.0F, enabled > 0.5F ? Color.WHITE.getRGB() : TEXT.getRGB());
        String expand = keyName != null && width >= 140.0F ? keyName : selected ? "−" : "+";
        this.text(expand, switchX - FONT.getStringWidth(expand) - 11.0F,
                y + (MODULE_ROW_HEIGHT - FONT.getFontHeight()) / 2.0F,
                keyName == null && selected ? accentColor().brighter().getRGB() : MUTED.getRGB());
        float switchY = y + 10.0F;

        int offTrack = lerpColor(bg, 0xFFFFFFFF, 0.12F + 0.05F * hover);
        this.round(switchX, switchY, 18.0F, 9.0F, lerpColor(offTrack, accentColor().getRGB(), enabled), 5.0F);
        this.round(switchX + 1.0F + enabled * 8.0F, switchY + 1.0F, 7.0F, 7.0F, knobColor(enabled), 4.0F);
    }

    private void renderInlineSettings(Module module, float x, float y, float width, float mouseX, float mouseY) {
        float height = this.expandedSettingsHeight();
        if (height <= 0.0F) return;
        this.round(x + 2.0F, y + 2.0F, width - 4.0F, height - 5.0F,
                new Color(24, 24, 27, 255).getRGB(), 6.0F);
        this.round(x + 3.0F, y + 3.0F, width - 6.0F, height - 7.0F,
                new Color(11, 11, 12, 255).getRGB(), 5.0F);
        this.text("SETTINGS", x + 12.0F, y + 10.0F, MUTED.getRGB());
        if (width >= 245.0F) {
            this.text("Middle-click module to bind", x + width - FONT.getStringWidth("Middle-click module to bind") - 12.0F,
                    y + 10.0F, MUTED.getRGB());
        }
        float settingY = y + 25.0F;
        this.settingsListY = settingY;
        this.settingsListHeight = this.propertyContentHeight();
        for (SettingComponent setting : this.settingComponents) {
            if (!setting.property.isVisible()) continue;
            float h = setting.getHeight();
            setting.render(x + 8.0F, settingY, (int) width - 16, mouseX, mouseY);
            settingY += h;
        }
        if (this.settingsListHeight <= 0.0F) this.text("No settings for this module", x + 12.0F, y + 31.0F, MUTED.getRGB());
    }

    private float propertyContentHeight() {
        float height = 0.0F;
        for (SettingComponent setting : this.settingComponents) if (setting.property.isVisible()) height += setting.getHeight();
        return height;
    }

    private float expandedSettingsHeight() {
        return this.selectedModule == null ? 0.0F : Math.max(42.0F, 30.0F + this.propertyContentHeight());
    }

    private float selectedModuleRowTop() {
        return this.moduleRowTop(this.selectedModule);
    }

    private float moduleRowTop(Module target) {
        if (target == null) return -1.0F;
        float y = this.moduleListY - this.moduleScroll;
        for (Module module : this.getVisibleModules()) {
            if (module == target) return y;
            y += MODULE_ROW_HEIGHT;
            if (module == this.selectedModule) y += this.expandedSettingsHeight();
        }
        return -1.0F;
    }

    private void renderConfigsPane(float mouseX, float mouseY) {
        float x = this.windowX + 14.0F;
        float width = Math.max(0.0F, this.windowWidth - 28.0F);
        String heading = width >= 100.0F ? "SAVED CONFIGS" : "CONFIGS";
        float buttonWidth = Math.min(width - 14.0F, FONT.getStringWidth("UPLOAD CONFIG") + 20.0F);
        float buttonX = x + width - 7.0F - buttonWidth;
        heading = this.trimToWidth(heading, (int) Math.max(0.0F, buttonX - x - 12.0F));
        this.text(heading, x + 2.0F, this.bodyY + 16.0F, MUTED.getRGB());

        float buttonY = this.bodyY + 10.0F;
        boolean buttonHover = mouseX >= buttonX && mouseX <= buttonX + buttonWidth
                && mouseY >= buttonY && mouseY <= buttonY + 20.0F;
        int buttonFill = buttonHover ? new Color(24, 24, 24, 255).getRGB() : new Color(15, 15, 15, 255).getRGB();
        this.round(buttonX, buttonY, buttonWidth, 20.0F,
                buttonHover ? withAlpha(accentColor().getRGB(), 0.55F) : new Color(39, 39, 42, 255).getRGB(), 6.0F);
        this.round(buttonX + 1.0F, buttonY + 1.0F, buttonWidth - 2.0F, 18.0F, buttonFill, 5.0F);
        String uploadLabel = this.trimToWidth("UPLOAD CONFIG", (int) buttonWidth - 14);
        this.text(uploadLabel, buttonX + (buttonWidth - FONT.getStringWidth(uploadLabel)) / 2.0F,
                buttonY + (20.0F - FONT.getFontHeight()) / 2.0F, Color.WHITE.getRGB());
        if (System.currentTimeMillis() < this.configImportStatusUntil && !this.configImportStatus.isEmpty()) {
            this.text(this.trimToWidth(this.configImportStatus, (int) width - 12), x + 7.0F,
                    this.bodyY + 31.0F, TEXT.getRGB());
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
                this.round(x + 1.0F, rowY + 1.0F, width - 7.0F, CONFIG_ROW_HEIGHT - 4.0F, background, 6.0F);
                if (selected) this.round(x + 1.0F, rowY + 8.0F, 2.0F, CONFIG_ROW_HEIGHT - 18.0F,
                        accentColor().getRGB(), 1.0F);
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
        File directory = Config.directory();
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

    private boolean isUploadConfigButtonHit(float mouseX, float mouseY) {
        float x = this.windowX + 14.0F;
        float width = Math.max(0.0F, this.windowWidth - 28.0F);
        float buttonWidth = Math.min(width - 14.0F, FONT.getStringWidth("UPLOAD CONFIG") + 20.0F);
        float buttonX = x + width - 7.0F - buttonWidth;
        float buttonY = this.bodyY + 10.0F;
        return mouseX >= buttonX && mouseX <= buttonX + buttonWidth
                && mouseY >= buttonY && mouseY <= buttonY + 20.0F;
    }

    private void openConfigFileChooser() {
        try {
            EventQueue.invokeLater(new Runnable() {
                @Override
                public void run() {
                    try {
                        JFileChooser chooser = new JFileChooser(new File(System.getProperty("user.home", ".")));
                        chooser.setDialogTitle("Upload Config (JSON)");
                        chooser.setAcceptAllFileFilterUsed(false);
                        chooser.setFileFilter(new FileNameExtensionFilter("JSON configs (*.json)", "json"));
                        chooser.setMultiSelectionEnabled(false);
                        JFrame owner = new JFrame();
                        owner.setUndecorated(true);
                        owner.setAlwaysOnTop(true);
                        owner.setLocationRelativeTo(null);
                        final File selected;
                        try {
                            selected = chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION
                                    ? chooser.getSelectedFile() : null;
                        } finally {
                            owner.dispose();
                        }
                        if (selected != null) {
                            pendingConfigImport = selected;
                        }
                    } catch (final Throwable error) {
                        crewx.util.ClientLog.log("Config picker failed: "
                                + error);
                        configPickerFailed = true;
                    }
                }
            });
        } catch (Throwable error) {
            crewx.util.ClientLog.log("Config picker startup failed: "
                    + error);
            this.setConfigImportStatus("Could not open the file picker.");
        }
    }

    private void importConfigFile(File source) {
        if (source == null || !source.isFile() || !source.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
            this.setConfigImportStatus("Choose a .json config file.");
            return;
        }
        try (Reader reader = new BufferedReader(new FileReader(source))) {
            JsonElement parsed = new JsonParser().parse(reader);
            if (parsed == null || !parsed.isJsonObject()) {
                this.setConfigImportStatus("This file is not a valid JSON config.");
                return;
            }
        } catch (Exception error) {
            this.setConfigImportStatus("Could not read the selected JSON file.");
            return;
        }

        try {
            File directory = Config.directory();
            if (!directory.exists() && !directory.mkdirs()) {
                this.setConfigImportStatus("Could not access the config folder.");
                return;
            }
            File canonicalDirectory = directory.getCanonicalFile();
            File canonicalSource = source.getCanonicalFile();
            if (canonicalSource.getParentFile() != null
                    && canonicalSource.getParentFile().equals(canonicalDirectory)) {
                this.refreshConfigs();
                this.setConfigImportStatus("Config is already in the list: " + this.configName(source));
                return;
            }

            String original = source.getName().substring(0, source.getName().length() - 5);
            String safeName = original.replaceAll("[^A-Za-z0-9 _-]", "_").trim();
            if (safeName.isEmpty() || ".".equals(safeName) || "..".equals(safeName)) safeName = "imported-config";
            if ("default".equalsIgnoreCase(safeName)) safeName += "-imported";
            if (safeName.length() > 75) safeName = safeName.substring(0, 75).trim();
            File target = new File(canonicalDirectory, safeName + ".json");
            int suffix = 2;
            while (target.exists()) {
                target = new File(canonicalDirectory, safeName + "-" + suffix++ + ".json");
            }
            java.nio.file.Files.copy(canonicalSource.toPath(), target.toPath());
            this.refreshConfigs();
            this.targetConfigScroll = 0.0F;
            this.configScroll = 0.0F;
            this.setConfigImportStatus("Imported: " + this.configName(target));
        } catch (Exception error) {
            crewx.util.ClientLog.log("Config import failed: "
                    + error);
            this.setConfigImportStatus("Could not import the selected config.");
        }
    }

    private void setConfigImportStatus(String message) {
        this.configImportStatus = message == null ? "" : message;
        this.configImportStatusUntil = System.currentTimeMillis() + 5000L;
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
        try {
            boolean loaded = new Config(name, false).load(false);
            if (loaded) {
                this.selectedConfigName = name;
                this.refreshConfigs();
            }
        } catch (Throwable error) {
            this.setConfigImportStatus("Could not apply config: " + name);
        }
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
        this.pendingModuleToggleHit = false;
    }

    private void updateScrollDrag(float mouseY) {
        if (this.scrollDragTarget == DRAG_NONE || !Mouse.isButtonDown(0)) return;
        if (!this.scrollDragActive && Math.abs(mouseY - this.scrollDragOriginY) >= 7.0F) {
            this.scrollDragActive = true;
            this.pendingModuleClick = null;
            this.pendingConfigClick = null;
        }
        if (this.scrollDragActive) {
            float delta = mouseY - this.lastScrollDragY;
            if (this.scrollDragTarget == DRAG_MODULES || this.scrollDragTarget == DRAG_SETTINGS) {
                this.targetModuleScroll -= delta;
            }
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
        this.pendingModuleToggleHit = false;
    }

    private void renderFooter() {
        float y = this.windowY + this.windowHeight - FOOTER_HEIGHT;
        this.round(this.windowX + 1.0F, y, this.windowWidth - 2.0F, 1.0F, new Color(26, 26, 28, 255).getRGB(), 0.0F);
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
        this.round(x, y + 4.0F, 2.0F, track, 0x14FFFFFF, 1.0F);
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
        if (query.isEmpty()) {
            result.addAll(this.getModules(this.selectedCategory));
        } else {
            for (ModuleCategory category : CATEGORIES) {
                for (Module module : this.getModules(category)) {
                    if (module.getName().toLowerCase(Locale.ROOT).contains(query)) {
                        result.add(module);
                    }
                }
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
        this.selectModule(null);
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
        if (this.listeningSettingBind != null) return;
        if (this.searchFocused && !this.insideSearch(mx, my)) this.searchFocused = false;
        if (mx >= this.windowX && mx <= this.windowX + this.windowWidth
                && my >= this.windowY && my <= this.windowY + HEADER_HEIGHT) {
            if (mouseButton == 0) {
                float modulesX = this.modulesTabX();
                float configsX = this.configsTabX();
                String modulesLabel = this.headerLabel(ViewTab.MODULES);
                String configsLabel = this.headerLabel(ViewTab.CONFIGS);
                if (mx >= modulesX - 5.0F && mx <= modulesX + FONT.getStringWidth(modulesLabel) + 6.0F) {
                    this.setActiveTab(ViewTab.MODULES); return;
                }
                if (mx >= configsX - 5.0F && mx <= configsX + FONT.getStringWidth(configsLabel) + 6.0F) {
                    this.setActiveTab(ViewTab.CONFIGS); return;
                }
            }
            this.draggingWindow = mouseButton == 0;
            this.dragMouseOffsetX = mx - this.windowX;
            this.dragMouseOffsetY = my - this.windowY;
            return;
        }
        if (this.activeTab == ViewTab.CONFIGS) {
            if (mouseButton == 0 && this.isUploadConfigButtonHit(mx, my)) {
                this.setFocus(null);
                this.openConfigFileChooser();
                return;
            }
            if (mouseButton == 0 && my >= this.configListY && my <= this.configListY + this.configListHeight) {
                this.beginScrollDrag(DRAG_CONFIGS, my);
                this.pendingConfigClick = this.getConfigAt(mx, my);
            }
            this.setFocus(null); return;
        }
        float navY = this.bodyY + 29.0F;
        float navStep = this.categoryNavStep();
        for (ModuleCategory category : CATEGORIES) {
            if (mx >= this.windowX + 7.0F && mx <= this.windowX + this.sidebarWidth - 7.0F
                    && my >= navY && my <= navY + navStep - 1.0F) {
                this.selectCategory(category); this.setFocus(null); return;
            }
            navY += navStep;
        }
        float searchX = this.windowX + this.sidebarWidth + 13.0F;
        float searchWidth = this.modulePaneWidth - 26.0F;
        if (mx >= searchX && mx <= searchX + searchWidth && my >= this.searchY && my <= this.searchY + 23.0F) {
            if (!this.searchText.isEmpty() && mx >= searchX + searchWidth - 20.0F) {
                this.searchText = ""; this.targetModuleScroll = 0.0F;
            }
            this.searchFocused = true; this.setFocus(null); return;
        }
        if (mx >= this.windowX + this.sidebarWidth && mx <= this.windowX + this.windowWidth
                && my >= this.moduleListY && my <= this.bodyY + this.bodyHeight) {
            float selectedTop = this.selectedModuleRowTop();
            if (this.selectedModule != null && this.getVisibleModules().contains(this.selectedModule)) {
                float settingsTop = selectedTop + MODULE_ROW_HEIGHT + 25.0F;
                if (my >= settingsTop && my <= settingsTop + this.propertyContentHeight()) {
                    boolean consumed = this.dispatchSettingClick(mx, my, mouseButton, true);
                    if (mouseButton == 0 && !consumed) this.beginScrollDrag(DRAG_SETTINGS, my);
                    if (consumed || mouseButton == 0 || mouseButton == 1) return;
                }
            }
            List<Module> visible = this.getVisibleModules();
            float y = this.moduleListY - this.moduleScroll;
            for (Module module : visible) {
                if (my >= y && my <= y + MODULE_ROW_HEIGHT) {
                    float rowX = this.windowX + this.sidebarWidth + 10.0F;
                    float rowWidth = this.modulePaneWidth - 20.0F;
                    boolean toggleHit = mx >= rowX + rowWidth - 27.0F
                            && mx <= rowX + rowWidth - 5.0F;
                    if (mouseButton == 0 && toggleHit) {
                        this.clearScrollDrag(); module.toggle(); return;
                    }
                    if (mouseButton == 2) { this.listeningModule = module; return; }
                    if (mouseButton == 1) { this.selectModule(module == this.selectedModule ? null : module); return; }
                    if (mouseButton == 0) {
                        this.beginScrollDrag(DRAG_MODULES, my);
                        this.pendingModuleClick = module;
                        this.pendingModuleSettingsHit = false;
                        this.pendingModuleToggleHit = true;
                    }
                    return;
                }
                y += MODULE_ROW_HEIGHT;
                if (module == this.selectedModule) {
                    y += this.expandedSettingsHeight();
                    if (my < y) return;
                }
            }
            if (mouseButton == 0) {
                this.beginScrollDrag(DRAG_MODULES, my); return;
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
        float y = this.settingsListY;
        float x = this.windowX + this.sidebarWidth + 18.0F;
        float width = this.modulePaneWidth - 36.0F;
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
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (this.draggingSetting != null && clickedMouseButton == 0) {
            float mx = this.toVirtualX(mouseX);
            float my = this.toVirtualY(mouseY);
            this.draggingSetting.updateDrag((int) mx, (int) my);
            this.lastMouseX = mx;
            this.lastMouseY = my;
            return;
        }
        if (this.draggingWindow && clickedMouseButton == 0) {
            float mx = this.toVirtualX(mouseX);
            float my = this.toVirtualY(mouseY);
            this.windowOffsetX = mx - this.dragMouseOffsetX - (this.virtualWidth - this.windowWidth) / 2.0F;
            this.windowOffsetY = my - this.dragMouseOffsetY - (this.virtualHeight - this.windowHeight) / 2.0F;
            this.computeLayout();
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        float mx = this.toVirtualX(mouseX);
        float my = this.toVirtualY(mouseY);
        if (state == 0 && !this.scrollDragActive) {
            if (this.scrollDragTarget == DRAG_MODULES && this.pendingModuleClick != null) {
                List<Module> visible = this.getVisibleModules();
                float pendingTop = this.moduleRowTop(this.pendingModuleClick);
                if (pendingTop >= 0.0F && my >= pendingTop && my <= pendingTop + MODULE_ROW_HEIGHT) {
                    if (this.pendingModuleSettingsHit && !this.pendingModuleToggleHit) {
                        this.selectModule(this.pendingModuleClick == this.selectedModule ? null : this.pendingModuleClick);
                    } else {
                        this.pendingModuleClick.toggle();
                    }
                }
            } else if (this.scrollDragTarget == DRAG_CONFIGS && this.pendingConfigClick != null) {
                File releasedConfig = this.getConfigAt(mx, my);
                if (this.pendingConfigClick.equals(releasedConfig)) this.loadConfig(releasedConfig);

            }
        }

        boolean releaseSettings = this.draggingSetting != null
                || (this.scrollDragTarget == DRAG_SETTINGS && !this.scrollDragActive);
        if (this.activeTab == ViewTab.MODULES && state == 0 && releaseSettings
                && this.selectedModule != null && mx >= this.windowX + this.sidebarWidth
                && my >= this.settingsListY && my <= this.settingsListY + this.settingsListHeight) {
            this.dispatchSettingClick(mx, my, state, false);
        }
        this.draggingWindow = false;
        this.draggingSetting = null;
        this.clearScrollDrag();
    }

    @Override
    public void handleMouseInput() throws IOException {
        int wheel = Mouse.getEventDWheel();
        int eventX = Mouse.getEventX();
        int eventY = Mouse.getEventY();
        super.handleMouseInput();
        if (wheel == 0) return;
        float mouseX = this.toVirtualX(eventX * this.width / mc.displayWidth);
        float mouseY = this.toVirtualY(this.height - eventY * this.height / mc.displayHeight - 1);
        if (this.activeTab == ViewTab.CONFIGS) {
            if (mouseX >= this.windowX && mouseX <= this.windowX + this.windowWidth
                    && mouseY >= this.configListY && mouseY <= this.configListY + this.configListHeight) {
                this.targetConfigScroll -= wheel / 120.0F * CONFIG_ROW_HEIGHT * 2.0F;
            }
        } else if (mouseX >= this.windowX + this.sidebarWidth && mouseX <= this.windowX + this.windowWidth
                && mouseY >= this.moduleListY && mouseY <= this.bodyY + this.bodyHeight) {
            this.targetModuleScroll -= wheel / 120.0F * MODULE_ROW_HEIGHT * 2.0F;
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
        if (this.listeningSettingBind != null) {
            IntProperty bind = (IntProperty) this.listeningSettingBind.property;
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_DELETE) {
                bind.setValue(0);
            } else if (keyCode != Keyboard.KEY_NONE) {
                bind.setValue(keyCode);
            }
            this.listeningSettingBind = null;
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
        this.listeningSettingBind = null;
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
        CategoryIconRenderer.draw(category, x - iconSize / 2.0F, y - iconSize / 2.0F, iconSize, this.fade(color));
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
        float x1 = (this.centerX + (rect[0] - this.centerX) * this.renderScale) * this.uiScale;
        float x2 = (this.centerX + (rect[2] - this.centerX) * this.renderScale) * this.uiScale;
        float y1 = (this.centerY + (rect[1] + this.renderOffsetY - this.centerY) * this.renderScale) * this.uiScale;
        float y2 = (this.centerY + (rect[3] + this.renderOffsetY - this.centerY) * this.renderScale) * this.uiScale;
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


    private static int knobColor(float enabled) {
        Color accent = accentColor();
        float luminance = (0.2126F * accent.getRed() + 0.7152F * accent.getGreen() + 0.0722F * accent.getBlue()) / 255.0F;
        if (luminance < 0.72F) return 0xFFFFFFFF;
        return lerpColor(0xFFFFFFFF, 0xFF141416, clamp01((enabled - 0.5F) * 2.0F));
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

        private boolean isManualBind() {
            return this.property instanceof IntProperty && "manual-bind".equals(this.property.getName());
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
            } else if (this.isManualBind()) {
                this.renderManualBind(x, y, width);
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
            ClickGui.this.round(switchX + 1.5F + 12.0F * this.toggleAnim, switchY + 1.5F, 8.0F, 8.0F, knobColor(this.toggleAnim), 4.0F);
        }

        private void renderManualBind(float x, float y, int width) {
            IntProperty bind = (IntProperty) this.property;
            boolean listening = ClickGui.this.listeningSettingBind == this;
            String keyText = listening ? "Press a key..."
                    : bind.getValue() == 0 ? "None" : KeyBindUtil.getKeyName(bind.getValue());
            keyText = ClickGui.this.trimToWidth(keyText, Math.max(16, (int) (width * 0.52F)));
            float boxWidth = Math.min(width * 0.58F, Math.max(34.0F, FONT.getStringWidth(keyText) + 12.0F));
            float boxX = x + width - boxWidth - 7.0F;
            String label = ClickGui.this.trimToWidth(this.property.getName().replace('-', ' '),
                    Math.max(0, (int) (boxX - x - 15.0F)));
            float textY = y + (SETTING_HEIGHT - FONT.getFontHeight()) / 2.0F;
            ClickGui.this.text(label, x + 10.0F, textY, TEXT.getRGB());
            ClickGui.this.round(boxX, y + 3.0F, boxWidth, 17.0F,
                    listening ? withAlpha(accentColor().getRGB(), 0.42F) : TRACK.getRGB(), 5.0F);
            ClickGui.this.text(keyText, boxX + (boxWidth - FONT.getStringWidth(keyText)) / 2.0F,
                    y + 4.5F, listening ? 0xFFFFFFFF : accentColor().brighter().getRGB());
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
            if (this.isManualBind()) {
                if (pressPhase) {
                    if (mouseButton != 0 || !onRow) return false;
                    ClickGui.this.setFocus(null);
                    ClickGui.this.listeningSettingBind = this;
                    return true;
                }
                return false;
            }
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
            if (this.property instanceof ModeProperty) {
                ModeProperty mode = (ModeProperty) this.property;
                String[] values = getModeValues(mode);
                float optionY = y + SETTING_HEIGHT - 1.0F;
                if (pressPhase) {
                    if (this.dropdownOpen) {
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
        float scaledX = screenX / this.uiScale;
        return (scaledX - this.centerX) / this.renderScale + this.centerX;
    }

    private float toVirtualY(float screenY) {
        float scaledY = screenY / this.uiScale;
        return (scaledY - this.centerY) / this.renderScale + this.centerY - this.renderOffsetY;
    }
}
