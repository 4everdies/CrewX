package crewx.gui;

import crewx.CrewX;
import crewx.clickgui.render.RoundedUtils;
import crewx.module.Module;
import crewx.module.modules.render.HUD;
import crewx.util.KeyBindUtil;
import crewx.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Compact, draggable list of configured module binds. */
public final class BindViewer {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final float PAD = 7.0F;
    private static final float HEADER_HEIGHT = 18.0F;
    private static final float ROW_HEIGHT = 14.0F;
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

        List<Module> bound = new ArrayList<Module>();
        for (Module module : CrewX.moduleManager.modules.values()) {
            if (module.getKey() != 0 && module != hud) bound.add(module);
        }
        bound.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));

        ScaledResolution resolution = new ScaledResolution(MC);
        float width = 116.0F;
        for (Module module : bound) {
            String key = "[" + KeyBindUtil.getKeyName(module.getKey()) + "]";
            width = Math.max(width, PAD + ClientFont.getStringWidthFloat(module.getName())
                    + 9.0F + ClientFont.getStringWidthFloat(key) + PAD);
        }
        float height = HEADER_HEIGHT + bound.size() * ROW_HEIGHT + 5.0F;
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

        GlStateManager.pushMatrix();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        BackdropBlur.drawRoundedPanel(x, y, width, height, 5.0F, 0xB516171C);

        int titleColor = hud.getColor(System.currentTimeMillis()).getRGB();
        ClientFont.drawStringWithShadow("Binds", x + PAD, y + 5.0F, titleColor);
        drawKeyboardIcon(x + width - 22.0F, y + 5.0F, 14.0F, 8.0F);

        float rowY = y + HEADER_HEIGHT + 1.0F;
        for (Module module : bound) {
            boolean active = module.isEnabled();
            int nameColor = active ? 0xFF55D889 : 0xFFE2E2E6;
            int keyColor = active ? 0xFF6C9DFF : 0xFFB8B8C2;
            String key = "[" + KeyBindUtil.getKeyName(module.getKey()) + "]";
            ClientFont.drawStringWithShadow(module.getName(), x + PAD, rowY, nameColor);
            ClientFont.drawStringWithShadow(key, x + width - PAD - ClientFont.getStringWidthFloat(key), rowY, keyColor);
            rowY += ROW_HEIGHT;
        }

        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    private static void drawKeyboardIcon(float x, float y, float width, float height) {
        RenderUtil.enableRenderState();
        RenderUtil.drawRect(x, y, x + width, y + height, 0xAA8F949D);
        RenderUtil.drawRect(x + 1.0F, y + 1.0F, x + width - 1.0F, y + height - 1.0F, 0xDD252830);
        for (int i = 0; i < 4; i++) {
            RenderUtil.drawRect(x + 2.0F + i * 3.0F, y + 2.0F, x + 4.0F + i * 3.0F, y + 3.5F, 0xFFD7D9DE);
        }
        RenderUtil.drawRect(x + 3.0F, y + 5.0F, x + width - 3.0F, y + 6.5F, 0xFFD7D9DE);
        RenderUtil.disableRenderState();
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
