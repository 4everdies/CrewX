package crewx.config;

import com.google.gson.*;
import crewx.CrewX;
import crewx.mixin.IAccessorMinecraft;
import crewx.module.Module;
import crewx.module.modules.render.GuiModule;
import crewx.util.ChatUtil;
import crewx.property.Property;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;

public class Config {
    public static Minecraft mc = Minecraft.getMinecraft();
    public static Gson gson = new GsonBuilder().setPrettyPrinting().create();
    public String name;
    public File file;

    public static String lastConfig = "default";
    private static boolean loading;
    private static boolean autoSaveReady;
    private static long dirtyAt;

    /** Keep the active on-disk config unchanged while modules are stopped for unload. */
    public static void suspendAutoSave() {
        autoSaveReady = false;
        dirtyAt = 0L;
    }

    public static File directory() {
        return new File(mc.mcDataDir, "config/CrewX");
    }

    private static boolean validName(String name) {
        return name != null && name.matches("[A-Za-z0-9 _-]{1,80}");
    }

    public static String activeName() {
        File marker = new File(directory(), ".active-config");
        try {
            String name = new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8).trim();
            if (validName(name) && new File(directory(), name + ".json").isFile()) return name;
        } catch (IOException ignored) {
            // Existing installations have no marker; keep their default config.
        }
        return "default";
    }

    private void rememberActive() {
        lastConfig = name;
        try {
            Files.write(new File(directory(), ".active-config").toPath(), name.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            ((IAccessorMinecraft) mc).getLogger().warn("Couldn't remember active CrewX config: " + e.getMessage());
        }
    }

    public static void markDirty() {
        if (autoSaveReady && !loading) dirtyAt = System.currentTimeMillis();
    }

    /** Flush on the client thread, after slider edits have settled. */
    public static void flushPending() {
        if (!autoSaveReady || loading || dirtyAt == 0L
                || System.currentTimeMillis() - dirtyAt < 300L) return;
        dirtyAt = 0L;
        new Config(lastConfig, false).save(false);
    }

    public Config(String name, boolean newConfig) {
        this.name = "!".equals(name) ? "default" : name;
        if (!validName(this.name)) throw new IllegalArgumentException("Invalid config name");
        // The injected process may start from the launcher's directory, not --gameDir.
        // Resolve the same CrewX config folder used by the normal Minecraft client.
        this.file = new File(directory(), String.format("%s.json", this.name));
        try {
            file.getParentFile().mkdirs();
            if (newConfig) {
                ((IAccessorMinecraft) mc).getLogger().info(String.format("Created: %s", this.file.getName()));
            }
        } catch (Exception e) {
            ((IAccessorMinecraft) mc).getLogger().error(e.getMessage());
        }
    }

    public void load() {
        this.load(false);
    }

    public boolean load(boolean resetTogglesToDefaults) {
        loading = true;
        try {

            if (!file.exists()) {
                ChatUtil.sendFormatted(String.format("%sConfig file not found (&c&o%s&r). Creating default config...&r", CrewX.clientName, file.getName()));
                save();
                return false;
            }

            JsonElement parsed = new JsonParser().parse(new BufferedReader(new FileReader(file)));
            if (parsed == null || !parsed.isJsonObject()) {
                ChatUtil.sendFormatted(String.format("%sInvalid config format (&c&o%s&r)&r", CrewX.clientName, file.getName()));
                return false;
            }

            JsonObject jsonObject = parsed.getAsJsonObject();
            for (Module module : CrewX.moduleManager.modules.values()) {
                JsonElement moduleObj = jsonObject.get(module.getName());
                boolean legacy = false;
                if (moduleObj == null && module instanceof crewx.module.modules.render.MediaPlayer) {
                    moduleObj = jsonObject.get("Spotify");
                    legacy = moduleObj != null;
                }
                if (moduleObj != null && moduleObj.isJsonObject()) {
                    JsonObject object = moduleObj.getAsJsonObject();

                    ArrayList<Property<?>> list = CrewX.propertyManager.properties.get(module);
                    if (list != null) {
                        for (Property<?> property : list) {
                            if (object.has(property.getName())) {
                                try {
                                    property.read(object);
                                } catch (Exception e) {
                                    ((IAccessorMinecraft) mc).getLogger().warn(String.format("Failed to load property %s for module %s", property.getName(), module.getName()));
                                }
                            }
                        }
                    }

                    if (!(module instanceof GuiModule) && object.has("toggled") && !resetTogglesToDefaults && !legacy) {
                        JsonElement toggled = object.get("toggled");
                        if (toggled != null && toggled.isJsonPrimitive()
                                && toggled.getAsJsonPrimitive().isBoolean()) {
                            try {
                                boolean want = toggled.getAsBoolean();
                                if ("Insults".equals(module.getName())) {
                                    File marker = new File(file.getParentFile(), ".crewx_reset_insults_v4");
                                    if (!marker.exists()) {
                                        want = false;
                                        try { marker.createNewFile(); } catch (Exception ignored) {}
                                    }
                                }
                                module.setEnabled(want);
                            } catch (Exception e) {
                                ((IAccessorMinecraft) mc).getLogger().warn(String.format("Invalid toggle state for module %s", module.getName()));
                            }
                        }
                    }

                    if (object.has("key")) {
                        JsonElement key = object.get("key");
                        if (key != null && key.isJsonPrimitive() && key.getAsJsonPrimitive().isNumber()) {
                            try {
                                module.setKey(key.getAsInt());
                            } catch (Exception e) {
                                ((IAccessorMinecraft) mc).getLogger().warn(String.format("Invalid keybind for module %s", module.getName()));
                            }
                        }
                    }

                    if (object.has("hidden") && !legacy) {
                        JsonElement hidden = object.get("hidden");
                        if (hidden != null && hidden.isJsonPrimitive()
                                && hidden.getAsJsonPrimitive().isBoolean()) {
                            try {
                                module.setHidden(hidden.getAsBoolean());
                            } catch (Exception e) {
                                ((IAccessorMinecraft) mc).getLogger().warn(String.format("Invalid hidden state for module %s", module.getName()));
                            }
                        }
                    }
                }
            }
            ChatUtil.sendFormatted(String.format("%sConfig has been loaded (&a&o%s&r)&r", CrewX.clientName, file.getName()));
            rememberActive();
            return true;
        } catch (FileNotFoundException e) {
            ChatUtil.sendFormatted(String.format("%sConfig file not found (&c&o%s&r)&r", CrewX.clientName, file.getName()));
            return false;
        } catch (JsonSyntaxException e) {
            ChatUtil.sendFormatted(String.format("%sConfig has invalid JSON syntax (&c&o%s&r)&r", CrewX.clientName, file.getName()));
            ((IAccessorMinecraft) mc).getLogger().error("JSON Syntax Error: " + e.getMessage());
            return false;
        } catch (Exception e) {
            ((IAccessorMinecraft) mc).getLogger().error("Error loading config: " + e.getMessage());
            ChatUtil.sendFormatted(String.format("%sConfig couldn't be loaded (&c&o%s&r)&r", CrewX.clientName, file.getName()));
            return false;
        } finally {
            loading = false;
            autoSaveReady = true;
            dirtyAt = 0L;
        }
    }

    public void save() {
        save(true);
    }

    public void save(boolean announce) {
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            JsonObject object = new JsonObject();
            for (Module module : CrewX.moduleManager.modules.values()) {
                JsonObject moduleObject = new JsonObject();
                moduleObject.addProperty("toggled", module.isEnabled());
                moduleObject.addProperty("key", module.getKey());
                moduleObject.addProperty("hidden", module.isHidden());

                ArrayList<Property<?>> list = CrewX.propertyManager.properties.get(module);
                if (list != null) {
                    for (Property<?> property : list) {
                        try {
                            property.write(moduleObject);
                        } catch (Exception e) {
                            ((IAccessorMinecraft) mc).getLogger().warn(String.format("Failed to save property %s for module %s", property.getName(), module.getName()));
                        }
                    }
                }
                object.add(module.getName(), moduleObject);
            }

            PrintWriter printWriter = new PrintWriter(new FileWriter(file));
            printWriter.println(gson.toJson(object));
            printWriter.close();
            rememberActive();
            if (announce) ChatUtil.sendFormatted(String.format("%sConfig has been saved (&a&o%s&r)&r", CrewX.clientName, file.getName()));
        } catch (IOException e) {
            ((IAccessorMinecraft) mc).getLogger().error("Error saving config: " + e.getMessage());
            ChatUtil.sendFormatted(String.format("%sConfig couldn't be saved (&c&o%s&r)&r", CrewX.clientName, file.getName()));
        }
    }
}
