package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.ModeProperty;
import myau.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.util.Vec3;

public class RemoteShop extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty shopType = new ModeProperty("shop-type", 0, new String[]{"Shop Fast", "Improvements Shop", "Ender Chest"});
    private boolean triggerNext = false;

    public RemoteShop() {
        super("RemoteShop", false);
    }

    @Override
    public void onEnabled() {
        triggerNext = true;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;
        if (!triggerNext) return;
        triggerNext = false;

        Entity npc = findNPC();
        if (npc != null) {
            Vec3 hitVec = new Vec3(0.0, npc.height / 2.0, 0.0);
            PacketUtil.sendPacket(new C02PacketUseEntity(npc, hitVec));
            PacketUtil.sendPacket(new C02PacketUseEntity(npc, C02PacketUseEntity.Action.INTERACT));
        }
        this.setEnabled(false);
    }

    private Entity findNPC() {
        String[] keywords;
        switch (shopType.getValue()) {
            case 1:
                keywords = new String[]{"melhorias", "improve", "upgrade"};
                break;
            case 2:
                keywords = new String[]{"ender", "chest", "baú", "bau"};
                break;
            default:
                keywords = new String[]{"loja", "compra", "rápida", "rapida"};
        }
        Entity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Entity e : mc.theWorld.loadedEntityList) {
            String name = e.getName().toLowerCase();
            String display = e.getDisplayName().getUnformattedText().toLowerCase();
            String customTag = e.hasCustomName() ? e.getCustomNameTag().toLowerCase() : "";
            for (String kw : keywords) {
                if (name.contains(kw) || display.contains(kw) || customTag.contains(kw)) {
                    double dist = mc.thePlayer.getDistanceToEntity(e);
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearest = e;
                    }
                    break;
                }
            }
        }
        if (nearest == null) {
            for (Entity e : mc.theWorld.loadedEntityList) {
                if (e.getName().toLowerCase().contains("npc:")) {
                    double dist = mc.thePlayer.getDistanceToEntity(e);
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearest = e;
                    }
                }
            }
        }
        return nearest;
    }
}