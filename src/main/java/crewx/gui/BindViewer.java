package crewx.gui;

import crewx.CrewX;
import crewx.clickgui.render.RoundedUtils;
import crewx.module.Module;
import crewx.module.modules.render.HUD;
import crewx.util.KeyBindUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Compact, draggable list of configured module binds: a glass card with keycaps, in the client's style. */
public final class BindViewer {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final float PAD = 8.0F;
    private static final float HEADER_HEIGHT = 22.0F;
    private static final float ROW_HEIGHT = 15.0F;
    private static final float KEY_SCALE = 0.82F;
    private static final java.util.Map<Module, float[]> ACTIVE_ANIM = new java.util.WeakHashMap<Module, float[]>();
    private static long lastFrame;
    private static boolean dragging;
    private static int lastMouseX;
    private static int lastMouseY;
    private static int startOffsetX;
    private static int startOffsetY;
    private static float lastX;
    private static float lastY;
    private static float lastWidth;
    private static float lastHeight;

    private BindViewer() {
    }

    public static void render(HUD hud) {
        if (hud == null || !hud.isEnabled() || !hud.bindViewer.getValue()
                || MC.gameSettings.showDebugInfo || CrewX.moduleManager == null) {
            lastWidth = 0.0F;
            lastHeight = 0.0F;
            return;
        }
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0L ? 0.016F : Math.min(0.1F, (now - lastFrame) / 1000.0F);
        lastFrame = now;

        List<Module> bound = new ArrayList<Module>();
        for (Module module : CrewX.moduleManager.modules.values()) {
            if (module.getKey() != 0 && module != hud) bound.add(module);
        }
        bound.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));

        ScaledResolution resolution = new ScaledResolution(MC);
        String title = "Keybinds";
        float width = PAD + 14.0F + 6.0F + ClientFont.getStringWidthFloat(title) + 30.0F + PAD;
        for (Module module : bound) {
            width = Math.max(width, PAD + 10.0F + ClientFont.getStringWidthFloat(module.getName())
                    + 12.0F + keyWidth(KeyBindUtil.getKeyName(module.getKey())) + PAD);
        }
        width = Math.max(width, 120.0F);
        float rows = Math.max(1, bound.size());
        float height = HEADER_HEIGHT + rows * ROW_HEIGHT + 6.0F;
        boolean right = hud.bindViewerSide.getValue() == 1;
        float x = (right ? resolution.getScaledWidth() - width - 8.0F : 8.0F)
                + hud.bindViewerOffsetX.getValue();
        float y = 8.0F + hud.bindViewerOffsetY.getValue();
        x = clamp(x, 2.0F, Math.max(2.0F, resolution.getScaledWidth() - width - 2.0F));
        y = clamp(y, 2.0F, Math.max(2.0F, resolution.getScaledHeight() - height - 2.0F));
        lastX = x;
        lastY = y;
        lastWidth = width;
        lastHeight = height;
        int accent = hud.getColor(now).getRGB() | 0xFF000000;

        GlStateManager.pushMatrix();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        // soft shadow, glass body, hairline edge
        for (int i = 3; i >= 1; i--) {
            RoundedUtils.drawRoundedRect(x - i, y - i + 1.0F, width + i * 2.0F, height + i * 2.0F, 0x12000000, 7.0F + i);
        }
        BackdropBlur.drawRoundedPanel(x, y, width, height, 7.0F, 0xB80F1013);
        GuiRender.roundedOutline(x, y, width, height, 7.0, 1.0F, 0x18FFFFFF);

        // header: keyboard glyph, title, count
        drawKeyboardGlyph(x + PAD, y + 6.5F, accent);
        ClientFont.drawStringStyled(title, x + PAD + 20.0F, y + (HEADER_HEIGHT - ClientFont.getHeight()) / 2.0F + 0.5F,
                offset -> 0xFFFFFFFF, 0.25F, 0, 0.0F);
        String count = String.valueOf(bound.size());
        float countW = ClientFont.getStringWidthFloat(count) * 0.85F + 10.0F;
        float countX = x + width - PAD - countW;
        float countY = y + (HEADER_HEIGHT - 10.0F) / 2.0F;
        RoundedUtils.drawRoundedRect(countX, countY, countW, 10.0F, GuiRender.alpha(accent, 0.18F), 5.0F);
        drawScaled(count, countX + 5.0F, countY + 1.6F, 0.85F, GuiRender.mix(accent, 0xFFFFFFFF, 0.35F));
        GuiRender.rect(x + PAD, y + HEADER_HEIGHT - 1.0, width - PAD * 2.0F, 1.0, 0x14FFFFFF);

        float rowY = y + HEADER_HEIGHT + 3.0F;
        if (bound.isEmpty()) {
            drawScaled("No binds yet", x + PAD, rowY + 3.0F, 0.9F, 0xFF8C9099);
        }
        for (Module module : bound) {
            float[] anim = ACTIVE_ANIM.get(module);
            if (anim == null) {
                anim = new float[]{module.isEnabled() ? 1.0F : 0.0F};
                ACTIVE_ANIM.put(module, anim);
            }
            float target = module.isEnabled() ? 1.0F : 0.0F;
            anim[0] += (target - anim[0]) * (1.0F - (float) Math.exp(-dt * 14.0F));
            float on = anim[0];
            float textY = rowY + (ROW_HEIGHT - ClientFont.getHeight()) / 2.0F;
            // status dot: accent with a soft halo when the module is on
            float dotX = x + PAD + 2.5F;
            float dotY = rowY + ROW_HEIGHT / 2.0F;
            if (on > 0.02F) {
                RoundedUtils.drawRoundedRect(dotX - 3.5F, dotY - 3.5F, 7.0F, 7.0F, GuiRender.alpha(accent, 0.25F * on), 3.5F);
            }
            RoundedUtils.drawRoundedRect(dotX - 2.0F, dotY - 2.0F, 4.0F, 4.0F, GuiRender.mix(0xFF4A4D55, accent, on), 2.0F);
            ClientFont.drawString(module.getName(), x + PAD + 10.0F, textY, GuiRender.mix(0xFFA4A7AE, 0xFFFFFFFF, on), false);
            String key = KeyBindUtil.getKeyName(module.getKey());
            float keyW = keyWidth(key);
            drawKeycap(key, x + width - PAD - keyW, rowY + 1.5F, keyW, ROW_HEIGHT - 3.0F, on, accent);
            rowY += ROW_HEIGHT;
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    private static float keyWidth(String key) {
        return Math.max(14.0F, ClientFont.getStringWidthFloat(key) * KEY_SCALE + 9.0F);
    }

    /** A keyboard key: dark cap with a lighter rim and a thicker bottom edge; tinted while its module is on. */
    private static void drawKeycap(String key, float x, float y, float w, float h, float on, int accent) {
        RoundedUtils.drawRoundedRect(x, y + 1.0F, w, h, GuiRender.mix(0xFF0A0B0D, GuiRender.alpha(accent, 0.55F), on * 0.5F), 3.5F);
        RoundedUtils.drawRoundedRect(x, y, w, h - 0.5F, GuiRender.mix(0xFF1D1E23, GuiRender.mix(0xFF1D1E23, accent, 0.28F), on), 3.5F);
        GuiRender.roundedOutline(x, y, w, h - 0.5F, 3.5, 1.0F, GuiRender.mix(0x1EFFFFFF, GuiRender.alpha(accent, 0.6F), on));
        float textW = ClientFont.getStringWidthFloat(key) * KEY_SCALE;
        drawScaled(key, x + (w - textW) / 2.0F, y + (h - 0.5F - ClientFont.getHeight() * KEY_SCALE) / 2.0F + 0.3F, KEY_SCALE,
                GuiRender.mix(0xFFC9CBD1, 0xFFFFFFFF, on));
    }

    /** Small keyboard glyph for the header. */
    private static void drawKeyboardGlyph(float x, float y, int accent) {
        RoundedUtils.drawRoundedRect(x, y, 15.0F, 9.0F, GuiRender.alpha(accent, 0.22F), 2.5F);
        GuiRender.roundedOutline(x, y, 15.0F, 9.0F, 2.5, 1.0F, GuiRender.alpha(accent, 0.85F));
        for (int i = 0; i < 4; i++) {
            RoundedUtils.drawRoundedRect(x + 2.5F + i * 2.7F, y + 2.2F, 1.6F, 1.5F, 0xFFE6E7EA, 0.5F);
        }
        RoundedUtils.drawRoundedRect(x + 3.5F, y + 5.3F, 8.0F, 1.5F, 0xFFE6E7EA, 0.7F);
    }

    private static void drawScaled(String text, float x, float y, float scale, int color) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        ClientFont.drawString(text, 0.0F, 0.0F, color, false);
        GlStateManager.popMatrix();
    }

    public static boolean beginDrag(int mouseX, int mouseY, int button) {
        if (button != 0 || !(MC.currentScreen instanceof GuiChat) || lastWidth <= 0.0F) return false;
        if (mouseX < lastX || mouseX > lastX + lastWidth || mouseY < lastY || mouseY > lastY + lastHeight) return false;
        HUD hud = getHud();
        if (hud == null || !hud.bindViewer.getValue()) return false;
        dragging = true;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        startOffsetX = hud.bindViewerOffsetX.getValue();
        startOffsetY = hud.bindViewerOffsetY.getValue();
        return true;
    }

    public static boolean isDragging() {
        return dragging;
    }

    public static void dragTo(int mouseX, int mouseY) {
        if (!dragging) return;
        HUD hud = getHud();
        if (hud == null) return;
        ScaledResolution resolution = new ScaledResolution(MC);
        int nextX = startOffsetX + mouseX - lastMouseX;
        int nextY = startOffsetY + mouseY - lastMouseY;
        int minX = (int) (8.0F + lastWidth - resolution.getScaledWidth());
        int maxX = resolution.getScaledWidth() - 16;
        int minY = 2 - 8;
        int maxY = resolution.getScaledHeight() - (int) lastHeight - 10;
        hud.bindViewerOffsetX.setValue((int) clamp(nextX, minX, maxX));
        hud.bindViewerOffsetY.setValue((int) clamp(nextY, minY, maxY));
    }

    public static void endDrag() {
        dragging = false;
    }

    private static HUD getHud() {
        if (CrewX.moduleManager == null) return null;
        Object module = CrewX.moduleManager.modules.get(HUD.class);
        return module instanceof HUD ? (HUD) module : null;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
