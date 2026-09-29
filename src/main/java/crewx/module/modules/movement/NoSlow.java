package crewx.module.modules.movement;

/*
 * Adapted from LibreBounce NoSlow.
 * Upstream: https://github.com/LibreBounce/LibreBounce/blob/main/src/main/java/net/ccbluex/liquidbounce/features/module/modules/movement/NoSlow.kt
 * Copyright (c) CCBlueX contributors. Licensed under GPL-3.0.
 */

import crewx.CrewX;
import crewx.enums.BlinkModules;
import crewx.enums.FloatModules;
import crewx.event.EventTarget;
import crewx.event.types.EventType;
import crewx.event.types.Priority;
import crewx.events.LivingUpdateEvent;
import crewx.events.PacketEvent;
import crewx.events.PlayerUpdateEvent;
import crewx.events.RightClickMouseEvent;
import crewx.events.TickEvent;
import crewx.events.UpdateEvent;
import crewx.module.Module;
import crewx.module.modules.combat.KillAura;
import crewx.mixin.IAccessorEntityPlayer;
import crewx.property.properties.BooleanProperty;
import crewx.property.properties.FloatProperty;
import crewx.property.properties.IntProperty;
import crewx.property.properties.ModeProperty;
import crewx.util.BlockUtil;
import crewx.util.ItemUtil;
import crewx.util.PacketUtil;
import crewx.util.PlayerUtil;
import crewx.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemBucketMilk;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C12PacketUpdateSign;
import net.minecraft.network.play.client.C19PacketResourcePackStatus;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.client.C01PacketPing;
import net.minecraft.network.status.server.S01PacketPong;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public class NoSlow extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty swordMode = new ModeProperty("SwordMode", 0,
            new String[]{"None", "NCP", "UpdatedNCP", "AAC5", "SwitchItem", "InvalidC08", "Blink"});
    private final IntProperty reblinkTicks = new IntProperty("ReblinkTicks", 10, 1, 20,
            () -> "Blink".equals(this.swordMode.getModeString()));
    private final FloatProperty blockForwardMultiplier = new FloatProperty("BlockForwardMultiplier", 1.0F, 0.2F, 1.0F,
            () -> !"None".equals(this.swordMode.getModeString()));
    private final FloatProperty blockStrafeMultiplier = new FloatProperty("BlockStrafeMultiplier", 1.0F, 0.2F, 1.0F,
            () -> !"None".equals(this.swordMode.getModeString()));

    public final ModeProperty consumeMode = new ModeProperty("ConsumeMode", 0,
            new String[]{"None", "UpdatedNCP", "AAC5", "SwitchItem", "InvalidC08", "Intave", "Drop", "Float"});
    private final FloatProperty consumeForwardMultiplier = new FloatProperty("ConsumeForwardMultiplier", 1.0F, 0.2F, 1.0F,
            () -> !"None".equals(this.consumeMode.getModeString()));
    private final FloatProperty consumeStrafeMultiplier = new FloatProperty("ConsumeStrafeMultiplier", 1.0F, 0.2F, 1.0F,
            () -> !"None".equals(this.consumeMode.getModeString()));
    private final BooleanProperty consumeFoodOnly = new BooleanProperty("ConsumeFood", true,
            () -> this.consumeForwardMultiplier.getValue() > 0.2F || this.consumeStrafeMultiplier.getValue() > 0.2F);
    private final BooleanProperty consumeDrinkOnly = new BooleanProperty("ConsumeDrink", true,
            () -> this.consumeForwardMultiplier.getValue() > 0.2F || this.consumeStrafeMultiplier.getValue() > 0.2F);

    public final ModeProperty bowMode = new ModeProperty("BowMode", 0,
            new String[]{"None", "UpdatedNCP", "AAC5", "SwitchItem", "InvalidC08", "Float"});
    private final FloatProperty bowForwardMultiplier = new FloatProperty("BowForwardMultiplier", 1.0F, 0.2F, 1.0F,
            () -> !"None".equals(this.bowMode.getModeString()));
    private final FloatProperty bowStrafeMultiplier = new FloatProperty("BowStrafeMultiplier", 1.0F, 0.2F, 1.0F,
            () -> !"None".equals(this.bowMode.getModeString()));

    public final BooleanProperty soulSand = new BooleanProperty("Soulsand", true);
    public final BooleanProperty liquidPush = new BooleanProperty("LiquidPush", true);

    private boolean shouldSwap;
    private boolean shouldBlink = true;
    private boolean shouldNoSlow;
    private boolean hasDropped;
    private int blinkTicks;
    private int lastSlot = -1;

    public NoSlow() {
        super("NoSlow", false);
    }

    public boolean isSwordActive() {
        return mc.thePlayer != null && !"None".equals(this.swordMode.getModeString())
                && mc.thePlayer.getHeldItem() != null && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword;
    }

    public boolean isFoodActive() {
        return mc.thePlayer != null && this.isConsumeItem(mc.thePlayer.getHeldItem())
                && this.consumeItemAllowed(mc.thePlayer.getHeldItem())
                && !"None".equals(this.consumeMode.getModeString());
    }

    public boolean isBowActive() {
        return mc.thePlayer != null && mc.thePlayer.getHeldItem() != null
                && mc.thePlayer.getHeldItem().getItem() instanceof ItemBow
                && !"None".equals(this.bowMode.getModeString());
    }

    public boolean isFloatMode() {
        if (mc.thePlayer == null || mc.thePlayer.getHeldItem() == null) {
            return false;
        }
        ItemStack held = mc.thePlayer.getHeldItem();
        return ("Float".equals(this.consumeMode.getModeString()) && this.isConsumeItem(held)
                && this.consumeItemAllowed(held))
                || ("Float".equals(this.bowMode.getModeString()) && held.getItem() instanceof ItemBow);
    }

    public boolean isAnyActive() {
        if (mc.thePlayer == null || !this.usingItemFunc()) {
            return false;
        }
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null) {
            return false;
        }
        Item item = held.getItem();
        if (item instanceof ItemSword) {
            return !"None".equals(this.swordMode.getModeString());
        }
        if (this.isConsumeItem(held) && this.consumeItemAllowed(held)) {
            if ("Drop".equals(this.consumeMode.getModeString())) {
                return this.shouldNoSlow;
            }
            return !"None".equals(this.consumeMode.getModeString());
        }
        if (item instanceof ItemBow) {
            return !"None".equals(this.bowMode.getModeString());
        }
        return false;
    }

    public boolean canSprint() {
        if (!this.isAnyActive() || mc.thePlayer == null || mc.thePlayer.getHeldItem() == null) {
            return false;
        }
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held.getItem() instanceof ItemSword) {
            return !"None".equals(this.swordMode.getModeString());
        }
        if (this.isConsumeItem(held)) {
            return !"None".equals(this.consumeMode.getModeString());
        }
        return held.getItem() instanceof ItemBow && !"None".equals(this.bowMode.getModeString());
    }

    public int getMotionMultiplier() {
        float multiplier = this.getForwardMultiplier();
        return Math.max(0, Math.min(100, Math.round(multiplier * 100.0F)));
    }

    private float getForwardMultiplier() {
        if (mc.thePlayer == null || mc.thePlayer.getHeldItem() == null) {
            return 1.0F;
        }
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held.getItem() instanceof ItemSword && this.isSwordActive()) {
            return this.blockForwardMultiplier.getValue();
        }
        if (this.isConsumeItem(held) && this.isFoodActive()) {
            return "Drop".equals(this.consumeMode.getModeString()) && !this.shouldNoSlow
                    ? 0.2F : this.consumeForwardMultiplier.getValue();
        }
        if (held.getItem() instanceof ItemBow && this.isBowActive()) {
            return this.bowForwardMultiplier.getValue();
        }
        return 1.0F;
    }

    private float getStrafeMultiplier() {
        if (mc.thePlayer == null || mc.thePlayer.getHeldItem() == null) {
            return 1.0F;
        }
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held.getItem() instanceof ItemSword && this.isSwordActive()) {
            return this.blockStrafeMultiplier.getValue();
        }
        if (this.isConsumeItem(held) && this.isFoodActive()) {
            return "Drop".equals(this.consumeMode.getModeString()) && !this.shouldNoSlow
                    ? 0.2F : this.consumeStrafeMultiplier.getValue();
        }
        if (held.getItem() instanceof ItemBow && this.isBowActive()) {
            return this.bowStrafeMultiplier.getValue();
        }
        return 1.0F;
    }

    private boolean isConsumeItem(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        Item item = stack.getItem();
        return item instanceof ItemFood || item instanceof ItemPotion || item instanceof ItemBucketMilk;
    }

    private boolean consumeItemAllowed(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        Item item = stack.getItem();
        if (item instanceof ItemFood) {
            return this.consumeFoodOnly.getValue();
        }
        if (item instanceof ItemPotion || item instanceof ItemBucketMilk) {
            return this.consumeDrinkOnly.getValue();
        }
        return false;
    }

    public boolean usingItemFunc() {
        if (mc.thePlayer == null || mc.thePlayer.getHeldItem() == null) {
            return false;
        }
        boolean using = mc.thePlayer.isUsingItem();
        if (!using && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword) {
            KillAura aura = (KillAura) CrewX.moduleManager.modules.get(KillAura.class);
            using = aura != null && aura.isPlayerBlocking();
            using |= "UpdatedNCP".equals(this.swordMode.getModeString())
                    && PlayerUtil.isUsingItem();
        }
        return using;
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || !this.isAnyActive()) {
            return;
        }
        mc.thePlayer.movementInput.moveForward *= this.getForwardMultiplier();
        mc.thePlayer.movementInput.moveStrafe *= this.getStrafeMultiplier();
        if (!this.canSprint()) {
            mc.thePlayer.setSprinting(false);
        }
    }

    @EventTarget(Priority.LOW)
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) {
            this.resetTransientState();
            return;
        }
        if (this.isFloatMode() && PlayerUtil.isUsingItem()) {
            int slot = mc.thePlayer.inventory.currentItem;
            if (this.lastSlot != slot) {
                this.lastSlot = slot;
                CrewX.floatManager.setFloatState(true, FloatModules.NO_SLOW);
            }
        } else {
            this.lastSlot = -1;
            CrewX.floatManager.setFloatState(false, FloatModules.NO_SLOW);
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.thePlayer.getHeldItem() == null) {
            return;
        }
        ItemStack held = mc.thePlayer.getHeldItem();
        Item item = held.getItem();
        boolean pre = event.getType() == EventType.PRE;
        boolean post = event.getType() == EventType.POST;
        if (item instanceof ItemSword && this.usingItemFunc()) {
            String mode = this.swordMode.getModeString();
            if ("NCP".equals(mode)) {
                if (pre) {
                    PacketUtil.sendPacket(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                            BlockPos.ORIGIN, EnumFacing.DOWN));
                } else if (post) {
                    this.sendPlacement(held, new BlockPos(-1, -1, -1), 255);
                }
            } else if ("UpdatedNCP".equals(mode) && post) {
                this.sendPlacement(held, BlockPos.ORIGIN, 255);
            } else if ("AAC5".equals(mode) && post) {
                this.sendPlacement(held, new BlockPos(-1, -1, -1), 255);
            } else if ("SwitchItem".equals(mode) && pre) {
                this.updateSlot();
            } else if ("InvalidC08".equals(mode) && pre) {
                this.sendInvalidPlacement();
            }
        }
        if (this.isConsumeItem(held) && this.consumeItemAllowed(held) && this.usingItemFunc()) {
            String mode = this.consumeMode.getModeString();
            if ("AAC5".equals(mode) && pre) {
                this.sendPlacement(held, new BlockPos(-1, -1, -1), 255);
            } else if ("SwitchItem".equals(mode) && pre) {
                this.updateSlot();
            } else if ("UpdatedNCP".equals(mode) && pre && this.shouldSwap) {
                this.updateSlot();
                this.sendPlacement(held, BlockPos.ORIGIN, 255);
                this.shouldSwap = false;
            } else if ("InvalidC08".equals(mode) && pre) {
                this.sendInvalidPlacement();
            } else if ("Intave".equals(mode) && pre) {
                PacketUtil.sendPacket(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                        BlockPos.ORIGIN, EnumFacing.UP));
            }
        }
        if (item instanceof ItemBow && this.usingItemFunc()) {
            String mode = this.bowMode.getModeString();
            if ("AAC5".equals(mode) && pre) {
                this.sendPlacement(held, new BlockPos(-1, -1, -1), 255);
            } else if ("SwitchItem".equals(mode) && pre) {
                this.updateSlot();
            } else if ("UpdatedNCP".equals(mode) && pre && this.shouldSwap) {
                this.updateSlot();
                this.sendPlacement(held, BlockPos.ORIGIN, 255);
                this.shouldSwap = false;
            } else if ("InvalidC08".equals(mode) && pre) {
                this.sendInvalidPlacement();
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.isCancelled() || mc.thePlayer == null) {
            return;
        }
        Packet<?> packet = event.getPacket();
        if (event.getType() == EventType.SEND) {
            this.handleBlinkPacket(packet);
            if (this.shouldSwap) {
                return;
            }
            if (packet instanceof C08PacketPlayerBlockPlacement) {
                this.handlePlacement((C08PacketPlayerBlockPlacement) packet);
            }
        } else if (event.getType() == EventType.RECEIVE) {
            if ("Drop".equals(this.consumeMode.getModeString()) && packet instanceof S2FPacketSetSlot) {
                this.handleDropSlot((S2FPacketSetSlot) packet, event);
            }
            if (packet instanceof S12PacketEntityVelocity
                    && ((S12PacketEntityVelocity) packet).getEntityID() == mc.thePlayer.getEntityId()) {
                this.flushBlink();
            } else if (packet instanceof S27PacketExplosion) {
                this.flushBlink();
            }
        }
    }

    private void handlePlacement(C08PacketPlayerBlockPlacement packet) {
        ItemStack sentStack = packet.getStack();
        ItemStack held = mc.thePlayer.getHeldItem();
        if (sentStack == null || held == null || sentStack.getItem() != held.getItem()) {
            return;
        }
        Item item = sentStack.getItem();
        if ("UpdatedNCP".equals(this.consumeMode.getModeString()) && this.isConsumeItem(sentStack)
                || "UpdatedNCP".equals(this.bowMode.getModeString()) && item instanceof ItemBow) {
            this.shouldSwap = true;
        }
        if ("Drop".equals(this.consumeMode.getModeString()) && item instanceof ItemFood) {
            if (!this.isMoving() || !mc.thePlayer.isUsingItem()) {
                this.shouldNoSlow = false;
                this.hasDropped = false;
            }
            if (packet.getPlacedBlockDirection() == 255 && !this.hasDropped) {
                PacketUtil.sendPacket(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.DROP_ITEM,
                        BlockPos.ORIGIN, EnumFacing.DOWN));
                this.shouldNoSlow = false;
                this.hasDropped = true;
            }
        }
    }

    private void handleDropSlot(S2FPacketSetSlot packet, PacketEvent event) {
        if (mc.thePlayer.getHeldItem() == null || !(mc.thePlayer.getHeldItem().getItem() instanceof ItemFood)
                || !this.isMoving()) {
            this.shouldNoSlow = false;
            return;
        }
        if (!mc.thePlayer.isUsingItem()) {
            this.shouldNoSlow = false;
            this.hasDropped = false;
            return;
        }
        if (packet.func_149175_c() != 0 || packet.func_149173_d() != mc.thePlayer.inventory.currentItem + 36) {
            return;
        }
        event.setCancelled(true);
        this.shouldNoSlow = true;
        ((IAccessorEntityPlayer) mc.thePlayer).setItemInUse(packet.func_149174_e());
        if (!mc.thePlayer.isUsingItem()) {
            ((IAccessorEntityPlayer) mc.thePlayer).setItemInUseCount(0);
        }
        mc.thePlayer.inventory.mainInventory[mc.thePlayer.inventory.currentItem] = packet.func_149174_e();
    }

    private void handleBlinkPacket(Packet<?> packet) {
        if (!"Blink".equals(this.swordMode.getModeString())) {
            this.flushBlink();
            return;
        }
        if (packet instanceof C00Handshake || packet instanceof C00PacketServerQuery || packet instanceof C01PacketPing
                || packet instanceof C00PacketKeepAlive || packet instanceof C01PacketChatMessage || packet instanceof S01PacketPong) {
            this.flushBlink();
            return;
        }
        if (packet instanceof C07PacketPlayerDigging || packet instanceof C02PacketUseEntity
                || packet instanceof C12PacketUpdateSign || packet instanceof C19PacketResourcePackStatus) {
            this.updateBlinkTimer();
            if (this.shouldBlink && this.blinkTicks >= this.reblinkTicks.getValue()
                    && (CrewX.blinkManager.getBlinkingModule() == BlinkModules.NO_SLOW
                    && CrewX.blinkManager.countMovement() > 0)) {
                this.flushBlink();
                this.blinkTicks = 0;
                this.shouldBlink = false;
            } else if (this.blinkTicks < this.reblinkTicks.getValue()) {
                this.shouldBlink = true;
            }
            return;
        }
        if (packet instanceof C03PacketPlayer) {
            if (this.isMoving() && mc.thePlayer.getHeldItem() != null
                    && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword && this.usingItemFunc()) {
                if (this.shouldBlink && (CrewX.blinkManager.getBlinkingModule() == BlinkModules.NONE
                        || CrewX.blinkManager.getBlinkingModule() == BlinkModules.NO_SLOW)) {
                    CrewX.blinkManager.setBlinkState(true, BlinkModules.NO_SLOW);
                }
            } else {
                this.shouldBlink = true;
                this.flushBlink();
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE) {
            return;
        }
        if (!this.isEnabled() || mc.thePlayer == null || !"Blink".equals(this.swordMode.getModeString())
                || mc.thePlayer.getHeldItem() == null || !(mc.thePlayer.getHeldItem().getItem() instanceof ItemSword)
                || !this.usingItemFunc() || !this.isMoving()) {
            this.flushBlink();
            this.shouldBlink = true;
            this.blinkTicks = 0;
            return;
        }
        this.updateBlinkTimer();
        if (this.shouldBlink && this.blinkTicks >= this.reblinkTicks.getValue()
                && CrewX.blinkManager.getBlinkingModule() == BlinkModules.NO_SLOW
                && CrewX.blinkManager.countMovement() > 0) {
            this.flushBlink();
            this.blinkTicks = 0;
            this.shouldBlink = false;
        } else if (this.blinkTicks < this.reblinkTicks.getValue()) {
            this.shouldBlink = true;
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) {
            return;
        }
        if (mc.objectMouseOver != null) {
            switch (mc.objectMouseOver.typeOfHit) {
                case BLOCK:
                    BlockPos blockPos = mc.objectMouseOver.getBlockPos();
                    if (BlockUtil.isInteractable(blockPos) && !PlayerUtil.isSneaking()) {
                        return;
                    }
                    break;
                case ENTITY:
                    Entity entityHit = mc.objectMouseOver.entityHit;
                    if (entityHit instanceof EntityVillager) {
                        return;
                    }
                    if (entityHit instanceof EntityLivingBase && TeamUtil.isShop((EntityLivingBase) entityHit)) {
                        return;
                    }
                    break;
                default:
                    break;
            }
        }
        if (this.isFloatMode() && !CrewX.floatManager.isPredicted() && mc.thePlayer.onGround) {
            event.setCancelled(true);
            mc.thePlayer.motionY = 0.42F;
        }
    }

    @Override
    public void onEnabled() {
        this.resetTransientState();
    }

    @Override
    public void onDisabled() {
        this.flushBlink();
        CrewX.floatManager.setFloatState(false, FloatModules.NO_SLOW);
        this.resetTransientState();
    }

    private void sendPlacement(ItemStack held, BlockPos pos, int face) {
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(pos, face, held, 0.0F, 0.0F, 0.0F));
    }

    private void sendInvalidPlacement() {
        if (this.hasSpaceInInventory() && mc.thePlayer.ticksExisted % 3 == 0) {
            PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(new BlockPos(-1, -1, -1), 1, null,
                    0.0F, 0.0F, 0.0F));
        }
    }

    private boolean hasSpaceInInventory() {
        if (mc.thePlayer == null) {
            return false;
        }
        for (int i = 0; i < 36; i++) {
            if (mc.thePlayer.inventory.mainInventory[i] == null) {
                return true;
            }
        }
        return false;
    }

    private void updateSlot() {
        if (mc.thePlayer == null || mc.getNetHandler() == null) {
            return;
        }
        int slot = mc.thePlayer.inventory.currentItem;
        PacketUtil.sendPacketNoEvent(new C09PacketHeldItemChange((slot + 1) % 9));
        PacketUtil.sendPacketNoEvent(new C09PacketHeldItemChange(slot));
    }

    private boolean isMoving() {
        return mc.thePlayer != null && mc.thePlayer.movementInput != null
                && (mc.thePlayer.movementInput.moveForward != 0.0F || mc.thePlayer.movementInput.moveStrafe != 0.0F);
    }

    private void updateBlinkTimer() {
        if (this.blinkTicks < Integer.MAX_VALUE) {
            this.blinkTicks++;
        }
    }

    private void flushBlink() {
        if (CrewX.blinkManager != null && CrewX.blinkManager.getBlinkingModule() == BlinkModules.NO_SLOW) {
            CrewX.blinkManager.setBlinkState(false, BlinkModules.NO_SLOW);
        }
    }

    private void resetTransientState() {
        this.shouldSwap = false;
        this.shouldBlink = true;
        this.shouldNoSlow = false;
        this.hasDropped = false;
        this.blinkTicks = 0;
        this.lastSlot = -1;
    }
}
