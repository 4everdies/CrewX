package crewx.module.modules.movement;
import crewx.module.modules.combat.*;
import crewx.module.modules.movement.*;
import crewx.module.modules.render.*;
import crewx.module.modules.player.*;
import crewx.module.modules.misc.*;
import crewx.CrewX;
import crewx.enums.BlinkModules;
import crewx.event.EventTarget;
import crewx.event.types.EventType;
import crewx.event.types.Priority;
import crewx.events.LoadWorldEvent;
import crewx.events.TickEvent;
import crewx.module.Module;
import crewx.property.properties.IntProperty;
import crewx.property.properties.ModeProperty;
public class Blink extends Module {
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"Default", "Pulse"});
    public final IntProperty ticks = new IntProperty("ticks", 20, 0, 1200);
    public Blink() {
        super("Blink", false);
    }
    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.POST) {
            if (!CrewX.blinkManager.getBlinkingModule().equals(BlinkModules.BLINK)) {
                this.setEnabled(false);
            } else {
                if (this.ticks.getValue() > 0 && CrewX.blinkManager.countMovement() > (long) this.ticks.getValue()) {
                    switch (this.mode.getValue()) {
                        case 0:
                            this.setEnabled(false);
                            break;
                        case 1:
                            CrewX.blinkManager.setBlinkState(false, BlinkModules.BLINK);
                            CrewX.blinkManager.setBlinkState(true, BlinkModules.BLINK);
                    }
                }
            }
        }
    }
    @EventTarget
    public void onWorldLoad(LoadWorldEvent event) {
        this.setEnabled(false);
    }
    @Override
    public void onEnabled() {
        CrewX.blinkManager.setBlinkState(false, CrewX.blinkManager.getBlinkingModule());
        CrewX.blinkManager.setBlinkState(true, BlinkModules.BLINK);
    }
    @Override
    public void onDisabled() {
        CrewX.blinkManager.setBlinkState(false, BlinkModules.BLINK);
    }
}
