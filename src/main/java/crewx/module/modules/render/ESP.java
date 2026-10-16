package crewx.module.modules.render;
import crewx.module.modules.combat.*;
import crewx.module.modules.movement.*;
import crewx.module.modules.render.*;
import crewx.module.modules.player.*;
import crewx.module.modules.misc.*;

import crewx.CrewX;
import crewx.enums.ChatColors;
import crewx.event.EventTarget;
import crewx.event.types.Priority;
import crewx.events.Render2DEvent;
import crewx.events.Render3DEvent;
import crewx.events.ResizeEvent;
import crewx.mixin.IAccessorEntityRenderer;
import crewx.mixin.IAccessorRenderManager;
import crewx.module.Module;
import crewx.util.ColorUtil;
import crewx.util.RenderUtil;
import crewx.util.TeamUtil;
import crewx.util.shader.GlowShader;
import crewx.util.shader.OutlineShader;
import crewx.property.properties.BooleanProperty;
import crewx.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

import crewx.util.RenderUtil.ScreenRect;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ESP extends Module {
    public boolean needsSmoothOverlay() {
        return this.isEnabled() && (this.mode.getValue() == 3
                || ((this.mode.getValue() == 1 || this.healthBar.getValue() == 1)
                && !this.boxPlayers.isEmpty()));
    }
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final OutlineShader outlineRenderer = new OutlineShader();
    private final GlowShader glowShader = new GlowShader();
    private Framebuffer framebuffer = null;
    private boolean outline = true;
    private boolean glow = true;

    public final ModeProperty mode = new ModeProperty("mode", 2, new String[]{"None", "2D", "3D", "Outline", "FakeCorner", "Fake2D"});
    public final ModeProperty color = new ModeProperty("color", 0, new String[]{"Default", "Teams", "HUD"});
    public final ModeProperty healthBar = new ModeProperty("health-bar", 0, new String[]{"None", "2D", "Raven"});
    public final BooleanProperty players = new BooleanProperty("players", true);
    public final BooleanProperty friends = new BooleanProperty("friends", true);
    public final BooleanProperty enemies = new BooleanProperty("enemies", true);
    public final BooleanProperty self = new BooleanProperty("self", false);
    public final BooleanProperty bots = new BooleanProperty("bots", false);

    public ESP() {
        super("ESP", false);
    }

    public boolean isOutlineEnabled() {
        return this.outline;
    }

    public boolean isGlowEnabled() {
        return this.glow;
    }

    private boolean shouldRenderPlayer(EntityPlayer entityPlayer) {
        if (entityPlayer.deathTime > 0) return false;
        if (mc.getRenderViewEntity().getDistanceToEntity(entityPlayer) > 512.0F) return false;
        if (!entityPlayer.ignoreFrustumCheck && !RenderUtil.isInViewFrustum(entityPlayer.getEntityBoundingBox(), 0.1F)) return false;

        if (entityPlayer == mc.thePlayer || entityPlayer == mc.getRenderViewEntity()) {
            return this.self.getValue() && mc.gameSettings.thirdPersonView != 0;
        }
        AntiBot antiBot = (AntiBot) CrewX.moduleManager.modules.get(AntiBot.class);
        if (antiBot.isEnabled() && antiBot.isBot(entityPlayer) && !TeamUtil.isInTabList(entityPlayer)) return this.bots.getValue();
        if (TeamUtil.isFriend(entityPlayer)) return this.friends.getValue();
        return TeamUtil.isTarget(entityPlayer) ? this.enemies.getValue() : this.players.getValue();
    }

    private Color getEntityColor(EntityPlayer entityPlayer) {
        if (TeamUtil.isFriend(entityPlayer)) return CrewX.friendManager.getColor();
        if (TeamUtil.isTarget(entityPlayer)) return CrewX.targetManager.getColor();

        switch (this.color.getValue()) {
            case 0:
                return TeamUtil.getTeamColor(entityPlayer, 1.0F);
            case 1:
                int teamColor = TeamUtil.isSameTeam(entityPlayer) ? ChatColors.BLUE.toAwtColor() : ChatColors.RED.toAwtColor();
                return new Color(teamColor);
            case 2:
                int hudColor = ((HUD) CrewX.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()).getRGB();
                return new Color(hudColor);
            default:
                return Color.WHITE;
        }
    }

    /** Shown health fraction per player: slides towards the real one instead of jumping. */
    private final java.util.Map<EntityPlayer, float[]> shownHealth = new java.util.WeakHashMap<EntityPlayer, float[]>();
    private long lastHealthFrame;
    private float healthDt;

    private float smoothHealth(EntityPlayer player, float target) {
        float[] shown = this.shownHealth.get(player);
        if (shown == null) {
            this.shownHealth.put(player, new float[]{target});
            return target;
        }
        shown[0] += (target - shown[0]) * (1.0F - (float) Math.exp(-this.healthDt * 10.0F));
        return shown[0];
    }

    /** 2D boxes of this frame, projected in the world pass (same camera, partial tick and render position). */
    private final List<EntityPlayer> boxPlayers = new ArrayList<>();
    private final List<ScreenRect> boxRects = new ArrayList<>();
    private boolean outlineScissorValid;
    private int outlineScissorX, outlineScissorY, outlineScissorW, outlineScissorH;

    private List<EntityPlayer> getRenderedPlayers() {
        List<EntityPlayer> result = new ArrayList<>();
        for (Object entity : TeamUtil.getLoadedEntitiesSorted()) {
            if (entity instanceof EntityPlayer && shouldRenderPlayer((EntityPlayer) entity)) {
                result.add((EntityPlayer) entity);
            }
        }
        return result;
    }

    @EventTarget
    public void onResize(ResizeEvent event) {
        if (this.framebuffer != null) {
            this.framebuffer.deleteFramebuffer();
        }
        this.framebuffer = new Framebuffer(mc.displayWidth, mc.displayHeight, false);
    }

    @EventTarget(Priority.HIGH)
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled()) return;

        int modeVal = this.mode.getValue();
        int healthBarVal = this.healthBar.getValue();
        boolean needs2D = modeVal == 1 || modeVal == 3 || healthBarVal == 1;
        if (!needs2D) return;

        List<EntityPlayer> renderedEntities = getRenderedPlayers();
        if (renderedEntities.isEmpty()) return;
        long frameNow = System.currentTimeMillis();
        this.healthDt = this.lastHealthFrame == 0L ? 0.016F : Math.max(0.0F, Math.min(0.1F, (frameNow - this.lastHealthFrame) / 1000.0F));
        this.lastHealthFrame = frameNow;

        if (modeVal == 3) {
            GlStateManager.pushMatrix();
            double[] glCache = crewx.util.GlStateSync.snapshot();
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            if (this.framebuffer == null) {
                this.framebuffer = new Framebuffer(mc.displayWidth, mc.displayHeight, false);
            }
            this.framebuffer.bindFramebuffer(false);
            ((IAccessorEntityRenderer) mc.entityRenderer).callSetupCameraTransform(event.getPartialTicks(), 0);
            boolean shadow = mc.gameSettings.entityShadows;
            mc.gameSettings.entityShadows = false;
            this.outline = false;
            this.glow = false;
            this.glowShader.use();
            for (EntityPlayer player : renderedEntities) {
                Color entityColor = this.getEntityColor(player);
                this.glowShader.W(entityColor);
                boolean invisible = player.isInvisible();
                player.setInvisible(false);
                mc.getRenderManager().renderEntityStatic(player, event.getPartialTicks(), true);
                player.setInvisible(invisible);
            }
            this.glowShader.stop();
            this.glow = true;
            this.outline = true;
            mc.gameSettings.entityShadows = shadow;
            mc.entityRenderer.disableLightmap();
            mc.entityRenderer.setupOverlayRendering();
            mc.getFramebuffer().bindFramebuffer(false);
            this.outlineRenderer.use();
            if (this.outlineScissorValid) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(this.outlineScissorX, this.outlineScissorY,
                        this.outlineScissorW, this.outlineScissorH);
            }
            RenderUtil.drawFramebuffer(this.framebuffer);
            if (this.outlineScissorValid) GL11.glDisable(GL11.GL_SCISSOR_TEST);
            this.outlineRenderer.stop();
            this.framebuffer.framebufferClear();
            mc.getFramebuffer().bindFramebuffer(false);
            GlStateManager.popAttrib();
            crewx.util.GlStateSync.afterPopAttrib(glCache);
            GlStateManager.popMatrix();
        }

        if (modeVal == 1 || healthBarVal == 1) {
            RenderUtil.enableRenderState();
            GlStateManager.pushMatrix();
            try {
            for (int index = 0; index < this.boxPlayers.size(); index++) {
                // projected during this frame's world pass (GUI units): the HUD matrices stay as they are
                EntityPlayer player = this.boxPlayers.get(index);
                ScreenRect screenPosition = this.boxRects.get(index);
                if (player.isDead) continue;

                float x = (float) screenPosition.x;
                float y = (float) screenPosition.y;
                float z = (float) screenPosition.z;
                float w = (float) screenPosition.w;

                if (modeVal == 1) {
                    int rgb = this.getEntityColor(player).getRGB() & 0xFFFFFF;
                    // faint tinted fill (clear at the head, denser at the feet), dark rim, soft glow, crisp frame
                    RenderUtil.drawGradientRect2D(x, y, z, w, 0x00000000 | rgb, 0x30000000 | rgb);
                    RenderUtil.drawOutlineRect(x, y, z, w, 3.5F, 0, 0x99000000);
                    RenderUtil.drawOutlineRect(x, y, z, w, 4.5F, 0, 0x26000000 | rgb);
                    RenderUtil.drawOutlineRect(x, y, z, w, 1.5F, 0, 0xFF000000 | rgb);
                }
                if (healthBarVal == 1) {
                    float heal = player.getHealth() + player.getAbsorptionAmount();
                    float target = Math.min(Math.max(heal / player.getMaxHealth(), 0.0F), 1.0F);
                    float percent = this.smoothHealth(player, target);
                    float box = (z - x) * 0.08F;
                    float barX = x - box - 4.0F;
                    Color healthColor = ColorUtil.getHealthBlend(percent);
                    int color = healthColor.getRGB() & 0xFFFFFF;
                    // dark track, then the fill growing from the feet with a lighter top
                    RenderUtil.drawRect(barX - 1.0F, y - 1.0F, barX + 3.0F, w + 1.0F, 0xA0000000);
                    float top = w + (y - w) * percent;
                    RenderUtil.drawGradientRect2D(barX, top, barX + 2.0F, w,
                            0xFF000000 | crewx.gui.GuiRender.mix(0xFF000000 | color, 0xFFFFFFFF, 0.25F) & 0xFFFFFF, 0xFF000000 | color);
                }
            }
            } finally {
                // whatever happens above, never leave the HUD with this matrix or render state
                GlStateManager.popMatrix();
                RenderUtil.disableRenderState();
            }
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        this.boxPlayers.clear();
        this.boxRects.clear();
        this.outlineScissorValid = false;
        if (!this.isEnabled()) return;

        int modeVal = this.mode.getValue();
        int healthBarVal = this.healthBar.getValue();
        if (modeVal == 3) {
            double factor = new ScaledResolution(mc).getScaleFactor();
            double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
            boolean allProjected = true;
            for (EntityPlayer player : getRenderedPlayers()) {
                ScreenRect rect = RenderUtil.projectToScreen(player, factor);
                if (rect == null) { allProjected = false; break; }
                minX = Math.min(minX, rect.x); minY = Math.min(minY, rect.y);
                maxX = Math.max(maxX, rect.z); maxY = Math.max(maxY, rect.w);
            }
            if (allProjected && minX != Double.POSITIVE_INFINITY) {
                int x1 = Math.max(0, (int) Math.floor(minX * factor) - 8);
                int x2 = Math.min(mc.displayWidth, (int) Math.ceil(maxX * factor) + 8);
                int y1 = Math.max(0, (int) Math.floor(mc.displayHeight - maxY * factor) - 8);
                int y2 = Math.min(mc.displayHeight, (int) Math.ceil(mc.displayHeight - minY * factor) + 8);
                if (x2 > x1 && y2 > y1) {
                    this.outlineScissorX = x1; this.outlineScissorY = y1;
                    this.outlineScissorW = x2 - x1; this.outlineScissorH = y2 - y1;
                    this.outlineScissorValid = true;
                }
            }
        }
        if (modeVal == 1 || healthBarVal == 1) {
            // The 2D box is projected here, while the camera matrices, render position and partial tick are exactly
            // the ones this frame's world (and the player models) were drawn with, and drawn later on the HUD.
            double scaleFactor = new ScaledResolution(mc).getScaleFactor();
            for (EntityPlayer player : getRenderedPlayers()) {
                ScreenRect rect = RenderUtil.projectToScreen(player, scaleFactor);
                if (rect == null) continue;
                this.boxPlayers.add(player);
                this.boxRects.add(rect);
            }
        }
        boolean needs3D = modeVal == 2 || modeVal == 4 || modeVal == 5 || healthBarVal == 2;
        if (!needs3D) return;

        RenderUtil.enableRenderState();
        IAccessorRenderManager renderManager = (IAccessorRenderManager) mc.getRenderManager();
        double renderX = renderManager.getRenderPosX();
        double renderY = renderManager.getRenderPosY();
        double renderZ = renderManager.getRenderPosZ();

        for (EntityPlayer player : getRenderedPlayers()) {
            if (!player.ignoreFrustumCheck && !RenderUtil.isInViewFrustum(player.getEntityBoundingBox(), 0.1F)) continue;

            Color entityColor = this.getEntityColor(player);

            if (modeVal == 2) {
                RenderUtil.drawStyledEntityBox(player, entityColor.getRGB() & 0xFFFFFF, 0.1, 0.20F, 0.02F, 1.5F);
                GlStateManager.resetColor();
            } else if (modeVal == 4) {
                RenderUtil.drawCornerESP(player, entityColor.getRed() / 255.0F, entityColor.getGreen() / 255.0F, entityColor.getBlue() / 255.0F);
            } else if (modeVal == 5) {
                RenderUtil.drawFake2DESP(player, entityColor.getRed() / 255.0F, entityColor.getGreen() / 255.0F, entityColor.getBlue() / 255.0F);
            }

            if (healthBarVal == 2) {
                double x = RenderUtil.lerpDouble(player.posX, player.lastTickPosX, event.getPartialTicks()) - renderX;
                double y = RenderUtil.lerpDouble(player.posY, player.lastTickPosY, event.getPartialTicks()) - renderY - 0.1F;
                double z = RenderUtil.lerpDouble(player.posZ, player.lastTickPosZ, event.getPartialTicks()) - renderZ;
                GlStateManager.pushMatrix();
                GlStateManager.translate(x, y, z);
                GlStateManager.rotate(mc.getRenderManager().playerViewY * -1.0F, 0.0F, 1.0F, 0.0F);
                float heal = player.getHealth() + player.getAbsorptionAmount();
                float percent = Math.min(Math.max(heal / player.getMaxHealth(), 0.0F), 1.0F);
                Color healthColor = ColorUtil.getHealthBlend(percent);
                float height = player.height + 0.2F;
                RenderUtil.drawRect3D(0.57250005F, -0.027500002F, 0.7275F, height + 0.027500002F, Color.black.getRGB());
                RenderUtil.drawRect3D(0.6F, 0.0F, 0.70000005F, height, Color.darkGray.getRGB());
                RenderUtil.drawRect3D(0.6F, 0.0F, 0.70000005F, height * percent, healthColor.getRGB());
                GlStateManager.popMatrix();
            }
        }
        RenderUtil.disableRenderState();
    }
}
