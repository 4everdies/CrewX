package crewx.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;

import java.util.Map;
import java.lang.reflect.Field;

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
    /** Hover / press animation per button. Weak keys: the buttons of closed screens are not kept alive. */
    private static final Map<GuiButton, ButtonAnim> BUTTON_STATE = new java.util.WeakHashMap<GuiButton, ButtonAnim>();

    private static final class ButtonAnim {
        float hover;
        float press;
        long last = Minecraft.getSystemTime();
    }
    private static final Field BUTTON_HEIGHT_FIELD = findButtonHeight();

    private CrewXTheme() {
    }

    private static Field findButtonHeight() {
        for (String name : new String[]{"height", "g"}) {
            try {
                Field field = GuiButton.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException | SecurityException ignored) {
                // Try the other name (MCP / Badlion Notch).
            }
        }
        return null;
    }

    private static int buttonHeight(GuiButton button) {
        if (button instanceof CrewXMainMenu.MenuButton) {
            return ((CrewXMainMenu.MenuButton) button).menuHeight();
        }
        if (BUTTON_HEIGHT_FIELD != null) {
            try {
                return BUTTON_HEIGHT_FIELD.getInt(button);
            } catch (IllegalAccessException ignored) {
                // Preserve rendering if a client refuses reflective access.
            }
        }
        return 20;
    }

    /** Space background (Earth at sunrise, mouse parallax) shared by every out-of-game CrewX screen. */
    public static void drawBackground(int width, int height) {
        drawBackground(width, height, true);
    }

    public static void drawBackground(int width, int height, boolean branding) {
        crewx.gui.menu.SpaceBackground.render(width, height);
        if (branding) drawBranding(width, height);
    }

    public static void drawBranding(int width, int height) {
        GuiRender.text("discord.gg/crackcrew", 10.0, 10.0, 0x99C7CBD6);
        GuiRender.text("Made by Crowly, Forever & Shinra", 10.0, height - 16.0, 0x667A7F8C);
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

    private static ButtonAnim buttonState(GuiButton button, boolean hovered, boolean pressed) {
        ButtonAnim state = BUTTON_STATE.get(button);
        if (state == null) {
            state = new ButtonAnim();
            BUTTON_STATE.put(button, state);
        }
        long now = Minecraft.getSystemTime();
        float dt = Math.max(0.0F, Math.min(0.1F, (now - state.last) / 1000.0F));
        state.last = now;
        // frame-rate independent easing
        state.hover = approach(state.hover, hovered && button.enabled ? 1.0F : 0.0F, dt, 16.0F);
        state.press = approach(state.press, pressed ? 1.0F : 0.0F, dt, 24.0F);
        return state;
    }

    private static float approach(float current, float target, float dt, float speed) {
        float next = current + (target - current) * (1.0F - (float) Math.exp(-dt * speed));
        return Math.abs(target - next) < 0.005F ? target : next;
    }

    /**
     * Every GuiButton of the client (pause menu, options, dialogs...): a dark rounded pill with a hairline edge, in
     * the same neutral tones as the ClickGUI and the Alt Manager. Hover lights the edge with the theme accent,
     * pressing darkens it, disabled buttons fade out.
     */
    public static void drawButton(GuiButton button, int mouseX, int mouseY) {
        if (!button.visible) return;
        // Badlion makes GuiButton.width/height protected. The menu's own
        // subclass exposes its bounds without reflective access per frame.
        int buttonWidth = button instanceof CrewXMainMenu.MenuButton
                ? ((CrewXMainMenu.MenuButton) button).menuWidth()
                : button.getButtonWidth();
        int buttonHeight = buttonHeight(button);

        boolean hovered = mouseX >= button.xPosition && mouseY >= button.yPosition
                && mouseX < button.xPosition + buttonWidth && mouseY < button.yPosition + buttonHeight;
        boolean pressed = hovered && button.enabled && org.lwjgl.input.Mouse.isButtonDown(0);
        ButtonAnim state = buttonState(button, hovered, pressed);
        float hover = state.hover;
        float press = state.press;
        int accent = accent();

        double x = button.xPosition;
        double y = button.yPosition;
        double radius = Math.min(5.5, buttonHeight / 2.0 - 1.0);
        int background;
        int edge;
        int text;
        if (button.enabled) {
            background = GuiRender.mix(GuiRender.mix(0xD9131418, 0xE61C1D23, hover), 0xF0101114, press);
            edge = GuiRender.mix(0x24FFFFFF, GuiRender.alpha(accent, 0.75F), hover);
            text = GuiRender.mix(0xFFD3D5DB, 0xFFFFFFFF, hover);
        } else {
            background = 0x990F1013;
            edge = 0x14FFFFFF;
            text = 0xFF5F636C;
        }
        // soft drop shadow, body, hairline edge, faint top highlight
        GuiRender.roundedRect(x, y + 1.0, buttonWidth, buttonHeight, radius + 0.5, button.enabled ? 0x38000000 : 0x1E000000);
        GuiRender.roundedRect(x, y, buttonWidth, buttonHeight, radius, background);
        GuiRender.roundedOutline(x, y, buttonWidth, buttonHeight, radius, 1.0F, edge);
        if (button.enabled && press < 0.5F) {
            GuiRender.rect(x + radius, y + 1.0, buttonWidth - radius * 2.0, 1.0, GuiRender.alpha(0x12FFFFFF, 1.0F - press));
        }
        if (hover > 0.01F) {
            // gentle accent glow around the edge
            GuiRender.roundedOutline(x - 1.0, y - 1.0, buttonWidth + 2.0, buttonHeight + 2.0, radius + 1.0, 1.0F,
                    GuiRender.alpha(accent, 0.22F * hover * (1.0F - press)));
        }
        GuiRender.textCentered(button.displayString, x + buttonWidth / 2.0,
                y + (buttonHeight - ClientFont.getHeight()) / 2.0 + 0.5 * press, text);
    }

    /** Theme accent (HUD colour), as the ClickGUI uses it. */
    private static int accent() {
        try {
            return crewx.module.modules.render.GuiModule.getAccent().getRGB() | 0xFF000000;
        } catch (Throwable ignored) {
            return ACCENT;
        }
    }

    public static void drawScreenBackground(int width, int height) {
        drawBackground(width, height);
    }
}
