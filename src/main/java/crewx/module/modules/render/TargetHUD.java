package crewx.module.modules.render;

import crewx.gui.ClientFont;
import crewx.module.modules.combat.*;
import crewx.module.modules.movement.*;
import crewx.module.modules.render.*;
import crewx.module.modules.player.*;
import crewx.module.modules.misc.*;

import crewx.CrewX;
import crewx.clickgui.render.RoundedUtils;
import crewx.event.EventTarget;
import crewx.event.types.EventType;
import crewx.events.PacketEvent;
import crewx.events.Render2DEvent;
import crewx.module.Module;
import crewx.util.ColorUtil;
import crewx.util.RenderUtil;
import crewx.util.TeamUtil;
import crewx.util.TimerUtil;
import crewx.property.properties.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.util.ResourceLocation;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import java.awt.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class TargetHUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    // Jello look: fixed colours on purpose (independent of the HUD theme)
    private static final int PANEL = 0x1C1E23;
    private static final int TRACK = 0x33FFFFFF;
    private static final int BAR = 0xFFE6E6E6;
    private static final int NAME = 0xFFFFFFFF;
    private static final int WIN = 0xFF5BD47A;
    private static final int LOSE = 0xFFEF5B5B;
    private static final int DRAW = 0xFFE9B949;
    private static final float HEIGHT = 32.0F;
    private static final float HEAD = 24.0F;
    private static final float PAD = 4.0F;

    private final TimerUtil lastAttackTimer = new TimerUtil();
    private EntityLivingBase lastTarget = null;
    private EntityLivingBase target = null;
    private float visualHealth = Float.NaN;
    private float scaleAnim = 0.0f;
    private long lastUpdate = System.nanoTime();
    private boolean dragging = false;
    private int dragX, dragY;

    public final FloatProperty scale = new FloatProperty("Scale", 1.0f, 0.5f, 1.5f);
    public final IntProperty offX = new IntProperty("Position X", 100, 0, 2000);
    public final IntProperty offY = new IntProperty("Position Y", 100, 0, 2000);
    public final PercentProperty backgroundAlpha = new PercentProperty("Background Alpha", 92);
    public final BooleanProperty showHead = new BooleanProperty("Show Head", true);

    public final BooleanProperty kaOnly = new BooleanProperty("KA Only", true);
    /** Screen: fixed, draggable panel. Follow target: smaller, next to the player you hit, until 1.5 s after the last hit. */
    public final ModeProperty position = new ModeProperty("Position", 0, new String[]{"Screen", "Follow target"});

    /** Follow mode: hidden 1.5 s after the last hit on the target. */
    private static final long FOLLOW_HIDE_MS = 1500L;
    /** Follow mode: the panel is drawn smaller, as a tag next to the player rather than a HUD element. */
    private static final float FOLLOW_SCALE = 0.72F;
    /** Follow mode: gap between the player's box and the panel (GUI units). */
    private static final float FOLLOW_GAP = 3.0F;
    /** Last projected bounds of the followed target on screen (scaled GUI units), from the 3D pass. */
    private final double[] followBounds = new double[4];
    private boolean followVisible;
    private EntityLivingBase followProjected;
    private boolean followOnLeft;

    public TargetHUD() {
        super("TargetHUD", false, true);
    }

    private boolean following() {
        return this.position.getValue() == 1;
    }

    private EntityLivingBase resolveTarget() {
        if (following()) {
            // whoever we hit last, while hits keep coming (any hit refreshes the timer, KillAura's too)
            if (!lastAttackTimer.hasTimeElapsed(FOLLOW_HIDE_MS) && TeamUtil.isEntityLoaded(lastTarget)
                    && !lastTarget.isDead && lastTarget.getHealth() > 0.0F) {
                return lastTarget;
            }
            return null;
        }
        KillAura killAura = (KillAura) CrewX.moduleManager.modules.get(KillAura.class);
        if (killAura.isEnabled() && killAura.isAttackAllowed() && TeamUtil.isEntityLoaded(killAura.getTarget())) {
            return killAura.getTarget();
        }
        if (!kaOnly.getValue() && !lastAttackTimer.hasTimeElapsed(1500L) && TeamUtil.isEntityLoaded(lastTarget)) {
            return lastTarget;
        }
        if (mc.currentScreen instanceof GuiChat) {
            return mc.thePlayer;
        }
        return null;
    }

    private float getEntityHealth(EntityLivingBase entity) {
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            Scoreboard scoreboard = player.getWorldScoreboard();
            ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(2);
            if (objective != null) {
                return (float) scoreboard.getValueFromObjective(player.getName(), objective).getScorePoints();
            }
            for (ScoreObjective obj : scoreboard.getScoreObjectives()) {
                String name = obj.getDisplayName().toLowerCase();
                if (name.contains("health") || name.contains("vida") || name.contains("hp")) {
                    return (float) scoreboard.getValueFromObjective(player.getName(), obj).getScorePoints();
                }
            }
        }
        return entity.getHealth() + entity.getAbsorptionAmount();
    }

    private ResourceLocation getSkin(EntityLivingBase entityLivingBase) {
        if (entityLivingBase instanceof EntityPlayer) {
            NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(entityLivingBase.getName());
            if (playerInfo != null) return playerInfo.getLocationSkin();
        }
        return null;
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) return;

        EntityLivingBase currentTarget = resolveTarget();
        long now = System.nanoTime();
        float delta = Math.max(0.0f, Math.min((now - lastUpdate) / 1_000_000_000.0f, 0.1f));
        lastUpdate = now;

        if (currentTarget != null) {
            if (currentTarget != target) visualHealth = Float.NaN;   // new target: no drain from the old one
            target = currentTarget;
            scaleAnim = Math.min(1.0f, scaleAnim + delta * 8.0f);
        } else {
            scaleAnim = Math.max(0.0f, scaleAnim - delta * 8.0f);
        }
        if (scaleAnim <= 0.0f) {
            target = null;
            visualHealth = Float.NaN;
            return;
        }
        if (target == null) return;

        float health = getEntityHealth(target);
        float maxHealth = Math.max(target.getMaxHealth() + target.getAbsorptionAmount(), health);
        if (maxHealth <= 0) maxHealth = 20.0f;
        visualHealth = TargetHudMath.animateHealth(visualHealth, health, delta);

        String name = target.getName();
        boolean head = showHead.getValue() && getSkin(target) != null;
        float textX = PAD + (head ? HEAD + 6.0F : 2.0F);
        float letterW = ClientFont.getStringWidthFloat("W");
        float width = Math.max(120.0F, textX + ClientFont.getStringWidthFloat(name) + 8.0F + letterW + PAD + 2.0F);
        float x = offX.getValue();
        float y = offY.getValue();
        if (following()) {
            if (!this.followVisible || this.followProjected != target) {
                // off screen / behind the camera: fade out like a lost target
                scaleAnim = Math.max(0.0f, scaleAnim - delta * 16.0f);
                if (scaleAnim <= 0.0f) return;
            }
            ScaledResolution sr = new ScaledResolution(mc);
            float boxLeft = (float) this.followBounds[0];
            float boxRight = (float) this.followBounds[2];
            float boxTop = (float) this.followBounds[1];
            float boxBottom = (float) this.followBounds[3];
            // where the (smaller) panel is actually drawn: right next to the box, at chest height
            float drawnW = width * scale.getValue() * FOLLOW_SCALE;
            float drawnH = HEIGHT * scale.getValue() * FOLLOW_SCALE;
            // Side of the box the panel sits on. It only changes when the current side really does not fit (with a
            // margin): near the screen edge it used to flip right/left/right every frame and looked like shaking.
            float rightLeft = boxRight + FOLLOW_GAP;
            float leftLeft = boxLeft - FOLLOW_GAP - drawnW;
            float edge = sr.getScaledWidth() - 4.0F;
            if (!this.followOnLeft && rightLeft + drawnW > edge) {
                this.followOnLeft = true;
            } else if (this.followOnLeft && rightLeft + drawnW < edge - 24.0F) {
                this.followOnLeft = false;
            }
            float left = this.followOnLeft ? leftLeft : rightLeft;
            float top = boxTop + (boxBottom - boxTop) * 0.18F;
            left = Math.max(4.0F, Math.min(sr.getScaledWidth() - drawnW - 4.0F, left));
            top = Math.max(4.0F, Math.min(sr.getScaledHeight() - drawnH - 4.0F, top));
            // the panel scales around its centre
            x = left + drawnW / 2.0F - width / 2.0F;
            y = top + drawnH / 2.0F - HEIGHT / 2.0F;
            // No smoothing: it lags behind the camera's view bobbing and makes the panel shake while walking.
            // Snap to whole framebuffer pixels instead.
            float pixel = 1.0F / Math.max(1, sr.getScaleFactor());
            x = Math.round(x / pixel) * pixel;
            y = Math.round(y / pixel) * pixel;
        }

        if (!following() && mc.currentScreen instanceof GuiChat) {
            ScaledResolution sr = new ScaledResolution(mc);
            int mouseX = Mouse.getX() * sr.getScaledWidth() / mc.displayWidth;
            int mouseY = sr.getScaledHeight() - Mouse.getY() * sr.getScaledHeight() / mc.displayHeight - 1;
            if (Mouse.isButtonDown(0)) {
                if (!dragging && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + HEIGHT) {
                    dragging = true;
                    dragX = (int) (mouseX - x);
                    dragY = (int) (mouseY - y);
                }
                if (dragging) {
                    offX.setValue(Math.max(0, mouseX - dragX));
                    offY.setValue(Math.max(0, mouseY - dragY));
                }
            } else {
                dragging = false;
            }
        }

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        float renderScale = scale.getValue() * scaleAnim * (following() ? FOLLOW_SCALE : 1.0F);
        GlStateManager.translate(x + width / 2.0f, y + HEIGHT / 2.0f, 0);
        GlStateManager.scale(renderScale, renderScale, 1.0f);
        GlStateManager.translate(-(x + width / 2.0f), -(y + HEIGHT / 2.0f), 0);

        // soft shadow + panel
        for (int i = 3; i >= 1; i--) {
            RoundedUtils.drawRoundedRect(x - i, y - i + 1, width + i * 2, HEIGHT + i * 2, 0x14000000, 5.0F + i);
        }
        int bgAlpha = Math.round(255 * (backgroundAlpha.getValue() / 100.0f));
        RoundedUtils.drawRoundedRect(x, y, width, HEIGHT, (bgAlpha << 24) | PANEL, 5.0F);

        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        if (head) {
            ResourceLocation skin = getSkin(target);
            float hurt = target.hurtTime > 0 ? 0.6F : 1.0F;     // red flash on hit, like vanilla
            mc.getTextureManager().bindTexture(skin);
            GlStateManager.color(1.0f, hurt, hurt, 1.0f);
            GlStateManager.pushMatrix();
            GlStateManager.translate(x + PAD, y + PAD, 0.0F);
            Gui.drawScaledCustomSizeModalRect(0, 0, 8.0f, 8.0f, 8, 8, (int) HEAD, (int) HEAD, 64.0f, 64.0f);
            Gui.drawScaledCustomSizeModalRect(0, 0, 40.0f, 8.0f, 8, 8, (int) HEAD, (int) HEAD, 64.0f, 64.0f);
            GlStateManager.popMatrix();
        }
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        // name + W/L/D verdict
        float fontH = ClientFont.getHeight();
        float nameY = y + 6.0F;
        float maxName = width - textX - PAD - letterW - 8.0F;
        String fitted = ClientFont.getStringWidthFloat(name) > maxName
                ? ClientFont.trimStringToWidth(name, Math.max(1, (int) maxName - 6)) + "\u2026" : name;
        ClientFont.drawString(fitted, x + textX, nameY, NAME, false);
        char verdict = TargetHudMath.verdict(getEntityHealth(mc.thePlayer), health);
        int verdictColor = verdict == 'W' ? WIN : verdict == 'L' ? LOSE : DRAW;
        String letter = String.valueOf(verdict);
        ClientFont.drawString(letter, x + width - PAD - 2.0F - ClientFont.getStringWidthFloat(letter), nameY, verdictColor, false);

        // health bar: drains with an animation when the target takes damage
        float barX = x + textX;
        float barW = width - textX - PAD - 2.0F;
        float barH = 3.0F;
        float barY = Math.max(nameY + fontH + 3.0F, y + HEIGHT - PAD - barH - 3.0F);
        RoundedUtils.drawRoundedRect(barX, barY, barW, barH, TRACK, barH / 2.0F);
        float filled = TargetHudMath.fraction(visualHealth, maxHealth) * barW;
        if (filled > 0.5F) {
            RoundedUtils.drawRoundedRect(barX, barY, Math.max(barH, filled), barH, BAR, barH / 2.0F);
        }

        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    /** Follow mode: where the target is on screen this frame, projected with the world camera. */
    @EventTarget
    public void onRender3D(crewx.events.Render3DEvent event) {
        this.followVisible = false;
        if (!this.isEnabled() || !following() || this.target == null || mc.thePlayer == null) return;
        ScaledResolution sr = new ScaledResolution(mc);
        javax.vecmath.Vector4d bounds = RenderUtil.projectToScreen(this.target, sr.getScaleFactor());
        if (bounds == null) return;
        this.followBounds[0] = bounds.x;
        this.followBounds[1] = bounds.y;
        this.followBounds[2] = bounds.z;
        this.followBounds[3] = bounds.w;
        this.followProjected = this.target;
        this.followVisible = bounds.z > 0.0 && bounds.x < sr.getScaledWidth() && bounds.w > 0.0 && bounds.y < sr.getScaledHeight();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.SEND && event.getPacket() instanceof C02PacketUseEntity) {
            C02PacketUseEntity packet = (C02PacketUseEntity) event.getPacket();
            if (packet.getAction() == Action.ATTACK) {
                Entity entity = packet.getEntityFromWorld(mc.theWorld);
                if (entity instanceof EntityLivingBase && !(entity instanceof EntityArmorStand)) {
                    this.lastAttackTimer.reset();
                    this.lastTarget = (EntityLivingBase) entity;
                }
            }
        }
    }
}
