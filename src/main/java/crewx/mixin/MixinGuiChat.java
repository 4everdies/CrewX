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
