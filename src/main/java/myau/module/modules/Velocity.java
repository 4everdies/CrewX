package myau.module.modules;

import com.google.common.base.CaseFormat;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.*;
import myau.mixin.IAccessorEntity;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import net.minecraft.client.Minecraft;

public class Velocity extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private int chanceCounter = 0;
    private boolean pendingExplosion = false;
    private boolean allowNext = true;

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"Vanilla", "Grim Jump", "JumpReset"});
    public final PercentProperty chance = new PercentProperty("chance", 100);
    public final PercentProperty horizontal = new PercentProperty("horizontal", 0);
    public final PercentProperty vertical = new PercentProperty("vertical", 100);
    public final PercentProperty explosionHorizontal = new PercentProperty("explosions-horizontal", 100);
    public final PercentProperty explosionVertical = new PercentProperty("explosions-vertical", 100);
    public final BooleanProperty fakeCheck = new BooleanProperty("fake-check", true);

    private boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava() || ((IAccessorEntity) mc.thePlayer).getIsInWeb();
    }

    private boolean isGrimJump() {
        return this.mode.getValue() == 1;
    }

    private boolean isJumpReset() {
        return this.mode.getValue() == 2;
    }

    public Velocity() {
        super("Velocity", false);
    }

    @EventTarget
    public void onKnockback(KnockbackEvent event) {
        if (!this.isEnabled() || event.isCancelled()) {
            this.pendingExplosion = false;
            this.allowNext = true;
        } else if (this.isGrimJump()) {
            this.allowNext = true;
        } else if (this.isJumpReset()) {
            this.allowNext = true;
            if (mc.thePlayer != null && mc.thePlayer.onGround && !this.isInLiquidOrWeb()) {
                mc.thePlayer.jump();
            }
        } else if (!this.allowNext || !(Boolean) this.fakeCheck.getValue()) {
            this.allowNext = true;
            if (this.pendingExplosion) {
                this.pendingExplosion = false;
                if (this.explosionHorizontal.getValue() > 0) {
                    event.setX(event.getX() * (double) this.explosionHorizontal.getValue() / 100.0);
                    event.setZ(event.getZ() * (double) this.explosionHorizontal.getValue() / 100.0);
                } else {
                    event.setX(mc.thePlayer.motionX);
                    event.setZ(mc.thePlayer.motionZ);
                }
                if (this.explosionVertical.getValue() > 0) {
                    event.setY(event.getY() * (double) this.explosionVertical.getValue() / 100.0);
                } else {
                    event.setY(mc.thePlayer.motionY);
                }
            } else {
                this.chanceCounter = this.chanceCounter % 100 + this.chance.getValue();
                if (this.chanceCounter >= 100) {
                    if (this.horizontal.getValue() > 0) {
                        event.setX(event.getX() * (double) this.horizontal.getValue() / 100.0);
                        event.setZ(event.getZ() * (double) this.horizontal.getValue() / 100.0);
                    } else {
                        event.setX(mc.thePlayer.motionX);
                        event.setZ(mc.thePlayer.motionZ);
                    }
                    if (this.vertical.getValue() > 0) {
                        event.setY(event.getY() * (double) this.vertical.getValue() / 100.0);
                    } else {
                        event.setY(mc.thePlayer.motionY);
                    }
                }
            }
        }
    }

    @EventTarget
    public void onAttack(AttackEvent event) {

        if (this.isEnabled() && this.isGrimJump() && !event.isCancelled()) {
            if (mc.thePlayer == null) return;
            boolean moving = mc.thePlayer.moveForward != 0.0F || mc.thePlayer.moveStrafing != 0.0F;
            if (!moving || !mc.thePlayer.isSprinting()) return;
            switch (mc.thePlayer.hurtTime) {
                case 9:
                    mc.thePlayer.motionX *= 0.8;
                    mc.thePlayer.motionZ *= 0.8;
                    break;
                case 8:
                    mc.thePlayer.motionX *= 0.11;
                    mc.thePlayer.motionZ *= 0.11;
                    break;
                case 7:
                    mc.thePlayer.motionX *= 0.4;
                    mc.thePlayer.motionZ *= 0.4;
                    break;
                case 4:
                    mc.thePlayer.motionX *= 0.37;
                    mc.thePlayer.motionZ *= 0.37;
                    break;
            }
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {

        if (this.isEnabled() && this.isGrimJump()) {
            if (mc.thePlayer.hurtTime > 5 && mc.thePlayer.onGround && !this.isInLiquidOrWeb()) {
                mc.thePlayer.jump();
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (this.isEnabled() && event.getType() == EventType.RECEIVE && !event.isCancelled()) {
            if (event.getPacket() instanceof net.minecraft.network.play.server.S27PacketExplosion) {
                net.minecraft.network.play.server.S27PacketExplosion packet =
                        (net.minecraft.network.play.server.S27PacketExplosion) event.getPacket();
                if (packet.func_149149_c() != 0.0F || packet.func_149144_d() != 0.0F || packet.func_149147_e() != 0.0F) {
                    this.pendingExplosion = true;
                    if (this.explosionHorizontal.getValue() == 0 || this.explosionVertical.getValue() == 0) {
                        event.setCancelled(true);
                    }
                }
            } else if (event.getPacket() instanceof net.minecraft.network.play.server.S19PacketEntityStatus) {
                net.minecraft.network.play.server.S19PacketEntityStatus packet =
                        (net.minecraft.network.play.server.S19PacketEntityStatus) event.getPacket();
                net.minecraft.entity.Entity entity = packet.getEntity(mc.theWorld);
                if (entity != null && entity.equals(mc.thePlayer) && packet.getOpCode() == 2) {
                    this.allowNext = false;
                }
            }
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.onDisabled();
    }

    @Override
    public void onDisabled() {
        this.pendingExplosion = false;
        this.allowNext = true;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }
}
