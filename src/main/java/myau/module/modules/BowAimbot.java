package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.util.RandomUtil;
import myau.util.RotationUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class BowAimbot extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int ROTATION_PRIORITY = 2;

    public final ModeProperty mode = new ModeProperty("mode", 1, new String[]{"Normal", "Silent"});
    public final ModeProperty sortMode = new ModeProperty("target", 0, new String[]{"Distance", "Angle", "Health"});
    public final FloatProperty range = new FloatProperty("range", 60.0F, 5.0F, 128.0F);
    public final IntProperty fov = new IntProperty("fov", 180, 30, 360);
    public final PercentProperty minCharge = new PercentProperty("min-charge", 15);
    public final PercentProperty smoothing = new PercentProperty("smoothing", 65);
    public final FloatProperty predict = new FloatProperty("predict", 1.0F, 0.0F, 5.0F);
    public final BooleanProperty throughWalls = new BooleanProperty("through-walls", false);
    public final BooleanProperty teams = new BooleanProperty("teams", true);
    public final BooleanProperty antiBot = new BooleanProperty("anti-bot", true);
    public final BooleanProperty moveFix = new BooleanProperty("move-fix", true, () -> this.mode.getValue() == 1);
    private EntityPlayer target = null;
    private float silentYaw = Float.NaN;
    private float silentPitch = Float.NaN;

    public BowAimbot() {
        super("BowAimbot", false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString().charAt(0) + this.mode.getModeString().substring(1).toLowerCase()};
    }

    @Override
    public void onDisabled() {
        this.reset();
    }

    private void reset() {
        this.target = null;
        this.silentYaw = Float.NaN;
        this.silentPitch = Float.NaN;
    }

    public EntityPlayer getTarget() {
        return this.target;
    }
    private float getBowPower() {
        float charge = (float) mc.thePlayer.getItemInUseDuration() / 20.0F;
        charge = (charge * charge + charge * 2.0F) / 3.0F;
        return Math.min(charge, 1.0F);
    }

    private boolean isHoldingDrawnBow() {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemBow)) {
            return false;
        }
        if (!mc.thePlayer.isUsingItem()) {
            return false;
        }
        return this.getBowPower() >= (float) this.minCharge.getValue() / 100.0F;
    }

    private boolean isValidTarget(EntityPlayer player) {
        if (player == mc.thePlayer || player == mc.thePlayer.ridingEntity) {
            return false;
        }
        if (player == mc.getRenderViewEntity() || player == mc.getRenderViewEntity().ridingEntity) {
            return false;
        }
        if (player.isDead || player.deathTime > 0 || player.getHealth() <= 0.0F) {
            return false;
        }
        if (player.isInvisible() || TeamUtil.isShop(player)) {
            return false;
        }
        if (mc.thePlayer.getDistanceToEntity(player) > this.range.getValue()) {
            return false;
        }
        if (RotationUtil.angleToEntity(player) > (float) this.fov.getValue()) {
            return false;
        }
        if (!this.throughWalls.getValue() && RotationUtil.rayTrace(player) != null) {
            return false;
        }
        if (TeamUtil.isFriend(player)) {
            return false;
        }
        if (this.teams.getValue() && TeamUtil.isSameTeam(player)) {
            return false;
        }
        if (this.antiBot.getValue()) {
            AntiBot antiBotModule = (AntiBot) Myau.moduleManager.modules.get(AntiBot.class);
            if (antiBotModule != null && antiBotModule.isEnabled() && antiBotModule.isBot(player)) {
                return false;
            }
        }
        return true;
    }

    private EntityPlayer findTarget() {
        List<EntityPlayer> candidates = mc.theWorld
                .loadedEntityList
                .stream()
                .filter(entity -> entity instanceof EntityPlayer)
                .map(entity -> (EntityPlayer) entity)
                .filter(this::isValidTarget)
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            return null;
        }

        Comparator<EntityPlayer> comparator;
        switch (this.sortMode.getValue()) {
            case 1:
                comparator = Comparator.comparingDouble(RotationUtil::angleToEntity);
                break;
            case 2:
                comparator = Comparator.comparingDouble(TeamUtil::getHealthScore);
                break;
            default:
                comparator = Comparator.comparingDouble(entity -> mc.thePlayer.getDistanceToEntity(entity));
                break;
        }
        return candidates.stream().min(comparator).orElse(null);
    }

    private float[] getBallisticRotations(EntityPlayer player, float power) {
        float predictSize = this.predict.getValue();
        double motionX = (player.posX - player.prevPosX) * predictSize;
        double motionY = (player.posY - player.prevPosY) * predictSize;
        double motionZ = (player.posZ - player.prevPosZ) * predictSize;

        double deltaX = player.posX + motionX - mc.thePlayer.posX;
        double deltaY = (player.posY + motionY + (double) player.getEyeHeight() - 0.15)
                - (mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight());
        double deltaZ = player.posZ + motionZ - mc.thePlayer.posZ;

        double horizontal = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float yaw = (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0F;

        float pitch;
        double gravity = 0.006;
        double powerSq = power * power;
        double discriminant = powerSq * powerSq - gravity * (gravity * horizontal * horizontal + 2.0 * deltaY * powerSq);

        if (discriminant < 0.0 || horizontal <= 0.0) {
            pitch = (float) -Math.toDegrees(Math.atan2(deltaY, horizontal));
        } else {
            pitch = (float) -Math.toDegrees(Math.atan((powerSq - Math.sqrt(discriminant)) / (gravity * horizontal)));
        }

        if (Float.isNaN(pitch) || Float.isInfinite(pitch)) {
            pitch = (float) -Math.toDegrees(Math.atan2(deltaY, horizontal));
        }

        return new float[]{yaw, MathHelper.clamp_float(pitch, -90.0F, 90.0F)};
    }

    private float[] applySmoothing(float currentYaw, float currentPitch, float targetYaw, float targetPitch) {
        float factor = 1.0F - MathHelper.clamp_float((float) this.smoothing.getValue() / 100.0F, 0.0F, 0.95F);

        float yawDelta = MathHelper.wrapAngleTo180_float(targetYaw - currentYaw);
        float pitchDelta = MathHelper.clamp_float(targetPitch - currentPitch, -90.0F, 90.0F);

        float yawStep = yawDelta * factor * RandomUtil.nextFloat(0.85F, 1.15F);
        float pitchStep = pitchDelta * factor * RandomUtil.nextFloat(0.85F, 1.15F);

        // Nunca ultrapassar o alvo.
        if (Math.abs(yawStep) > Math.abs(yawDelta)) {
            yawStep = yawDelta;
        }
        if (Math.abs(pitchStep) > Math.abs(pitchDelta)) {
            pitchStep = pitchDelta;
        }

        float newYaw = RotationUtil.quantizeAngle(currentYaw + yawStep);
        float newPitch = RotationUtil.quantizeAngle(MathHelper.clamp_float(currentPitch + pitchStep, -90.0F, 90.0F));
        return new float[]{newYaw, newPitch};
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (!this.isHoldingDrawnBow()) {
            this.reset();
            return;
        }

        this.target = this.findTarget();
        if (this.target == null) {
            this.silentYaw = Float.NaN;
            this.silentPitch = Float.NaN;
            return;
        }

        float[] wanted = this.getBallisticRotations(this.target, this.getBowPower());

        if (this.mode.getValue() == 0) {
            float[] stepped = this.applySmoothing(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, wanted[0], wanted[1]);
            Myau.rotationManager.setRotation(stepped[0], stepped[1], ROTATION_PRIORITY, true);
        } else {
            if (Float.isNaN(this.silentYaw) || Float.isNaN(this.silentPitch)) {
                this.silentYaw = event.getYaw();
                this.silentPitch = event.getPitch();
            }
            float[] stepped = this.applySmoothing(this.silentYaw, this.silentPitch, wanted[0], wanted[1]);
            this.silentYaw = stepped[0];
            this.silentPitch = stepped[1];
            event.setRotation(stepped[0], stepped[1], ROTATION_PRIORITY);
            if (this.moveFix.getValue()) {
                event.setPervRotation(stepped[0], ROTATION_PRIORITY);
            }
        }
    }
}
