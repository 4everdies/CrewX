package myau.script;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.Render2DEvent;
import myau.events.Render3DEvent;
import myau.events.UpdateEvent;
import org.luaj.vm2.LuaValue;

public class ScriptEvents {
    private final ScriptManager manager;

    public ScriptEvents(ScriptManager manager) {
        this.manager = manager;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) {
            return;
        }
        for (ScriptModule module : this.manager.getActiveModules()) {
            module.getScript().call("onUpdate");
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        LuaValue partial = LuaValue.valueOf(event.getPartialTicks());
        for (ScriptModule module : this.manager.getActiveModules()) {
            module.getScript().call("onRender2D", partial);
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        LuaValue partial = LuaValue.valueOf(event.getPartialTicks());
        for (ScriptModule module : this.manager.getActiveModules()) {
            module.getScript().call("onRender3D", partial);
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getPacket() == null) {
            return;
        }
        String name = event.getPacket().getClass().getSimpleName();
        boolean outgoing = event.getType() == EventType.SEND;
        String callback = outgoing ? "onPacketSent" : "onPacketReceived";
        LuaValue packetName = LuaValue.valueOf(name);

        for (ScriptModule module : this.manager.getActiveModules()) {
            if (!module.getScript().callAllowing(callback, packetName)) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
