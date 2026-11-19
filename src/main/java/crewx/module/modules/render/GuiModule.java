package crewx.module.modules.render;

import crewx.CrewX;
import crewx.clickgui.clone.CrewXClickGuiBridge;
import crewx.event.EventTarget;
import crewx.event.types.EventType;
import crewx.events.TickEvent;
import crewx.gui.ClickGui;
import crewx.module.Module;
import crewx.property.properties.ColorProperty;
import crewx.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

import java.awt.Color;

/**
 * Abre a ClickGUI.
 * Style: Modern (clone) | Legacy (GUI antiga).
 * Tecla padrão: INSERT.
 */
public class GuiModule extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static GuiModule INSTANCE;
    private static final Color FALLBACK_ACCENT = new Color(54, 211, 142);
    private static final int DEFAULT_KEY = Keyboard.KEY_INSERT;

    private boolean openNextTick = false;
    private ClickGui legacyGui;

    public final ModeProperty style = new ModeProperty(
            "style", 0, new String[]{"Modern", "Legacy"}
    );
    public final ModeProperty colorMode = new ModeProperty(
            "color", 1, new String[]{"HUD Theme", "Custom"}
    );
    public final ColorProperty accentColor = new ColorProperty(
            "accent-color", FALLBACK_ACCENT.getRGB() & 0xFFFFFF,
            () -> this.colorMode.getValue() == 1
    );
    public GuiModule() {
        super("ClickGui", false);
        setKey(DEFAULT_KEY);
        INSTANCE = this;
    }

    @Override
    public void setKey(int key) {
        super.setKey(key == 0 ? DEFAULT_KEY : key);
    }

    public boolean isModern() {
        Integer v = this.style.getValue();
        return v == null || v.intValue() == 0;
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
        openGui();
    }

    private void openGui() {
        if (isModern()) {
            try {
                CrewXClickGuiBridge.open();
                return;
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        try {
            if (this.legacyGui == null) {
                this.legacyGui = new ClickGui();
            }
            mc.displayGuiScreen(this.legacyGui);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static Color getAccent() {
        if (CrewX.moduleManager != null) {
            Module module = CrewX.moduleManager.modules.get(HUD.class);
            if (module instanceof HUD) {
                return ((HUD) module).getColor(System.currentTimeMillis());
            }
        }
        GuiModule instance = INSTANCE;
        return instance == null ? FALLBACK_ACCENT : new Color(instance.accentColor.getValue() & 0xFFFFFF);
    }

    /** Fixed dim for the legacy ClickGUI only (option removed from Modern GUI). */
    public static int getBackgroundAlpha() {
        return 200;
    }
}
