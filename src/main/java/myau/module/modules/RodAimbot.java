package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.RightClickMouseEvent;
import myau.events.TickEvent;
import myau.mixin.IAccessorMinecraft;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.util.RotationUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.util.MathHelper;

public class RodAimbot extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final FloatProperty fov = new FloatProperty("fov", 180f, 30f, 360f);
    public final IntProperty predictedTicks = new IntProperty("predicted-ticks", 5, 0, 20);
    public final FloatProperty distance = new FloatProperty("distance", 6f, 3f, 30f);
    public final BooleanProperty aimInvis = new BooleanProperty("aim-invis", false);
    public final BooleanProperty ignoreTeammates = new BooleanProperty("ignore-teammates", false);

    private boolean rotate = false;
    private boolean rightClick = false;
    private boolean processing = false;
    private EntityPlayer target = null;

    public RodAimbot() {
        super("RodAimbot", false);
    }

    @Override
    public void onDisabled() {
        rotate = false;
        rightClick = false;
        processing = false;
        target = null;
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (processing) return;
        if (mc.currentScreen != null) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.thePlayer.getCurrentEquippedItem() == null) return;
        if (!(mc.thePlayer.getCurrentEquippedItem().getItem() instanceof ItemFishingRod)) return;
        if (mc.thePlayer.fishEntity != null) return;

        EntityPlayer entity = findTarget();
        if (entity == null) return;

        event.setCancelled(true);
        target = entity;
        rightClick = true;
        rotate = true;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.POST) return;
        if (!rotate && !rightClick) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.thePlayer.getCurrentEquippedItem() == null) return;
        if (!(mc.thePlayer.getCurrentEquippedItem().getItem() instanceof ItemFishingRod)) return;
        if (target == null) return;

        float[] rotations = RotationUtil.getRotationsPredicated(target, predictedTicks.getValue());
        mc.thePlayer.rotationYaw = rotations[0];
        mc.thePlayer.rotationPitch = rotations[1];

        if (!rightClick && rotate) {
            rotate = false;
        }

        if (rightClick) {
            processing = true;
            ((IAccessorMinecraft) mc).callRightClickMouse();
            processing = false;
            rightClick = false;
            rotate = false;
            target = null;
        }
    }

    private EntityPlayer findTarget() {
        for (EntityPlayer entityPlayer : mc.theWorld.playerEntities) {
            if (entityPlayer == mc.thePlayer) continue;
            if (entityPlayer.deathTime != 0) continue;
            if (!aimInvis.getValue() && entityPlayer.isInvisible()) continue;
            if (mc.thePlayer.getDistanceSqToEntity(entityPlayer) > distance.getValue() * distance.getValue()) continue;
            if (Myau.friendManager.isFriend(entityPlayer.getName())) continue;
            float fovVal = fov.getValue();
            if (fovVal != 360.0f && !inFov(fovVal, entityPlayer)) continue;
            AntiBot antiBot = (AntiBot) Myau.moduleManager.modules.get(AntiBot.class);
            if (antiBot != null && antiBot.isBot(entityPlayer)) continue;
            if (ignoreTeammates.getValue() && TeamUtil.isSameTeam(entityPlayer)) continue;
            return entityPlayer;
        }
        return null;
    }

    private boolean inFov(float fovVal, EntityPlayer entity) {
        double dx = entity.posX - mc.thePlayer.posX;
        double dz = entity.posZ - mc.thePlayer.posZ;
        float angle = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float diff = MathHelper.wrapAngleTo180_float(angle - mc.thePlayer.rotationYaw);
        return Math.abs(diff) <= fovVal;
    }
}
