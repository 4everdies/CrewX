package crewx.module.modules.combat;

import crewx.CrewX;
import crewx.event.EventTarget;
import crewx.event.EventManager;
import crewx.event.types.EventType;
import crewx.events.LeftClickMouseEvent;
import crewx.events.AttackEvent;
import crewx.events.Render3DEvent;
import crewx.events.UpdateEvent;
import crewx.mixin.IAccessorRenderManager;
import crewx.event.types.Priority;
import crewx.module.Module;
import crewx.module.modules.combat.AutoClicker;
import crewx.module.modules.combat.KillAura;
import crewx.property.properties.BooleanProperty;
import crewx.property.properties.FloatProperty;
import crewx.property.properties.ModeProperty;
import crewx.util.PacketUtil;
import crewx.util.PlayerUtil;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import crewx.mixin.IAccessorPlayerControllerMP;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.opengl.GL11;

public class Displace
extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Random RANDOM = new Random();
    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Left", "Right", "Random", "Void"});
    public final FloatProperty angle = new FloatProperty("Angle", Float.valueOf(90.0f), Float.valueOf(0.0f), Float.valueOf(180.0f));
    public final FloatProperty cooldown = new FloatProperty("Cooldown", Float.valueOf(0.0f), Float.valueOf(0.0f), Float.valueOf(5.0f));
    public final BooleanProperty hideRotations = new BooleanProperty("Hide rotations", false);
    public final BooleanProperty onlyKnockbackItems = new BooleanProperty("Only knockback items", false);
    public final BooleanProperty overrideAttacks = new BooleanProperty("Override attacks", false);
    public final BooleanProperty playersOnly = new BooleanProperty("Players only", true);
    public final BooleanProperty renderArrow = new BooleanProperty("Render arrow", false);
    public final BooleanProperty manual = new BooleanProperty("Manual", true);
    public final BooleanProperty autoClicker = new BooleanProperty("AutoClicker", false);
    public final BooleanProperty killAura = new BooleanProperty("KillAura", false);
    private long lastAt;
    private int side = -1;
    private int lastSide = -1;
    private boolean rotate;
    private EntityLivingBase pendingManualTarget;
    private EntityLivingBase preparedAuraTarget;
    private EntityLivingBase lastTarget;
    private long arrowAt;
    private double arrowX;
    private double arrowY;
    private double arrowZ;
    private double dirX;
    private double dirZ;

    public Displace() {
        super("Displace", false);
    }

    @Override
    public void onDisabled() {
        this.pendingManualTarget = null;
        this.preparedAuraTarget = null;
        this.rotate = false;
        this.lastSide = -1;
        this.side = -1;
        this.arrowAt = 0L;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }

    private boolean canDisplace(Entity entity, boolean checkHurtTime, boolean checkCooldown) {
        if (Displace.mc.thePlayer == null || Displace.mc.theWorld == null || !(entity instanceof EntityLivingBase)) {
            return false;
        }
        EntityLivingBase living = (EntityLivingBase)entity;
        if (checkHurtTime && living.hurtTime > 0) {
            return false;
        }
        if (((Boolean)this.playersOnly.getValue()).booleanValue() && !(entity instanceof EntityPlayer)) {
            return false;
        }
        int knockback = EnchantmentHelper.getKnockbackModifier((EntityLivingBase)Displace.mc.thePlayer);
        if (!Displace.mc.thePlayer.isSprinting() && knockback == 0) {
            return false;
        }
        if (((Boolean)this.onlyKnockbackItems.getValue()).booleanValue() && knockback == 0) {
            return false;
        }
        if (checkCooldown && (double)Math.abs(System.currentTimeMillis() - this.lastAt) < (double)((Float)this.cooldown.getValue()).floatValue() * 1000.0 - 20.0) {
            return false;
        }
        if ((Integer)this.mode.getValue() == 3) {
            this.side = this.lastSide = this.dropSide(living, this.lastSide);
            if (this.lastSide == -1) {
                return false;
            }
        }
        this.lastTarget = living;
        return true;
    }

    private int dropSide(EntityLivingBase target, int previous) {
        double dx = target.posX - Displace.mc.thePlayer.posX;
        double dz = target.posZ - Displace.mc.thePlayer.posZ;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.001) {
            return -1;
        }
        double rightX = -dz / length;
        double rightZ = dx / length;
        int left = 0;
        int right = 0;
        int y = (int)Math.floor(target.posY - 1.0);
        for (int i = 1; i <= 12; ++i) {
            double distance = (double)i * 0.5;
            if (this.isDrop(target.posX - rightX * distance, y, target.posZ - rightZ * distance)) {
                ++left;
            }
            if (!this.isDrop(target.posX + rightX * distance, y, target.posZ + rightZ * distance)) continue;
            ++right;
        }
        if (left == 0 && right == 0) {
            return -1;
        }
        return left == right && previous >= 0 ? previous : (left >= right ? 0 : 1);
    }

    private boolean isDrop(double x, int y, double z) {
        int bx = (int)Math.floor(x);
        int bz = (int)Math.floor(z);
        for (int depth = 0; depth < 10; ++depth) {
            if (Displace.mc.theWorld.isAirBlock(new BlockPos(bx, y - depth, bz))) continue;
            return false;
        }
        return true;
    }

    private float offset(float baseYaw) {
        float result = ((Float)this.angle.getValue()).floatValue();
        if (this.side >= 0) {
            if (this.side == 0) {
                result = -result;
            }
            this.side = -1;
        } else if ((Integer)this.mode.getValue() == 0 || (Integer)this.mode.getValue() == 2 && RANDOM.nextBoolean()) {
            result = -result;
        }
        result += RANDOM.nextFloat() * 10.0f - 5.0f;
        if (this.renderArrow.getValue() && this.lastTarget != null) {
            this.setArrow(this.lastTarget, baseYaw, result);
        }
        return result;
    }

    @EventTarget(Priority.HIGHEST)
    public void onLeftClick(LeftClickMouseEvent event) {
        AutoClicker clicker;
        if (!this.isEnabled() || !((Boolean)this.manual.getValue()).booleanValue() || Displace.mc.currentScreen != null || event.isCancelled()) {
            return;
        }
        if (!((Boolean)this.autoClicker.getValue()).booleanValue() && (clicker = (AutoClicker)CrewX.moduleManager.modules.get(AutoClicker.class)) != null && clicker.isEnabled()) {
            return;
        }
        MovingObjectPosition hit = Displace.mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY) {
            return;
        }
        Entity entity = hit.entityHit;
        if (this.canDisplace(entity, true, true)) {
            event.setCancelled(true);
            this.pendingManualTarget = (EntityLivingBase) entity;
        } else if (((Boolean)this.overrideAttacks.getValue()).booleanValue() && this.canDisplace(entity, false, false)) {
            event.setCancelled(true);
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || Displace.mc.thePlayer == null || Displace.mc.theWorld == null) {
            this.pendingManualTarget = null;
            return;
        }
        if (event.getType() == EventType.POST) {
            EntityLivingBase target = this.pendingManualTarget;
            this.pendingManualTarget = null;
            if (target != null && !target.isDead && Displace.mc.theWorld.loadedEntityList.contains(target)) {
                Displace.mc.thePlayer.swingItem();
                EventManager.call(new AttackEvent(target));
                ((IAccessorPlayerControllerMP) Displace.mc.playerController).callSyncCurrentPlayItem();
                PacketUtil.sendPacket(new C02PacketUseEntity(target, Action.ATTACK));
                if (Displace.mc.playerController.getCurrentGameType() != net.minecraft.world.WorldSettings.GameType.SPECTATOR) {
                    PlayerUtil.attackEntity(target);
                }
                this.lastAt = System.currentTimeMillis();
                this.rotate = true;
            }
            return;
        }
        if (event.getType() != EventType.PRE || !this.rotate) return;
        this.rotate = false;
        float baseYaw = Displace.mc.thePlayer.rotationYaw;
        float yaw = baseYaw + this.offset(baseYaw);
        event.setRotation(yaw, Displace.mc.thePlayer.rotationPitch, 2);
        event.setPervRotation(yaw, 2);
    }

    public boolean prepareKillAuraDisplacement(EntityLivingBase target) {
        this.preparedAuraTarget = null;
        if (!this.isEnabled() || !this.killAura.getValue() || !this.canDisplace(target, true, true)) {
            return false;
        }
        this.preparedAuraTarget = target;
        return true;
    }
    public void applyKillAuraDisplacement(UpdateEvent event, EntityLivingBase target) {
        if (!this.isEnabled() || event == null || event.getType() != EventType.PRE
                || target == null || target != this.preparedAuraTarget) {
            this.preparedAuraTarget = null;
            return;
        }
        this.preparedAuraTarget = null;
        this.lastAt = System.currentTimeMillis();
        float baseYaw = event.getNewYaw();
        float yaw = baseYaw + this.offset(baseYaw);
        event.setRotation(yaw, event.getNewPitch(), 2);
        event.setPervRotation(yaw, 2);
    }
    private void setArrow(EntityLivingBase target, float baseYaw, float yawOffset) {
        double direction = Math.toRadians(baseYaw + yawOffset);
        this.dirX = -Math.sin(direction);
        this.dirZ = Math.cos(direction);
        this.arrowX = target.posX + this.dirX * 0.8;
        this.arrowY = target.posY + 1.2;
        this.arrowZ = target.posZ + this.dirZ * 0.8;
        this.arrowAt = System.currentTimeMillis();
    }

    @EventTarget
    public void onRenderWorld(Render3DEvent event) {
        if (!this.isEnabled() || !((Boolean)this.renderArrow.getValue()).booleanValue() || this.arrowAt == 0L || Displace.mc.theWorld == null) {
            return;
        }
        long elapsed = System.currentTimeMillis() - this.arrowAt;
        if (elapsed > 600L) {
            this.arrowAt = 0L;
            return;
        }
        double appear = Math.min(1.0, (double)elapsed / 100.0);
        double fade = elapsed > 500L ? Math.max(0.0, 1.0 - (double)(elapsed - 500L) / 100.0) : 1.0;
        float alpha = (float)((0.08 + 0.92 * appear * appear * (3.0 - 2.0 * appear)) * fade);
        RenderManager render = mc.getRenderManager();
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate((double)(this.arrowX - ((IAccessorRenderManager) render).getRenderPosX() + this.dirX * 0.8), (double)(this.arrowY - ((IAccessorRenderManager) render).getRenderPosY() + Math.sin((double)elapsed * 0.015) * 0.02), (double)(this.arrowZ - ((IAccessorRenderManager) render).getRenderPosZ() + this.dirZ * 0.8));
            GlStateManager.rotate((float)((float)Math.toDegrees(Math.atan2(-this.dirZ, this.dirX))), (float)0.0f, (float)1.0f, (float)0.0f);
            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.disableCull();
            GlStateManager.disableDepth();
            GlStateManager.depthMask((boolean)false);
            GlStateManager.color((float)1.0f, (float)1.0f, (float)1.0f, (float)alpha);
            GL11.glBegin((int)7);
            GL11.glVertex3d((double)-0.2784, (double)-0.0816, (double)0.0);
            GL11.glVertex3d((double)0.0, (double)-0.0288, (double)0.0);
            GL11.glVertex3d((double)0.0, (double)0.0288, (double)0.0);
            GL11.glVertex3d((double)-0.2784, (double)0.0816, (double)0.0);
            GL11.glEnd();
            GL11.glBegin((int)4);
            GL11.glVertex3d((double)0.0, (double)-0.144, (double)0.0);
            GL11.glVertex3d((double)0.288, (double)0.0, (double)0.0);
            GL11.glVertex3d((double)0.0, (double)0.144, (double)0.0);
            GL11.glEnd();
        }
        finally {
            GlStateManager.color((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
            GlStateManager.depthMask((boolean)true);
            GlStateManager.enableDepth();
            GlStateManager.enableCull();
            GlStateManager.disableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.popMatrix();
        }
    }
}
