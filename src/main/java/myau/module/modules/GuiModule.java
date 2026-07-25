package myau.module.modules;

import myau.Myau;
import myau.gui.ClickGui;
import myau.module.Module;
import myau.property.properties.ColorProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

import java.awt.Color;

public class GuiModule extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static GuiModule INSTANCE;

    private static final Color FALLBACK_ACCENT = new Color(150, 150, 255);

    public final ModeProperty colorMode = new ModeProperty(
            "color", 1, new String[]{"HUD Theme", "Custom"}
    );
    public final ColorProperty accentColor = new ColorProperty(
            "accent-color", FALLBACK_ACCENT.getRGB() & 0xFFFFFF,
            () -> this.colorMode.getValue() == 1
    );
    public final PercentProperty backgroundAlpha = new PercentProperty("background-alpha", 78);

    public GuiModule() {
        super("ClickGui", false);
        setKey(Keyboard.KEY_RSHIFT);
        INSTANCE = this;
    }

    @Override
    public void onEnabled() {
        setEnabled(false);
        mc.displayGuiScreen(new ClickGui());
    }

    /**
     * Cor de destaque da ClickGUI. Em "HUD Theme" acompanha o tema do HUD
     * (rainbow, chroma, astolfo, custom...), em "Custom" usa a cor escolhida.
     */
    public static Color getAccent() {
        GuiModule instance = INSTANCE;
        if (instance == null) return FALLBACK_ACCENT;

        if (instance.colorMode.getValue() == 0) {
            Module module = Myau.moduleManager.modules.get(HUD.class);
            if (module instanceof HUD) {
                return ((HUD) module).getColor(System.currentTimeMillis());
            }
        }
        return new Color(instance.accentColor.getValue() & 0xFFFFFF);
    }

    /** opacidade do fundo dos paineis, de 0 a 255. */
    public static int getBackgroundAlpha() {
        GuiModule instance = INSTANCE;
        int percent = instance == null ? 78 : instance.backgroundAlpha.getValue();
        int alpha = Math.round(255.0F * percent / 100.0F);
        if (alpha < 0) alpha = 0;
        if (alpha > 255) alpha = 255;
        return alpha;
    }
}