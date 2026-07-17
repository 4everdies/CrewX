package myau.util;

import myau.Myau;
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

    public static List<Entity> getLoadedEntitiesSorted() {
        if (mc.theWorld == null) return java.util.Collections.emptyList();
        
        return mc.theWorld.loadedEntityList.stream().sorted((entity1, entity2) -> {
            double dist1 = mc.getRenderManager().getDistanceToCamera(entity1.posX, entity1.posY, entity1.posZ);
            double dist2 = mc.getRenderManager().getDistanceToCamera(entity2.posX, entity2.posY, entity2.posZ);
            if (dist1 < dist2) return 1;
            if (dist1 > dist2) return -1;
            return entity1.getUniqueID().toString().compareTo(entity2.getUniqueID().toString());
        }).collect(Collectors.toList());
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
        return Myau.friendManager.isFriend(player.getName());
    }

    public static boolean isTarget(EntityPlayer player) {
        return Myau.targetManager.isFriend(player.getName());
    }
}
