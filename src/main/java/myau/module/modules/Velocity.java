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
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;

import java.util.concurrent.ConcurrentLinkedDeque;

public class Velocity extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private int chanceCounter = 0;
    private boolean pendingExplosion = false;
    private boolean allowNext = true;

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"Vanilla", "Grim Jump", "JumpReset", "Kaizen Delay [BETA]"});
    public final PercentProperty chance = new PercentProperty("chance", 100);
    public final PercentProperty horizontal = new PercentProperty("horizontal", 0);
    public final PercentProperty vertical = new PercentProperty("vertical", 100);
    public final PercentProperty explosionHorizontal = new PercentProperty("explosions-horizontal", 100);
    public final PercentProperty explosionVertical = new PercentProperty("explosions-vertical", 100);
    public final BooleanProperty fakeCheck = new BooleanProperty("fake-check", true);

    private boolean freezeDelaying = false;
    private int freezeTimeout = 0;
    private boolean receivedS08 = false;
    private boolean gotKnockback = false;
    private int spoofTimer = 0;
    private final ConcurrentLinkedDeque<Packet<INetHandlerPlayClient>> freezePacketQueue = new ConcurrentLinkedDeque<>();
    private boolean spoofingPositions = false;

    private boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava() || ((IAccessorEntity) mc.thePlayer).getIsInWeb();
    }

    private boolean isGrimJump() {
        return this.mode.getValue() == 1;
    }

    private boolean isJumpReset() {
        return this.mode.getValue() == 2;
    }

    private boolean isKaizenDelayMode() {
        return this.mode.getValue() == 3;
    }

    public Velocity() {
        super("Velocity", false);
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        freezePacketQueue.clear();
        freezeDelaying = false;
        freezeTimeout = 0;
        receivedS08 = false;
        gotKnockback = false;
        this.pendingExplosion = false;
        this.allowNext = true;
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        flushFreeze();
        freezePacketQueue.clear();
        freezeDelaying = false;
        freezeTimeout = 0;
        spoofTimer = 0;
        spoofingPositions = false;
        receivedS08 = false;
        gotKnockback = false;
        this.pendingExplosion = false;
        this.allowNext = true;
    }

    @EventTarget
    public void onKnockback(KnockbackEvent event) {
        if (!this.isEnabled() || event.isCancelled()) {
            this.pendingExplosion = false;
            this.allowNext = true;
        } else if (this.isKaizenDelayMode()) {
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
        if (!this.isEnabled() || event.isCancelled()) {
            this.pendingExplosion = false;
            this.allowNext = true;
            return;
        }

        if (this.isKaizenDelayMode()) {
            if (event.getType() == EventType.SEND && freezeDelaying) {
                Packet<?> packet = event.getPacket();
                if (packet instanceof net.minecraft.network.play.client.C03PacketPlayer) {
                    net.minecraft.network.play.client.C03PacketPlayer.C06PacketPlayerPosLook fakePacket = 
                        new net.minecraft.network.play.client.C03PacketPlayer.C06PacketPlayerPosLook(
                            mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ,
                            mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch,
                            false
                        );
                    myau.util.PacketUtil.sendPacketNoEvent(fakePacket);
                    event.setCancelled(true);
                    return;
                }
            }

            if (event.getType() == EventType.RECEIVE) {
                Packet<?> packet = event.getPacket();

                if (packet instanceof S08PacketPlayerPosLook) {
                    if (freezeDelaying) {
                        freezePacketQueue.clear();
                        freezeDelaying = false;
                        freezeTimeout = 0;
                        gotKnockback = false;
                    }
                    spoofingPositions = false;
                    spoofTimer = 0;
                    receivedS08 = true;
                    return;
                }

                if (packet instanceof S19PacketEntityStatus) {
                    S19PacketEntityStatus s19 = (S19PacketEntityStatus) packet;
                    if (s19.getEntity(mc.theWorld) != null &&
                            s19.getEntity(mc.theWorld).equals(mc.thePlayer) &&
                            s19.getOpCode() == 2) {
                        gotKnockback = true;
                    }
                }

                if (packet instanceof S12PacketEntityVelocity) {
                    S12PacketEntityVelocity s12 = (S12PacketEntityVelocity) packet;
                    if (s12.getEntityID() == mc.thePlayer.getEntityId()) {
                        if (receivedS08) {
                            receivedS08 = false;
                        } else {
                            return; 
                        }
                        
                        if (!gotKnockback) {
                            return; 
                        }
                        
                        if (freezeDelaying) {
                            event.setCancelled(true);
                            return;
                        }
                        
                        freezeDelaying = true;
                        freezeTimeout = 0;
                        spoofTimer = 0;
                        spoofingPositions = true;
                        event.setCancelled(true);
                        @SuppressWarnings("unchecked")
                        Packet<INetHandlerPlayClient> playPacket = (Packet<INetHandlerPlayClient>) packet;
                        freezePacketQueue.add(playPacket);
                        return;
                    }
                }

                if (freezeDelaying && packet instanceof S32PacketConfirmTransaction) {
                    event.setCancelled(true);
                    @SuppressWarnings("unchecked")
                    Packet<INetHandlerPlayClient> playPacket = (Packet<INetHandlerPlayClient>) packet;
                    freezePacketQueue.add(playPacket);
                    return;
                }

                if (packet instanceof S27PacketExplosion) {
                    S27PacketExplosion pkt = (S27PacketExplosion) packet;
                    if (pkt.func_149149_c() != 0.0F || pkt.func_149144_d() != 0.0F || pkt.func_149147_e() != 0.0F) {
                        this.pendingExplosion = true;
                    }
                }
            }
        } else {
            if (event.getType() == EventType.RECEIVE && !event.isCancelled()) {
                if (event.getPacket() instanceof S27PacketExplosion) {
                    S27PacketExplosion packet = (S27PacketExplosion) event.getPacket();
                    if (packet.func_149149_c() != 0.0F || packet.func_149144_d() != 0.0F || packet.func_149147_e() != 0.0F) {
                        this.pendingExplosion = true;
                        if (this.explosionHorizontal.getValue() == 0 || this.explosionVertical.getValue() == 0) {
                            event.setCancelled(true);
                        }
                    }
                } else if (event.getPacket() instanceof S19PacketEntityStatus) {
                    S19PacketEntityStatus packet = (S19PacketEntityStatus) event.getPacket();
                    net.minecraft.entity.Entity entity = packet.getEntity(mc.theWorld);
                    if (entity != null && entity.equals(mc.thePlayer) && packet.getOpCode() == 2) {
                        this.allowNext = false;
                    }
                }
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled())
            return;

        if (event.getType() == EventType.POST) {
            if (this.isKaizenDelayMode()) {
                if (this.freezeDelaying) {
                    this.freezeTimeout++;

                    if (gotKnockback) {
                        boolean moving = mc.thePlayer.moveForward != 0.0F || mc.thePlayer.moveStrafing != 0.0F;
                        if (moving && this.freezeTimeout >= 15) { 
                            this.freezeTimeout = 30; 
                        }
                    }

                    if (this.freezeTimeout >= 30) {
                        flushFreeze();
                    }
                }
                receivedS08 = false;
            } else {
                this.pendingExplosion = false;
                this.allowNext = true;
            }
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        flushFreeze();
        this.onDisabled();
    }

    private void flushFreeze() {
        if (freezePacketQueue.isEmpty()) {
            freezeDelaying = false;
            freezeTimeout = 0;
            gotKnockback = false;
            return;
        }

        synchronized (freezePacketQueue) {
            while (!freezePacketQueue.isEmpty()) {
                Packet<INetHandlerPlayClient> packet = freezePacketQueue.poll();
                if (packet != null) {
                    try {
                        packet.processPacket(mc.getNetHandler());
                    } catch (Exception e) {
                    }
                }
            }
        }
        freezeDelaying = false;
        freezeTimeout = 0;
        gotKnockback = false;
        receivedS08 = false;
    }

    @Override
    public String[] getSuffix() {
        if (this.isKaizenDelayMode() && freezeDelaying) {
            return new String[]{"Kaizen Delay " + freezeTimeout + "/30"};
        }
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }
}
