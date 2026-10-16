package crewx.util;

import crewx.CrewX;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

public class TeamUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static boolean isKaizenServer() {
        try {
            if (mc.getCurrentServerData() != null) {
                String serverIp = mc.getCurrentServerData().serverIP;
                return serverIp != null && serverIp.toLowerCase().contains("kaizen");
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static boolean isEntityLoaded(Entity entity) {
        if (entity == null || mc.theWorld == null) return false;
        return mc.theWorld.loadedEntityList.contains(entity);
    }

    private static List<Entity> sortedCache = java.util.Collections.emptyList();
    private static Object sortedWorld;
    private static int sortedTick = Integer.MIN_VALUE;
    private static int sortedSize = -1;

    /**
     * Loaded entities, farthest first. Several render modules ask for this during each frame; cache the ordering
     * for the current game tick so mobile clients do not allocate and sort entity arrays at display refresh rate.
     * The ordering may be up to one tick old, which is visually negligible. The returned list must not be modified.
     */
    public static List<Entity> getLoadedEntitiesSorted() {
        if (mc.theWorld == null) return java.util.Collections.emptyList();
        net.minecraft.client.renderer.entity.RenderManager renderManager = mc.getRenderManager();
        double cx = renderManager.viewerPosX, cy = renderManager.viewerPosY, cz = renderManager.viewerPosZ;
        List<Entity> loaded = mc.theWorld.loadedEntityList;
        int tick = mc.thePlayer != null ? mc.thePlayer.ticksExisted : (int) mc.theWorld.getTotalWorldTime();
        if (sortedWorld == mc.theWorld && sortedSize == loaded.size() && sortedTick == tick) {
            return sortedCache;
        }
        int n = loaded.size();
        final Entity[] entities = loaded.toArray(new Entity[n]);
        final double[] distance = new double[n];
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            Entity entity = entities[i];
            double dx = entity.posX - cx, dy = entity.posY - cy, dz = entity.posZ - cz;
            distance[i] = dx * dx + dy * dy + dz * dz;
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> {
            int byDistance = Double.compare(distance[b], distance[a]);
            return byDistance != 0 ? byDistance : Integer.compare(entities[a].getEntityId(), entities[b].getEntityId());
        });
        List<Entity> result = new java.util.ArrayList<Entity>(n);
        for (int i = 0; i < n; i++) result.add(entities[order[i]]);
        sortedCache = java.util.Collections.unmodifiableList(result);
        sortedWorld = mc.theWorld;
        sortedSize = n;
        sortedTick = tick;
        return sortedCache;
    }

    /**
     * True when the player is listed in the server's tab list. For visuals (tags, boxes) a listed player is a real
     * player even when a bot check flags it: some servers never send the packets those checks wait for (attributes,
     * for example), which used to leave ordinary players without a name tag.
     */
    public static boolean isInTabList(EntityPlayer player) {
        return player != null && mc.getNetHandler() != null && player.getUniqueID() != null
                && mc.getNetHandler().getPlayerInfo(player.getUniqueID()) != null;
    }

    public static float getHealthScore(EntityLivingBase entityLivingBase) {
        int armor = entityLivingBase.getTotalArmorValue();
        float armorFactor = (armor == 0) ? 1.0f : (20.0f / (float) armor);
        return entityLivingBase.getHealth() * armorFactor;
    }

    public static String stripName(Entity entity) {
        return entity.getDisplayName().getFormattedText().replaceAll("§\\S$", "").replaceAll("(?i)§r", "§f").trim();
    }

    public static Color getTeamColor(EntityPlayer player, float alpha) {
        int colorCode = 0xFFFFFF;
        ScorePlayerTeam playerTeam = (ScorePlayerTeam) player.getTeam();
        if (playerTeam != null) {
            String colorPrefix = FontRenderer.getFormatFromString(playerTeam.getColorPrefix());
            if (colorPrefix.length() >= 2) {
                colorCode = mc.fontRendererObj.getColorCode(colorPrefix.charAt(1));
            }
        }
        return new Color((colorCode & 0xFFFFFF) | ((int)(alpha * 255) << 24), true);
    }

    public static String getPlayerTeamTag(EntityPlayer player) {
        String displayName = player.getDisplayName().getUnformattedText().toUpperCase();
        if (displayName.contains("[VERMELHO]")) return "VERMELHO";
        if (displayName.contains("[AZUL]")) return "AZUL";
        if (displayName.contains("[AMARELO]")) return "AMARELO";
        if (displayName.contains("[AQUA]")) return "AQUA";
        if (displayName.contains("[ROXO]")) return "ROXO";
        if (displayName.contains("[PRETO]")) return "PRETO";
        if (displayName.contains("[CINZA]")) return "CINZA";
        if (displayName.contains("[ROSA]")) return "ROSA";
        if (displayName.contains("[VERDE]")) return "VERDE";
        if (displayName.contains("[LARANJA]")) return "LARANJA";
        if (displayName.contains("[BRANCO]")) return "BRANCO";
        if (displayName.contains("[MARROM]")) return "MARROM";
        return null;
    }

    public static boolean isSameTeam(EntityPlayer player) {
        if (player == mc.thePlayer) {
            return true;
        }

        if (isKaizenServer()) {
            String selfTag = getPlayerTeamTag(mc.thePlayer);
            String targetTag = getPlayerTeamTag(player);
            if (selfTag != null && targetTag != null) {
                return selfTag.equals(targetTag);
            }
            return false;
        }

        NetworkPlayerInfo selfInfo = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        if (selfInfo == null) return false;

        ScorePlayerTeam selfTeam = selfInfo.getPlayerTeam();
        if (selfTeam == null) return false;

        NetworkPlayerInfo targetInfo = mc.getNetHandler().getPlayerInfo(player.getUniqueID());
        if (targetInfo == null) return false;

        ScorePlayerTeam targetTeam = targetInfo.getPlayerTeam();
        if (targetTeam == null) return false;

        String selfPrefix = selfTeam.getColorPrefix();
        String targetPrefix = targetTeam.getColorPrefix();

        if (selfPrefix == null || targetPrefix == null) return false;

        return selfPrefix.equals(targetPrefix);
    }

    public static boolean hasTeamColor(EntityLivingBase entity) {
        if (entity == mc.thePlayer) {
            return true;
        }

        if (isKaizenServer()) {
            if (!(entity instanceof EntityPlayer)) return false;
            return getPlayerTeamTag((EntityPlayer) entity) != null;
        }

        NetworkPlayerInfo selfInfo = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        if (selfInfo == null) return false;

        ScorePlayerTeam selfTeam = selfInfo.getPlayerTeam();
        if (selfTeam == null) return false;

        String selfPrefix = selfTeam.getColorPrefix();
        if (selfPrefix == null || selfPrefix.length() < 2) return false;

        EntityLivingBase nearestArmorStand = mc.theWorld.findNearestEntityWithinAABB(EntityArmorStand.class, entity.getEntityBoundingBox(), entity);
        if (nearestArmorStand != null) {
            return nearestArmorStand.getName().contains(selfPrefix.substring(0, 2));
        }
        return false;
    }

    public static boolean isShop(EntityLivingBase entity) {
        if (entity == mc.thePlayer || mc.theWorld == null) {
            return false;
        }

        EntityLivingBase armorStand = mc.theWorld.findNearestEntityWithinAABB(EntityArmorStand.class, entity.getEntityBoundingBox(), entity);
        if (armorStand == null) return false;

        String displayName = armorStand.getName();
        if (displayName.contains("RIGHT CLICK")) return true;
        if (displayName.contains("ITEM SHOP")) return true;
        if (displayName.contains("UPGRADES")) return true;
        if (displayName.contains("BANKER")) return true;
        return displayName.contains("STREAK POWERS");
    }

    public static boolean isFriend(EntityPlayer player) {
        return CrewX.friendManager.isFriend(player.getName());
    }

    public static boolean isTarget(EntityPlayer player) {
        return CrewX.targetManager.isFriend(player.getName());
    }
}
