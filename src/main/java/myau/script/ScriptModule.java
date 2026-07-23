package myau.script;

import myau.module.Module;
import myau.property.Property;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class ScriptModule extends Module {
    private final LuaScript script;
    private final ArrayList<Property<?>> properties = new ArrayList<>();
    private final LinkedHashMap<String, Property<?>> propertiesByName = new LinkedHashMap<>();

    public ScriptModule(LuaScript script) {
        super(script.getName(), false);
        this.script = script;
    }

    public LuaScript getScript() {
        return this.script;
    }

    public ArrayList<Property<?>> getProperties() {
        return this.properties;
    }

    public Property<?> getProperty(String name) {
        return this.propertiesByName.get(name.toLowerCase());
    }

    public void addProperty(Property<?> property) {
        String key = property.getName().toLowerCase();
        if (this.propertiesByName.containsKey(key)) {
            return;
        }
        property.setOwner(this);
        this.properties.add(property);
        this.propertiesByName.put(key, property);
    }

    public void clearProperties() {
        this.properties.clear();
        this.propertiesByName.clear();
    }

    @Override
    public String[] getSuffix() {
        if (!this.script.isLoaded()) {
            return new String[]{"error"};
        }
        return new String[0];
    }

    @Override
    public void onEnabled() {
        if (!this.script.isLoaded()) {
            return;
        }
        this.script.resetErrorCount();
        this.script.call("onEnable");
    }

    @Override
    public void onDisabled() {
        if (!this.script.isLoaded()) {
            return;
        }
        ScriptGuiScreen.close(this.script);
        ScriptSkinOverrides.clearOwnedBy(this.script);
        this.script.call("onDisable");
    }
}
