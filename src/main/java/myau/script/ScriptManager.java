package myau.script;

import myau.Myau;
import myau.event.EventManager;
import myau.property.Property;
import myau.util.ChatUtil;
import net.minecraft.client.Minecraft;

import java.io.File;
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
        this.directory = new File(Minecraft.getMinecraft().mcDataDir, "config" + File.separator + "CrewX" + File.separator + "script");
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

    /**
     * Reconcilia os scripts em disco com o que ja esta carregado. Scripts cujo
     * arquivo nao mudou continuam vivos e com o mesmo estado (ligado/desligado);
     * so os novos e os alterados sao recarregados, e os que sumiram do disco
     * sao removidos. Reload nunca desliga um script que voce deixou ligado.
     */
    public void loadAll(boolean announce) {
        File[] files = this.directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".lua"));
        if (files == null) {
            return;
        }
        Arrays.sort(files);

        java.util.HashSet<String> seen = new java.util.HashSet<>();
        int added = 0;
        int reloaded = 0;
        int failed = 0;
        boolean structureChanged = false;

        for (File file : files) {
            String key = fileKey(file);
            seen.add(key);
            ScriptModule existing = this.modules.get(key);

            if (existing != null && !existing.getScript().hasChangedOnDisk()) {
                // Inalterado: deixa exatamente como esta.
                continue;
            }

            LuaScript script = new LuaScript(file);
            ScriptModule module = new ScriptModule(script);
            LuaApi.install(script, module);

            if (!script.load()) {
                failed++;
                // Um erro de sintaxe durante hot-reload nao derruba a versao
                // que ja estava funcionando. Scripts novos com erro ainda
                // aparecem na ClickGUI com sufixo "error".
                if (existing == null) {
                    this.modules.put(key, module);
                    this.register(module);
                    structureChanged = true;
                }
                continue;
            }

            boolean wasEnabled = existing != null && existing.isEnabled();
            if (existing != null) {
                if (existing.isEnabled()) {
                    existing.setEnabled(false);
                }
                Myau.moduleManager.modules.remove(this.keyFor(existing));
                Myau.propertyManager.properties.remove(existing);
                reloaded++;
            } else {
                added++;
            }

            this.modules.put(key, module);
            this.register(module);
            structureChanged = true;

            // Restaura o estado ligado depois da troca atomica.
            if (wasEnabled) {
                module.setEnabled(true);
            }
        }

        // Remove scripts cujo arquivo sumiu da pasta.
        java.util.Iterator<java.util.Map.Entry<String, ScriptModule>> it = this.modules.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<String, ScriptModule> entry = it.next();
            if (!seen.contains(entry.getKey())) {
                ScriptModule gone = entry.getValue();
                if (gone.isEnabled()) {
                    gone.setEnabled(false);
                }
                Myau.moduleManager.modules.remove(this.keyFor(gone));
                Myau.propertyManager.properties.remove(gone);
                it.remove();
                structureChanged = true;
            }
        }

        if (structureChanged) {
            this.dirty = true;
            Myau.initClickGui();
        }

        // Reload em si nao fala nada. Erros ja se anunciam sozinhos em
        // LuaScript.load(). So avisamos quando algo NOVO aparece, para dar
        // retorno ao botao Reload sem floodar.
        if (announce && (added > 0 || reloaded > 0 || failed > 0)) {
            StringBuilder message = new StringBuilder("&7scripts:");
            if (added > 0) {
                message.append(" &a+").append(added).append(" novo").append(added > 1 ? "s" : "");
            }
            if (reloaded > 0) {
                message.append(" &b").append(reloaded).append(" recarregado").append(reloaded > 1 ? "s" : "");
            }
            if (failed > 0) {
                message.append(" &c").append(failed).append(" com erro");
            }
            ChatUtil.sendFormatted(message.toString());
        }
    }

    /**
     * Chave estavel de um arquivo, independente de estar carregado ou nao.
     */
    private String fileKey(File file) {
        String name = file.getName();
        if (name.toLowerCase().endsWith(".lua")) {
            name = name.substring(0, name.length() - 4);
        }
        return name.toLowerCase();
    }

    /**
     * Chave global do modulo no ModuleManager. Prefixada para nao colidir com
     * os modulos nativos, que sao chaveados por Class.
     */
    private String keyFor(ScriptModule module) {
        return "script:" + module.getName().toLowerCase();
    }

    /**
     * Registra o modulo no ModuleManager e suas propriedades no PropertyManager.
     */
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
                ChatUtil.sendFormatted("&cScript &f" + name + " &cnot found in &f" + this.directory);
                return;
            }
            file = alt;
            key = name;
        }
        String moduleName = this.fileKey(file);

        ScriptModule old = this.modules.get(moduleName);

        LuaScript script = new LuaScript(file);
        ScriptModule module = new ScriptModule(script);
        LuaApi.install(script, module);

        if (script.load()) {
            boolean wasEnabled = old != null && old.isEnabled();
            if (old != null) {
                if (old.isEnabled()) old.setEnabled(false);
                Myau.moduleManager.modules.remove(this.keyFor(old));
                Myau.propertyManager.properties.remove(old);
            }
            this.modules.put(moduleName, module);
            this.register(module);
            if (wasEnabled) module.setEnabled(true);
            this.dirty = true;
            Myau.initClickGui();
            ChatUtil.sendFormatted("&aScript &f" + moduleName + " &aloaded");
        } else {
            ChatUtil.sendFormatted("&cFailed to load &f" + moduleName);
        }
    }

}
