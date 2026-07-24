package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.util.RenderUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.server.S00PacketKeepAlive;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.AxisAlignedBB;

import java.awt.Color;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

public class Backtrack extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Random RANDOM = new Random();

    public final IntProperty minDelay = new IntProperty("min-delay", 100, 0, 1000);
    public final IntProperty maxDelay = new IntProperty("max-delay", 150, 0, 1000);
    public final FloatProperty range = new FloatProperty("range", 6.0F, 3.0F, 10.0F);
    public final IntProperty combatExpiry = new IntProperty("combat-expiry", 3000, 500, 10000);
    public final BooleanProperty pauseOnHurtTime = new BooleanProperty("pause-on-hurttime", false);
    public final IntProperty hurtTimeThreshold = new IntProperty("hurttime-threshold", 3, 0, 10);
    public final BooleanProperty showPosition = new BooleanProperty("show-position", true);

    private EntityPlayer target = null;
    private boolean inCombat = false;
    private long lastCombatMs = 0L;
    private long lagStartTime = 0L;
    private int currentDelay = 0;

    private final CopyOnWriteArrayList<DelayedPacket> packets = new CopyOnWriteArrayList<>();

    private static class DelayedPacket {
        public final Packet packet;
        public final long time;

        public DelayedPacket(Packet packet) {
            this.packet = packet;
            this.time = System.currentTimeMillis();
        }
    }

    public Backtrack() {
        super("Backtrack", false);
    }

    private int rollDelay() {
        int min = Math.min(this.minDelay.getValue(), this.maxDelay.getValue());
        int max = Math.max(this.minDelay.getValue(), this.maxDelay.getValue());
        if (max <= min) return min;
        return min + RANDOM.nextInt(max - min + 1);
    }

    private void flush() {
        this.inCombat = false;
        this.target = null;
        for (DelayedPacket dp : this.packets) {
            try {
                dp.packet.processPacket(mc.getNetHandler());
            } catch (Exception ignored) {}
        }
        this.packets.clear();
    }

    private boolean isValidTarget(Entity entity) {
        if (!(entity instanceof EntityPlayer)) return false;
        if (entity == mc.thePlayer) return false;
        
        EntityPlayer player = (EntityPlayer) entity;
        if (TeamUtil.isSameTeam(player)) return false;
        
        AntiBot antiBot = (AntiBot) Myau.moduleManager.modules.get(AntiBot.class);
        if (antiBot != null && antiBot.isEnabled() && antiBot.isBot(player)) return false;
        
        return true;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || mc.theWorld == null || mc.thePlayer == null) return;

        Packet packet = event.getPacket();

        if (event.getType() == myau.event.types.EventType.SEND) {
            if (packet instanceof C02PacketUseEntity) {
                C02PacketUseEntity p = (C02PacketUseEntity) packet;
                if (p.getAction() == C02PacketUseEntity.Action.ATTACK) {
                    Entity ent = p.getEntityFromWorld(mc.theWorld);
                    if (this.isValidTarget(ent)) {
                        this.target = (EntityPlayer) ent;
                        this.lastCombatMs = System.currentTimeMillis();
                        
                        if (!this.inCombat) {
                            this.inCombat = true;
                            this.currentDelay = this.rollDelay();
                            this.lagStartTime = System.currentTimeMillis();
                        }
                    }
                }
            }
        } else if (event.getType() == myau.event.types.EventType.RECEIVE) {
            if (this.inCombat && this.target != null) {
                if (packet instanceof S00PacketKeepAlive) return;

                if (packet instanceof S08PacketPlayerPosLook) {
                    this.flush();
                    return;
                }
                
                if (packet instanceof S06PacketUpdateHealth) {
                    S06PacketUpdateHealth s06 = (S06PacketUpdateHealth) packet;
                    if (s06.getHealth() <= 0.0F) {
                        this.flush();
                        return;
                    }
                }

                boolean isPaused = this.pauseOnHurtTime.getValue() && this.target.hurtTime >= this.hurtTimeThreshold.getValue();
                if (!isPaused) {
                    event.setCancelled(true);
                    this.packets.add(new DelayedPacket(packet));
                } else {
                    this.flush();
                }
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || mc.theWorld == null || mc.thePlayer == null) {
            this.flush();
            return;
        }

        if (this.inCombat) {
            if (this.target == null || this.target.isDead || System.currentTimeMillis() - this.lastCombatMs > this.combatExpiry.getValue()) {
                this.flush();
                return;
            }

            if (mc.thePlayer.getDistanceSqToEntity(this.target) > this.range.getValue() * this.range.getValue() * 4) {
                this.flush();
                return;
            }

            long now = System.currentTimeMillis();
            if (now - this.lagStartTime >= this.currentDelay) {
                for (DelayedPacket dp : this.packets) {
                    try {
                        dp.packet.processPacket(mc.getNetHandler());
                    } catch (Exception ignored) {}
                }
                this.packets.clear();
                this.lagStartTime = now;
                this.currentDelay = this.rollDelay();
            }
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (this.isEnabled() && this.showPosition.getValue() && this.inCombat && this.target != null && !this.packets.isEmpty()) {
            Color color = new Color(255, 165, 0, 120);
            
            double x = this.target.lastTickPosX + (this.target.posX - this.target.lastTickPosX) * event.getPartialTicks();
            double y = this.target.lastTickPosY + (this.target.posY - this.target.lastTickPosY) * event.getPartialTicks();
            double z = this.target.lastTickPosZ + (this.target.posZ - this.target.lastTickPosZ) * event.getPartialTicks();

            float size = this.target.width / 2.0F;
            AxisAlignedBB aabb = new AxisAlignedBB(
                    x - size, y, z - size,
                    x + size, y + this.target.height, z + size
            ).offset(
                    -((myau.mixin.IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(),
                    -((myau.mixin.IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(),
                    -((myau.mixin.IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()
            );

            RenderUtil.enableRenderState();
            RenderUtil.drawFilledBox(aabb, color.getRed(), color.getGreen(), color.getBlue());
            RenderUtil.disableRenderState();
        }
    }

    @Override
    public void onDisabled() {
        this.flush();
    }

    @Override
    public String[] getSuffix() {
        int min = Math.min(this.minDelay.getValue(), this.maxDelay.getValue());
        int max = Math.max(this.minDelay.getValue(), this.maxDelay.getValue());
        if (min == max) {
            return new String[]{min + "ms"};
        }
        return new String[]{min + "-" + max + "ms"};
    }
}

