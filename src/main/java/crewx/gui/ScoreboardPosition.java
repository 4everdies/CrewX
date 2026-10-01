package crewx.gui;

import crewx.CrewX;
import crewx.module.modules.render.HUD;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;

public final class ScoreboardPosition {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static float baseX;
    private static float baseY;
    private static float panelWidth;
    private static float panelHeight;
    private static boolean boundsAvailable;
    private static boolean dragging;
    private static int lastMouseX;
    private static int lastMouseY;
    private static int startingOffsetX;
    private static int startingOffsetY;

    private ScoreboardPosition() {
    }

    public static int getOffsetX() {
        HUD hud = getHud();
        return hud == null ? 0 : hud.scoreboardOffsetX.getValue();
    }

    public static int getOffsetY() {
        HUD hud = getHud();
        return hud == null ? 0 : hud.scoreboardOffsetY.getValue();
    }

    public static void updateBounds(float x, float y, float width, float height) {
        baseX = x;
        baseY = y;
        panelWidth = width;
        panelHeight = height;
        boundsAvailable = width > 0.0F && height > 0.0F;
    }

    public static void clearBounds() {
        boundsAvailable = false;
    }

    public static boolean beginDrag(int mouseX, int mouseY, int button) {
        if (button != 1 || !(MC.currentScreen instanceof GuiChat) || !boundsAvailable) return false;
        float left = baseX + getOffsetX();
        float top = baseY + getOffsetY();
        if (mouseX < left || mouseX > left + panelWidth || mouseY < top || mouseY > top + panelHeight) return false;

        dragging = true;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        startingOffsetX = getOffsetX();
        startingOffsetY = getOffsetY();
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
        int minX = (int) Math.ceil(-baseX);
        int minY = (int) Math.ceil(-baseY);
        int maxX = (int) Math.floor(resolution.getScaledWidth() - baseX - panelWidth);
        int maxY = (int) Math.floor(resolution.getScaledHeight() - baseY - panelHeight);
        if (maxX < minX) maxX = minX;
        if (maxY < minY) maxY = minY;

        int offsetX = clamp(startingOffsetX + mouseX - lastMouseX, minX, maxX);
        int offsetY = clamp(startingOffsetY + mouseY - lastMouseY, minY, maxY);
        hud.scoreboardOffsetX.setValue(offsetX);
        hud.scoreboardOffsetY.setValue(offsetY);
    }

    public static void endDrag() {
        dragging = false;
    }

    private static HUD getHud() {
        if (CrewX.moduleManager == null) return null;
        Object module = CrewX.moduleManager.modules.get(HUD.class);
        return module instanceof HUD ? (HUD) module : null;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
