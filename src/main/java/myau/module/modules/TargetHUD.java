package myau.module.modules;
import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.util.ColorUtil;
import myau.util.RenderUtil;
import myau.util.TeamUtil;
import myau.util.TimerUtil;
import myau.property.properties.*;
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
    private static final DecimalFormat healthFormat = new DecimalFormat("0.0", new DecimalFormatSymbols(Locale.US));
    private final TimerUtil lastAttackTimer = new TimerUtil();
    private EntityLivingBase lastTarget = null;
    private EntityLivingBase target = null;
    private float visualHealth = 0.0f;
    private float damageAnim = 0.0f;
    private float scaleAnim = 0.0f;
    private long lastUpdate = System.currentTimeMillis();
    private boolean dragging = false;
    private int dragX, dragY;
    public final ModeProperty colorMode = new ModeProperty("Color Mode", 0, new String[]{"Health", "HUD", "Gradient"});
    public final FloatProperty scale = new FloatProperty("Scale", 1.0f, 0.5f, 1.5f);
    public final IntProperty offX = new IntProperty("Position X", 100, 0, 2000);
    public final IntProperty offY = new IntProperty("Position Y", 100, 0, 2000);
    public final PercentProperty backgroundAlpha = new PercentProperty("Background Alpha", 70);
    public final BooleanProperty showHead = new BooleanProperty("Show Head", true);
    public final BooleanProperty showIndicator = new BooleanProperty("Indicator", true);
    public final BooleanProperty kaOnly = new BooleanProperty("KA Only", true);
    public TargetHUD() {
        super("TargetHUD", false, true);
    }
    private EntityLivingBase resolveTarget() {
        KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
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
        long now = System.currentTimeMillis();
        float delta = (now - lastUpdate) / 1000.0f;
        lastUpdate = now;
        if (currentTarget != null) {
            target = currentTarget;
            scaleAnim = Math.min(1.0f, scaleAnim + delta * 8.0f);
        } else {
            scaleAnim = Math.max(0.0f, scaleAnim - delta * 8.0f);
        }
        if (scaleAnim <= 0.0f) {
            target = null;
            visualHealth = 0;
            return;
        }
        if (target == null) return;
        float health = getEntityHealth(target);
        float maxHealth = target.getMaxHealth() + target.getAbsorptionAmount();
        if (maxHealth <= 0) maxHealth = 20.0f;
        if (visualHealth <= 0 || Math.abs(visualHealth - health) > 20) visualHealth = health;
        visualHealth = RenderUtil.lerpFloat(visualHealth, health, delta * 9.0f);
        if (damageAnim < visualHealth) damageAnim = visualHealth;
        damageAnim = RenderUtil.lerpFloat(damageAnim, visualHealth, delta * 4.5f);
        float width = 145.0f;
        float height = 45.0f;
        float x = offX.getValue();
        float y = offY.getValue();
        if (mc.currentScreen instanceof GuiChat) {
            ScaledResolution sr = new ScaledResolution(mc);
            int mouseX = Mouse.getX() * sr.getScaledWidth() / mc.displayWidth;
            int mouseY = sr.getScaledHeight() - Mouse.getY() * sr.getScaledHeight() / mc.displayHeight - 1;
            if (Mouse.isButtonDown(0)) {
                if (!dragging && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
                    dragging = true;
                    dragX = (int) (mouseX - x);
                    dragY = (int) (mouseY - y);
                }
                if (dragging) {
                    offX.setValue(mouseX - dragX);
                    offY.setValue(mouseY - dragY);
                }
            } else {
                dragging = false;
            }
        }
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        float renderScale = scale.getValue() * scaleAnim;
        GlStateManager.translate(x + width / 2.0f, y + height / 2.0f, 0);
        GlStateManager.scale(renderScale, renderScale, 1.0f);
        GlStateManager.translate(-(x + width / 2.0f), -(y + height / 2.0f), 0);
        int bgColor = new Color(0, 0, 0, (int)(255 * (backgroundAlpha.getValue() / 100.0f))).getRGB();
        drawRectInternal(x, y, x + width, y + height, bgColor);
        drawOutlineInternal(x, y, x + width, y + height, 1.0f, new Color(255, 255, 255, 40).getRGB());
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        if (showHead.getValue()) {
            ResourceLocation skin = getSkin(target);
            if (skin != null) {
                mc.getTextureManager().bindTexture(skin);
                GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
                Gui.drawScaledCustomSizeModalRect((int)x + 6, (int)y + 6, 8.0f, 8.0f, 8, 8, 33, 33, 64.0f, 64.0f);
                Gui.drawScaledCustomSizeModalRect((int)x + 6, (int)y + 6, 40.0f, 8.0f, 8, 8, 33, 33, 64.0f, 64.0f);
            }
        }
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        String name = target.getName();
        mc.fontRendererObj.drawStringWithShadow(name, x + 46, y + 8, -1);
        String hpText = healthFormat.format(health) + " HP";
        mc.fontRendererObj.drawStringWithShadow(hpText, x + 46, y + 20, new Color(180, 180, 180).getRGB());
        float barX = x + 46;
        float barY = y + 33;
        float barWidth = width - 52;
        float barHeight = 6;
        GlStateManager.disableTexture2D();
        drawRectInternal(barX, barY, barX + barWidth, barY + barHeight, new Color(0, 0, 0, 120).getRGB());
        float dWidth = Math.min(1.0f, damageAnim / maxHealth) * barWidth;
        if (dWidth > 0) {
            drawRectInternal(barX, barY, barX + dWidth, barY + barHeight, new Color(255, 255, 255, 140).getRGB());
        }
        float hWidth = Math.min(1.0f, visualHealth / maxHealth) * barWidth;
        if (hWidth > 0) {
            int hpColor = getDisplayColor(visualHealth, maxHealth).getRGB();
            drawRectInternal(barX, barY, barX + hWidth, barY + barHeight, hpColor);
        }
        GlStateManager.enableTexture2D();
        if (showIndicator.getValue()) {
            float myHP = mc.thePlayer.getHealth();
            String status = health == myHP ? "D" : (health < myHP ? "W" : "L");
            int sCol = health < myHP ? Color.GREEN.getRGB() : (health > myHP ? Color.RED.getRGB() : Color.YELLOW.getRGB());
            mc.fontRendererObj.drawStringWithShadow(status, x + width - 12, y + 8, sCol);
        }
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
    private void drawRectInternal(float left, float top, float right, float bottom, int color) {
        float f3 = (float) (color >> 24 & 255) / 255.0F;
        float f = (float) (color >> 16 & 255) / 255.0F;
        float f1 = (float) (color >> 8 & 255) / 255.0F;
        float f2 = (float) (color & 255) / 255.0F;
        GL11.glColor4f(f, f1, f2, f3);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(left, bottom);
        GL11.glVertex2f(right, bottom);
        GL11.glVertex2f(right, top);
        GL11.glVertex2f(left, top);
        GL11.glEnd();
    }
    private void drawOutlineInternal(float left, float top, float right, float bottom, float lineWidth, int color) {
        float f3 = (float) (color >> 24 & 255) / 255.0F;
        float f = (float) (color >> 16 & 255) / 255.0F;
        float f1 = (float) (color >> 8 & 255) / 255.0F;
        float f2 = (float) (color & 255) / 255.0F;
        GL11.glLineWidth(lineWidth);
        GL11.glColor4f(f, f1, f2, f3);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(left, bottom);
        GL11.glVertex2f(right, bottom);
        GL11.glVertex2f(right, top);
        GL11.glVertex2f(left, top);
        GL11.glEnd();
    }
    private Color getDisplayColor(float health, float maxHealth) {
        float ratio = Math.max(0.0f, Math.min(1.0f, health / maxHealth));
        switch (colorMode.getValue()) {
            case 1:
                HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
                return hud != null ? hud.getColor(System.currentTimeMillis()) : Color.CYAN;
            case 2:
                return ColorUtil.interpolate((float)(Math.sin(System.currentTimeMillis() / 400.0) + 1) / 2.0f, new Color(80, 140, 255), new Color(180, 80, 255));
            default:
                return ColorUtil.getHealthBlend(ratio);
        }
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
