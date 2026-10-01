package crewx.mixin;

import crewx.CrewX;
import crewx.gui.BackdropBlur;
import crewx.gui.ScoreboardPosition;
import crewx.module.modules.player.AutoBlockIn;
import crewx.module.modules.player.Scaffold;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.Collection;

@SideOnly(Side.CLIENT)
@Mixin(value = {GuiIngame.class}, priority = 9999)
public abstract class MixinGuiIngame {
    private ScoreObjective crewx$currentScoreObjective;
    private boolean crewx$scoreboardPanelDrawn;

    @Redirect(
            method = {"updateTick"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/InventoryPlayer;getCurrentItem()Lnet/minecraft/item/ItemStack;"
            )
    )
    private ItemStack updateTick(InventoryPlayer inventoryPlayer) {
        if (CrewX.moduleManager == null) return inventoryPlayer.getCurrentItem();

        Scaffold scaffold = (Scaffold) CrewX.moduleManager.modules.get(Scaffold.class);
        if (scaffold.isEnabled() && scaffold.itemSpoof.getValue()) {
            int slot = scaffold.getSlot();
            if (slot >= 0) return inventoryPlayer.getStackInSlot(slot);
        }

        AutoBlockIn autoBlockIn = (AutoBlockIn) CrewX.moduleManager.modules.get(AutoBlockIn.class);
        if (autoBlockIn.isEnabled() && autoBlockIn.itemSpoof.getValue()) {
            int slot = autoBlockIn.getSlot();
            if (slot >= 0) return inventoryPlayer.getStackInSlot(slot);
        }
        return inventoryPlayer.getCurrentItem();
    }

    @Inject(method = {"renderScoreboard"}, at = @At("HEAD"))
    private void crewx$beginScoreboard(ScoreObjective objective, net.minecraft.client.gui.ScaledResolution resolution, CallbackInfo callbackInfo) {
        this.crewx$currentScoreObjective = objective;
        this.crewx$scoreboardPanelDrawn = false;
        ScoreboardPosition.clearBounds();
    }

    @ModifyArgs(
            method = {"renderScoreboard"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;III)I")
    )
    private void crewx$moveScoreboardText(Args args) {
        args.set(1, ((Integer) args.get(1)) + ScoreboardPosition.getOffsetX());
        args.set(2, ((Integer) args.get(2)) + ScoreboardPosition.getOffsetY());
    }

    @Redirect(
            method = {"renderScoreboard"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiIngame;drawRect(IIIII)V")
    )
    private void crewx$drawUnifiedScoreboard(int left, int top, int right, int bottom, int color) {
        if (this.crewx$scoreboardPanelDrawn) return;
        this.crewx$scoreboardPanelDrawn = true;

        ScoreObjective objective = this.crewx$currentScoreObjective;
        if (objective == null) return;
        Scoreboard scoreboard = objective.getScoreboard();
        Collection<Score> scores = scoreboard.getSortedScores(objective);
        int visibleCount = 0;
        for (Score score : scores) {
            if (score != null && score.getPlayerName() != null && !score.getPlayerName().startsWith("#")) {
                visibleCount++;
            }
        }
        int rows = Math.min(visibleCount, 15);
        if (rows <= 0) return;

        FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
        float panelX = left;
        float panelY = top - rows * font.FONT_HEIGHT - 1.0F;
        float panelWidth = right - left;
        float panelHeight = bottom - panelY;
        ScoreboardPosition.updateBounds(panelX, panelY, panelWidth, panelHeight);
        BackdropBlur.drawRoundedPanel(panelX + ScoreboardPosition.getOffsetX(),
                panelY + ScoreboardPosition.getOffsetY(), panelWidth, panelHeight, 3.0F, 0x900D0E10);
    }
}
