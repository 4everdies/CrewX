package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.enums.ChatColors;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.Render2DEvent;
import myau.events.TickEvent;
import myau.mixin.IAccessorGuiChat;
import myau.module.Module;
import myau.util.ColorUtil;
import myau.util.RenderUtil;
import myau.property.properties.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class HUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private List<Module> activeModules = new ArrayList<>();
    private List<float[]> moduleRects = new ArrayList<>();
    public final ModeProperty colorMode = new ModeProperty(
            "color", 3, new String[]{"RAINBOW", "CHROMA", "ASTOLFO", "CUSTOM1", "CUSTOM2", "CUSTOM3"}
    );
    public final FloatProperty colorSpeed = new FloatProperty("color-speed", 1.0F, 0.5F, 1.5F);
    public final PercentProperty colorSaturation = new PercentProperty("color-saturation", 50);
    public final PercentProperty colorBrightness = new PercentProperty("color-brightness", 100);
    public final ColorProperty custom1 = new ColorProperty("custom-color-1", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 3 || this.colorMode.getValue() == 4 || this.colorMode.getValue() == 5);
    public final ColorProperty custom2 = new ColorProperty("custom-color-2", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 4 || this.colorMode.getValue() == 5);
    public final ColorProperty custom3 = new ColorProperty("custom-color-3", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 5);
    public final ModeProperty posX = new ModeProperty("position-x", 0, new String[]{"LEFT", "RIGHT"});
    public final ModeProperty posY = new ModeProperty("position-y", 0, new String[]{"TOP", "BOTTOM"});
    public final IntProperty offsetX = new IntProperty("offset-x", 2, 0, 255);
    public final IntProperty offsetY = new IntProperty("offset-y", 2, 0, 255);
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);
    public final PercentProperty backgroundAlpha = new PercentProperty("background-alpha", 25);
    public final IntProperty backgroundThickness = new IntProperty("background-thickness", 2, 1, 15);
    public final IntProperty backgroundCurve = new IntProperty("background-curve", 3, 0, 10);
    public final PercentProperty outlineThickness = new PercentProperty("outline-thickness", 0);
    public final ModeProperty outlineColorMode = new ModeProperty("outline-color", 0, new String[]{"THEME", "CUSTOM"});
    public final ColorProperty customOutlineColor = new ColorProperty("custom-outline-color", Color.WHITE.getRGB(), () -> this.outlineColorMode.getValue() == 1);
    public final ModeProperty waveMode = new ModeProperty("wave-mode", 0, new String[]{"NONE", "VERTICAL", "HORIZONTAL"});
    public final BooleanProperty joinBands = new BooleanProperty("join-bands", true);
    public final BooleanProperty glowEnabled = new BooleanProperty("glow", false);
    public final FloatProperty glowSize = new FloatProperty("glow-size", 2.0F, 0.5F, 10.0F);
    public final BooleanProperty showBar = new BooleanProperty("bar", true);
    public final BooleanProperty shadow = new BooleanProperty("shadow", true);
    public final BooleanProperty suffixes = new BooleanProperty("suffixes", true);
    public final BooleanProperty lowerCase = new BooleanProperty("lower-case", false);
    public final BooleanProperty chatOutline = new BooleanProperty("chat-outline", true);
    public final BooleanProperty blinkTimer = new BooleanProperty("blink-timer", true);
    public final BooleanProperty toggleSound = new BooleanProperty("toggle-sounds", true);
    public final BooleanProperty toggleAlerts = new BooleanProperty("toggle-alerts", false);

    private String getModuleName(Module module) {
        String moduleName = module.getName();
        if (this.lowerCase.getValue()) {
            moduleName = moduleName.toLowerCase(Locale.ROOT);
        }
        return moduleName;
    }

    private String[] getModuleSuffix(Module module) {
        String[] moduleSuffix = module.getSuffix();
        if (this.lowerCase.getValue()) {
            for (int i = 0; i < moduleSuffix.length; i++) {
                moduleSuffix[i] = moduleSuffix[i].toLowerCase();
            }
        }
        return moduleSuffix;
    }

    private int getModuleWidth(Module module) {
        return this.calculateStringWidth(
                this.getModuleName(module), this.getModuleSuffix(module)
        );
    }

    private int calculateStringWidth(String string, String[] arr) {
        int width = mc.fontRendererObj.getStringWidth(string);
        if (this.suffixes.getValue()) {
            for (String str : arr) {
                width += 3 + mc.fontRendererObj.getStringWidth(str);
            }
        }
        return width;
    }

    private float getColorCycle(long long3, long long4) {
        long speed = (long) (3000.0 / Math.pow(Math.min(Math.max(0.5F, this.colorSpeed.getValue()), 1.5F), 3.0));
        return 1.0F - (float) (Math.abs(long3 - long4 * 300L) % speed) / (float) speed;
    }

    public HUD() {
        super("HUD", false, true);
    }

    public Color getColor(long time) {
        return this.getColor(time, 0L);
    }

    public Color getColor(long time, long offset) {
        Color color = Color.white;
        switch (this.colorMode.getValue()) {
            case 0:
                color = ColorUtil.fromHSB(this.getColorCycle(time, offset), 1.0F, 1.0F);
                break;
            case 1:
                color = ColorUtil.fromHSB(this.getColorCycle(time / 3L, 0L), 1.0F, 1.0F);
                break;
            case 2:
                float cycle = this.getColorCycle(time, offset);
                if (cycle % 1.0F < 0.5F) {
                    cycle = 1.0F - cycle % 1.0F;
                }
                color = ColorUtil.fromHSB(cycle, 1.0F, 1.0F);
                break;
            case 3:
                color = new Color(this.custom1.getValue());
                break;
            case 4:
                double cycle1 = this.getColorCycle(time, offset);
                color = ColorUtil.interpolate(
                        (float) (2.0 * Math.abs(cycle1 - Math.floor(cycle1 + 0.5))),
                        new Color(this.custom1.getValue()),
                        new Color(this.custom2.getValue())
                );
                break;
            case 5:
                double cycle2 = this.getColorCycle(time, offset);
                float floor = (float) (2.0 * Math.abs(cycle2 - Math.floor(cycle2 + 0.5)));
                if (floor <= 0.5F) {
                    color = ColorUtil.interpolate(floor * 2.0F, new Color(this.custom1.getValue()), new Color(this.custom2.getValue()));
                } else {
                    color = ColorUtil.interpolate((floor - 0.5F) * 2.0F, new Color(this.custom2.getValue()), new Color(this.custom3.getValue()));
                }
        }
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        return Color.getHSBColor(
                hsb[0],
                hsb[1] * (this.colorSaturation.getValue().floatValue() / 100.0F),
                hsb[2] * (this.colorBrightness.getValue().floatValue() / 100.0F)
        );
    }

    private Color getColorWithWave(long time, long moduleIndex, int totalModules) {
        int wave = this.waveMode.getValue();
        if (wave == 0 || totalModules <= 1) {
            return this.getColor(time, moduleIndex);
        }
        long waveOffset;
        if (wave == 1) {
            waveOffset = (long) (moduleIndex * 300L);
        } else {
            waveOffset = (long) (moduleIndex * 300L);
        }
        return this.getColor(time, waveOffset);
    }

    private Color getOutlineColor(Color themeColor) {
        if (this.outlineColorMode.getValue() == 1) {
            return new Color(this.customOutlineColor.getValue());
        }
        return new Color(
                Math.min(255, themeColor.getRed() + 120),
                Math.min(255, themeColor.getGreen() + 120),
                Math.min(255, themeColor.getBlue() + 120),
                180
        );
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.POST) {
            this.activeModules = Myau.moduleManager.modules.values().stream()
                    .filter(module -> module.isEnabled() && !module.isHidden())
                    .sorted(Comparator.comparingInt(this::getModuleWidth).reversed())
                    .collect(Collectors.<Module>toList());
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (this.chatOutline.getValue() && mc.currentScreen instanceof GuiChat) {
            String text = ((IAccessorGuiChat) mc.currentScreen).getInputField().getText().trim();
            if (Myau.commandManager != null && Myau.commandManager.isTypingCommand(text)) {
                RenderUtil.enableRenderState();
                RenderUtil.drawOutlineRect(
                        2.0F,
                        (float) (mc.currentScreen.height - 14),
                        (float) (mc.currentScreen.width - 2),
                        (float) (mc.currentScreen.height - 2),
                        1.5F,
                        0,
                        this.getColor(System.currentTimeMillis()).getRGB()
                );
                RenderUtil.disableRenderState();
            }
        }
        if (!this.isEnabled() || mc.gameSettings.showDebugInfo) {
            return;
        }

        ScaledResolution sr = new ScaledResolution(mc);
        float screenW = sr.getScaledWidth();
        float screenH = sr.getScaledHeight();
        float height = (float) mc.fontRendererObj.FONT_HEIGHT;
        float lineH = height + this.backgroundThickness.getValue();
        float curve = this.backgroundCurve.getValue();
        float bgAlpha = this.backgroundAlpha.getValue().floatValue() / 100.0F;
        float glowSz = this.glowSize.getValue();

        GlStateManager.pushMatrix();
        GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 0.0F);

        long l = System.currentTimeMillis();
        int totalModules = this.activeModules.size();

        this.moduleRects.clear();

        float baseX = this.offsetX.getValue();
        float baseY = this.offsetY.getValue();
        boolean rightAlign = this.posX.getValue() == 1;
        boolean bottomAlign = this.posY.getValue() == 1;

        for (int i = 0; i < totalModules; i++) {
            Module module = this.activeModules.get(i);
            String moduleName = this.getModuleName(module);
            String[] moduleSuffix = this.getModuleSuffix(module);
            float textW = mc.fontRendererObj.getStringWidth(moduleName);
            float totalWidth = this.calculateStringWidth(moduleName, moduleSuffix);

            float modX;
            float modY;
            if (rightAlign) {
                modX = screenW / this.scale.getValue() - baseX - totalWidth;
            } else {
                modX = baseX;
            }
            if (bottomAlign) {
                modY = screenH / this.scale.getValue() - baseY - (totalModules - i) * lineH;
            } else {
                modY = baseY + i * lineH;
            }
            Color themeColor = this.getColorWithWave(l, i, totalModules);
            int colorRGB = themeColor.getRGB();
            RenderUtil.enableRenderState();
            if (this.glowEnabled.getValue() && bgAlpha > 0) {
                int glowAlpha = (int) (bgAlpha * 255 * 0.4F);
                int glowColor = (glowAlpha << 24) | (colorRGB & 0x00FFFFFF);
                float gX1 = modX - glowSz + (rightAlign ? 0 : 0);
                float gX2 = modX + totalWidth + glowSz + (rightAlign ? 0 : 0);
                float gY1 = modY - 1 - glowSz;
                float gY2 = modY + height - 1 + glowSz;
                if (curve > 0) {
                    drawRoundedRect(gX1, gY1, gX2, gY2, curve, glowColor);
                } else {
                    RenderUtil.drawRect(gX1, gY1, gX2, gY2, glowColor);
                }
            }
            if (this.joinBands.getValue() && bgAlpha > 0 && i > 0) {
                int bandAlpha = (int) (bgAlpha * 255);
                int bandColor = (bandAlpha << 24);
                float bandX1 = rightAlign ? modX + totalWidth : modX;
                float bandX2 = rightAlign ? screenW / this.scale.getValue() - baseX : baseX + this.getMaxModuleWidth();
                float bandY1 = modY;
                float bandY2 = modY + lineH;
                RenderUtil.drawRect(bandX1, bandY1, bandX2, bandY2, bandColor);
            }
            if (bgAlpha > 0) {
                int bgAlphaInt = (int) (bgAlpha * 255);
                int bgColor = (bgAlphaInt << 24);
                float bgX1 = modX - this.backgroundThickness.getValue();
                float bgX2 = modX + totalWidth + this.backgroundThickness.getValue();
                float bgY1 = modY - 1;
                float bgY2 = modY + height - 1;
                if (curve > 0) {
                    drawRoundedRect(bgX1, bgY1, bgX2, bgY2, curve, bgColor);
                } else {
                    RenderUtil.drawRect(bgX1, bgY1, bgX2, bgY2, bgColor);
                }
            }
            if (this.outlineThickness.getValue() > 0 && bgAlpha > 0) {
                Color outlineCol = this.getOutlineColor(themeColor);
                float olX1 = modX - this.backgroundThickness.getValue();
                float olX2 = modX + totalWidth + this.backgroundThickness.getValue();
                float olY1 = modY - 1;
                float olY2 = modY + height - 1;
                RenderUtil.enableRenderState();
                RenderUtil.setColor(outlineCol.getRGB());
                GL11.glLineWidth(this.outlineThickness.getValue() * this.scale.getValue());
                GL11.glEnable(GL11.GL_LINE_SMOOTH);
                GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
                if (curve > 0) {
                    drawRoundedOutline(olX1, olY1, olX2, olY2, curve, this.outlineThickness.getValue() * this.scale.getValue());
                } else {
                    GL11.glBegin(GL11.GL_LINE_LOOP);
                    GL11.glVertex2f(olX1, olY1);
                    GL11.glVertex2f(olX2, olY1);
                    GL11.glVertex2f(olX2, olY2);
                    GL11.glVertex2f(olX1, olY2);
                    GL11.glEnd();
                }
                GL11.glDisable(GL11.GL_LINE_SMOOTH);
                GlStateManager.resetColor();
            }
            if (this.showBar.getValue()) {
                float barW = this.shadow.getValue() ? 2.0F : 1.0F;
                float barX1 = rightAlign ? modX - barW - 1 : modX - barW - 1;
                float barX2 = rightAlign ? modX - 1 : modX - 1;
                RenderUtil.drawRect(barX1, modY - 1, barX2, modY + height - 1, colorRGB);
            }
            RenderUtil.disableRenderState();
            GlStateManager.disableDepth();
            float textX = rightAlign ? modX : modX;
            float textY = modY;

            if (this.shadow.getValue()) {
                mc.fontRendererObj.drawStringWithShadow(moduleName, textX, textY, colorRGB);
            } else {
                mc.fontRendererObj.drawString(moduleName, textX, textY, colorRGB, false);
            }

            if (this.suffixes.getValue() && moduleSuffix.length > 0) {
                float suffixX = textX + textW + 3.0F;
                for (String string : moduleSuffix) {
                    if (this.shadow.getValue()) {
                        mc.fontRendererObj.drawStringWithShadow(string, suffixX, textY, ChatColors.GRAY.toAwtColor());
                    } else {
                        mc.fontRendererObj.drawString(string, suffixX, textY, ChatColors.GRAY.toAwtColor(), false);
                    }
                    suffixX += (float) mc.fontRendererObj.getStringWidth(string) + (this.shadow.getValue() ? 3.0F : 2.0F);
                }
            }

            this.moduleRects.add(new float[]{modX - this.backgroundThickness.getValue(), modY - 1, modX + totalWidth + this.backgroundThickness.getValue(), modY + height - 1});
        }
        if (this.blinkTimer.getValue()) {
            BlinkModules blinkingModule = Myau.blinkManager.getBlinkingModule();
            if (blinkingModule != BlinkModules.NONE && blinkingModule != BlinkModules.AUTO_BLOCK) {
                long movementPacketSize = Myau.blinkManager.countMovement();
                if (movementPacketSize > 0L) {
                    GlStateManager.enableBlend();
                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    mc.fontRendererObj.drawString(
                            String.valueOf(movementPacketSize),
                            screenW / 2.0F / this.scale.getValue()
                                    - (float) mc.fontRendererObj.getStringWidth(String.valueOf(movementPacketSize)) / 2.0F,
                            screenH / 5.0F * 3.0F / this.scale.getValue(),
                            this.getColor(l, (long) totalModules).getRGB() & 16777215 | -1090519040,
                            this.shadow.getValue()
                    );
                    GlStateManager.disableBlend();
                }
            }
        }

        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    private float getMaxModuleWidth() {
        float max = 0;
        for (Module m : this.activeModules) {
            float w = this.calculateStringWidth(this.getModuleName(m), this.getModuleSuffix(m));
            if (w > max) max = w;
        }
        return max;
    }

    private static void drawRoundedRect(float x1, float y1, float x2, float y2, float radius, int color) {
        radius = Math.min(radius, Math.min((x2 - x1) / 2.0F, (y2 - y1) / 2.0F));
        if (radius <= 0) {
            RenderUtil.drawRect(x1, y1, x2, y2, color);
            return;
        }
        float r = radius;
        int segments = Math.max(8, (int) (r * 2));
        RenderUtil.setColor(color);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f((x1 + x2) / 2.0F, (y1 + y2) / 2.0F);
        for (int i = 0; i <= segments / 4; i++) {
            double angle = Math.PI + (Math.PI / 2.0) * ((double) i / (segments / 4.0));
            GL11.glVertex2f((float) (x1 + r + Math.cos(angle) * r), (float) (y1 + r + Math.sin(angle) * r));
        }
        for (int i = 0; i <= segments / 4; i++) {
            double angle = -Math.PI / 2.0 + (Math.PI / 2.0) * ((double) i / (segments / 4.0));
            GL11.glVertex2f((float) (x2 - r + Math.cos(angle) * r), (float) (y1 + r + Math.sin(angle) * r));
        }
        for (int i = 0; i <= segments / 4; i++) {
            double angle = 0 + (Math.PI / 2.0) * ((double) i / (segments / 4.0));
            GL11.glVertex2f((float) (x2 - r + Math.cos(angle) * r), (float) (y2 - r + Math.sin(angle) * r));
        }
        for (int i = 0; i <= segments / 4; i++) {
            double angle = Math.PI / 2.0 + (Math.PI / 2.0) * ((double) i / (segments / 4.0));
            GL11.glVertex2f((float) (x1 + r + Math.cos(angle) * r), (float) (y2 - r + Math.sin(angle) * r));
        }
        GL11.glVertex2f(x1, y1 + r);
        GL11.glEnd();
        GlStateManager.resetColor();
    }

    private static void drawRoundedOutline(float x1, float y1, float x2, float y2, float radius, float lineWidth) {
        radius = Math.min(radius, Math.min((x2 - x1) / 2.0F, (y2 - y1) / 2.0F));
        if (radius <= 0) {
            GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glVertex2f(x1, y1);
            GL11.glVertex2f(x2, y1);
            GL11.glVertex2f(x2, y2);
            GL11.glVertex2f(x1, y2);
            GL11.glEnd();
            return;
        }
        float r = radius;
        int segments = Math.max(8, (int) (r * 2));
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i <= segments / 4; i++) {
            double angle = Math.PI + (Math.PI / 2.0) * ((double) i / (segments / 4.0));
            GL11.glVertex2f((float) (x1 + r + Math.cos(angle) * r), (float) (y1 + r + Math.sin(angle) * r));
        }
        for (int i = 0; i <= segments / 4; i++) {
            double angle = -Math.PI / 2.0 + (Math.PI / 2.0) * ((double) i / (segments / 4.0));
            GL11.glVertex2f((float) (x2 - r + Math.cos(angle) * r), (float) (y1 + r + Math.sin(angle) * r));
        }
        for (int i = 0; i <= segments / 4; i++) {
            double angle = 0 + (Math.PI / 2.0) * ((double) i / (segments / 4.0));
            GL11.glVertex2f((float) (x2 - r + Math.cos(angle) * r), (float) (y2 - r + Math.sin(angle) * r));
        }
        for (int i = 0; i <= segments / 4; i++) {
            double angle = Math.PI / 2.0 + (Math.PI / 2.0) * ((double) i / (segments / 4.0));
            GL11.glVertex2f((float) (x1 + r + Math.cos(angle) * r), (float) (y2 - r + Math.sin(angle) * r));
        }
        GL11.glEnd();
    }
}