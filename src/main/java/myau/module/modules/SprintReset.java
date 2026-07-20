package myau.module.modules;

import myau.event.EventTarget;
import myau.events.AttackEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0BPacketEntityAction;

public class SprintReset extends Module {

    public final ModeProperty mode = new ModeProperty("mode", 2, new String[]{"Packet", "Legit", "Silent"});
    public final BooleanProperty onlyWhileSprinting = new BooleanProperty("only-while-sprinting", true);
    public final BooleanProperty onlyWhileMoving = new BooleanProperty("only-while-moving", true);
    public final BooleanProperty resetOnCrit = new BooleanProperty("reset-on-crit", true);
    public final BooleanProperty smartReset = new BooleanProperty("smart-reset", false);
    public final BooleanProperty fastReset = new BooleanProperty("fast-reset", false);
    private static final Minecraft mc = Minecraft.getMinecraft();
    private boolean attacked = false;
    private boolean needsReset = false;
    private int ticksSinceAttack = 0;
    private Entity lastTarget = null;

    public SprintReset() {
        super("SprintReset", false, false);
    }

    @Override
    public void onEnabled() { resetState(); }

    @Override
    public void onDisabled() { resetState(); }

    private void resetState() {
        attacked = false;
        needsReset = false;
        ticksSinceAttack = 0;
        lastTarget = null;
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled()) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        Entity target = event.getTarget();
        if (!(target instanceof EntityLivingBase)) return;
        if (onlyWhileSprinting.getValue() && !mc.thePlayer.isSprinting()) return;
        if (onlyWhileMoving.getValue() && !isMoving()) return;
        if (smartReset.getValue()) {
            if (((EntityLivingBase) target).getHealth() <= 0) return;
            if (mc.thePlayer.getDistanceToEntity(target) > 6.0f) return;
        }
        if (fastReset.getValue() && lastTarget != null && lastTarget != target) {
            handlePacketReset();
        }
        lastTarget = target;
        attacked = true;
        switch (mode.getValue()) {
            case 0: handlePacketReset(); break;
            case 1: handleLegitReset(); break;
            case 2: handleSilentReset(); break;
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) return;
        if (attacked) ticksSinceAttack++;
        if (mode.getValue() == 1 && needsReset && ticksSinceAttack >= 1) {
            mc.thePlayer.setSprinting(true);
            needsReset = false; attacked = false; ticksSinceAttack = 0;
        }
        if (mode.getValue() == 2 && needsReset && ticksSinceAttack >= 1) {
            mc.thePlayer.sendQueue.addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING));
            needsReset = false; attacked = false; ticksSinceAttack = 0;
        }
        if (ticksSinceAttack > 5) resetState();
    }

    private void handlePacketReset() {
        mc.thePlayer.sendQueue.addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING));
        mc.thePlayer.sendQueue.addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING));
        if (resetOnCrit.getValue() && canCrit()) performCritReset();
        attacked = false; ticksSinceAttack = 0;
    }

    private void handleLegitReset() {
        mc.thePlayer.setSprinting(false);
        needsReset = true; ticksSinceAttack = 0;
        if (resetOnCrit.getValue() && canCrit()) performCritReset();
    }

    private void handleSilentReset() {
        mc.thePlayer.sendQueue.addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING));
        needsReset = true; ticksSinceAttack = 0;
        if (resetOnCrit.getValue() && canCrit()) performCritReset();
    }

    private void performCritReset() {
        if (mc.thePlayer.onGround && !mc.thePlayer.isInWater() && !mc.thePlayer.isOnLadder()) {
            double x = mc.thePlayer.posX, y = mc.thePlayer.posY, z = mc.thePlayer.posZ;
            mc.thePlayer.sendQueue.addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 0.0625, z, false));
            mc.thePlayer.sendQueue.addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, false));
            mc.thePlayer.sendQueue.addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(x, y + 1.1E-5, z, false));
            mc.thePlayer.sendQueue.addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, false));
        }
    }

    private boolean canCrit() {
        return mc.thePlayer.onGround && !mc.thePlayer.isInWater() && !mc.thePlayer.isInLava()
                && !mc.thePlayer.isOnLadder()
                && !mc.thePlayer.isPotionActive(net.minecraft.potion.Potion.blindness)
                && mc.thePlayer.ridingEntity == null;
    }

    private boolean isMoving() {
        return mc.thePlayer.moveForward != 0 || mc.thePlayer.moveStrafing != 0;
    }
}
