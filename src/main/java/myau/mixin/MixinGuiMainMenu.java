package myau.mixin;

import myau.accountmanager.gui.GuiAccountManager;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiMainMenu.class)
public class MixinGuiMainMenu extends GuiScreen {

    @Inject(method = "initGui", at = @At("RETURN"))
    private void addAltManagerButton(CallbackInfo ci) {
        GuiButton realmsButton = null;
        for (GuiButton button : this.buttonList) {
            if (button.id == 14) {
                realmsButton = button;
                break;
            }
        }

        if (realmsButton != null) {
            this.buttonList.remove(realmsButton);
            this.buttonList.add(new GuiButton(1337, realmsButton.xPosition, realmsButton.yPosition, realmsButton.width, realmsButton.height, "Alt Manager"));
        } else {
            this.buttonList.add(new GuiButton(1337, this.width / 2 - 100, this.height / 4 + 48 + 72 + 12 + 24, "Alt Manager"));
        }
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"))
    private void onActionPerformed(GuiButton button, CallbackInfo ci) {
        if (button.id == 1337) {
            this.mc.displayGuiScreen(new GuiAccountManager(this));
        }
    }
}
