package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.Priority;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
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

    public final IntProperty minDelay = new IntProperty("min-delay", 100, 0, 1000);
    public final IntProperty maxDelay = new IntProperty("max-delay", 250, 0, 1000);
    public final FloatProperty range = new FloatProperty("range", 3.0F, 0.0F, 10.0F);
    public final IntProperty chance = new IntProperty("chance", 100, 0, 100);
    public final IntProperty nextBacktrackDelay = new IntProperty("next-backtrack-delay", 0, 0, 2000);
    public final BooleanProperty pauseOnHurtTime = new BooleanProperty("pause-on-hurttime", false);
    public final IntProperty hurtTimeThreshold = new IntProperty("hurttime-threshold", 3, 0, 10, () -> this.pauseOnHurtTime.getValue());
    public final BooleanProperty standalone = new BooleanProperty("standalone", true);
    public final BooleanProperty showPosition = new BooleanProperty("show-position", true);

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

    @EventTarget(Priority.LOW)
    public void onTick(TickEvent event) {
        if (!this.isEnabled()) {
            return;
        }

        KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        EntityLivingBase target = null;
        if (killAura != null && killAura.isEnabled()) {
            target = killAura.getTarget();
        }

        if (target == null && this.standalone.getValue()
                && mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.ENTITY
                && mc.objectMouseOver.entityHit instanceof EntityLivingBase
                && mc.objectMouseOver.entityHit != mc.thePlayer) {
            target = (EntityLivingBase) mc.objectMouseOver.entityHit;
        }


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
