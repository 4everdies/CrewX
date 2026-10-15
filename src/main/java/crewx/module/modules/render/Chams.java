package crewx.module.modules.render;
import crewx.module.modules.combat.*;
import crewx.module.modules.movement.*;
import crewx.module.modules.render.*;
import crewx.module.modules.player.*;
import crewx.module.modules.misc.*;

import crewx.CrewX;
import crewx.event.EventTarget;
import crewx.events.RenderLivingEvent;
import crewx.module.Module;
import crewx.util.TeamUtil;
import crewx.property.properties.BooleanProperty;
import crewx.property.properties.ModeProperty;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.*;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

public class Chams extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static boolean loggedPlayerRender;
    private static final int GL_POLYGON_OFFSET_FILL = 32823;
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"Offset", "NoDepth"});
    public final BooleanProperty flat = new BooleanProperty("flat", false);
    public final BooleanProperty players = new BooleanProperty("players", true);
    public final BooleanProperty friends = new BooleanProperty("friends", true);
    public final BooleanProperty enemiess = new BooleanProperty("enemies", true);
    public final BooleanProperty bosses = new BooleanProperty("bosses", false);
    public final BooleanProperty mobs = new BooleanProperty("mobs", false);
    public final BooleanProperty creepers = new BooleanProperty("creepers", false);
    public final BooleanProperty enderman = new BooleanProperty("endermen", false);
    public final BooleanProperty blaze = new BooleanProperty("blazes", false);
    public final BooleanProperty animals = new BooleanProperty("animals", false);
    public final BooleanProperty self = new BooleanProperty("self", false);
    public final BooleanProperty bots = new BooleanProperty("bots", false);

    public boolean shouldRenderChams(EntityLivingBase entityLivingBase) {
        if (entityLivingBase.deathTime > 0) {
            return false;
        } else if (mc.getRenderViewEntity().getDistanceToEntity(entityLivingBase) > 512.0F) {
            return false;
        } else if (entityLivingBase instanceof EntityPlayer) {
            if (entityLivingBase != mc.thePlayer && entityLivingBase != mc.getRenderViewEntity()) {
                AntiBot antiBot = (AntiBot) CrewX.moduleManager.modules.get(AntiBot.class);
                if (antiBot.isEnabled() && antiBot.isBot((EntityPlayer) entityLivingBase)) {
                    return this.bots.getValue();
                } else if (TeamUtil.isFriend((EntityPlayer) entityLivingBase)) {
                    return this.friends.getValue();
                } else {
                    return TeamUtil.isTarget((EntityPlayer) entityLivingBase) ? this.enemiess.getValue() : this.players.getValue();
                }
            } else {
                return this.self.getValue() && mc.gameSettings.thirdPersonView != 0;
            }
        } else if (entityLivingBase instanceof EntityDragon || entityLivingBase instanceof EntityWither) {
            return !entityLivingBase.isInvisible() && this.bosses.getValue();
        } else if (!(entityLivingBase instanceof EntityMob) && !(entityLivingBase instanceof EntitySlime)) {
            return (entityLivingBase instanceof EntityAnimal
                    || entityLivingBase instanceof EntityBat
                    || entityLivingBase instanceof EntitySquid
                    || entityLivingBase instanceof EntityVillager) && this.animals.getValue();
        } else if (entityLivingBase instanceof EntityCreeper) {
            return this.creepers.getValue();
        } else if (entityLivingBase instanceof EntityEnderman) {
            return this.enderman.getValue();
        } else {
            return entityLivingBase instanceof EntityBlaze ? this.blaze.getValue() : this.mobs.getValue();
        }
    }


    public Chams() {
        super("Chams", false);
    }

    @EventTarget
    public void onRenderLiving(RenderLivingEvent event) {
        if (!this.isEnabled()) return;
        boolean selected = this.shouldRenderChams(event.getEntity());
        if (!loggedPlayerRender && event.getType() == crewx.event.types.EventType.PRE
                && event.getEntity() instanceof EntityPlayer && event.getEntity() != mc.thePlayer) {
            loggedPlayerRender = true;
            crewx.util.ClientLog.log("Chams player render reached; selected=" + selected
                    + " mode=" + this.mode.getValue());
        }
        if (!selected) return;

        boolean noDepth = this.mode.getValue() == 1;

        switch (event.getType()) {
            case PRE:
                if (noDepth) {
                    GlStateManager.disableDepth();
                } else {
                    GL11.glEnable(GL_POLYGON_OFFSET_FILL);
                    GL11.glPolygonOffset(1.0F, -2500000.0F);
                }
                if (this.flat.getValue()) {
                    GlStateManager.disableTexture2D();
                }
                break;
            case POST:
                if (this.flat.getValue()) {
                    GlStateManager.enableTexture2D();
                }
                if (noDepth) {
                    GlStateManager.enableDepth();
                } else {
                    GL11.glPolygonOffset(1.0F, 2500000.0F);
                    GL11.glDisable(GL_POLYGON_OFFSET_FILL);
                }
                break;
            default:
                break;
        }
    }
}
