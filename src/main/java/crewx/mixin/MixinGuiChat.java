package crewx.mixin;

import crewx.gui.ChatPosition;
import crewx.gui.BindViewer;
import crewx.gui.ScoreboardPosition;
import crewx.CrewX;
import crewx.module.modules.render.HUD;
import net.minecraft.client.gui.GuiChat;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(GuiChat.class)
public abstract class MixinGuiChat {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void crewx$startChatDrag(int mouseX, int mouseY, int button, CallbackInfo callbackInfo) {
        if (BindViewer.beginDrag(mouseX, mouseY, button)) {
            callbackInfo.cancel();
            return;
        }
        if (ScoreboardPosition.beginDrag(mouseX, mouseY, button)) {
            callbackInfo.cancel();
            return;
        }
        if (ChatPosition.beginDrag(mouseX, mouseY, button)) callbackInfo.cancel();
    }

    /** The typing bar: a rounded frosted pill with a faint border instead of vanilla's flat black strip. */
    @Redirect(
            method = "drawScreen",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiChat;drawRect(IIIII)V", ordinal = 0)
    )
    private void crewx$roundedInputBar(int left, int top, int right, int bottom, int color) {
        float x = left;
        float y = top - 1.0F;
        float w = right - left;
        float h = bottom - top + 1.0F;
        for (int i = 2; i >= 1; i--) {
            crewx.clickgui.render.RoundedUtils.drawRoundedRect(x - i, y - i + 1.0F, w + i * 2.0F, h + i * 2.0F, 0x14000000, 5.0F + i);
        }
        crewx.gui.BackdropBlur.drawRoundedPanel(x, y, w, h, 5.0F, 0xA60E0F12);
        // the outline shader works in real pixels: convert the radius with the GUI scale
        int scale = new net.minecraft.client.gui.ScaledResolution(net.minecraft.client.Minecraft.getMinecraft()).getScaleFactor();
        crewx.clickgui.render.RoundedUtils.drawRoundedOutlinedRect(x, y, w, h, 0x2EFFFFFF, 5.0F * scale / 2.0F, 1.0F);
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Inject(method = "drawScreen", at = @At("TAIL"))
    private void crewx$drawChatDragHandle(int mouseX, int mouseY, float partialTicks, CallbackInfo callbackInfo) {
        ChatPosition.renderHandle();
        if (CrewX.moduleManager != null) {
            Object module = CrewX.moduleManager.modules.get(HUD.class);
            if (module instanceof HUD) BindViewer.render((HUD) module);
        }
    }

    @Inject(method = "onGuiClosed", at = @At("TAIL"))
    private void crewx$clearChatDrag(CallbackInfo callbackInfo) {
        ChatPosition.endDrag();
        ScoreboardPosition.endDrag();
        BindViewer.endDrag();
    }
}
