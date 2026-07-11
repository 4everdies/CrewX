package myau.module.modules;
import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.BlockPos;

public class AutoPot extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final BooleanProperty smartPot = new BooleanProperty("smart-pot", false);
    public final FloatProperty health = new FloatProperty("health", 6.0F, 1.0F, 10.0F);
    public final FloatProperty delay = new FloatProperty("delay", 400.0F, 0.0F, 2000.0F);
    public final ModeProperty potMode = new ModeProperty("pot-mode", 2, new String[]{"Floor", "Jump", "Jump Only"});
    public final BooleanProperty speed = new BooleanProperty("speed", true);
    public final BooleanProperty jumpBoost = new BooleanProperty("jump-boost", false);
    public final BooleanProperty healing = new BooleanProperty("healing", true);
    public final BooleanProperty regeneration = new BooleanProperty("regeneration", true);
    public final BooleanProperty fireResistance = new BooleanProperty("fire-resistance", true);
    public final BooleanProperty strength = new BooleanProperty("strength", true);

    private static final PotionType[] VALID_POTIONS = new PotionType[]{
            PotionType.HEALING, PotionType.REGEN, PotionType.SPEED,
            PotionType.STRENGTH, PotionType.FIRERESISTANCE, PotionType.JUMP_BOOST
    };




    private final SimpleTimer interactionTimer = new SimpleTimer();
    private int prevSlot = -1;
    private int potSlot = -1;
    public static boolean potting = false;
    private boolean thrown = false;
    private boolean readyToThrow = false;
    private int jumpTicks = -1;
    private boolean jump = false;
    public static int haltTicks = -1;

    public AutoPot() {
        super("AutoPot", false, false);
    }
    @Override
    public void onEnabled() {
        this.prevSlot = -1;
        this.potSlot = -1;
        this.jump = false;
        this.jumpTicks = -1;
        potting = false;
        haltTicks = -1;
        this.thrown = false;
        this.readyToThrow = false;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
        if (event.getType() == EventType.PRE) {
            boolean forceJump = potMode.getValue() == 2;
            if (mc.currentScreen instanceof GuiInventory) {
                haltTicks = 10;
                this.interactionTimer.reset();
                return;
            }
            if (this.jump && this.jumpTicks >= 0) {
                mc.thePlayer.motionX = 0.0D;
                mc.thePlayer.motionZ = 0.0D;
            }

            if (potting && haltTicks < 0) {
                potting = false;
                this.thrown = false;
                this.readyToThrow = false;
                if (this.prevSlot != -1) {
                    mc.thePlayer.inventory.currentItem = this.prevSlot;
                    mc.getNetHandler().addToSendQueue(new C09PacketHeldItemChange(this.prevSlot));
                    this.prevSlot = -1;
                }
                this.potSlot = -1;
            }

            if (haltTicks > 6) {
                haltTicks--;
                return;
            }

            if (this.jump) {
                this.jumpTicks--;
                if (mc.thePlayer.onGround) {
                    this.jump = false;
                    this.jumpTicks = -1;
                }
            }

            if (!potting) {
                handlePotions(forceJump);
            }

            haltTicks--;

            if (this.readyToThrow && potting) {
                if (this.jump && this.jumpTicks >= 0) {
                    event.setRotation(mc.thePlayer.rotationYaw, -90.0F, 10);
                } else {
                    event.setRotation(mc.thePlayer.rotationYaw, 90.0F, 10);
                }
            }
        }
        if (event.getType() == EventType.POST) {
            if (potting && !this.thrown && this.potSlot != -1 && this.readyToThrow) {
                ItemStack heldItem = mc.thePlayer.getHeldItem();
                if (heldItem != null && heldItem.getItem() instanceof ItemPotion) {
                    mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, heldItem);
                }
                this.thrown = true;
            }
        }
    }

    private void handlePotions(boolean forceJump) {
        boolean near = isNearby(3.7F);

        Module scaffold = Myau.moduleManager.modules.get(Scaffold.class);
        boolean isScaffoldOn = scaffold != null && scaffold.isEnabled();

        if ((isScaffoldOn || !mc.thePlayer.onGround) && ((mc.thePlayer.hurtResistantTime > 0 && near) || !smartPot.getValue() || !near)) {
            return;
        }

        if (this.interactionTimer.hasReached(delay.getValue().longValue())) {
            float hpTarget = health.getValue() * 2.0F;
            for (int slot = 9; slot < 45; slot++) {
                ItemStack stack = mc.thePlayer.inventoryContainer.getSlot(slot).getStack();
                if (stack != null && stack.getItem() instanceof ItemPotion && ItemPotion.isSplash(stack.getMetadata()) && isBuffPotion(stack)) {
                    if (validatePotion(stack, hpTarget)) {
                        if (isOverVoid()) return;

                        this.prevSlot = mc.thePlayer.inventory.currentItem;
                        double xDist = mc.thePlayer.posX - mc.thePlayer.lastTickPosX;
                        double zDist = mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ;
                        double speed = StrictMath.sqrt(xDist * xDist + zDist * zDist);

                        boolean shouldJump = ((speed < 0.27D || forceJump) && potMode.getValue() != 0);

                        if (shouldJump && mc.thePlayer.onGround && !isBlockAbove() && getJumpBoostModifier() == 0) {
                            mc.thePlayer.motionX = 0.0D;
                            mc.thePlayer.motionZ = 0.0D;
                            mc.thePlayer.jump();
                            this.jump = true;
                            this.jumpTicks = 9;
                        }

                        haltTicks = 6;
                        if (slot >= 36) {
                            this.potSlot = slot - 36;
                            mc.thePlayer.inventory.currentItem = this.potSlot;
                            mc.getNetHandler().addToSendQueue(new C09PacketHeldItemChange(this.potSlot));
                        } else {
                            this.potSlot = 6;
                            mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, slot, this.potSlot, 2, mc.thePlayer);
                            mc.thePlayer.inventory.currentItem = this.potSlot;
                            mc.getNetHandler().addToSendQueue(new C09PacketHeldItemChange(this.potSlot));
                        }

                        potting = true;
                        this.thrown = false;
                        this.readyToThrow = true;
                        this.interactionTimer.reset();
                        return;
                    }
                }
            }
        }
    }

    private boolean validatePotion(ItemStack stack, float hpTarget) {
        ItemPotion itemPotion = (ItemPotion) stack.getItem();
        for (PotionEffect effect : itemPotion.getEffects(stack)) {
            if (checkEffectAmplifier(stack, effect)) {
                for (PotionType potionType : VALID_POTIONS) {
                    if (isPotionTypeEnabled(potionType) && potionType.potionId == effect.getPotionID()) {
                        for (Requirement requirement : potionType.requirements) {
                            if (!requirement.test(hpTarget, effect.getAmplifier(), potionType.potionId)) {
                                return false;
                            }
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isPotionTypeEnabled(PotionType type) {
        if (type == PotionType.SPEED) return this.speed.getValue();
        if (type == PotionType.JUMP_BOOST) return this.jumpBoost.getValue();
        if (type == PotionType.HEALING) return this.healing.getValue();
        if (type == PotionType.REGEN) return this.regeneration.getValue();
        if (type == PotionType.FIRERESISTANCE) return this.fireResistance.getValue();
        if (type == PotionType.STRENGTH) return this.strength.getValue();
        return false;
    }

    private boolean checkEffectAmplifier(ItemStack stack, PotionEffect effectToCheck) {
        int bestAmplifier = -1;
        ItemStack bestStack = null;
        for (int i = 9; i < 45; i++) {
            ItemStack stackInSlot = mc.thePlayer.inventoryContainer.getSlot(i).getStack();
            if (stackInSlot != null && stackInSlot.getItem() instanceof ItemPotion) {
                ItemPotion itemPotion = (ItemPotion) stackInSlot.getItem();
                for (PotionEffect effect : itemPotion.getEffects(stackInSlot)) {
                    int amp = effect.getAmplifier();
                    if (effect.getPotionID() == effectToCheck.getPotionID() && amp > bestAmplifier) {
                        bestStack = stackInSlot;
                        bestAmplifier = amp;
                    }
                }
            }
        }
        return (bestStack == stack);
    }

    private boolean isBuffPotion(ItemStack stack) {
        if (stack != null && stack.getItem() instanceof ItemPotion) {
            ItemPotion potion = (ItemPotion) stack.getItem();
            if (ItemPotion.isSplash(stack.getMetadata())) {
                for (PotionEffect effect : potion.getEffects(stack)) {
                    int id = effect.getPotionID();
                    if ((id == Potion.moveSpeed.id && this.speed.getValue()) ||
                            (id == Potion.regeneration.id && this.regeneration.getValue()) ||
                            (id == Potion.jump.id && this.jumpBoost.getValue()) ||
                            (id == Potion.heal.id && this.healing.getValue()) ||
                            (id == Potion.damageBoost.id && this.strength.getValue()) ||
                            (id == Potion.fireResistance.id && this.fireResistance.getValue()) ||
                            id == Potion.resistance.id) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isNearby(float dist) {
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player != mc.thePlayer && mc.thePlayer.getDistanceToEntity(player) <= dist) {
                return true;
            }
        }
        return false;
    }

    private boolean isOverVoid() {
        for (int i = 0; i < 256; i++) {
            if (mc.theWorld.getBlockState(new BlockPos(mc.thePlayer.posX, i, mc.thePlayer.posZ)).getBlock().getMaterial().isSolid()) {
                return false;
            }
        }
        return true;
    }

    private boolean isBlockAbove() {
        return !mc.theWorld.getBlockState(new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY + 2.0D, mc.thePlayer.posZ)).getBlock().getMaterial().isReplaceable();
    }

    private int getJumpBoostModifier() {
        Potion jumpPotion = Potion.jump;
        if (mc.thePlayer.isPotionActive(jumpPotion)) {
            return mc.thePlayer.getActivePotionEffect(jumpPotion).getAmplifier() + 1;
        }
        return 0;
    }

    private static class SimpleTimer {
        private long lastMS = System.currentTimeMillis();

        public void reset() {
            lastMS = System.currentTimeMillis();
        }

        public boolean hasReached(long time) {
            return System.currentTimeMillis() - lastMS >= time;
        }
    }

    private enum Requirements {
        BETTER_THAN_CURRENT(new BetterThanCurrentRequirement()),
        HEALTH_BELOW(new HealthBelowRequirement());

        private final Requirement requirement;

        Requirements(Requirement requirement) {
            this.requirement = requirement;
        }
    }

    private enum PotionType {
        SPEED(Potion.moveSpeed.id, Requirements.BETTER_THAN_CURRENT.requirement),
        REGEN(Potion.regeneration.id, Requirements.HEALTH_BELOW.requirement, Requirements.BETTER_THAN_CURRENT.requirement),
        JUMP_BOOST(Potion.jump.id, Requirements.BETTER_THAN_CURRENT.requirement),
        FIRERESISTANCE(Potion.fireResistance.id, Requirements.BETTER_THAN_CURRENT.requirement),
        STRENGTH(Potion.damageBoost.id, Requirements.BETTER_THAN_CURRENT.requirement),
        HEALING(Potion.heal.id, Requirements.HEALTH_BELOW.requirement);

        private final int potionId;
        private final Requirement[] requirements;

        PotionType(int potionId, Requirement... requirements) {
            this.potionId = potionId;
            this.requirements = requirements;
        }
    }

    private interface Requirement {
        boolean test(float healthTarget, int currentAmplifier, int potionId);
    }

    private static class HealthBelowRequirement implements Requirement {
        @Override
        public boolean test(float healthTarget, int currentAmplifier, int potionId) {
            return Minecraft.getMinecraft().thePlayer.getHealth() < healthTarget;
        }
    }

    private static class BetterThanCurrentRequirement implements Requirement {
        @Override
        public boolean test(float healthTarget, int currentAmplifier, int potionId) {
            Potion potion = Potion.potionTypes[potionId];
            if (potion == null) return true;
            PotionEffect effect = Minecraft.getMinecraft().thePlayer.getActivePotionEffect(potion);
            return effect == null || effect.getAmplifier() < currentAmplifier;
        }
    }
}
