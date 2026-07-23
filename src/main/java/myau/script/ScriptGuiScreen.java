package myau.script;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.luaj.vm2.LuaValue;

import java.io.IOException;

/**
 * A blank, script-driven GuiScreen. Lua owns drawing and interaction through
 * callbacks, allowing a script to build a ClickGUI from scratch without
 * exposing Minecraft or Java reflection directly.
 */
public final class ScriptGuiScreen extends GuiScreen {
    private final LuaScript script;
    private int mouseX;
    private int mouseY;

    private ScriptGuiScreen(LuaScript script) {
        this.script = script;
    }

    public static void open(LuaScript script) {
        if (script == null || !script.isLoaded()) return;
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> mc.displayGuiScreen(new ScriptGuiScreen(script)));
    }

    public static void close(LuaScript script) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            if (mc.currentScreen instanceof ScriptGuiScreen
                    && ((ScriptGuiScreen) mc.currentScreen).script == script) {
                mc.displayGuiScreen(null);
            }
        });
    }

    public static ScriptGuiScreen current(LuaScript script) {
        GuiScreen current = Minecraft.getMinecraft().currentScreen;
        if (current instanceof ScriptGuiScreen && ((ScriptGuiScreen) current).script == script) {
            return (ScriptGuiScreen) current;
        }
        return null;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.script.call("onGuiOpen", LuaValue.valueOf(this.width), LuaValue.valueOf(this.height));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.script.call("onGuiDraw",
                LuaValue.valueOf(mouseX),
                LuaValue.valueOf(mouseY),
                LuaValue.valueOf(partialTicks),
                LuaValue.valueOf(this.width),
                LuaValue.valueOf(this.height));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.script.call("onGuiMouseClicked",
                LuaValue.valueOf(mouseX), LuaValue.valueOf(mouseY), LuaValue.valueOf(mouseButton));
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.script.call("onGuiMouseReleased",
                LuaValue.valueOf(mouseX), LuaValue.valueOf(mouseY), LuaValue.valueOf(state));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        boolean allowDefault = this.script.callAllowing("onGuiKeyTyped",
                LuaValue.valueOf(String.valueOf(typedChar)), LuaValue.valueOf(keyCode));
        if (allowDefault && keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            this.script.call("onGuiMouseWheel", LuaValue.valueOf(wheel));
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        this.script.call("onGuiClose");
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    public int getMouseX() {
        return this.mouseX;
    }

    public int getMouseY() {
        return this.mouseY;
    }
}