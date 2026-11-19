package crewx.clickgui.clone.mc;

import crewx.clickgui.clone.CrewXClickGuiBridge;
import crewx.clickgui.clone.core.ClickGui;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.function.IntFunction;

/**
 * Tela do Minecraft 1.8.9 que hospeda a {@link ClickGui}.
 *
 * Uso:  mc.displayGuiScreen(new McClickGuiScreen(gui));
 * Mantenha o mesmo {@code ClickGui} entre aberturas para preservar posição dos painéis e estados.
 */
public class McClickGuiScreen extends GuiScreen {
    private static McGfx gfx; // reaproveitado: os atlas de fonte ficam na GPU
    private final ClickGui gui;
    private final int closeKey;
    private long last = System.nanoTime();

    public McClickGuiScreen(ClickGui gui) {
        this(gui, Keyboard.KEY_RSHIFT);
    }

    /** @param closeKey tecla que também fecha a GUI (-1 = só ESC). */
    public McClickGuiScreen(ClickGui gui, int closeKey) {
        this.gui = gui;
        this.closeKey = closeKey;
        gui.setKeyNamer(new IntFunction<String>() {
            @Override
            public String apply(int key) {
                return Keyboard.getKeyName(key);
            }
        });
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // sem fundo escurecido: o mundo continua visível atrás (e atrás dos painéis)
        int dw = mc.displayWidth, dh = mc.displayHeight;
        if (gfx == null) gfx = new McGfx();

        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1e9f);
        last = now;

        float mx = Mouse.getX(), my = dh - Mouse.getY() - 1;
        CrewXClickGuiBridge.setSkipNumberPull(gui.isDraggingSlider());
        CrewXClickGuiBridge.syncFromModules();
        gui.setScreenSize(dw, dh);
        gui.setModifiers(isCtrlKeyDown(), isShiftKeyDown());

        gfx.begin(dw, dh);
        try {
            gui.render(gfx, mx, my, dt);
        } finally {
            gfx.end();
        }
    }

    // O núcleo trabalha em pixels reais, então usamos as coordenadas "cruas" do LWJGL.
    private float rawX() {
        return Mouse.getEventX();
    }

    private float rawY() {
        return mc.displayHeight - Mouse.getEventY() - 1;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        gui.mouseClicked(rawX(), rawY(), mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (state != -1) gui.mouseReleased(rawX(), rawY(), state);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) gui.mouseWheel(wheel > 0 ? 1 : -1);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (gui.isListening()) {
            gui.keyPressed(keyCode, keyCode == Keyboard.KEY_ESCAPE);
            return;
        }
        if (gui.isEditingText()) {
            // ESC / Enter handled inside keyTyped
            gui.keyTyped(typedChar, keyCode, false);
            return;
        }
        if (keyCode == closeKey) {
            mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode); // ESC fecha
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
