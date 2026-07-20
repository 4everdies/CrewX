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
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class HUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    /** Horizontal gap between the module name and each suffix. Used by both measuring and drawing. */
    private static final float SUFFIX_GAP = 3.0F;

    private List<Module> activeModules = new ArrayList<>();
    private final Map<Module, Anim> anims = new LinkedHashMap<>();
    private long lastFrameTime = System.currentTimeMillis();

    /** Per-module animation state: fade/slide progress plus the smoothed vertical position. */
    private static final class Anim {
        float progress;
        float y;
        boolean placed;
    }

    // ---------------------------------------------------------------- colour
    public final ModeProperty colorMode = new ModeProperty(
            "color", 3, new String[]{"RAINBOW", "CHROMA", "ASTOLFO", "CUSTOM1", "CUSTOM2", "CUSTOM3"}
    );
    public final FloatProperty colorSpeed = new FloatProperty("color-speed", 1.0F, 0.5F, 1.5F,
            () -> this.colorMode.getValue() != 3);
    public final PercentProperty colorSaturation = new PercentProperty("color-saturation", 50);
    public final PercentProperty colorBrightness = new PercentProperty("color-brightness", 100);
    public final ColorProperty custom1 = new ColorProperty("custom-color-1", Color.WHITE.getRGB(),
            () -> this.colorMode.getValue() >= 3);
    public final ColorProperty custom2 = new ColorProperty("custom-color-2", Color.WHITE.getRGB(),
            () -> this.colorMode.getValue() >= 4);
    public final ColorProperty custom3 = new ColorProperty("custom-color-3", Color.WHITE.getRGB(),
            () -> this.colorMode.getValue() == 5);
    public final ModeProperty waveMode = new ModeProperty("wave-mode", 0, new String[]{"NONE", "VERTICAL", "HORIZONTAL"});
    public final PercentProperty waveSpread = new PercentProperty("wave-spread", 100, 5, 300,
            () -> this.waveMode.getValue() != 0);

    // ---------------------------------------------------------------- layout
    public final ModeProperty posX = new ModeProperty("position-x", 0, new String[]{"LEFT", "RIGHT"});
    public final ModeProperty posY = new ModeProperty("position-y", 0, new String[]{"TOP", "BOTTOM"});
    public final IntProperty offsetX = new IntProperty("offset-x", 2, 0, 255);
    public final IntProperty offsetY = new IntProperty("offset-y", 2, 0, 255);
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);
    public final ModeProperty sortMode = new ModeProperty("sort", 0, new String[]{"WIDTH", "ALPHABETICAL", "TOGGLE_ORDER"});
    public final IntProperty lineSpacing = new IntProperty("line-spacing", 0, 0, 10);

    // ------------------------------------------------------------ background
    public final ModeProperty backgroundMode = new ModeProperty("background", 1, new String[]{"NONE", "SOLID", "GRADIENT"});
    public final PercentProperty backgroundAlpha = new PercentProperty("background-alpha", 25,
            () -> this.backgroundMode.getValue() != 0);
    public final ModeProperty backgroundColorMode = new ModeProperty("background-color", 0,
            new String[]{"BLACK", "THEME", "CUSTOM"}, () -> this.backgroundMode.getValue() != 0);
    public final ColorProperty customBackgroundColor = new ColorProperty("custom-background-color", Color.BLACK.getRGB(),
            () -> this.backgroundMode.getValue() != 0 && this.backgroundColorMode.getValue() == 2);
    public final IntProperty backgroundThickness = new IntProperty("background-thickness", 2, 1, 15);
    public final IntProperty backgroundCurve = new IntProperty("background-curve", 3, 0, 10);
    public final BooleanProperty joinBands = new BooleanProperty("join-bands", true,
            () -> this.backgroundMode.getValue() != 0);

    // --------------------------------------------------------------- outline
    public final PercentProperty outlineThickness = new PercentProperty("outline-thickness", 0, 0, 5, null);
    public final ModeProperty outlineColorMode = new ModeProperty("outline-color", 0, new String[]{"THEME", "CUSTOM"},
            () -> this.outlineThickness.getValue() > 0);
    public final ColorProperty customOutlineColor = new ColorProperty("custom-outline-color", Color.WHITE.getRGB(),
            () -> this.outlineThickness.getValue() > 0 && this.outlineColorMode.getValue() == 1);

    // ------------------------------------------------------------- accent bar
    public final BooleanProperty showBar = new BooleanProperty("bar", true);
    public final IntProperty barWidth = new IntProperty("bar-width", 2, 1, 5, () -> this.showBar.getValue());

    // ------------------------------------------------------------------ glow
    public final BooleanProperty glowEnabled = new BooleanProperty("glow", false);
    public final FloatProperty glowSize = new FloatProperty("glow-size", 2.0F, 0.5F, 10.0F,
            () -> this.glowEnabled.getValue());

    // ------------------------------------------------------------- animation
    public final BooleanProperty animations = new BooleanProperty("animations", true);
    public final FloatProperty animationSpeed = new FloatProperty("animation-speed", 1.0F, 0.2F, 3.0F,
            () -> this.animations.getValue());

    // ------------------------------------------------------------------ text
    public final BooleanProperty shadow = new BooleanProperty("shadow", true);
    public final BooleanProperty suffixes = new BooleanProperty("suffixes", true);
    public final BooleanProperty lowerCase = new BooleanProperty("lower-case", false);

    // ------------------------------------------------------------- watermark
    public final BooleanProperty watermark = new BooleanProperty("watermark", false);
    public final TextProperty watermarkText = new TextProperty("watermark-text", "CrewX",
            () -> this.watermark.getValue());
    public final BooleanProperty watermarkFps = new BooleanProperty("watermark-fps", true,
            () -> this.watermark.getValue());

    // ------------------------------------------------------------------ misc
    public final BooleanProperty chatOutline = new BooleanProperty("chat-outline", true);
    public final BooleanProperty blinkTimer = new BooleanProperty("blink-timer", true);
    public final BooleanProperty toggleSound = new BooleanProperty("toggle-sounds", true);
    public final BooleanProperty toggleAlerts = new BooleanProperty("toggle-alerts", false);

    public HUD() {
        super("HUD", false, true);
    }

    // ================================================================ helpers

    private String getModuleName(Module module) {
        String moduleName = module.getName();
        if (this.lowerCase.getValue()) {
            moduleName = moduleName.toLowerCase(Locale.ROOT);
        }
        return moduleName;
    }

    private String[] getModuleSuffix(Module module) {
        String[] source = module.getSuffix();
        if (source == null) return new String[0];
        // copy before touching it - getSuffix() may hand back an array the module reuses
        String[] result = new String[source.length];
        for (int i = 0; i < source.length; i++) {
            String value = source[i] == null ? "" : source[i];
            result[i] = this.lowerCase.getValue() ? value.toLowerCase(Locale.ROOT) : value;
        }
        return result;
    }

    private int getModuleWidth(Module module) {
        return this.calculateStringWidth(this.getModuleName(module), this.getModuleSuffix(module));
    }

    private int calculateStringWidth(String string, String[] arr) {
        int width = mc.fontRendererObj.getStringWidth(string);
        if (this.suffixes.getValue()) {
            for (String str : arr) {
                width += (int) SUFFIX_GAP + mc.fontRendererObj.getStringWidth(str);
            }
        }
        return width;
    }

    private float getColorCycle(long time, double offsetUnits) {
        long speed = (long) (3000.0 / Math.pow(Math.min(Math.max(0.5F, this.colorSpeed.getValue()), 1.5F), 3.0));
        double delta = Math.abs(time - offsetUnits * 300.0) % speed;
        return 1.0F - (float) (delta / speed);
    }

    public Color getColor(long time) {
        return this.getColor(time, 0.0D);
    }

    public Color getColor(long time, long offset) {
        return this.getColor(time, (double) offset);
    }

    public Color getColor(long time, double offset) {
        Color color = Color.white;
        switch (this.colorMode.getValue()) {
            case 0:
                color = ColorUtil.fromHSB(this.getColorCycle(time, offset), 1.0F, 1.0F);
                break;
            case 1:
                color = ColorUtil.fromHSB(this.getColorCycle(time / 3L, 0.0D), 1.0F, 1.0F);
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

    /** Colour for module {@code index}, honouring VERTICAL wave (per-line offset). */
    private Color getLineColor(long time, int index) {
        if (this.waveMode.getValue() == 1) {
            return this.getColor(time, index * (this.waveSpread.getValue() / 100.0D));
        }
        return this.getColor(time);
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

    private int getBackgroundBaseColor(Color themeColor) {
        switch (this.backgroundColorMode.getValue()) {
            case 1:
                return ColorUtil.darker(themeColor, 0.25F).getRGB() & 0xFFFFFF;
            case 2:
                return this.customBackgroundColor.getValue() & 0xFFFFFF;
            default:
                return 0;
        }
    }

    private static int withAlpha(int rgb, float alpha) {
        int a = (int) (Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    // ================================================================== ticks

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.POST) {
            List<Module> visible = Myau.moduleManager.modules.values().stream()
                    .filter(module -> module.isEnabled() && !module.isHidden())
                    .collect(Collectors.<Module>toList());

            switch (this.sortMode.getValue()) {
                case 1:
                    visible.sort(Comparator.comparing((Module m) -> this.getModuleName(m).toLowerCase(Locale.ROOT)));
                    break;
                case 2:
                    break;
                default:
                    visible.sort(Comparator.<Module>comparingInt(this::getModuleWidth).reversed());
            }
            this.activeModules = visible;
        }
    }

    @Override
    public void onDisabled() {
        this.anims.clear();
    }

    // ================================================================= render

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

        long now = System.currentTimeMillis();
        float delta = Math.min(0.1F, (now - this.lastFrameTime) / 1000.0F);
        this.lastFrameTime = now;

        ScaledResolution sr = new ScaledResolution(mc);
        float scaleValue = this.scale.getValue();
        float screenW = sr.getScaledWidth() / scaleValue;
        float screenH = sr.getScaledHeight() / scaleValue;
        float fontH = mc.fontRendererObj.FONT_HEIGHT;
        float pad = this.backgroundThickness.getValue();
        float lineH = fontH + pad + this.lineSpacing.getValue();
        float curve = this.backgroundCurve.getValue();
        float bgAlpha = this.backgroundAlpha.getValue().floatValue() / 100.0F;
        if (this.backgroundMode.getValue() == 0) bgAlpha = 0.0F;

        boolean rightAlign = this.posX.getValue() == 1;
        boolean bottomAlign = this.posY.getValue() == 1;
        float baseX = this.offsetX.getValue();
        float baseY = this.offsetY.getValue();

        GlStateManager.pushMatrix();
        GlStateManager.scale(scaleValue, scaleValue, 1.0F);
        GlStateManager.disableDepth();

        // ---- watermark sits above the list and pushes it down
        float listTopPad = 0.0F;
        if (this.watermark.getValue()) {
            listTopPad = this.drawWatermark(now, screenW, screenH, baseX, baseY, rightAlign, bottomAlign, curve, bgAlpha);
        }

        // ---- advance animation state
        this.updateAnimations(delta);

        int count = this.activeModules.size();
        List<Module> renderOrder = new ArrayList<>(this.activeModules);
        for (Module m : this.anims.keySet()) {
            if (!this.activeModules.contains(m)) renderOrder.add(m);
        }

        float maxWidth = this.getMaxModuleWidth();

        for (Module module : renderOrder) {
            Anim anim = this.anims.get(module);
            if (anim == null || anim.progress <= 0.005F) continue;

            int index = this.activeModules.indexOf(module);
            String moduleName = this.getModuleName(module);
            String[] moduleSuffix = this.getModuleSuffix(module);
            float textW = mc.fontRendererObj.getStringWidth(moduleName);
            float totalWidth = this.calculateStringWidth(moduleName, moduleSuffix);

            // target vertical slot; fading-out entries keep whatever y they had
            if (index >= 0) {
                float targetY = bottomAlign
                        ? screenH - baseY - listTopPad - (count - index) * lineH
                        : baseY + listTopPad + index * lineH;
                if (!anim.placed) {
                    anim.y = targetY;
                    anim.placed = true;
                } else {
                    anim.y = this.approach(anim.y, targetY, delta);
                }
            }

            float ease = this.ease(anim.progress);
            float slide = (1.0F - ease) * (totalWidth + pad * 2.0F + 6.0F);

            float modX = rightAlign
                    ? screenW - baseX - totalWidth + slide
                    : baseX - slide;
            float modY = anim.y;

            Color themeColor = this.getLineColor(now, Math.max(index, 0));
            int lineColor = withAlpha(themeColor.getRGB(), ease);

            float boxX1 = modX - pad;
            float boxX2 = modX + totalWidth + pad;
            float boxY1 = modY - 1.0F;
            float boxY2 = modY + fontH - 1.0F;

            RenderUtil.enableRenderState();

            // glow
            if (this.glowEnabled.getValue() && bgAlpha > 0.0F) {
                float g = this.glowSize.getValue();
                int glowColor = withAlpha(themeColor.getRGB(), bgAlpha * 0.4F * ease);
                drawRoundedRect(boxX1 - g, boxY1 - g, boxX2 + g, boxY2 + g, curve + g, glowColor);
            }

            // connective band so stacked rows read as one panel
            if (this.joinBands.getValue() && bgAlpha > 0.0F && index > 0) {
                int bandColor = withAlpha(this.getBackgroundBaseColor(themeColor), bgAlpha * ease);
                float bandX1 = rightAlign ? boxX2 : boxX1;
                float bandX2 = rightAlign ? screenW - baseX + pad : baseX + maxWidth + pad;
                RenderUtil.drawRect(bandX1, modY - 1.0F, bandX2, modY + lineH - 1.0F, bandColor);
            }

            // background
            if (bgAlpha > 0.0F) {
                int base = this.getBackgroundBaseColor(themeColor);
                if (this.backgroundMode.getValue() == 2) {
                    int solid = withAlpha(base, bgAlpha * ease);
                    int fade = withAlpha(base, 0.0F);
                    if (rightAlign) {
                        drawHorizontalGradient(boxX1, boxY1, boxX2, boxY2, fade, solid);
                    } else {
                        drawHorizontalGradient(boxX1, boxY1, boxX2, boxY2, solid, fade);
                    }
                } else {
                    drawRoundedRect(boxX1, boxY1, boxX2, boxY2, curve, withAlpha(base, bgAlpha * ease));
                }
            }

            // outline
            if (this.outlineThickness.getValue() > 0 && bgAlpha > 0.0F) {
                Color outlineCol = this.getOutlineColor(themeColor);
                RenderUtil.setColor(withAlpha(outlineCol.getRGB(), ease));
                GL11.glLineWidth(this.outlineThickness.getValue() * scaleValue);
                GL11.glEnable(GL11.GL_LINE_SMOOTH);
                GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
                drawRoundedOutline(boxX1, boxY1, boxX2, boxY2, curve);
                GL11.glDisable(GL11.GL_LINE_SMOOTH);
                GlStateManager.resetColor();
            }

            // accent bar - now follows the alignment instead of always sitting on the left
            if (this.showBar.getValue()) {
                float bw = this.barWidth.getValue();
                float barX1 = rightAlign ? boxX2 : boxX1 - bw;
                float barX2 = rightAlign ? boxX2 + bw : boxX1;
                RenderUtil.drawRect(barX1, boxY1, barX2, boxY2, lineColor);
            }

            RenderUtil.disableRenderState();

            // text
            float textX = modX;
            if (this.waveMode.getValue() == 2) {
                this.drawWaveText(moduleName, textX, modY, now, Math.max(index, 0), ease);
            } else if (this.shadow.getValue()) {
                mc.fontRendererObj.drawStringWithShadow(moduleName, textX, modY, lineColor);
            } else {
                mc.fontRendererObj.drawString(moduleName, textX, modY, lineColor, false);
            }

            if (this.suffixes.getValue() && moduleSuffix.length > 0) {
                float suffixX = textX + textW + SUFFIX_GAP;
                int suffixColor = withAlpha(ChatColors.GRAY.toAwtColor(), ease);
                for (String string : moduleSuffix) {
                    if (this.shadow.getValue()) {
                        mc.fontRendererObj.drawStringWithShadow(string, suffixX, modY, suffixColor);
                    } else {
                        mc.fontRendererObj.drawString(string, suffixX, modY, suffixColor, false);
                    }
                    suffixX += mc.fontRendererObj.getStringWidth(string) + SUFFIX_GAP;
                }
            }
        }

        // ---- blink packet counter
        if (this.blinkTimer.getValue()) {
            BlinkModules blinkingModule = Myau.blinkManager.getBlinkingModule();
            if (blinkingModule != BlinkModules.NONE && blinkingModule != BlinkModules.AUTO_BLOCK) {
                long movementPacketSize = Myau.blinkManager.countMovement();
                if (movementPacketSize > 0L) {
                    String label = String.valueOf(movementPacketSize);
                    GlStateManager.enableBlend();
                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    mc.fontRendererObj.drawString(
                            label,
                            screenW / 2.0F - mc.fontRendererObj.getStringWidth(label) / 2.0F,
                            screenH / 5.0F * 3.0F,
                            this.getColor(now, (double) count).getRGB() & 0xFFFFFF | 0xBF000000,
                            this.shadow.getValue()
                    );
                    GlStateManager.disableBlend();
                }
            }
        }

        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    /** Draws the watermark pill and returns how much vertical room the module list must skip. */
    private float drawWatermark(long now, float screenW, float screenH, float baseX, float baseY,
                                boolean rightAlign, boolean bottomAlign, float curve, float bgAlpha) {
        String label = this.watermarkText.getValue();
        if (label == null || label.isEmpty()) label = "CrewX";
        String detail = "";
        if (this.watermarkFps.getValue()) {
            detail = " " + Minecraft.getDebugFPS() + " fps";
        }

        float fontH = mc.fontRendererObj.FONT_HEIGHT;
        float pad = this.backgroundThickness.getValue() + 2.0F;
        float labelW = mc.fontRendererObj.getStringWidth(label);
        float detailW = mc.fontRendererObj.getStringWidth(detail);
        float totalW = labelW + detailW;

        float x = rightAlign ? screenW - baseX - totalW : baseX;
        float y = bottomAlign ? screenH - baseY - fontH - 1.0F : baseY;

        Color themeColor = this.getColor(now);
        RenderUtil.enableRenderState();
        if (bgAlpha > 0.0F) {
            drawRoundedRect(x - pad, y - 2.0F, x + totalW + pad, y + fontH,
                    curve, withAlpha(this.getBackgroundBaseColor(themeColor), bgAlpha));
        }
        RenderUtil.disableRenderState();

        if (this.shadow.getValue()) {
            mc.fontRendererObj.drawStringWithShadow(label, x, y, themeColor.getRGB());
            if (!detail.isEmpty()) {
                mc.fontRendererObj.drawStringWithShadow(detail, x + labelW, y, ChatColors.GRAY.toAwtColor());
            }
        } else {
            mc.fontRendererObj.drawString(label, x, y, themeColor.getRGB(), false);
            if (!detail.isEmpty()) {
                mc.fontRendererObj.drawString(detail, x + labelW, y, ChatColors.GRAY.toAwtColor(), false);
            }
        }
        return fontH + 4.0F;
    }

    /** HORIZONTAL wave: each glyph gets its own point on the colour cycle. */
    private void drawWaveText(String text, float x, float y, long now, int index, float alpha) {
        float spread = this.waveSpread.getValue() / 100.0F;
        float cursor = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            Color c = this.getColor(now, index * spread + i * spread * 0.35D);
            int col = withAlpha(c.getRGB(), alpha);
            if (this.shadow.getValue()) {
                mc.fontRendererObj.drawStringWithShadow(ch, cursor, y, col);
            } else {
                mc.fontRendererObj.drawString(ch, cursor, y, col, false);
            }
            cursor += mc.fontRendererObj.getStringWidth(ch);
        }
    }

    // ============================================================= animation

    private void updateAnimations(float delta) {
        for (Module module : this.activeModules) {
            this.anims.computeIfAbsent(module, m -> new Anim());
        }
        java.util.Iterator<Map.Entry<Module, Anim>> it = this.anims.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Module, Anim> entry = it.next();
            Anim anim = entry.getValue();
            boolean visible = this.activeModules.contains(entry.getKey());
            if (!this.animations.getValue()) {
                anim.progress = visible ? 1.0F : 0.0F;
            } else {
                anim.progress = this.approach(anim.progress, visible ? 1.0F : 0.0F, delta);
            }
            if (!visible && anim.progress <= 0.005F) {
                it.remove();
            }
        }
    }

    /** Frame-rate independent exponential approach. */
    private float approach(float current, float target, float delta) {
        if (!this.animations.getValue()) return target;
        float factor = 1.0F - (float) Math.exp(-delta * 12.0F * this.animationSpeed.getValue());
        float next = current + (target - current) * factor;
        return Math.abs(target - next) < 0.002F ? target : next;
    }

    private float ease(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return 1.0F - (1.0F - t) * (1.0F - t);
    }

    private float getMaxModuleWidth() {
        float max = 0.0F;
        for (Module m : this.activeModules) {
            float w = this.calculateStringWidth(this.getModuleName(m), this.getModuleSuffix(m));
            if (w > max) max = w;
        }
        return max;
    }

    // ================================================================ shapes

    private static void drawHorizontalGradient(float x1, float y1, float x2, float y2, int left, int right) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(x1, y2, 0.0D).color((left >> 16 & 255) / 255.0F, (left >> 8 & 255) / 255.0F, (left & 255) / 255.0F, (left >>> 24) / 255.0F).endVertex();
        wr.pos(x2, y2, 0.0D).color((right >> 16 & 255) / 255.0F, (right >> 8 & 255) / 255.0F, (right & 255) / 255.0F, (right >>> 24) / 255.0F).endVertex();
        wr.pos(x2, y1, 0.0D).color((right >> 16 & 255) / 255.0F, (right >> 8 & 255) / 255.0F, (right & 255) / 255.0F, (right >>> 24) / 255.0F).endVertex();
        wr.pos(x1, y1, 0.0D).color((left >> 16 & 255) / 255.0F, (left >> 8 & 255) / 255.0F, (left & 255) / 255.0F, (left >>> 24) / 255.0F).endVertex();
        tessellator.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    private static void drawRoundedRect(float x1, float y1, float x2, float y2, float radius, int color) {
        radius = Math.min(radius, Math.min((x2 - x1) / 2.0F, (y2 - y1) / 2.0F));
        if (radius <= 0.0F) {
            RenderUtil.drawRect(x1, y1, x2, y2, color);
            return;
        }
        float r = radius;
        int quarter = Math.max(4, (int) (r * 2.0F) / 4 + 2);
        RenderUtil.setColor(color);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f((x1 + x2) / 2.0F, (y1 + y2) / 2.0F);
        appendArc(x1 + r, y1 + r, r, Math.PI, quarter);
        appendArc(x2 - r, y1 + r, r, -Math.PI / 2.0, quarter);
        appendArc(x2 - r, y2 - r, r, 0.0, quarter);
        appendArc(x1 + r, y2 - r, r, Math.PI / 2.0, quarter);
        GL11.glVertex2f(x1, y1 + r);
        GL11.glEnd();
        GlStateManager.resetColor();
    }

    private static void drawRoundedOutline(float x1, float y1, float x2, float y2, float radius) {
        radius = Math.min(radius, Math.min((x2 - x1) / 2.0F, (y2 - y1) / 2.0F));
        if (radius <= 0.0F) {
            GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glVertex2f(x1, y1);
            GL11.glVertex2f(x2, y1);
            GL11.glVertex2f(x2, y2);
            GL11.glVertex2f(x1, y2);
            GL11.glEnd();
            return;
        }
        float r = radius;
        int quarter = Math.max(4, (int) (r * 2.0F) / 4 + 2);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        appendArc(x1 + r, y1 + r, r, Math.PI, quarter);
        appendArc(x2 - r, y1 + r, r, -Math.PI / 2.0, quarter);
        appendArc(x2 - r, y2 - r, r, 0.0, quarter);
        appendArc(x1 + r, y2 - r, r, Math.PI / 2.0, quarter);
        GL11.glEnd();
    }

    private static void appendArc(float cx, float cy, float r, double startAngle, int steps) {
        for (int i = 0; i <= steps; i++) {
            double angle = startAngle + (Math.PI / 2.0) * ((double) i / steps);
            GL11.glVertex2f((float) (cx + Math.cos(angle) * r), (float) (cy + Math.sin(angle) * r));
        }
    }
}
