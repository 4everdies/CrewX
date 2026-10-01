package crewx.gui;

import crewx.CrewX;
import crewx.clickgui.render.RoundedUtils;
import crewx.module.modules.render.HUD;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;

public final class ChatPosition {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static boolean dragging;
    private static int lastMouseX;
    private static int lastMouseY;
    private static int initialOffsetX;
    private static int initialOffsetY;

    private ChatPosition() {
    }

    public static int getOffsetX() {
        HUD hud = getHud();
        return hud == null ? 0 : hud.chatOffsetX.getValue();
    }

    public static int getOffsetY() {
        HUD hud = getHud();
        return hud == null ? 0 : hud.chatOffsetY.getValue();
    }

    public static boolean isDragging() {
        return dragging;
    }

    public static boolean beginDrag(int mouseX, int mouseY, int button) {
        if (button != 0 || !(MC.currentScreen instanceof GuiChat)) return false;
        boolean handleHit = isHandleHit(mouseX, mouseY);
        boolean modifiedPanelHit = (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) && isPanelHit(mouseX, mouseY);
        if (!handleHit && !modifiedPanelHit) return false;

        dragging = true;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        initialOffsetX = getOffsetX();
        initialOffsetY = getOffsetY();
        return true;
    }

    public static void dragTo(int mouseX, int mouseY) {
        if (!dragging) return;
        HUD hud = getHud();
        if (hud == null) return;
        ScaledResolution resolution = new ScaledResolution(MC);
        GuiNewChat chat = MC.ingameGUI.getChatGUI();
        int width = chat.getChatWidth() + 8;
        int rows = Math.max(1, chat.getLineCount());
        float scale = chat.getChatScale();
        float baseBottom = resolution.getScaledHeight() - 28.0F;
        float panelHeight = rows * 9.0F * scale + 8.0F;
        int minX = -2;
        int maxX = Math.max(minX, resolution.getScaledWidth() - width - 4);
        int minY = (int) Math.ceil(2.0F - (baseBottom - panelHeight));
        int maxY = Math.max(minY, (int) (resolution.getScaledHeight() - 14.0F - baseBottom));
        int newX = clamp(initialOffsetX + mouseX - lastMouseX, minX, maxX);
        int newY = clamp(initialOffsetY + mouseY - lastMouseY, minY, maxY);
        hud.chatOffsetX.setValue(newX);
        hud.chatOffsetY.setValue(newY);
    }

    public static void endDrag() {
        dragging = false;
    }

    public static void renderHandle() {
        if (!(MC.currentScreen instanceof GuiChat) || MC.ingameGUI == null) return;
        ScaledResolution resolution = new ScaledResolution(MC);
        GuiNewChat chat = MC.ingameGUI.getChatGUI();
        int rows = Math.max(1, chat.getLineCount());
        float scale = chat.getChatScale();
        float left = 2.0F + getOffsetX();
        float bottom = resolution.getScaledHeight() - 28.0F + getOffsetY();
        float top = bottom - rows * 9.0F * scale;
        float x = Math.max(2.0F, Math.min(resolution.getScaledWidth() - 30.0F, left));
        float y = Math.max(2.0F, top - 9.0F);
        RoundedUtils.drawRoundedRect(x, y, 26.0F, 6.0F, 0xB8121212, 3.0F);
        int grip = 0xFF777777;
        RoundedUtils.drawRoundedRect(x + 7.0F, y + 2.0F, 3.0F, 2.0F, grip, 1.0F);
        RoundedUtils.drawRoundedRect(x + 12.0F, y + 2.0F, 3.0F, 2.0F, grip, 1.0F);
        RoundedUtils.drawRoundedRect(x + 17.0F, y + 2.0F, 3.0F, 2.0F, grip, 1.0F);
    }

    private static boolean isHandleHit(int mouseX, int mouseY) {
        if (MC.ingameGUI == null) return false;
        ScaledResolution resolution = new ScaledResolution(MC);
        GuiNewChat chat = MC.ingameGUI.getChatGUI();
        int rows = Math.max(1, chat.getLineCount());
        float top = resolution.getScaledHeight() - 28.0F + getOffsetY() - rows * 9.0F * chat.getChatScale();
        float x = Math.max(2.0F, Math.min(resolution.getScaledWidth() - 30.0F, 2.0F + getOffsetX()));
        float y = Math.max(2.0F, top - 9.0F);
        return mouseX >= x && mouseX <= x + 26.0F && mouseY >= y && mouseY <= y + 6.0F;
    }

    private static boolean isPanelHit(int mouseX, int mouseY) {
        if (MC.ingameGUI == null) return false;
        ScaledResolution resolution = new ScaledResolution(MC);
        GuiNewChat chat = MC.ingameGUI.getChatGUI();
        int rows = Math.max(1, chat.getLineCount());
        float scale = chat.getChatScale();
        float left = 2.0F + getOffsetX() - 3.0F;
        float right = left + chat.getChatWidth() + 10.0F;
        float bottom = resolution.getScaledHeight() - 28.0F + getOffsetY() + 2.0F;
        float top = bottom - rows * 9.0F * scale - 8.0F;
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
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
