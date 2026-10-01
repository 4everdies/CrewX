package crewx.module.modules.movement;

import crewx.event.EventTarget;
import crewx.event.types.EventType;
import crewx.event.types.Priority;
import crewx.events.PacketEvent;
import crewx.events.AttackEvent;
import crewx.events.KnockbackEvent;
import crewx.events.StrafeEvent;
import crewx.events.UpdateEvent;
import crewx.mixin.IAccessorPlayerControllerMP;
import crewx.module.Module;
import crewx.module.modules.combat.LagRange;
import crewx.property.properties.FloatProperty;
import crewx.property.properties.ModeProperty;
import crewx.util.ChatUtil;
import crewx.util.KeyBindUtil;
import crewx.util.MoveUtil;
import crewx.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.S39PacketPlayerAbilities;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.util.EnumChatFormatting;

public class Fly extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final long PHANTOM_JUMP_DELAY_MS = 50L;
    private static final long KAIZEN_FLY_DURATION_MS = 3000L;
    private static final long PHANTOM_FLIGHT_DETECTION_TIMEOUT_MS = 5000L;

    private double verticalMotion = 0.0;
    private boolean isKaizenMode = false;
    private volatile boolean isFlyPhysicallyEnabled = false;
    private volatile boolean phantomActivationPending = false;
    private volatile boolean awaitingPhantomFlight = false;
    private boolean phantomWasInHotbar = false;
    private long phantomJumpTime = 0L;
    private volatile long phantomUseTime = 0L;
    private volatile long flyEndTime = 0L;
    private volatile long kaizenAttackUntil = 0L;
    private boolean wasCreativeFlyingBeforePhantom = false;
    private int previousHotbarSlot = -1;
    private int phantomHotbarSlot = -1;
    private int phantomInventorySlot = -1;
    private int swordInventorySlot = -1;

    public final FloatProperty hSpeed = new FloatProperty("horizontal-speed", 1.0F, 0.0F, 100.0F);
    public final FloatProperty vSpeed = new FloatProperty("vertical-speed", 1.0F, 0.0F, 100.0F);
    public final ModeProperty flyMode = new ModeProperty("mode", 0, new String[]{"Normal", "Kaizen"});

    public Fly() {
        super("Fly", false);
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) {
            return;
        }
        if (this.isKaizenMode && !this.isFlyPhysicallyEnabled) {
            return;
        }
        if (mc.thePlayer.posY % 1.0 != 0.0) {
            mc.thePlayer.motionY = this.verticalMotion;
        }
        MoveUtil.setSpeed(0.0);
        event.setFriction((float) (MoveUtil.getBaseMoveSpeed() * this.getActiveHorizontalSpeed()));
    }

    @EventTarget(Priority.HIGHEST)
    public void onKnockback(KnockbackEvent event) {
        if (this.isEnabled() && this.isKaizenMode && this.isFlyPhysicallyEnabled) {
            this.setEnabled(false);
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onVelocityPacket(PacketEvent event) {
        if (!this.isEnabled() || !this.isKaizenMode || !this.isFlyPhysicallyEnabled
                || event.getType() != EventType.RECEIVE) return;
        if (event.getPacket() instanceof S12PacketEntityVelocity
                && mc.thePlayer != null
                && ((S12PacketEntityVelocity) event.getPacket()).getEntityID() == mc.thePlayer.getEntityId()
                || event.getPacket() instanceof S27PacketExplosion) {
            this.setEnabled(false);
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onAttack(AttackEvent event) {
        if (this.isKaizenMode && this.isEnabled()) {
            this.kaizenAttackUntil = System.currentTimeMillis() + 150L;
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE || !this.isEnabled()) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            this.setEnabled(false);
            return;
        }

        long now = System.currentTimeMillis();
        if (this.isKaizenMode && this.phantomActivationPending) {
            if (now - this.phantomJumpTime < PHANTOM_JUMP_DELAY_MS) {
                return;
            }
            ItemStack phantom = mc.thePlayer.getHeldItem();
            if (!isPhantom(phantom)) {
                ChatUtil.sendFormatted("&cPhantom was not ready in the selected slot; Kaizen Fly was stopped.");
                this.setEnabled(false);
                return;
            }
            this.awaitingPhantomFlight = true;
            this.phantomUseTime = now;
            PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(phantom));
            this.restoreSwordAfterPhantom();
            this.phantomActivationPending = false;
        }

        if (this.isKaizenMode && this.awaitingPhantomFlight && !this.wasCreativeFlyingBeforePhantom
                && mc.thePlayer.capabilities.isFlying) {
            this.activateKaizenFly(System.currentTimeMillis());
        }

        if (this.isKaizenMode && this.awaitingPhantomFlight
                && now - this.phantomUseTime >= PHANTOM_FLIGHT_DETECTION_TIMEOUT_MS) {
            ChatUtil.sendFormatted("&cPhantom did not grant creative flight; Kaizen Fly was stopped.");
            this.setEnabled(false);
            return;
        }

        if (this.isKaizenMode && this.isFlyPhysicallyEnabled && now >= this.flyEndTime) {
            this.setEnabled(false);
            return;
        }

        if (this.isKaizenMode && !this.isFlyPhysicallyEnabled) {
            return;
        }

        this.verticalMotion = 0.0;
        if (mc.currentScreen == null) {
            if (KeyBindUtil.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode())) {
                this.verticalMotion += this.vSpeed.getValue().doubleValue() * 0.42F;
            }
            if (KeyBindUtil.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) {
                this.verticalMotion -= this.vSpeed.getValue().doubleValue() * 0.42F;
            }
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onAbilities(PacketEvent event) {
        if (!this.isEnabled() || !this.isKaizenMode || !this.awaitingPhantomFlight
                || event.getType() != EventType.RECEIVE || event.isCancelled()
                || !(event.getPacket() instanceof S39PacketPlayerAbilities)) {
            return;
        }
        S39PacketPlayerAbilities abilities = (S39PacketPlayerAbilities) event.getPacket();
        if (abilities.isFlying()) {
            this.activateKaizenFly(System.currentTimeMillis());
        }
    }

    private synchronized void activateKaizenFly(long activationTime) {
        if (!this.isEnabled() || !this.isKaizenMode || !this.awaitingPhantomFlight) {
            return;
        }
        this.awaitingPhantomFlight = false;
        this.isFlyPhysicallyEnabled = true;
        this.flyEndTime = activationTime + KAIZEN_FLY_DURATION_MS;
    }

    @Override
    public void onEnabled() {
        this.isKaizenMode = this.flyMode.getValue() == 1;
        this.setLagRangeSuspended(this.isKaizenMode);
        this.isFlyPhysicallyEnabled = false;
        this.phantomActivationPending = false;
        this.awaitingPhantomFlight = false;
        this.phantomWasInHotbar = false;
        this.phantomJumpTime = 0L;
        this.phantomUseTime = 0L;
        this.flyEndTime = 0L;
        this.kaizenAttackUntil = 0L;
        this.previousHotbarSlot = -1;
        this.phantomHotbarSlot = -1;
        this.phantomInventorySlot = -1;
        this.swordInventorySlot = -1;

        if (!this.isKaizenMode) {
            this.isFlyPhysicallyEnabled = true;
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            this.setEnabled(false);
            return;
        }
        this.wasCreativeFlyingBeforePhantom = mc.thePlayer.capabilities.isFlying;

        int phantomSlot = findPhantomSlot();
        if (phantomSlot == -1) {
            ChatUtil.sendFormatted("&cNo Phantom item found in your inventory.");
            this.setEnabled(false);
            return;
        }

        this.previousHotbarSlot = mc.thePlayer.inventory.currentItem;
        this.phantomHotbarSlot = this.previousHotbarSlot;
        this.swordInventorySlot = findSwordSlot();
        this.phantomWasInHotbar = phantomSlot < 9;
        if (this.phantomWasInHotbar) {
            this.phantomHotbarSlot = phantomSlot;
            mc.thePlayer.inventory.currentItem = phantomSlot;
        } else {
            this.phantomInventorySlot = phantomSlot;
            mc.playerController.windowClick(
                    mc.thePlayer.inventoryContainer.windowId,
                    phantomSlot,
                    this.phantomHotbarSlot,
                    2,
                    mc.thePlayer
            );
            mc.thePlayer.inventory.currentItem = this.phantomHotbarSlot;
        }
        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();

        mc.thePlayer.jump();
        this.phantomJumpTime = System.currentTimeMillis();
        this.phantomActivationPending = true;
    }

    @Override
    public void onDisabled() {
        this.setLagRangeSuspended(false);
        if (this.phantomActivationPending) {
            this.restoreSwordAfterPhantom();
        }
        this.phantomActivationPending = false;
        this.awaitingPhantomFlight = false;
        this.isFlyPhysicallyEnabled = false;
        this.flyEndTime = 0L;
        this.kaizenAttackUntil = 0L;
        this.phantomUseTime = 0L;

        if (!this.isKaizenMode && mc.thePlayer != null) {
            mc.thePlayer.motionY = 0.0;
            MoveUtil.setSpeed(0.0);
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindSneak.getKeyCode());
        }
    }

    private void restoreSwordAfterPhantom() {
        if (mc.thePlayer == null) {
            this.clearPhantomSelection();
            return;
        }

        if (!this.phantomWasInHotbar && this.phantomInventorySlot >= 9 && this.phantomHotbarSlot >= 0) {
            mc.playerController.windowClick(
                    mc.thePlayer.inventoryContainer.windowId,
                    this.phantomInventorySlot,
                    this.phantomHotbarSlot,
                    2,
                    mc.thePlayer
            );
        }

        int swordHotbarSlot = findSwordHotbarSlot();
        if (swordHotbarSlot >= 0) {
            mc.thePlayer.inventory.currentItem = swordHotbarSlot;
        } else if (this.swordInventorySlot >= 9 && this.phantomHotbarSlot >= 0) {
            mc.playerController.windowClick(
                    mc.thePlayer.inventoryContainer.windowId,
                    this.swordInventorySlot,
                    this.phantomHotbarSlot,
                    2,
                    mc.thePlayer
            );
            mc.thePlayer.inventory.currentItem = this.phantomHotbarSlot;
        } else if (this.previousHotbarSlot >= 0) {
            mc.thePlayer.inventory.currentItem = this.previousHotbarSlot;
        }

        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        this.clearPhantomSelection();
    }

    private void clearPhantomSelection() {
        this.phantomWasInHotbar = false;
        this.phantomInventorySlot = -1;
        this.phantomHotbarSlot = -1;
        this.swordInventorySlot = -1;
        this.previousHotbarSlot = -1;
    }

    private static int findPhantomSlot() {
        if (mc.thePlayer == null) {
            return -1;
        }
        for (int slot = 0; slot < 36; slot++) {
            if (isPhantom(mc.thePlayer.inventory.getStackInSlot(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private static int findSwordSlot() {
        if (mc.thePlayer == null) {
            return -1;
        }
        int selected = mc.thePlayer.inventory.currentItem;
        ItemStack held = mc.thePlayer.inventory.getStackInSlot(selected);
        if (held != null && held.getItem() instanceof ItemSword) {
            return selected;
        }
        for (int slot = 0; slot < 9; slot++) {
            ItemStack item = mc.thePlayer.inventory.getStackInSlot(slot);
            if (item != null && item.getItem() instanceof ItemSword) {
                return slot;
            }
        }
        for (int slot = 9; slot < 36; slot++) {
            ItemStack item = mc.thePlayer.inventory.getStackInSlot(slot);
            if (item != null && item.getItem() instanceof ItemSword) {
                return slot;
            }
        }
        return -1;
    }

    private static int findSwordHotbarSlot() {
        if (mc.thePlayer == null) {
            return -1;
        }
        for (int slot = 0; slot < 9; slot++) {
            ItemStack item = mc.thePlayer.inventory.getStackInSlot(slot);
            if (item != null && item.getItem() instanceof ItemSword) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean isPhantom(ItemStack stack) {
        if (stack == null || stack.getDisplayName() == null) {
            return false;
        }
        String itemName = EnumChatFormatting.getTextWithoutFormattingCodes(stack.getDisplayName());
        return itemName != null && itemName.toLowerCase(java.util.Locale.ROOT).contains("phantom");
    }

    public double getKaizenSecondsRemaining() {
        if (!this.isEnabled() || !this.isKaizenMode || !this.isFlyPhysicallyEnabled || this.flyEndTime <= 0L) {
            return 0.0D;
        }
        return Math.max(0.0D, (this.flyEndTime - System.currentTimeMillis()) / 1000.0D);
    }

    public void disableFlyAndBlink() {
        this.setEnabled(false);
    }

    public boolean isKaizenMode() {
        return this.isKaizenMode;
    }

    private double getActiveHorizontalSpeed() {
        if (!this.isKaizenMode) return this.hSpeed.getValue().doubleValue();
        return System.currentTimeMillis() < this.kaizenAttackUntil
                ? 4.0D
                : this.hSpeed.getValue().doubleValue();
    }

    /** True only while Kaizen has actually granted flight; pending activation is excluded. */
    public boolean isKaizenFlightActive() {
        return this.isEnabled()
                && this.isKaizenMode
                && this.isFlyPhysicallyEnabled
                && !this.awaitingPhantomFlight
                && mc.thePlayer != null
                && mc.thePlayer.capabilities.isFlying
                && System.currentTimeMillis() < this.flyEndTime;
    }

    public boolean isBlinkActive() {
        return false;
    }

    private void setLagRangeSuspended(boolean suspended) {
        if (crewx.CrewX.moduleManager == null) return;
        Object module = crewx.CrewX.moduleManager.modules.get(LagRange.class);
        if (module instanceof LagRange) {
            ((LagRange) module).setSuspended(suspended);
        }
    }
}
