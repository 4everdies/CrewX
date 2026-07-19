package myau.clickgui.value.settings;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import myau.clickgui.value.Value;

public class ModeValue extends Value {
    private List<Value> modes;
    private Value active;
    
    public ModeValue(Object parent, String id, String name, Value[] modes) {
        super(parent, id, name); this.modes = Arrays.asList(modes); setMode(this.modes.get(0));
    }
    public ModeValue(Object parent, String name, Value[] modes) {
        super(parent, name); this.modes = Arrays.asList(modes); setMode(this.modes.get(0));
    }
    public List<Value> getModes() { return modes; }
    public Value getMode(String name) {
        return modes.stream().filter(m -> m.getName().toLowerCase().equals(name.toLowerCase())).findFirst().orElse(null);
    }
    public Value getActiveMode() { return active; }
    public String getMode() { return active.getName(); }
    public boolean isMode(String st) { return active.getName().toLowerCase().equals(st.toLowerCase()); }
    public void setMode(Value mode) { active = mode; }
}
