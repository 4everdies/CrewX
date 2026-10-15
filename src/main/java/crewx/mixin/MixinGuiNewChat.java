package crewx.mixin;

import crewx.gui.BackdropBlur;
import crewx.gui.ChatMessageAnimator;
import crewx.gui.ChatPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.List;

@SideOnly(Side.CLIENT)
@Mixin(GuiNewChat.class)
public abstract class MixinGuiNewChat {
    @Shadow @Final private List<ChatLine> drawnChatLines;
    @Shadow private int scrollPos;

    private int crewx$currentChatCounter;

    @Inject(method = "drawChat", at = @At("HEAD"))
    private void crewx$rememberChatCounter(int updateCounter, CallbackInfo callbackInfo) {
        this.crewx$currentChatCounter = updateCounter;
    }

    @ModifyArgs(
            method = "drawChat",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;translate(FFF)V", ordinal = 0)
    )
    private void crewx$moveChatFeed(Args args) {
        GuiNewChat chat = (GuiNewChat) (Object) this;
        float scale = Math.max(0.01F, chat.getChatScale());
        args.set(0, ((Float) args.get(0)) + ChatPosition.getOffsetX() / scale);
        args.set(1, ((Float) args.get(1)) + ChatPosition.getOffsetY() / scale);
    }

    @Inject(
            method = "drawChat",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;scale(FFF)V", shift = At.Shift.AFTER)
    )
    private void crewx$drawUnifiedChatPanel(int updateCounter, CallbackInfo callbackInfo) {
        GuiNewChat chat = (GuiNewChat) (Object) this;
        int visibleLines = this.crewx$visibleLines(chat, updateCounter);
        if (visibleLines <= 0) return;

        float scale = Math.max(0.01F, chat.getChatScale());
        float contentWidth = (float) Math.ceil(chat.getChatWidth() / scale) + 8.0F;
        float contentHeight = visibleLines * 9.0F;
        int alpha = this.crewx$panelAlpha(chat, updateCounter);
        if (alpha <= 0) return;
        int color = (alpha << 24) | 0x000C0D0F;
        // starts just left of the text (the chat is drawn 2px from the screen edge) so the rounded corner shows
        float x = -1.5F;
        float y = -contentHeight - 4.0F;
        float w = contentWidth + 0.5F;
        float h = contentHeight + 8.0F;
        // soft drop shadow, then the frosted panel with rounder corners
        for (int i = 3; i >= 1; i--) {
            int shadow = Math.round(alpha * 0.10F) << 24;
            crewx.clickgui.render.RoundedUtils.drawRoundedRect(x - i, y - i + 1.0F, w + i * 2.0F, h + i * 2.0F, shadow, 5.0F + i);
        }
        // drawn inside the chat's own translate/scale: let the blur copy the whole window rather than guess
        BackdropBlur.beginUnknownTransform();
        try {
            BackdropBlur.drawRoundedPanel(x, y, w, h, 5.0F, color);
        } finally {
            BackdropBlur.endUnknownTransform();
        }
    }

    @Redirect(
            method = "drawChat",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiNewChat;drawRect(IIIII)V", ordinal = 0)
    )
    private void crewx$replacePerLineBackground(int left, int top, int right, int bottom, int color) {
    }

    @ModifyArgs(
            method = "drawChat",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;FFI)I")
    )
    private void crewx$animateNewChatLine(Args args) {
        if (this.drawnChatLines == null || this.drawnChatLines.isEmpty()) return;
        String renderedText = (String) args.get(0);
        if (renderedText == null) return;

        int start = Math.max(0, this.scrollPos);
        int end = Math.min(this.drawnChatLines.size(), start + ((GuiNewChat) (Object) this).getLineCount());
        for (int index = start; index < end; index++) {
            ChatLine line = this.drawnChatLines.get(index);
            if (line == null || !renderedText.equals(line.getChatComponent().getFormattedText())) continue;
            float progress = ChatMessageAnimator.getProgress(line, this.crewx$currentChatCounter);
            if (progress < 1.0F) {
                args.set(2, ((Float) args.get(2)) + ChatMessageAnimator.getRiseOffset(line, this.crewx$currentChatCounter));
                args.set(3, ChatMessageAnimator.fadeColor((Integer) args.get(3), progress));
            }
            return;
        }
    }

    private int crewx$visibleLines(GuiNewChat chat, int updateCounter) {
        if (this.drawnChatLines == null || this.drawnChatLines.isEmpty()) return 0;
        int start = Math.max(0, this.scrollPos);
        int end = Math.min(this.drawnChatLines.size(), start + Math.max(0, chat.getLineCount()));
        int count = 0;
        for (int index = start; index < end; index++) {
            ChatLine line = this.drawnChatLines.get(index);
            if (line == null) break;
            int age = updateCounter - line.getUpdatedCounter();
            if (chat.getChatOpen() || (age >= 0 && age < 200)) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private int crewx$panelAlpha(GuiNewChat chat, int updateCounter) {
        if (chat.getChatOpen()) return 148;
        if (this.drawnChatLines == null || this.drawnChatLines.isEmpty()) return 0;
        int newestIndex = Math.min(this.drawnChatLines.size() - 1, Math.max(0, this.scrollPos));
        ChatLine newest = this.drawnChatLines.get(newestIndex);
        if (newest == null) return 0;
        double fade = 1.0D - (updateCounter - newest.getUpdatedCounter()) / 200.0D;
        fade = Math.max(0.0D, Math.min(1.0D, fade));
        fade *= fade;
        float opacity = Minecraft.getMinecraft().gameSettings.chatOpacity * 0.9F + 0.1F;
        return Math.max(0, Math.min(200, (int) (148.0D * fade * opacity)));
    }
}
