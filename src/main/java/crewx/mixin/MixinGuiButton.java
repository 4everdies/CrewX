package crewx.mixin;

import crewx.gui.CrewXTheme;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(GuiButton.class)
public abstract class MixinGuiButton {
    @Inject(method = "drawButton", at = @At("HEAD"), cancellable = true)
    private void crewx$drawButton(Minecraft minecraft, int mouseX, int mouseY, CallbackInfo callbackInfo) {
        if (!crewx.gui.InterfaceSettings.isEnabled()) return;   // "Background" off: Minecraft's buttons
        CrewXTheme.drawButton((GuiButton) (Object) this, mouseX, mouseY);
        callbackInfo.cancel();
    }
}
