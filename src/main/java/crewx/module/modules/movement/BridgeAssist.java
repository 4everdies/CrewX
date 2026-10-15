package crewx.module.modules.movement;
import crewx.module.modules.combat.*;
import crewx.module.modules.movement.*;
import crewx.module.modules.render.*;
import crewx.module.modules.player.*;
import crewx.module.modules.misc.*;

import crewx.event.EventTarget;
import crewx.event.types.EventType;
import crewx.event.types.Priority;
import crewx.events.MoveInputEvent;
import crewx.events.TickEvent;
import crewx.module.Module;
import crewx.util.ItemUtil;
import crewx.util.MoveUtil;
import crewx.util.PlayerUtil;
import crewx.property.properties.BooleanProperty;
import crewx.property.properties.IntProperty;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import org.apache.commons.lang3.RandomUtils;
import org.lwjgl.input.Keyboard;

import java.util.Objects;

public class BridgeAssist extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int sneakDelay = 0;
    private int autoPlacePreviousSlot = -1;
    private long lastAutoPlaceMs;
    public final IntProperty minDelay = new IntProperty("min-delay", 2, 0, 10);
    public final IntProperty maxDelay = new IntProperty("max-delay", 3, 0, 10);
    public final BooleanProperty directionCheck = new BooleanProperty("direction-check", true);
    public final BooleanProperty jumpCheck = new BooleanProperty("jump-check", true);
    public final BooleanProperty pitchCheck = new BooleanProperty("pitch-check", true);
    public final BooleanProperty blocksOnly = new BooleanProperty("blocks-only", true);
    public final BooleanProperty blockCounter = new BooleanProperty("block-counter", true);
    public final BooleanProperty sneakOnly = new BooleanProperty("sneaking-only", false);
    public final BooleanProperty autoPlaceBlock = new BooleanProperty("auto-place-block", false);
    public final BooleanProperty autoSwitchBlock = new BooleanProperty(
            "auto-switch-block", true, this.autoPlaceBlock::getValue
    );

    private boolean canMoveSafely() {
        double[] offset = MoveUtil.predictMovement();
        return PlayerUtil.canMove(mc.thePlayer.motionX + offset[0], mc.thePlayer.motionZ + offset[1]);
    }

    private boolean shouldSneak() {
        if (this.directionCheck.getValue() && mc.gameSettings.keyBindForward.isKeyDown()) {
            return false;
        } else if (this.jumpCheck.getValue() && mc.gameSettings.keyBindJump.isKeyDown()) {
            return false;
        } else if (this.pitchCheck.getValue() && mc.thePlayer.rotationPitch < 69.0F) {
            return false;
        } else if (sneakOnly.getValue() && !Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) {
            return false;
        } else {
            return (!this.blocksOnly.getValue() || ItemUtil.isHoldingBlock()) && mc.thePlayer.onGround;
        }
    }

    public BridgeAssist() {
        super("BridgeAssist", false);
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            if (!this.autoPlaceBlock.getValue() || !this.autoSwitchBlock.getValue()) {
                this.restoreAutoPlaceSlot();
            }
            if (this.autoPlaceBlock.getValue() && mc.thePlayer != null && mc.theWorld != null
                    && mc.currentScreen == null) {
                if (this.autoSwitchBlock.getValue()) this.selectAutoPlaceBlock();
                this.tryPlaceAimedBlock();
            }
            if (this.sneakDelay > 0) {
                this.sneakDelay--;
            }
            if (this.sneakDelay == 0 && this.canMoveSafely()) {
                this.sneakDelay = RandomUtils.nextInt(this.minDelay.getValue(), this.maxDelay.getValue() + 1);
            }
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (this.isEnabled() && mc.currentScreen == null) {

            if (sneakOnly.getValue() && Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()) && shouldSneak()) {
                mc.thePlayer.movementInput.sneak = false;
                mc.thePlayer.movementInput.moveForward /= 0.3F;
                mc.thePlayer.movementInput.moveStrafe /= 0.3F;
            }

            if (!mc.thePlayer.movementInput.sneak) {
                if (this.shouldSneak() && (this.sneakDelay > 0 || this.canMoveSafely())) {
                    mc.thePlayer.movementInput.sneak = true;
                    mc.thePlayer.movementInput.moveStrafe *= 0.3F;
                    mc.thePlayer.movementInput.moveForward *= 0.3F;
                }
            }
        }
    }

    @Override
    public void onDisabled() {
        this.sneakDelay = 0;
        this.restoreAutoPlaceSlot();
        this.lastAutoPlaceMs = 0L;
    }

    private void selectAutoPlaceBlock() {
        int blockSlot = this.findBlockSlot();
        if (blockSlot < 0) return;
        if (this.autoPlacePreviousSlot < 0) {
            this.autoPlacePreviousSlot = mc.thePlayer.inventory.currentItem;
        }
        if (mc.thePlayer.inventory.currentItem != blockSlot) {
            mc.thePlayer.inventory.currentItem = blockSlot;
            mc.playerController.updateController();
        }
    }

    private int findBlockSlot() {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack != null && stack.stackSize > 0 && stack.getItem() instanceof ItemBlock) {
                Block block = ((ItemBlock) stack.getItem()).getBlock();
                if (block != null && block.getMaterial().isSolid()) return slot;
            }
        }
        return -1;
    }

    private void tryPlaceAimedBlock() {
        ItemStack held = mc.thePlayer.getHeldItem();
        MovingObjectPosition hit = mc.objectMouseOver;
        long now = System.currentTimeMillis();
        if (held == null || !(held.getItem() instanceof ItemBlock) || hit == null
                || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || hit.getBlockPos() == null || hit.sideHit == null || now - this.lastAutoPlaceMs < 100L) {
            return;
        }
        BlockPos adjacent = hit.getBlockPos().offset(hit.sideHit);
        Block target = mc.theWorld.getBlockState(adjacent).getBlock();
        if (target == null || !target.isReplaceable(mc.theWorld, adjacent)) return;
        if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, held,
                hit.getBlockPos(), hit.sideHit, hit.hitVec)) {
            mc.thePlayer.swingItem();
            this.lastAutoPlaceMs = now;
        }
    }

    private void restoreAutoPlaceSlot() {
        if (this.autoPlacePreviousSlot >= 0) {
            if (mc.thePlayer != null && mc.thePlayer.inventory.currentItem != this.autoPlacePreviousSlot) {
                mc.thePlayer.inventory.currentItem = this.autoPlacePreviousSlot;
                if (mc.playerController != null) mc.playerController.updateController();
            }
            this.autoPlacePreviousSlot = -1;
            this.lastAutoPlaceMs = 0L;
        }
    }

    @Override
    public void verifyValue(String name) {
        switch (name) {
            case "min-delay":
                if (this.minDelay.getValue() > this.maxDelay.getValue()) {
                    this.maxDelay.setValue(this.minDelay.getValue());
                }
                break;
            case "max-delay":
                if (this.minDelay.getValue() > this.maxDelay.getValue()) {
                    this.minDelay.setValue(this.maxDelay.getValue());
                }
        }
    }

    @Override
    public String[] getSuffix() {
        return Objects.equals(this.minDelay.getValue(), this.maxDelay.getValue())
                ? new String[]{this.minDelay.getValue().toString()}
                : new String[]{String.format("%d-%d", this.minDelay.getValue(), this.maxDelay.getValue())};
    }
}
