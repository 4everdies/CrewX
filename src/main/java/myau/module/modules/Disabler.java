package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C16PacketClientStatus;

public class Disabler extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"HYPIXEL_PORTAL", "WATCHDOG", "NCP", "VERUS", "VULCAN"});
    public final BooleanProperty cancelGroundSpoof = new BooleanProperty("ground-spoof", false);

    public Disabler() {
        super("Disabler", false);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.SEND || mc.thePlayer == null) return;
        Object p = event.getPacket();

        switch (mode.getValue()) {
            case 0:
                if (p instanceof C16PacketClientStatus) {
                    C16PacketClientStatus pkt = (C16PacketClientStatus) p;
                    if (pkt.getStatus() == C16PacketClientStatus.EnumState.PERFORM_RESPAWN) {
                        event.setCancelled(true);
                    }
                }
                break;
            case 1:
                if (p instanceof C0BPacketEntityAction) {
                    C0BPacketEntityAction pkt = (C0BPacketEntityAction) p;
                    if (pkt.getAction() == C0BPacketEntityAction.Action.STOP_SPRINTING) {
                        event.setCancelled(true);
                    }
                }
                break;
            case 2:
            case 3:
            case 4:
                if (cancelGroundSpoof.getValue() && p instanceof C03PacketPlayer) {
                    C03PacketPlayer pkt = (C03PacketPlayer) p;
                    if (!mc.thePlayer.onGround && pkt.isOnGround()) {
                        event.setCancelled(true);
                    }
                }
                break;
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{mode.getModeString()};
    }
}