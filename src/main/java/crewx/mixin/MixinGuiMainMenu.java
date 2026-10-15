package crewx.mixin;

import crewx.accountmanager.gui.GuiAccountManager;
import crewx.gui.CrewXMainMenu;
import crewx.gui.InterfaceSettings;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The vanilla title screen is replaced by the CrewX main menu (same screen the injected build uses), so
 * there is one implementation of the menu, its buttons and its background. With "Background" turned off
 * the vanilla menu stays, with two extra buttons: one to turn the CrewX interface back on and one for the alts.
 */
@SideOnly(Side.CLIENT)
@Mixin(GuiMainMenu.class)
public class MixinGuiMainMenu extends GuiScreen {
    private static final int CREWX_INTERFACE_BUTTON = 9810;
    private static final int CREWX_ALTS_BUTTON = 9811;

    @Inject(method = "initGui", at = @At("RETURN"))
    private void crewx$initGui(CallbackInfo callbackInfo) {
        if (InterfaceSettings.isEnabled()) {
            // no vanilla buttons, not even for the single frame before the swap
            buttonList.clear();
            return;
        }
        // bottom-right, above the copyright line: turns the CrewX menus back on
        buttonList.add(new GuiButton(CREWX_INTERFACE_BUTTON, width - 84, height - 34, 80, 20, "Background"));
        buttonList.add(new GuiButton(CREWX_ALTS_BUTTON, 4, 4, 50, 20, "Alts"));
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void crewx$actionPerformed(GuiButton button, CallbackInfo callbackInfo) {
        if (button.id == CREWX_INTERFACE_BUTTON) {
            callbackInfo.cancel();
            InterfaceSettings.setEnabled(true);
            mc.displayGuiScreen(new CrewXMainMenu());
        } else if (button.id == CREWX_ALTS_BUTTON) {
            callbackInfo.cancel();
            mc.displayGuiScreen(new GuiAccountManager(this));
        }
    }

    @Inject(method = "drawScreen", at = @At("HEAD"), cancellable = true)
    private void crewx$drawScreen(int mouseX, int mouseY, float partialTicks, CallbackInfo callbackInfo) {
        if (!InterfaceSettings.isEnabled()) return;
        callbackInfo.cancel();
        crewx.gui.CrewXTheme.drawBackground(width, height);
        if (mc.currentScreen == (Object) this) {
            mc.displayGuiScreen(new CrewXMainMenu());
        }
    }
}
