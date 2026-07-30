package myau.module.modules;

import myau.Myau;
import myau.data.PlayerData;
import myau.enums.ChatColors;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.LoadWorldEvent;
import myau.events.PacketEvent;
import myau.events.PlayerUpdateEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.util.ChatUtil;
import myau.util.TeamUtil;
import net.minecraft.block.BlockAir;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.DataWatcher.WatchableObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.event.ClickEvent;
import net.minecraft.item.ItemBlock;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class HackerDetector extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanProperty detectAutoBlock = new BooleanProperty("detect-autoblock", true);
    public final BooleanProperty detectKillAura = new BooleanProperty("detect-killaura", true);
    public final BooleanProperty detectScaffold = new BooleanProperty("detect-scaffold", true);
    public final BooleanProperty detectNoSlow = new BooleanProperty("detect-noslow", true);
    public final BooleanProperty detectNoFall = new BooleanProperty("detect-nofall", true);
    public final BooleanProperty detectLegitScaffold = new BooleanProperty("detect-legit-scaffold", true);
    public final BooleanProperty addToTargets = new BooleanProperty("add-to-targets", true);
    public final BooleanProperty enemyAdd = new BooleanProperty("add-as-enemy", false);
    public final BooleanProperty autoReport = new BooleanProperty("auto-report", false);
    public final BooleanProperty ignoreTeammates = new BooleanProperty("ignore-teammates", false);
    public final BooleanProperty atlasSuspect = new BooleanProperty("atlas-suspect-only", false);
    public final BooleanProperty shouldPing = new BooleanProperty("play-sound", true);
    public final IntProperty flagWindow = new IntProperty("flag-window-seconds", 5, 1, 30);
    public final IntProperty alertCooldown = new IntProperty("alert-cooldown-seconds", 5, 1, 60);

    private static final String CHEAT_AUTOBLOCK = "AutoBlock";
    private static final String CHEAT_NOSLOW = "Noslow";
    private static final String CHEAT_KILLAURA = "KillAura";
    private static final String CHEAT_SCAFFOLD = "Scaffold";
    private static final String CHEAT_NOFALL = "NoFall";
    private static final String CHEAT_LEGIT_SCAFFOLD = "LegitScaffold";

    private final Map<String, int[]> flagMap = new HashMap<>();
    private final Map<String, Integer> alertCooldowns = new HashMap<>();
    private final HashMap<UUID, PlayerData> players = new HashMap<>();
    private long lastAlert = 0;

    public HackerDetector() {
        super("HackerDetector", false);
    }

    private int nowSecs() {
        return mc.theWorld == null ? 0 : (int) (mc.theWorld.getTotalWorldTime() / 20);
    }

    private boolean shouldSkip(EntityPlayer player) {
        if (player == null || player == mc.thePlayer) return true;
        if (Myau.friendManager.isFriend(player.getName())) return true;
        if (ignoreTeammates.getValue() && TeamUtil.isSameTeam(player)) return true;
        if (atlasSuspect.getValue() && !player.getName().equals("Suspect\u00a7r")) return true;
        AntiBot antiBot = (AntiBot) Myau.moduleManager.modules.get(AntiBot.class);
        return antiBot != null && antiBot.isBot(player);
    }

    public void receiveSignal(EntityPlayer player, String cheatName) {
        if (!this.isEnabled()) return;
        if (player == null || cheatName == null) return;
        if (mc.theWorld == null || mc.thePlayer == null) return;
        if (shouldSkip(player)) return;
        if (!isKnownCheck(cheatName)) return;

        String playerName = player.getName();
        int now = nowSecs();
        String key = playerName.toLowerCase(Locale.ROOT) + ":" + cheatName;
        int[] data = flagMap.getOrDefault(key, new int[]{0, now});
        if (now - data[1] > flagWindow.getValue()) data[0] = 0;
        data[0] += 1;
        data[1] = now;
        flagMap.put(key, data);

        int max = maxFlagsFor(cheatName);
        int lastAlertTime = alertCooldowns.getOrDefault(key, -alertCooldown.getValue());
        if (data[0] >= max && now - lastAlertTime >= alertCooldown.getValue()) {
            ChatComponentText msg = new ChatComponentText(ChatColors.formatColor(
                    "&7[&dR&7]&r " + player.getDisplayName().getUnformattedText() + " &7detected for &d" + cheatName));
            ChatStyle style = new ChatStyle();
            style.setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wdr " + playerName));
            ChatComponentText wdr = new ChatComponentText(ChatColors.formatColor(" \u00a77[\u00a7cWDR\u00a77]"));
            wdr.setChatStyle(style);
            msg.appendSibling(wdr);
            mc.thePlayer.addChatMessage(msg);

            try {
                myau.module.modules.Notifications.pushRaw(
                        "Hacker Detected",
                        playerName + " - " + cheatName + " suspected");
            } catch (Throwable ignored) {}

            long nowMs = System.currentTimeMillis();
            if (shouldPing.getValue() && nowMs - lastAlert >= 1500L) {
                mc.thePlayer.playSound("note.pling", 1.0f, 1.0f);
                lastAlert = nowMs;
            } else {
                mc.thePlayer.playSound("random.orb", 0.3f, 1.0f);
            }

            if (enemyAdd.getValue() && Myau.targetManager != null) {
                Myau.targetManager.add(playerName);
            }
            if (addToTargets.getValue() && Myau.targetManager != null) {
                Myau.targetManager.add(playerName);
            }
            if (autoReport.getValue()) {
                mc.thePlayer.sendChatMessage("/wdr " + stripColor(player.getGameProfile().getName()));
            }

            alertCooldowns.put(key, now);
            flagMap.remove(key);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.POST || mc.theWorld == null) return;
        int now = nowSecs();
        flagMap.entrySet().removeIf(e -> now - e.getValue()[1] > flagWindow.getValue());
        alertCooldowns.entrySet().removeIf(e -> now - e.getValue() > alertCooldown.getValue());
    }

    @EventTarget
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (!this.isEnabled() || mc.isSingleplayer() || mc.theWorld == null || mc.thePlayer == null) return;

        for (EntityPlayer entityPlayer : mc.theWorld.playerEntities) {
            if (shouldSkip(entityPlayer)) continue;

            PlayerData data = players.get(entityPlayer.getUniqueID());
            if (data == null) {
                data = new PlayerData();
            }
            data.update(entityPlayer);
            performCheck(entityPlayer, data);
            data.updateServerPos(entityPlayer);
            data.updateSneak(entityPlayer);
            players.put(entityPlayer.getUniqueID(), data);
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.RECEIVE) return;
        if (mc.theWorld == null || mc.thePlayer == null) return;

        Object p = event.getPacket();

        if (p instanceof S08PacketPlayerPosLook) {
            ChatUtil.sendFormatted(Myau.clientName + "&7Server flag detected (Lagback)");
        }

        if (p instanceof S1CPacketEntityMetadata && detectAutoBlock.getValue()) {
            S1CPacketEntityMetadata pkt = (S1CPacketEntityMetadata) p;
            List<WatchableObject> data = pkt.func_149376_c();
            if (data == null) return;
            Entity entity = mc.theWorld.getEntityByID(pkt.getEntityId());
            if (!(entity instanceof EntityPlayer) || entity == mc.thePlayer) return;
            for (WatchableObject wo : data) {
                if (wo.getDataValueId() == 0 && wo.getObject() instanceof Byte) {
                    byte flags = (Byte) wo.getObject();
                    boolean using = (flags & 16) != 0;
                    if (using) {
                        receiveSignal((EntityPlayer) entity, CHEAT_AUTOBLOCK);
                    }
                }
            }
        }

        if (p instanceof S12PacketEntityVelocity && detectKillAura.getValue()) {
            S12PacketEntityVelocity pkt = (S12PacketEntityVelocity) p;
            Entity entity = mc.theWorld.getEntityByID(pkt.getEntityID());
            if (entity instanceof EntityPlayer && entity != mc.thePlayer) {
                double d = entity.getDistanceToEntity(mc.thePlayer);
                if (d < 4.5D) {
                    receiveSignal((EntityPlayer) entity, CHEAT_KILLAURA);
                }
            }
        }

        if (p instanceof S18PacketEntityTeleport && detectScaffold.getValue()) {
            S18PacketEntityTeleport pkt = (S18PacketEntityTeleport) p;
            Entity entity = mc.theWorld.getEntityByID(pkt.getEntityId());
            if (entity instanceof EntityPlayer && entity != mc.thePlayer) {
                double dx = entity.posX - (pkt.getX() / 32.0D);
                double dz = entity.posZ - (pkt.getZ() / 32.0D);
                if (dx * dx + dz * dz > 4.0D) {
                    receiveSignal((EntityPlayer) entity, CHEAT_SCAFFOLD);
                }
            }
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        players.clear();
        flagMap.clear();
        alertCooldowns.clear();
    }

    @Override
    public void onDisabled() {
        players.clear();
        flagMap.clear();
        alertCooldowns.clear();
        lastAlert = 0;
    }

    private void performCheck(EntityPlayer entityPlayer, PlayerData playerData) {
        if (detectAutoBlock.getValue() && playerData.autoBlockTicks >= 10) {
            receiveSignal(entityPlayer, CHEAT_AUTOBLOCK);
            return;
        }
        if (detectLegitScaffold.getValue() && playerData.sneakTicks >= 3) {
            receiveSignal(entityPlayer, CHEAT_LEGIT_SCAFFOLD);
            return;
        }
        if (detectNoSlow.getValue() && playerData.noSlowTicks >= 11 && playerData.speed >= 0.08) {
            receiveSignal(entityPlayer, CHEAT_NOSLOW);
            return;
        }
        if (detectScaffold.getValue()
                && entityPlayer.isSwingInProgress
                && entityPlayer.rotationPitch >= 70.0f
                && entityPlayer.getHeldItem() != null
                && entityPlayer.getHeldItem().getItem() instanceof ItemBlock
                && playerData.fastTick >= 20
                && entityPlayer.ticksExisted - playerData.lastSneakTick >= 30
                && entityPlayer.ticksExisted - playerData.aboveVoidTicks >= 20) {
            boolean overAir = true;
            BlockPos blockPos = entityPlayer.getPosition().down(2);
            for (int i = 0; i < 4; ++i) {
                if (!(mc.theWorld.getBlockState(blockPos).getBlock() instanceof BlockAir)) {
                    overAir = false;
                    break;
                }
                blockPos = blockPos.down();
            }
            if (overAir) {
                receiveSignal(entityPlayer, CHEAT_SCAFFOLD);
                return;
            }
        }
        if (detectNoFall.getValue() && !entityPlayer.capabilities.isFlying) {
            double serverPosX = (double) entityPlayer.serverPosX / 32.0;
            double serverPosY = (double) entityPlayer.serverPosY / 32.0;
            double serverPosZ = (double) entityPlayer.serverPosZ / 32.0;
            double deltaX = Math.abs(playerData.serverPosX - serverPosX);
            double deltaY = playerData.serverPosY - serverPosY;
            double deltaZ = Math.abs(playerData.serverPosZ - serverPosZ);
            if (deltaY >= 5 && deltaX <= 10 && deltaZ <= 10 && deltaY <= 40) {
                if (!overVoid(serverPosX, serverPosY, serverPosZ) && entityPlayer.fallDistance > 3) {
                    receiveSignal(entityPlayer, CHEAT_NOFALL);
                }
            }
        }
    }

    private boolean overVoid(double x, double y, double z) {
        BlockPos pos = new BlockPos(x, y, z);
        for (int i = 0; i < 6; i++) {
            pos = pos.down();
            if (!(mc.theWorld.getBlockState(pos).getBlock() instanceof BlockAir)) {
                return false;
            }
        }
        return true;
    }

    private String stripColor(String input) {
        return input.replaceAll("\u00a7[0-9a-fk-or]", "");
    }

    private static boolean isKnownCheck(String c) {
        return c.equals(CHEAT_AUTOBLOCK) || c.equals(CHEAT_NOSLOW)
                || c.equals(CHEAT_KILLAURA) || c.equals(CHEAT_SCAFFOLD)
                || c.equals(CHEAT_NOFALL) || c.equals(CHEAT_LEGIT_SCAFFOLD);
    }

    private static int maxFlagsFor(String c) {
        if (c.equals(CHEAT_AUTOBLOCK)) return 5;
        if (c.equals(CHEAT_NOSLOW)) return 3;
        if (c.equals(CHEAT_KILLAURA)) return 4;
        if (c.equals(CHEAT_SCAFFOLD)) return 4;
        if (c.equals(CHEAT_NOFALL)) return 3;
        if (c.equals(CHEAT_LEGIT_SCAFFOLD)) return 3;
        return 2;
    }
}
