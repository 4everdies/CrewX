package crewx.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;

import java.util.IdentityHashMap;
import java.util.Map;

public final class CrewXTheme {
    public static final int BACKGROUND_TOP = 0xFF02040A;
    public static final int BACKGROUND_BOTTOM = 0xFF070A14;
    public static final int BUTTON_IDLE = 0xB9161B2B;
    public static final int BUTTON_HOVER = 0xDD252D4A;
    public static final int BUTTON_BORDER = 0x80394766;
    public static final int BUTTON_BORDER_HOVER = 0xCC6F75C8;
    public static final int ACCENT = 0xFF7770D9;
    public static final int TEXT = 0xFFF1F2F8;
    public static final int MUTED = 0xFF9CA2BC;
    private static final Map<GuiButton, Float> HOVER_PROGRESS = new IdentityHashMap<GuiButton, Float>();

    private CrewXTheme() {
    }

    public static void drawBackground(int width, int height) {
        GuiRender.gradientV(0.0, 0.0, width, height, BACKGROUND_TOP, BACKGROUND_BOTTOM);

        float time = (System.currentTimeMillis() % 14000L) / 14000.0f;
        float cx = width * 0.50f + (float) Math.sin(time * Math.PI * 2.0) * width * 0.10f;
        float cy = height * 0.60f;
        float baseRadius = Math.min(width, height) * 0.20f;

        for (int i = 17; i >= 1; i--) {
            float radius = baseRadius + i * 10.0f + (float) Math.sin(time * Math.PI * 2.0 + i * 0.28) * 3.0f;
            int alpha = 5 + (17 - i) * 2;
            int color = (alpha << 24) | (i % 3 == 0 ? 0x4A63B5 : i % 3 == 1 ? 0x6D45A8 : 0x1676A8);
            GuiRender.circleOutline(cx, cy, radius, 7.0f, color);
        }

        GuiRender.gradientH(0.0, 0.0, width, height, 0x54000000, 0x25000000);
        drawBranding(width, height);
    }

    public static void drawBranding(int width, int height) {
        GuiRender.textShadow("discord.gg/crackcrew", 12.0, 10.0, 0xD0C7CBEA);
        GuiRender.textShadow("Made by Crowly & 4ever", 12.0, height - 18.0, 0x886D75A2);
    }

    public static void drawMainTitle(int width, int height) {
        Minecraft minecraft = Minecraft.getMinecraft();
        float centerX = width / 2.0f;
        float titleY = height / 2.0f - 116.0f;
        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, titleY, 0.0f);
        GlStateManager.scale(2.60f, 2.60f, 1.0f);
        GuiRender.textCentered("CrewX", 0.0, -ClientFont.getHeight() / 2.0, TEXT);
        GlStateManager.popMatrix();
    }

    private static float updateHover(GuiButton button, boolean hovered) {
        Float previous = HOVER_PROGRESS.get(button);
        float current = previous == null ? 0.0f : previous;
        float target = hovered && button.enabled ? 1.0f : 0.0f;
        current += (target - current) * 0.28f;
        if (Math.abs(target - current) < 0.01f) current = target;
        HOVER_PROGRESS.put(button, current);
        return current;
    }

    public static void drawButton(GuiButton button, int mouseX, int mouseY) {
        if (!button.visible) return;

        boolean hovered = mouseX >= button.xPosition && mouseY >= button.yPosition
                && mouseX < button.xPosition + button.width && mouseY < button.yPosition + button.height;
        float hover = updateHover(button, hovered);
        int background = button.enabled
                ? GuiRender.mix(BUTTON_IDLE, BUTTON_HOVER, hover)
                : 0x8010141E;
        int border = button.enabled
                ? GuiRender.mix(BUTTON_BORDER, BUTTON_BORDER_HOVER, hover)
                : 0x50313A4F;
        int text = button.enabled
                ? GuiRender.mix(0xFFD1D4E5, TEXT, hover)
                : 0xFF6E7388;

        GuiRender.roundedRect(button.xPosition, button.yPosition, button.width, button.height, 6.0, background);
        GuiRender.roundedOutline(button.xPosition, button.yPosition, button.width, button.height, 6.0, 1.0f, border);
        if (hover > 0.01f) {
            GuiRender.roundedRect(button.xPosition + 2.0, button.yPosition + 2.0,
                    2.0 + 12.0 * hover, button.height - 4.0, 2.0,
                    GuiRender.alpha(ACCENT, 0.35f * hover));
            GuiRender.roundedOutline(button.xPosition - 1.0, button.yPosition - 1.0,
                    button.width + 2.0, button.height + 2.0, 7.0, 1.0f,
                    GuiRender.alpha(BUTTON_BORDER_HOVER, hover * 0.55f));
        }
        double textOffset = hover * 1.5;
        GuiRender.textCentered(button.displayString, button.xPosition + button.width / 2.0 + textOffset,
                button.yPosition + (button.height - ClientFont.getHeight()) / 2.0, text);
    }

    public static void drawScreenBackground(int width, int height) {
        drawBackground(width, height);
    }
}
