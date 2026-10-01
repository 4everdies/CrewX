package crewx.module.modules.combat;

import crewx.CrewX;
import crewx.event.EventTarget;
import crewx.event.types.EventType;
import crewx.event.types.Priority;
import crewx.events.Render3DEvent;
import crewx.events.KnockbackEvent;
import crewx.events.StrafeEvent;
import crewx.events.UpdateEvent;
import crewx.module.Module;
import crewx.module.modules.movement.Fly;
import crewx.module.modules.movement.LongJump;
import crewx.module.modules.movement.Speed;
import crewx.module.modules.render.HUD;
import crewx.property.properties.BooleanProperty;
import crewx.property.properties.FloatProperty;
import crewx.property.properties.IntProperty;
import crewx.property.properties.ModeProperty;
import crewx.util.ColorUtil;
import crewx.util.MoveUtil;
import crewx.util.PlayerUtil;
import crewx.util.RotationUtil;
import crewx.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;

import java.awt.Color;

/** Keeps the player on a circular path around the active KillAura target. */
public class TargetStrafe extends Module {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final double TWO_PI = Math.PI * 2.0D;

    private EntityLivingBase target;
    private float targetYaw = Float.NaN;
    private int direction = 1;
    private int cachedPoints = -1;
    private double[] circleX = new double[0];
    private double[] circleZ = new double[0];
    private boolean orbitActive;

