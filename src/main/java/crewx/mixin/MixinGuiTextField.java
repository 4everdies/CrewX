package crewx.mixin;

import crewx.gui.BackdropBlur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@SideOnly(Side.CLIENT)
@Mixin(GuiTextField.class)
public abstract class MixinGuiTextField {
    @Redirect(
            method = "drawTextBox",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiTextField;drawRect(IIIII)V")
    )
    private void crewx$roundedChatInput(int left, int top, int right, int bottom, int color) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof GuiChat && right - left >= 100 && bottom - top >= 9) {
            int alpha = color == -16777216 ? 0xA6000000 : 0xB8141414;
            BackdropBlur.drawRoundedPanel(left, top, right - left, bottom - top, 4.0F, alpha | (color & 0x00FFFFFF));
        } else {
            Gui.drawRect(left, top, right, bottom, color);
        }
    }
}
