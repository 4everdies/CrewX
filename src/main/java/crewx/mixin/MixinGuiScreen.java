package crewx.mixin;

import crewx.gui.CrewXTheme;
import crewx.gui.BindViewer;
import crewx.gui.ChatPosition;
import crewx.gui.ScoreboardPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(GuiScreen.class)
public abstract class MixinGuiScreen {
    @Shadow protected int width;
    @Shadow protected int height;

    @Inject(method = "drawDefaultBackground", at = @At("HEAD"), cancellable = true)
    private void crewx$drawDefaultBackground(CallbackInfo callbackInfo) {
        if (!(Minecraft.getMinecraft().currentScreen instanceof GuiMainMenu)) return;
        CrewXTheme.drawScreenBackground(width, height);
        callbackInfo.cancel();
    }

    @Inject(method = "mouseClickMove", at = @At("HEAD"), cancellable = true)
    private void crewx$dragChat(int mouseX, int mouseY, int button, long timeSinceLastClick, CallbackInfo callbackInfo) {
        if (!(Minecraft.getMinecraft().currentScreen instanceof GuiChat)) return;
        if (BindViewer.isDragging()) {
            BindViewer.dragTo(mouseX, mouseY);
            callbackInfo.cancel();
        } else if (ScoreboardPosition.isDragging()) {
            ScoreboardPosition.dragTo(mouseX, mouseY);
            callbackInfo.cancel();
        } else if (ChatPosition.isDragging()) {
            ChatPosition.dragTo(mouseX, mouseY);
            callbackInfo.cancel();
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void crewx$releaseChatDrag(int mouseX, int mouseY, int button, CallbackInfo callbackInfo) {
        if (!(Minecraft.getMinecraft().currentScreen instanceof GuiChat)) return;
        if (BindViewer.isDragging() && button == 0) {
            BindViewer.endDrag();
            callbackInfo.cancel();
        } else if (ScoreboardPosition.isDragging() && button == 1) {
            ScoreboardPosition.endDrag();
            callbackInfo.cancel();
        } else if (ChatPosition.isDragging() && button == 0) {
            ChatPosition.endDrag();
            callbackInfo.cancel();
        }
    }
}
