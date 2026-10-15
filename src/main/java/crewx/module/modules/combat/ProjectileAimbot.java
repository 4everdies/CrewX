package crewx.module.modules.combat;

import crewx.CrewX;
import crewx.event.EventTarget;
import crewx.event.types.EventType;
import crewx.events.UpdateEvent;
import crewx.module.Module;
import crewx.module.modules.misc.AntiBot;
import crewx.property.properties.BooleanProperty;
import crewx.property.properties.FloatProperty;
import crewx.property.properties.IntProperty;
import crewx.property.properties.ModeProperty;
import crewx.property.properties.PercentProperty;
import crewx.util.RotationUtil;
import crewx.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;

import java.util.Comparator;

/**
 * Native CrewX implementation of projectile aiming for Minecraft 1.8.9.
 * Written independently for CrewX; it does not include code from GPL sources.
 */
public class ProjectileAimbot extends Module {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final int ROTATION_PRIORITY = 2;

    public final BooleanProperty bow = new BooleanProperty("bow", true);
    public final BooleanProperty egg = new BooleanProperty("egg", true);
    public final BooleanProperty snowball = new BooleanProperty("snowball", true);
    public final BooleanProperty pearl = new BooleanProperty("ender-pearl", false);
    public final BooleanProperty otherProjectiles = new BooleanProperty("other-projectiles", false);
    public final FloatProperty range = new FloatProperty("range", 30.0F, 3.0F, 100.0F);
    public final IntProperty fov = new IntProperty("fov", 180, 10, 360);
    public final ModeProperty priority = new ModeProperty("priority", 1,
            new String[]{"Distance", "Angle", "Health"});
    public final ModeProperty rotationMode = new ModeProperty("rotation-mode", 1,
            new String[]{"Normal", "Silent"});
    public final PercentProperty smoothing = new PercentProperty("smoothing", 65);
    public final FloatProperty prediction = new FloatProperty("prediction", 1.0F, 0.0F, 5.0F);
    public final BooleanProperty throughWalls = new BooleanProperty("through-walls", false);
    public final BooleanProperty teams = new BooleanProperty("teams", true);
    public final BooleanProperty antiBot = new BooleanProperty("anti-bot", true);
    public final PercentProperty minimumCharge = new PercentProperty("minimum-charge", 15,
            () -> this.bow.getValue());

    private EntityPlayer target;

    public ProjectileAimbot() {
        super("ProjectileAimbot", false);
    }

