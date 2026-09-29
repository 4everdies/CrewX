package crewx.module.modules.render;
import crewx.module.modules.combat.*;
import crewx.module.modules.movement.*;
import crewx.module.modules.render.*;
import crewx.module.modules.player.*;
import crewx.module.modules.misc.*;

import crewx.CrewX;
import crewx.event.EventTarget;
import crewx.event.types.EventType;
import crewx.events.TickEvent;
import crewx.gui.ClickGui;
import crewx.module.Module;
import crewx.property.properties.ColorProperty;
import crewx.property.properties.ModeProperty;
import crewx.property.properties.PercentProperty;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

import java.awt.Color;

public class GuiModule extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static GuiModule INSTANCE;
    private static final Color FALLBACK_ACCENT = new Color(232, 42, 52);
    private static final int DEFAULT_KEY = Keyboard.KEY_RSHIFT;

    private boolean openNextTick = false;
    private ClickGui clickGui;

    public final ModeProperty colorMode = new ModeProperty(
            "color", 1, new String[]{"HUD Theme", "Custom"}
    );
    public final ColorProperty accentColor = new ColorProperty(
            "accent-color", FALLBACK_ACCENT.getRGB() & 0xFFFFFF,
            () -> this.colorMode.getValue() == 1
    );
    public final PercentProperty backgroundAlpha = new PercentProperty("background-alpha", 100);

    public GuiModule() {
        super("ClickGui", false);
        setKey(DEFAULT_KEY);
        INSTANCE = this;
    }
    @Override
    public void setKey(int key) {
        super.setKey(key == 0 ? DEFAULT_KEY : key);
    }

    @Override
    public void onEnabled() {
        setEnabled(false);
        this.openNextTick = true;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (!this.openNextTick) return;
        this.openNextTick = false;
        if (this.clickGui == null) {
            this.clickGui = new ClickGui();
        }
        mc.displayGuiScreen(this.clickGui);
    }
    public static Color getAccent() {
        GuiModule instance = INSTANCE;
        if (instance == null) return FALLBACK_ACCENT;

        if (instance.colorMode.getValue() == 0) {
            Module module = CrewX.moduleManager.modules.get(HUD.class);
            if (module instanceof HUD) {
                return ((HUD) module).getColor(System.currentTimeMillis());
            }
        }
        return new Color(instance.accentColor.getValue() & 0xFFFFFF);
    }
    public static int getBackgroundAlpha() {
        GuiModule instance = INSTANCE;
        int percent = instance == null ? 78 : instance.backgroundAlpha.getValue();
        int alpha = Math.round(255.0F * percent / 100.0F);
        if (alpha < 0) alpha = 0;
        if (alpha > 255) alpha = 255;
        return alpha;
    }
}
