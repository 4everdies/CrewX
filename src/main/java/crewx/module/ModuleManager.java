package crewx.module;

import crewx.CrewX;
import crewx.event.EventTarget;
import crewx.events.KeyEvent;
import crewx.module.modules.render.GuiModule;
import crewx.module.modules.render.HUD;
import crewx.util.ChatUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class ModuleManager {
    public final LinkedHashMap<Object, Module> modules = new LinkedHashMap<>();

    public Module getModule(String string) {
        if (string == null) return null;
        // names may contain spaces ("Media Player") but commands split on spaces: also match without them
        String compact = string.replace(" ", "");
        Module found = this.modules.values().stream()
                .filter(module -> module.getName().equalsIgnoreCase(string)
                        || module.getName().replace(" ", "").equalsIgnoreCase(compact))
                .findFirst()
                .orElse(null);
        if (found == null && "spotify".equalsIgnoreCase(compact)) {
            found = this.modules.get(crewx.module.modules.render.MediaPlayer.class);   // old module name
        }
        return found;
    }

    public Module getModule(Class<?> clazz) {
        return this.modules.get(clazz);
    }

    public ModuleCategory getCategory(Module module) {
        return module == null ? ModuleCategory.MISC : module.getCategory();
    }

    public List<Module> getModules(ModuleCategory category) {
        List<Module> result = new ArrayList<>();
        if (category == null) {
            return result;
        }
        for (Module module : this.modules.values()) {
            if (this.getCategory(module) == category) {
                result.add(module);
            }
        }
        return result;
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        if (event.getKey() == 0) {
            return;
        }
        for (Module module : this.modules.values()) {
            if (module.getKey() == 0 || module.getKey() != event.getKey()) {
                continue;
            }
            boolean shouldNotify = module.toggle();
            HUD hud = (HUD) this.modules.get(HUD.class);
            if (hud != null && shouldNotify) {
                shouldNotify = hud.toggleAlerts.getValue();
            }
            if (module instanceof GuiModule) {
                shouldNotify = false;
            }
            if (shouldNotify) {
                String status = module.isEnabled() ? "&a&lON" : "&c&lOFF";
                String message = String.format("%s%s: %s&r", CrewX.clientName, module.getName(), status);
                ChatUtil.sendFormatted(message);
            }
        }
    }
}
