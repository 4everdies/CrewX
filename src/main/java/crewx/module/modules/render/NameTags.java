package crewx.module.modules.render;

import crewx.gui.ClientFont;
import crewx.module.modules.combat.*;
import crewx.module.modules.movement.*;
import crewx.module.modules.render.*;
import crewx.module.modules.player.*;
import crewx.module.modules.misc.*;

import crewx.CrewX;
import crewx.enums.ChatColors;
import crewx.event.EventTarget;
import crewx.events.Render2DEvent;
import crewx.events.Render3DEvent;
import crewx.clickgui.render.RoundedUtils;
import crewx.gui.GuiRender;
import crewx.mixin.IAccessorRenderManager;
import crewx.module.Module;
import crewx.util.ColorUtil;
import crewx.util.RenderUtil;
import crewx.util.TeamUtil;
import crewx.property.properties.*;
import crewx.property.properties.BooleanProperty;
import crewx.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.*;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;
import org.apache.commons.lang3.StringUtils;

import java.awt.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class NameTags extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final DecimalFormat healthFormatter = new DecimalFormat("0.0", new DecimalFormatSymbols(Locale.US));
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 2.0F);
    public final BooleanProperty autoScale = new BooleanProperty("auto-scale", true);
    public final PercentProperty backgroundOpacity = new PercentProperty("background", 25);
    public final FloatProperty borderWidth = new FloatProperty("border-width", 0.0F, 0.0F, 8.0F);
    public final BooleanProperty shadow = new BooleanProperty("shadow", true);
    public final ModeProperty distanceMode = new ModeProperty("distance", 0, new String[]{"None", "Default", "Vape"});
    public final ModeProperty healthMode = new ModeProperty("health", 2, new String[]{"None", "HP", "Hearts", "Tab"});
    public final BooleanProperty armor = new BooleanProperty("armor", true);
    public final BooleanProperty effects = new BooleanProperty("effects", true);
    public final BooleanProperty players = new BooleanProperty("players", true);
    public final BooleanProperty friends = new BooleanProperty("friends", true);
    public final BooleanProperty enemies = new BooleanProperty("enemies", true);
    public final BooleanProperty bossees = new BooleanProperty("bosses", false);
    public final BooleanProperty mobs = new BooleanProperty("mobs", false);
    public final BooleanProperty creepers = new BooleanProperty("creepers", false);
    public final BooleanProperty endermans = new BooleanProperty("endermen", false);
    public final BooleanProperty blazes = new BooleanProperty("blazes", false);
    public final BooleanProperty animals = new BooleanProperty("animals", false);
    public final BooleanProperty self = new BooleanProperty("self", false);
    public final BooleanProperty bots = new BooleanProperty("bots", false);

    public NameTags() {
        super("NameTags", false);
    }

    public boolean shouldRenderTags(EntityLivingBase entityLivingBase) {
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
                    return TeamUtil.isTarget((EntityPlayer) entityLivingBase) ? this.enemies.getValue() : this.players.getValue();
                }
            } else {
                return this.self.getValue() && mc.gameSettings.thirdPersonView != 0;
            }
        } else if (entityLivingBase instanceof EntityDragon || entityLivingBase instanceof EntityWither) {
            return !entityLivingBase.isInvisible() && this.bossees.getValue();
        } else if (!(entityLivingBase instanceof EntityMob) && !(entityLivingBase instanceof EntitySlime)) {
            return (entityLivingBase instanceof EntityAnimal
                    || entityLivingBase instanceof EntityBat
                    || entityLivingBase instanceof EntitySquid
                    || entityLivingBase instanceof EntityVillager) && this.animals.getValue();
        } else if (entityLivingBase instanceof EntityCreeper) {
            return this.creepers.getValue();
        } else if (entityLivingBase instanceof EntityEnderman) {
            return this.endermans.getValue();
        } else {
            return entityLivingBase instanceof EntityBlaze ? this.blazes.getValue() : this.mobs.getValue();
        }
    }

    /** One tag to draw this frame: where the entity's head is on screen (scaled GUI units), from the world camera. */
    private static final class Tag {
        final EntityLivingBase entity;
        final float x;
        final float y;
        final double distance;

        Tag(EntityLivingBase entity, float x, float y, double distance) {
            this.entity = entity;
            this.x = x;
            this.y = y;
            this.distance = distance;
        }
    }

    private static final int GOLD = 0xFFF5C542;
    private final float[] projected = new float[3];
    private final List<Tag> tags = new ArrayList<>();
    public boolean hasProjectedTags() { return this.isEnabled() && !this.tags.isEmpty(); }
    /** Per entity: shown health (slides to the real one) and the lighter "damage trail" that follows it. */
    private final Map<EntityLivingBase, float[]> shownHealth = new WeakHashMap<>();
    private final float[] screenPosition = new float[2];
    private long lastFrame;

    /**
     * The tags are drawn flat on the HUD (crisp client font, rounded panel) at the point above each head, projected
     * with the matrices of the world pass - the same camera the entities were drawn with, view bobbing included.
     */
    @EventTarget
    public void onRender(Render3DEvent event) {
        this.tags.clear();
        if (!this.isEnabled() || mc.getRenderViewEntity() == null) return;
        ScaledResolution sr = new ScaledResolution(mc);
        int factor = sr.getScaleFactor();
        IAccessorRenderManager renderManager = (IAccessorRenderManager) mc.getRenderManager();
        for (Entity entity : TeamUtil.getLoadedEntitiesSorted()) {
            if (!(entity instanceof EntityLivingBase) || !this.shouldRenderTags((EntityLivingBase) entity)) continue;
            if (!entity.ignoreFrustumCheck && !RenderUtil.isInViewFrustum(entity.getEntityBoundingBox(), 2.0)) continue;
            if (StringUtils.isBlank(EnumChatFormatting.getTextWithoutFormattingCodes(TeamUtil.stripName(entity)))) continue;
            float partialTicks = event.getPartialTicks();
            double x = RenderUtil.lerpDouble(entity.posX, entity.lastTickPosX, partialTicks) - renderManager.getRenderPosX();
            double y = RenderUtil.lerpDouble(entity.posY, entity.lastTickPosY, partialTicks) - renderManager.getRenderPosY()
                    + entity.height + (entity.isSneaking() ? 0.12 : 0.28);
            double z = RenderUtil.lerpDouble(entity.posZ, entity.lastTickPosZ, partialTicks) - renderManager.getRenderPosZ();
            // the frame's world camera as Minecraft already read it: no extra glGet (each one can stall the frame)
            if (crewx.util.CameraMatrices.project(x, y, z, this.projected) == null) continue;   // behind the camera
            this.tags.add(new Tag((EntityLivingBase) entity, this.projected[0] / factor,
                    (mc.displayHeight - this.projected[1]) / factor, mc.getRenderViewEntity().getDistanceToEntity(entity)));
        }
        // far ones first, so the closest tag ends up on top
        this.tags.sort((a, b) -> Double.compare(b.distance, a.distance));
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        long now = System.nanoTime();
        float dt = this.lastFrame == 0L ? 0.016F : Math.max(0.0F, Math.min(0.1F, (now - this.lastFrame) / 1_000_000_000.0F));
        this.lastFrame = now;
        if (!this.isEnabled() || this.tags.isEmpty()) return;
        GlStateManager.pushMatrix();
        try {
            for (Tag tag : this.tags) {
                if (tag.entity.isDead) continue;
                drawTag(tag, dt);
            }
        } finally {
            GlStateManager.popMatrix();
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private void drawTag(Tag tag, float dt) {
        EntityLivingBase entity = tag.entity;
        // The tag is projected with this very frame's camera (view bobbing included), so it is drawn exactly there.
        // Any time-based smoothing of the screen position lags behind the bobbing the player model follows and
        // shows up as the tag shaking while walking. Snapping to whole framebuffer pixels stops sub-pixel shimmer.
        float pixel = 1.0F / Math.max(1, new ScaledResolution(mc).getScaleFactor());
        float[] screen = this.screenPosition;
        screen[0] = Math.round(tag.x / pixel) * pixel;
        screen[1] = Math.round(tag.y / pixel) * pixel;
        float size = this.scale.getValue() * (this.autoScale.getValue()
                ? clamp(1.1F - (float) tag.distance * 0.004F, 0.85F, 1.1F)
                : clamp(7.0F / (float) Math.max(tag.distance, 1.0), 0.45F, 1.1F));

        float health = entity.getHealth();
        float absorption = entity.getAbsorptionAmount();
        float percent = clamp((health + absorption) / Math.max(1.0F, entity.getMaxHealth()), 0.0F, 1.0F);
        float[] shown = this.shownHealth.get(entity);
        if (shown == null) {
            shown = new float[]{percent, percent};
            this.shownHealth.put(entity, shown);
        }
        shown[0] += (percent - shown[0]) * (1.0F - (float) Math.exp(-dt * 14.0F));
        // the trail holds on, then catches up: shows how much the last hit took
        shown[1] = percent > shown[1] ? shown[0] : shown[1] + (shown[0] - shown[1]) * (1.0F - (float) Math.exp(-dt * 3.0F));
        int healthColor = ColorUtil.getHealthBlend(percent).getRGB() | 0xFF000000;

        // text pieces: distance, name (team colours kept), health
        String distanceText = null;
        int distanceColor = 0xFF8C9099;
        switch (this.distanceMode.getValue()) {
            case 1:
                distanceText = (int) tag.distance + "m";
                break;
            case 2:
                distanceText = "[" + (int) tag.distance + "]";
                distanceColor = 0xFF7BE08A;
                break;
        }
        String healthText = null;
        int healthTextColor = healthColor;
        switch (this.healthMode.getValue()) {
            case 1:
                healthText = String.valueOf((int) Math.ceil(health));
                break;
            case 2:
                healthText = healthFormatter.format(health / 2.0);
                break;
            case 3:
                if (entity instanceof EntityPlayer && mc.theWorld.getScoreboard() != null) {
                    ScoreObjective objective = mc.theWorld.getScoreboard().getObjectiveInDisplaySlot(2);
                    if (objective != null) {
                        Score score = mc.theWorld.getScoreboard().getValueFromObjective(entity.getName(), objective);
                        if (score != null) {
                            healthText = String.valueOf(score.getScorePoints());
                            healthTextColor = 0xFFFFE066;
                        }
                    }
                }
                break;
        }
        String absorptionText = absorption > 0.0F && (this.healthMode.getValue() == 1 || this.healthMode.getValue() == 2)
                ? "+" + (this.healthMode.getValue() == 1 ? String.valueOf((int) Math.ceil(absorption)) : healthFormatter.format(absorption / 2.0))
                : null;
        List<Object[]> name = segments(TeamUtil.stripName(entity), 0xFFFFFFFF);

        float gap = 4.0F;
        float nameW = 0.0F;
        for (Object[] part : name) nameW += ClientFont.getStringWidthFloat((String) part[0]);
        float small = 0.86F;
        float distanceW = distanceText == null ? 0.0F : ClientFont.getStringWidthFloat(distanceText) * small + gap;
        float healthW = healthText == null ? 0.0F : gap + ClientFont.getStringWidthFloat(healthText);
        float absorptionW = absorptionText == null ? 0.0F : 2.0F + ClientFont.getStringWidthFloat(absorptionText) * small;
        float padX = 5.0F;
        float fontH = ClientFont.getHeight();
        float w = padX * 2.0F + distanceW + nameW + healthW + absorptionW;
        float h = fontH + 7.0F;

        GlStateManager.pushMatrix();
        GlStateManager.translate(screen[0], screen[1], 0.0F);
        GlStateManager.scale(size, size, 1.0F);
        float x = -w / 2.0F;
        float y = -h;

        boolean dimmed = entity.isSneaking() || entity.isInvisible();
        int accent = 0;
        if (entity instanceof EntityPlayer) {
            if (TeamUtil.isFriend((EntityPlayer) entity)) accent = CrewX.friendManager.getColor().getRGB() | 0xFF000000;
            else if (TeamUtil.isTarget((EntityPlayer) entity)) accent = CrewX.targetManager.getColor().getRGB() | 0xFF000000;
        }
        float opacity = this.backgroundOpacity.getValue() / 100.0F;
        if (opacity > 0.0F) {
            int alpha = (int) (255.0F * clamp(0.42F + 0.55F * opacity, 0.0F, 0.97F));
            if (this.shadow.getValue()) {
                for (int i = 3; i >= 1; i--) {
                    RoundedUtils.drawRoundedRect(x - i, y - i + 1.0F, w + i * 2.0F, h + i * 2.0F, (alpha / 9) << 24, 4.5F + i);
                }
            }
            int fill = (dimmed ? 0x2A1530 : 0x0F1013) | alpha << 24;
            RoundedUtils.drawRoundedRect(x, y, w, h, fill, 4.5F);
            GuiRender.roundedOutline(x, y, w, h, 4.5, 1.0F, accent != 0 ? GuiRender.alpha(accent, 0.9F) : 0x1CFFFFFF);
        } else if (accent != 0) {
            GuiRender.roundedOutline(x, y, w, h, 4.5, 1.0F, GuiRender.alpha(accent, 0.9F));
        }
        if (this.borderWidth.getValue() > 0.0F) {
            GuiRender.roundedOutline(x - 0.5F, y - 0.5F, w + 1.0F, h + 1.0F, 5.0, this.borderWidth.getValue(), 0xC8000000);
        }

        // health line along the bottom edge: dark track, lighter trail of the last hit, then the health itself
        float barX = x + 4.0F;
        float barW = w - 8.0F;
        float barY = y + h - 2.6F;
        RoundedUtils.drawRoundedRect(barX, barY, barW, 1.5F, 0x50000000, 0.75F);
        if (shown[1] > shown[0] + 0.005F) {
            RoundedUtils.drawRoundedRect(barX, barY, barW * Math.min(1.0F, shown[1]), 1.5F, 0xB0FFFFFF & GuiRender.mix(healthColor, 0xFFFFFFFF, 0.6F), 0.75F);
        }
        if (shown[0] > 0.01F) {
            RoundedUtils.drawRoundedRect(barX, barY, Math.max(1.5F, barW * shown[0]), 1.5F, healthColor, 0.75F);
        }

        // text
        int textAlpha = dimmed ? 0xB0 : 0xFF;
        float tx = x + padX;
        float ty = y + 2.5F;
        boolean shadowText = this.shadow.getValue();
        if (distanceText != null) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(tx, ty + fontH * (1.0F - small) * 0.6F, 0.0F);
            GlStateManager.scale(small, small, 1.0F);
            text(distanceText, 0.0F, 0.0F, withAlpha(distanceColor, textAlpha), shadowText);
            GlStateManager.popMatrix();
            tx += distanceW;
        }
        for (Object[] part : name) {
            int color = (Integer) part[1];
            if (accent != 0 && color == 0xFFFFFFFF) color = GuiRender.mix(0xFFFFFFFF, accent, 0.35F);
            text((String) part[0], tx, ty, withAlpha(color, textAlpha), shadowText);
            tx += ClientFont.getStringWidthFloat((String) part[0]);
        }
        if (healthText != null) {
            tx += gap;
            text(healthText, tx, ty, withAlpha(healthTextColor, textAlpha), shadowText);
            tx += ClientFont.getStringWidthFloat(healthText);
        }
        if (absorptionText != null) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(tx + 2.0F, ty + fontH * (1.0F - small) * 0.6F, 0.0F);
            GlStateManager.scale(small, small, 1.0F);
            text(absorptionText, 0.0F, 0.0F, withAlpha(GOLD, textAlpha), shadowText);
            GlStateManager.popMatrix();
        }

        // armour and held item above the panel, then the potion icons above those
        float top = y - 2.0F;
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            if (this.armor.getValue()) {
                List<ItemStack> items = new ArrayList<>();
                for (int i = 4; i >= 0; i--) {
                    ItemStack stack = i == 0 ? player.getHeldItem() : player.inventory.armorInventory[i - 1];
                    if (stack != null) items.add(stack);
                }
                if (!items.isEmpty()) {
                    float rowW = items.size() * 17.0F - 1.0F;
                    float ix = -rowW / 2.0F;
                    float iy = top - 17.0F;
                    if (opacity > 0.0F) {
                        RoundedUtils.drawRoundedRect(ix - 2.0F, iy - 1.0F, rowW + 4.0F, 18.0F,
                                (int) (255.0F * clamp(0.25F + 0.4F * opacity, 0.0F, 0.8F)) << 24 | 0x0F1013, 4.0F);
                    }
                    for (int i = 0; i < items.size(); i++) drawItem(items.get(i), ix + i * 17.0F, iy);
                    top = iy - 3.0F;
                }
            }
            if (this.effects.getValue()) {
                List<PotionEffect> effects = player.getActivePotionEffects().stream()
                        .filter(effect -> Potion.potionTypes[effect.getPotionID()].hasStatusIcon())
                        .collect(Collectors.toList());
                if (!effects.isEmpty()) {
                    float iconSize = 10.0F;
                    float rowW = effects.size() * (iconSize + 1.0F) - 1.0F;
                    for (int i = 0; i < effects.size(); i++) {
                        drawEffect(effects.get(i), -rowW / 2.0F + i * (iconSize + 1.0F), top - iconSize, iconSize);
                    }
                }
            }
        }
        GlStateManager.popMatrix();
    }

    /** Splits a formatted name into (text, ARGB colour) runs, keeping team colours; other codes are dropped. */
    private static List<Object[]> segments(String formatted, int defaultColor) {
        List<Object[]> out = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        int color = defaultColor;
        for (int i = 0; i < formatted.length(); i++) {
            char c = formatted.charAt(i);
            if (c == '\u00a7' && i + 1 < formatted.length()) {
                char code = Character.toLowerCase(formatted.charAt(++i));
                int index = "0123456789abcdef".indexOf(code);
                if (index < 0 && code != 'r') continue;
                if (run.length() > 0) {
                    out.add(new Object[]{run.toString(), color});
                    run.setLength(0);
                }
                color = index < 0 ? defaultColor : mc.fontRendererObj.getColorCode(code) | 0xFF000000;
                continue;
            }
            run.append(c);
        }
        if (run.length() > 0) out.add(new Object[]{run.toString(), color});
        return out;
    }

    private static void text(String text, float x, float y, int color, boolean shadow) {
        if (shadow) ClientFont.drawString(text, x + 0.6F, y + 0.6F, (color >>> 24) / 3 << 24, false);
        ClientFont.drawString(text, x, y, color, false);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0xFFFFFF) | alpha << 24;
    }

    private static void drawItem(ItemStack stack, float x, float y) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().zLevel = -100.0F;
        mc.getRenderItem().renderItemAndEffectIntoGUI(stack, 0, 0);
        mc.getRenderItem().renderItemOverlays(mc.fontRendererObj, stack, 0, 0);
        mc.getRenderItem().zLevel = 0.0F;
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.popMatrix();
    }

    private static void drawEffect(PotionEffect effect, float x, float y, float size) {
        int icon = Potion.potionTypes[effect.getPotionID()].getStatusIconIndex();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.scale(size / 18.0F, size / 18.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(INVENTORY);
        Gui.drawModalRectWithCustomSizedTexture(0, 0, icon % 8 * 18, 198 + icon / 8 * 18, 18, 18, 256.0F, 256.0F);
        GlStateManager.popMatrix();
    }

    private static final ResourceLocation INVENTORY = new ResourceLocation("textures/gui/container/inventory.png");

    private static float clamp(float value, float min, float max) {
        return value < min ? min : value > max ? max : value;
    }
}
