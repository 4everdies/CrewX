package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.DataWatcher.WatchableObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.util.EnumChatFormatting;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HackerDetector extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanProperty detectAutoBlock = new BooleanProperty("detect-autoblock", true);
    public final BooleanProperty detectKillAura = new BooleanProperty("detect-killaura", true);
    public final BooleanProperty detectScaffold = new BooleanProperty("detect-scaffold", true);
    public final BooleanProperty detectNoSlow = new BooleanProperty("detect-noslow", true);
    public final BooleanProperty addToTargets = new BooleanProperty("add-to-targets", true);
    public final IntProperty flagWindow = new IntProperty("flag-window-seconds", 5, 1, 30);
    public final IntProperty alertCooldown = new IntProperty("alert-cooldown-seconds", 5, 1, 60);

    private static final String CHEAT_AUTOBLOCK = "AutoBlock";
    private static final String CHEAT_NOSLOW = "Noslow";
    private static final String CHEAT_KILLAURA = "KillAura";
    private static final String CHEAT_SCAFFOLD = "Scaffold";

    private final Map<String, int[]> flagMap = new HashMap<>();
    private final Map<String, Integer> alertCooldowns = new HashMap<>();

    public HackerDetector() {
        super("HackerDetector", false);
    }

    private int nowSecs() {
        return mc.theWorld == null ? 0 : (int) (mc.theWorld.getTotalWorldTime() / 20);
    }

    public void receiveSignal(String playerName, String cheatName) {
        if (!this.isEnabled()) return;
        if (playerName == null || playerName.isEmpty() || cheatName == null) return;
        if (mc.theWorld == null || mc.thePlayer == null) return;
        if (playerName.equalsIgnoreCase(mc.thePlayer.getName())) return;
        if (!isKnownCheck(cheatName)) return;

        int now = nowSecs();
        String key = playerName.toLowerCase(Locale.ROOT) + ":" + cheatName;
        int[] data = flagMap.getOrDefault(key, new int[]{0, now});
        if (now - data[1] > flagWindow.getValue()) data[0] = 0;
        data[0] += 1;
        data[1] = now;
        flagMap.put(key, data);

        int max = maxFlagsFor(cheatName);
        int lastAlert = alertCooldowns.getOrDefault(key, -alertCooldown.getValue());
        if (data[0] >= max && now - lastAlert >= alertCooldown.getValue()) {
            ChatUtil.sendFormatted(String.format("%s%s%s%s failed %s%s",
                    Myau.clientName,
                    EnumChatFormatting.RED, playerName,
                    EnumChatFormatting.GRAY,
                    EnumChatFormatting.RED, cheatName));
            // Fix #3: on-screen notification integrating with the notification system.
            // Title: "Hacker Detected", body: "<player> - <cheatName>".
            try {
                myau.module.modules.Notifications.pushRaw(
                        "Hacker Detected",
                        playerName + " - " + cheatName + " suspected");
            } catch (Throwable ignored) {}
            mc.thePlayer.playSound("random.orb", 0.3f, 1.0f);
            if (addToTargets.getValue() && Myau.targetManager != null) {
                Myau.targetManager.add(playerName);
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
                        receiveSignal(entity.getName(), CHEAT_AUTOBLOCK);
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
                    receiveSignal(entity.getName(), CHEAT_KILLAURA);
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
                    receiveSignal(entity.getName(), CHEAT_SCAFFOLD);
                }
            }
        }
    }

    private static boolean isKnownCheck(String c) {
        return c.equals(CHEAT_AUTOBLOCK) || c.equals(CHEAT_NOSLOW)
                || c.equals(CHEAT_KILLAURA) || c.equals(CHEAT_SCAFFOLD);
    }

    private static int maxFlagsFor(String c) {
        if (c.equals(CHEAT_AUTOBLOCK)) return 5;
        if (c.equals(CHEAT_NOSLOW)) return 3;
        if (c.equals(CHEAT_KILLAURA)) return 4;
        if (c.equals(CHEAT_SCAFFOLD)) return 4;
        return 2;
    }

    @Override
    public void onDisabled() {
        flagMap.clear();
        alertCooldowns.clear();
    }
}