    public final FloatProperty radius = new FloatProperty("radius", 1.0F, 0.0F, 6.0F);
    public final IntProperty points = new IntProperty("points", 6, 3, 24);
    public final BooleanProperty requirePress = new BooleanProperty("require-press", true);
    public final BooleanProperty speedOnly = new BooleanProperty("speed-only", true);
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"Normal", "Kaizen"});
    public final ModeProperty showTarget = new ModeProperty("show-target", 1, new String[]{"None", "Default", "HUD"});

    public TargetStrafe() {
        super("TargetStrafe", false);
    }

    public float getTargetYaw() {
        return this.targetYaw;
    }

    private boolean kaizenActive() {
        if (this.mode.getValue() != 1) return true;
        Fly fly = (Fly) CrewX.moduleManager.modules.get(Fly.class);
        return fly != null && fly.isKaizenFlightActive();
    }

    private boolean canStrafe() {
        if (!this.kaizenActive()) return false;
        if (this.requirePress.getValue() && !PlayerUtil.isJumping()) return false;
        if (!this.speedOnly.getValue()) return true;

        Speed speed = (Speed) CrewX.moduleManager.modules.get(Speed.class);
        Fly fly = (Fly) CrewX.moduleManager.modules.get(Fly.class);
        LongJump longJump = (LongJump) CrewX.moduleManager.modules.get(LongJump.class);
        return speed != null && fly != null && longJump != null
                && (speed.isEnabled() || fly.isEnabled() || longJump.isEnabled() && longJump.isJumping());
    }

    private void clearOrbit() {
        this.target = null;
        this.targetYaw = Float.NaN;
        this.orbitActive = false;
    }

    private boolean hasMovementInput() {
        return MC.thePlayer != null && MC.thePlayer.movementInput != null
                && (Math.abs(MC.thePlayer.movementInput.moveForward) > 0.01F
                || Math.abs(MC.thePlayer.movementInput.moveStrafe) > 0.01F);
    }

    private EntityLivingBase getKillAuraTarget() {
        KillAura killAura = (KillAura) CrewX.moduleManager.modules.get(KillAura.class);
        if (killAura == null || !killAura.isEnabled() || !killAura.isAttackAllowed()) return null;
        EntityLivingBase candidate = killAura.getTarget();
        return TeamUtil.isEntityLoaded(candidate) ? candidate : null;
    }

    private void rebuildCircle() {
        int count = Math.max(3, this.points.getValue());
        if (count == this.cachedPoints) return;
        this.cachedPoints = count;
        this.circleX = new double[count];
        this.circleZ = new double[count];
        for (int i = 0; i < count; i++) {
            double angle = i * TWO_PI / count;
            this.circleX[i] = Math.cos(angle);
            this.circleZ[i] = Math.sin(angle);
        }
    }

    private Color getTargetColor(EntityLivingBase entity) {
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            if (TeamUtil.isFriend(player)) return CrewX.friendManager.getColor();
            if (TeamUtil.isTarget(player)) return CrewX.targetManager.getColor();
        }
        if (this.showTarget.getValue() == 1) {
            return entity instanceof EntityPlayer ? TeamUtil.getTeamColor((EntityPlayer) entity, 1.0F) : Color.WHITE;
        }
        if (this.showTarget.getValue() == 2) {
            HUD hud = (HUD) CrewX.moduleManager.modules.get(HUD.class);
            return hud == null ? Color.WHITE : hud.getColor(System.currentTimeMillis());
        }
        return Color.WHITE;
    }

    private boolean isInWater(double x, double z) {
        return PlayerUtil.checkInWater(new AxisAlignedBB(
                x - 0.015D, MC.thePlayer.posY, z - 0.015D,
                x + 0.015D, MC.thePlayer.posY + MC.thePlayer.height, z + 0.015D));
    }

    private int closestPoint() {
        int closest = 0;
        double best = Double.MAX_VALUE;
        double radiusValue = Math.max(0.05D, this.radius.getValue().doubleValue());
        for (int i = 0; i < this.circleX.length; i++) {
            double x = this.target.posX + circleX[i] * radiusValue;
            double z = this.target.posZ + circleZ[i] * radiusValue;
            double dx = MC.thePlayer.posX - x;
            double dz = MC.thePlayer.posZ - z;
            double distanceSq = dx * dx + dz * dz;
            if (distanceSq < best) {
                best = distanceSq;
                closest = i;
            }
        }
        return closest;
    }

    @EventTarget(Priority.HIGHEST)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || MC.thePlayer == null || MC.theWorld == null) return;
        this.orbitActive = false;
        this.targetYaw = Float.NaN;
        if (PlayerUtil.isMovingLeft() ^ PlayerUtil.isMovingRight()) {
            this.direction = PlayerUtil.isMovingLeft() ? 1 : -1;
        }
        if (MC.thePlayer.isCollidedHorizontally) this.direction *= -1;

        if (!this.canStrafe() || !this.hasMovementInput()
                || (this.target = this.getKillAuraTarget()) == null) {
            this.clearOrbit();
            return;
        }

        this.rebuildCircle();
        int current = this.closestPoint();
        double orbitRadius = Math.max(0.05D, this.radius.getValue().doubleValue());
        double currentX = this.target.posX + this.circleX[current] * orbitRadius;
        double currentZ = this.target.posZ + this.circleZ[current] * orbitRadius;
        if (this.isInWater(currentX, currentZ)) {
            this.direction *= -1;
        }

        // Move along the circle tangent. Pointing at the next waypoint creates
        // radial correction and is what previously pulled the player backward.
        double offsetX = MC.thePlayer.posX - this.target.posX;
        double offsetZ = MC.thePlayer.posZ - this.target.posZ;
        double distance = Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);
        if (distance < 0.001D) {
            offsetX = Math.cos(Math.toRadians(MC.thePlayer.rotationYaw));
            offsetZ = Math.sin(Math.toRadians(MC.thePlayer.rotationYaw));
            distance = 1.0D;
        }
        double radialX = offsetX / distance;
        double radialZ = offsetZ / distance;
        double tangentX = this.direction > 0 ? -radialZ : radialZ;
        double tangentZ = this.direction > 0 ? radialX : -radialX;
        double radiusError = orbitRadius - distance;
        // Tangent movement remains dominant; radial correction only keeps the
        // actual player-to-target distance on the configured radius.
        double correction = Math.max(-0.85D, Math.min(0.85D, radiusError * 1.35D));
        double desiredX = tangentX + radialX * correction;
        double desiredZ = tangentZ + radialZ * correction;
        this.targetYaw = (float) Math.toDegrees(Math.atan2(desiredZ, desiredX)) - 90.0F;
        this.targetYaw = net.minecraft.util.MathHelper.wrapAngleTo180_float(this.targetYaw);
        this.orbitActive = true;
        event.setPervRotation(this.targetYaw, 10);
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (this.isEnabled() && this.orbitActive && !Float.isNaN(this.targetYaw)) {
            // Do not create movement: standing still must keep the player still.
            // The original input is checked before replacing it with orbit input.
            if (Math.abs(event.getForward()) <= 0.01F && Math.abs(event.getStrafe()) <= 0.01F) {
                event.setStrafe(0.0F);
                event.setForward(0.0F);
                return;
            }
            event.setStrafe(0.0F);
            event.setForward(1.0F);
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onKnockback(KnockbackEvent event) {
        if (this.isEnabled() && this.mode.getValue() == 1) {
            // Keep the module enabled, but invalidate its movement target until
            // Fly Kaizen grants flight again.
            this.clearOrbit();
        }
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (!this.isEnabled() || this.showTarget.getValue() == 0 || !TeamUtil.isEntityLoaded(this.target)) return;
        Color color = this.getTargetColor(this.target);
        crewx.util.RenderUtil.enableRenderState();
        crewx.util.RenderUtil.drawEntityCircle(this.target, this.radius.getValue(), this.points.getValue(), ColorUtil.darker(color, 0.2F).getRGB());
        crewx.util.RenderUtil.drawEntityCircle(this.target, this.radius.getValue(), this.points.getValue(), color.getRGB());
        crewx.util.RenderUtil.disableRenderState();
    }

    @Override
    public void onDisabled() {
        this.target = null;
        this.targetYaw = Float.NaN;
        this.orbitActive = false;
    }
}