    public EntityPlayer getTarget() {
        return this.target;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.rotationMode.getModeString()};
    }

    @Override
    public void onDisabled() {
        this.target = null;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE
                || MC.thePlayer == null || MC.theWorld == null) {
            this.target = null;
            return;
        }

        ItemStack held = MC.thePlayer.getHeldItem();
        if (held == null) {
            this.target = null;
            return;
        }

        Projectile projectile = this.projectileFor(held);
        if (projectile == null) {
            this.target = null;
            return;
        }

        this.target = MC.theWorld.playerEntities.stream()
                .filter(entity -> this.isValidTarget(entity))
                .min(this.targetComparator())
                .orElse(null);
        if (this.target == null) return;

        float[] desired = this.projectileRotation(this.target, projectile);
        float factor = 1.0F - MathHelper.clamp_float(this.smoothing.getValue().floatValue() / 100.0F, 0.0F, 0.95F);
        float yawDelta = MathHelper.wrapAngleTo180_float(desired[0] - event.getYaw());
        float pitchDelta = desired[1] - event.getPitch();
        float yaw = RotationUtil.quantizeAngle(event.getYaw() + yawDelta * factor);
        float pitch = RotationUtil.quantizeAngle(MathHelper.clamp_float(
                event.getPitch() + pitchDelta * factor, -90.0F, 90.0F));

        if (this.rotationMode.getValue() == 0) {
            CrewX.rotationManager.setRotation(yaw, pitch, ROTATION_PRIORITY, true);
        } else {
            event.setRotation(yaw, pitch, ROTATION_PRIORITY);
            event.setPervRotation(yaw, ROTATION_PRIORITY);
        }
    }

    private Projectile projectileFor(ItemStack stack) {
        if (stack.getItem() instanceof ItemBow) {
            if (!this.bow.getValue() || !MC.thePlayer.isUsingItem()) return null;
            float charge = MC.thePlayer.getItemInUseDuration() / 20.0F;
            charge = (charge * charge + charge * 2.0F) / 3.0F;
            charge = Math.min(charge, 1.0F);
            if (charge * 100.0F < this.minimumCharge.getValue()) return null;
            return new Projectile(Math.max(0.1F, charge * 3.0F), 0.05F);
        }
        if (stack.getItem() instanceof ItemEgg) {
            return this.egg.getValue() ? new Projectile(1.5F, 0.03F) : null;
        }
        if (stack.getItem() instanceof ItemSnowball) {
            return this.snowball.getValue() ? new Projectile(1.5F, 0.03F) : null;
        }
        if (stack.getItem() instanceof ItemEnderPearl) {
            return this.pearl.getValue() ? new Projectile(1.5F, 0.03F) : null;
        }
        return this.otherProjectiles.getValue() ? new Projectile(1.5F, 0.03F) : null;
    }

    private boolean isValidTarget(EntityPlayer player) {
        if (player == null || player == MC.thePlayer || player.isDead || player.getHealth() <= 0.0F
                || player.isInvisible() || player == MC.thePlayer.ridingEntity) return false;
        double distance = MC.thePlayer.getDistanceToEntity(player);
        if (distance > this.range.getValue()) return false;
        if (RotationUtil.angleToEntity(player) * 0.5F > this.fov.getValue()) return false;
        if (!this.throughWalls.getValue() && RotationUtil.rayTrace(player) != null) return false;
        if (TeamUtil.isFriend(player) || TeamUtil.isShop(player)) return false;
        if (this.teams.getValue() && TeamUtil.isSameTeam(player)) return false;
        if (this.antiBot.getValue()) {
            AntiBot module = (AntiBot) CrewX.moduleManager.modules.get(AntiBot.class);
            if (module != null && module.isBot(player)) return false;
        }
        return true;
    }

    private Comparator<EntityPlayer> targetComparator() {
        switch (this.priority.getValue()) {
            case 1:
                return Comparator.comparingDouble(RotationUtil::angleToEntity);
            case 2:
                return Comparator.comparingDouble(TeamUtil::getHealthScore);
            default:
                return Comparator.comparingDouble(MC.thePlayer::getDistanceToEntity);
        }
    }

    private float[] projectileRotation(EntityPlayer entity, Projectile projectile) {
        double eyeX = MC.thePlayer.posX;
        double eyeY = MC.thePlayer.posY + MC.thePlayer.getEyeHeight();
        double eyeZ = MC.thePlayer.posZ;
        double targetX = entity.posX;
        double targetY = entity.posY + entity.getEyeHeight() * 0.82D;
        double targetZ = entity.posZ;
        double flightTime = 0.0D;
        double horizontal = 0.0D;
        double vertical = 0.0D;
        double speed = projectile.speed;
        double gravity = projectile.gravity;

        for (int i = 0; i < 4; i++) {
            double lead = flightTime * this.prediction.getValue();
            double dx = targetX + entity.motionX * lead - eyeX;
            double dz = targetZ + entity.motionZ * lead - eyeZ;
            horizontal = Math.sqrt(dx * dx + dz * dz);
            vertical = targetY + entity.motionY * lead - eyeY;
            flightTime = horizontal / Math.max(0.2D, speed);
        }

        double dx = targetX + entity.motionX * flightTime * this.prediction.getValue() - eyeX;
        double dz = targetZ + entity.motionZ * flightTime * this.prediction.getValue() - eyeZ;
        horizontal = Math.sqrt(dx * dx + dz * dz);
        vertical = targetY + entity.motionY * flightTime * this.prediction.getValue() - eyeY;
        double v2 = speed * speed;
        double discriminant = v2 * v2 - gravity * (gravity * horizontal * horizontal + 2.0D * vertical * v2);
        double pitchRadians;
        if (horizontal < 1.0E-4D || discriminant < 0.0D) {
            pitchRadians = Math.atan2(vertical, Math.max(1.0E-4D, horizontal));
        } else {
            double tangent = (v2 - Math.sqrt(discriminant)) / (gravity * horizontal);
            pitchRadians = Math.atan(tangent);
        }
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) -Math.toDegrees(pitchRadians);
        return new float[]{MathHelper.wrapAngleTo180_float(yaw), MathHelper.clamp_float(pitch, -90.0F, 90.0F)};
    }

    private static final class Projectile {
        private final float speed;
        private final float gravity;

        private Projectile(float speed, float gravity) {
            this.speed = speed;
            this.gravity = gravity;
        }
    }
}
