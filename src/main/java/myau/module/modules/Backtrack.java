package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.Priority;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

import java.awt.Color;
import java.util.Random;

public class Backtrack extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Random RANDOM = new Random();

    private Vec3 lastPosition = null;
    private Vec3 currentPosition = null;
    private boolean hasTarget = false;
    private int currentChance = -1;
    private long nextAllowedTime = 0L;

    private EntityLivingBase manualTarget = null;
    private long manualTargetTime = 0L;

    public final IntProperty minDelay = new IntProperty("min-delay", 100, 0, 1000);
    public final IntProperty maxDelay = new IntProperty("max-delay", 150, 0, 1000);
    public final FloatProperty range = new FloatProperty("range", 3.0F, 0.0F, 10.0F);
    public final IntProperty chance = new IntProperty("chance", 100, 0, 100);
    public final IntProperty nextBacktrackDelay = new IntProperty("next-backtrack-delay", 0, 0, 2000);
    public final BooleanProperty pauseOnHurtTime = new BooleanProperty("pause-on-hurttime", false);
    public final IntProperty hurtTimeThreshold = new IntProperty("hurttime-threshold", 3, 0, 10, () -> this.pauseOnHurtTime.getValue());
    public final BooleanProperty showPosition = new BooleanProperty("show-position", true);
    public final IntProperty manualWindow = new IntProperty("manual-hit-window", 500, 50, 2000);

    public Backtrack() {
        super("Backtrack", false);
    }

    private int randomDelay() {
        int min = Math.min(this.minDelay.getValue(), this.maxDelay.getValue());
        int max = Math.max(this.minDelay.getValue(), this.maxDelay.getValue());
        if (max <= min) {
            return min;
        }
        return min + RANDOM.nextInt(max - min + 1);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled()) return;
        if (event.getType() != myau.event.types.EventType.SEND) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof C02PacketUseEntity) {
            C02PacketUseEntity p = (C02PacketUseEntity) packet;
            if (p.getAction() == C02PacketUseEntity.Action.ATTACK && mc.theWorld != null) {
                Entity ent = p.getEntityFromWorld(mc.theWorld);
                if (ent instanceof EntityLivingBase && ent != mc.thePlayer) {
                    this.manualTarget = (EntityLivingBase) ent;
                    this.manualTargetTime = System.currentTimeMillis();
                }
            }
        }
    }

    private EntityLivingBase resolveTarget() {
        KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        if (killAura != null && killAura.isEnabled()) {
            EntityLivingBase t = killAura.getTarget();
            if (t != null) return t;
        }

        if (this.manualTarget != null && !this.manualTarget.isDead) {
            long age = System.currentTimeMillis() - this.manualTargetTime;
            if (age <= this.manualWindow.getValue()) {
                return this.manualTarget;
            } else {
                this.manualTarget = null;
            }
        }

        if (mc.pointedEntity instanceof EntityLivingBase
                && mc.pointedEntity != mc.thePlayer) {
            return (EntityLivingBase) mc.pointedEntity;
        }

        return null;
    }

    @EventTarget(Priority.LOW)
    public void onTick(TickEvent event) {
        if (!this.isEnabled()) {
            return;
        }

        EntityLivingBase target = resolveTarget();

        boolean shouldBacktrack = false;
        if (target != null) {
            float distance = mc.thePlayer.getDistanceToEntity(target);
            boolean inRange = distance <= this.range.getValue();
            boolean chanceOk;
            if (this.currentChance < 0) {
                this.currentChance = RANDOM.nextInt(100);
            }
            chanceOk = this.currentChance < this.chance.getValue();
            boolean paused = this.pauseOnHurtTime.getValue()
                    && target.hurtTime >= this.hurtTimeThreshold.getValue();
            boolean cooldownOk = System.currentTimeMillis() >= this.nextAllowedTime;
            shouldBacktrack = inRange && chanceOk && !paused && cooldownOk && mc.thePlayer.ticksExisted > 10;
        }

        if (shouldBacktrack) {
            int ticks = Math.max(1, this.randomDelay() / 50);
            Myau.lagManager.setDelay(ticks);
            this.hasTarget = true;
        } else {
            if (this.hasTarget) {
                int cd = this.nextBacktrackDelay.getValue();
                if (cd > 0) {
                    this.nextAllowedTime = System.currentTimeMillis() + cd;
                }
                this.currentChance = -1;
            }
            Myau.lagManager.setDelay(0);
            this.hasTarget = false;
        }

        if (event.getType() == myau.event.types.EventType.POST) {
            Vec3 savedPosition = Myau.lagManager.getLastPosition();
            this.lastPosition = this.currentPosition;
            this.currentPosition = savedPosition;
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (this.isEnabled() && this.showPosition.getValue() && this.hasTarget
                && this.lastPosition != null && this.currentPosition != null) {
            Color color = new Color(60, 162, 253, 100);
            double x = RenderUtil.lerpDouble(this.currentPosition.xCoord, this.lastPosition.xCoord, event.getPartialTicks());
            double y = RenderUtil.lerpDouble(this.currentPosition.yCoord, this.lastPosition.yCoord, event.getPartialTicks());
            double z = RenderUtil.lerpDouble(this.currentPosition.zCoord, this.lastPosition.zCoord, event.getPartialTicks());

            float size = mc.thePlayer.getCollisionBorderSize();
            AxisAlignedBB aabb = new AxisAlignedBB(
                    x - (double) mc.thePlayer.width / 2.0, y, z - (double) mc.thePlayer.width / 2.0,
                    x + (double) mc.thePlayer.width / 2.0, y + (double) mc.thePlayer.height, z + (double) mc.thePlayer.width / 2.0
            ).expand(size, size, size).offset(
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
        Myau.lagManager.setDelay(0);
        this.hasTarget = false;
        this.lastPosition = null;
        this.currentPosition = null;
        this.currentChance = -1;
        this.nextAllowedTime = 0L;
        this.manualTarget = null;
        this.manualTargetTime = 0L;
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
