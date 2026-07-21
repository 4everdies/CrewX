package myau.script;

import myau.Myau;
import myau.event.EventManager;
import myau.property.Property;
import myau.util.ChatUtil;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

public class ScriptManager {
    private final File directory;
    private final LinkedHashMap<String, ScriptModule> modules = new LinkedHashMap<>();
    private final List<ScriptModule> activeCache = new ArrayList<>();
    private ScriptEvents events;
    private boolean dirty = true;

    public ScriptManager() {
        this.directory = new File(Minecraft.getMinecraft().mcDataDir, "crewx" + File.separator + "scripts");
    }

    public File getDirectory() {
        return this.directory;
    }

    public LinkedHashMap<String, ScriptModule> getModules() {
        return this.modules;
    }

    public void init() {
        if (!this.directory.exists() && !this.directory.mkdirs()) {
            System.err.println("[CrewX] could not create " + this.directory);
            return;
        }
        this.writeExampleIfMissing();
        this.events = new ScriptEvents(this);
        EventManager.register(this.events);
        this.loadAll(false);
    }

    public List<ScriptModule> getActiveModules() {
        if (this.dirty) {
            this.activeCache.clear();
            for (ScriptModule module : this.modules.values()) {
                if (module.isEnabled() && module.getScript().isLoaded()) {
                    this.activeCache.add(module);
                }
            }
            this.dirty = false;
        }
        for (ScriptModule module : this.modules.values()) {
            boolean shouldBeActive = module.isEnabled() && module.getScript().isLoaded();
            if (shouldBeActive != this.activeCache.contains(module)) {
                this.dirty = true;
                return this.recomputeActive();
            }
        }
        return this.activeCache;
    }

    private List<ScriptModule> recomputeActive() {
        this.activeCache.clear();
        for (ScriptModule module : this.modules.values()) {
            if (module.isEnabled() && module.getScript().isLoaded()) {
                this.activeCache.add(module);
            }
        }
        this.dirty = false;
        return this.activeCache;
    }

    public void loadAll(boolean announce) {
        File[] files = this.directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".lua"));
        if (files == null) {
            return;
        }
        Arrays.sort(files);

        for (ScriptModule module : this.modules.values()) {
            if (module.isEnabled()) {
                module.setEnabled(false);
            }
            Myau.moduleManager.modules.remove(this.keyFor(module));
            Myau.propertyManager.properties.remove(module);
        }
        this.modules.clear();

        int loaded = 0;
        int failed = 0;
        for (File file : files) {
            LuaScript script = new LuaScript(file);
            ScriptModule module = new ScriptModule(script);

            LuaApi.install(script, module);

            if (script.load()) {
                loaded++;
            } else {
                failed++;
            }

            this.modules.put(script.getName().toLowerCase(), module);
            this.register(module);
        }

        this.dirty = true;
        Myau.initClickGui();
        if (announce) {
            ChatUtil.sendMessage("&7scripts: &a" + loaded + " OK"
                    + (failed > 0 ? "&7, &c" + failed + " with errors" : ""));
        }
    }

    private String keyFor(ScriptModule module) {
        return "script:" + module.getName().toLowerCase();
    }

    private void register(ScriptModule module) {
        Myau.moduleManager.modules.put(this.keyFor(module), module);
        ArrayList<Property<?>> properties = new ArrayList<>(module.getProperties());
        Myau.propertyManager.properties.put(module, properties);
    }

    public void reload() {
        this.loadAll(true);
    }

    public void reloadChanged() {
        boolean any = false;
        for (ScriptModule module : this.modules.values()) {
            if (module.getScript().hasChangedOnDisk()) {
                any = true;
                break;
            }
        }
        if (any) {
            this.loadAll(true);
        }
    }

    public void loadScript(String name) {
        String key = name.toLowerCase();
        if (!key.endsWith(".lua")) {
            key = key + ".lua";
            name = name + ".lua";
        }
        File file = new File(this.directory, key);
        if (!file.exists()) {
            File alt = new File(this.directory, name);
            if (!alt.exists()) {
                ChatUtil.sendMessage("&cScript &f" + name + " &cnot found in &f" + this.directory);
                return;
            }
            file = alt;
            key = name;
        }
        String moduleName = key.toLowerCase().endsWith(".lua")
                ? key.substring(0, key.length() - 4)
                : key;

        ScriptModule old = this.modules.remove(moduleName);
        if (old != null) {
            if (old.isEnabled()) old.setEnabled(false);
            old.getScript().call("onDisable");
            Myau.moduleManager.modules.remove(this.keyFor(old));
            Myau.propertyManager.properties.remove(old);
        }

        LuaScript script = new LuaScript(file);
        ScriptModule module = new ScriptModule(script);
        LuaApi.install(script, module);

        if (script.load()) {
            this.modules.put(moduleName, module);
            this.register(module);
            this.dirty = true;
            Myau.initClickGui();
            ChatUtil.sendMessage("&aScript &f" + moduleName + " &aloaded");
        } else {
            ChatUtil.sendMessage("&cFailed to load &f" + moduleName);
        }
    }

    private void writeExampleIfMissing() {
        File example = new File(this.directory, "exemplo.lua");
        if (example.exists()) {
            return;
        }
        try {
            Files.write(example.toPath(), EXAMPLE.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("[CrewX] could not write example script: " + e);
        }
    }

    private static final String EXAMPLE =
            "-- exemplo.lua — simple script API demo\n"
                    + "-- The module name comes from the filename.\n"
                    + "\n"
                    + "script.registerBoolean(\"show-hud\", true)\n"
                    + "script.registerFloat(\"range\", 5.0, 1.0, 16.0)\n"
                    + "script.registerMode(\"color\", 0, { \"white\", \"red\", \"green\" })\n"
                    + "\n"
                    + "function onEnable()\n"
                    + "    client.print(\"&aexample enabled\")\n"
                    + "end\n"
                    + "\n"
                    + "function onDisable()\n"
                    + "    client.print(\"&cexample disabled\")\n"
                    + "end\n"
                    + "\n"
                    + "function onUpdate()\n"
                    + "    if not client.inWorld() then return end\n"
                    + "    local target = world.getClosestPlayer(script.get(\"range\"))\n"
                    + "    if target ~= nil and target.health <= 6 then\n"
                    + "        client.print(\"&e\" .. target.name .. \" is low: \" .. target.health)\n"
                    + "    end\n"
                    + "end\n"
                    + "\n"
                    + "function onRender2D(partialTicks)\n"
                    + "    if not script.get(\"show-hud\") then return end\n"
                    + "    local mode = script.get(\"color\")\n"
                    + "    local col = render.color(255, 255, 255)\n"
                    + "    if mode == \"red\" then col = render.color(255, 60, 60) end\n"
                    + "    if mode == \"green\" then col = render.color(60, 255, 60) end\n"
                    + "    render.drawString(\"fps: \" .. client.getFPS(), 4, 4, col)\n"
                    + "end\n"
                    + "\n"
                    + "-- Returning false cancels the packet.\n"
                    + "function onPacketSent(name)\n"
                    + "    return true\n"
                    + "end\n";
}
