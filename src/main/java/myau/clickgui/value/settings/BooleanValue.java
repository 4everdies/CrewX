package myau.clickgui.value.settings;

import java.util.function.Supplier;
import myau.clickgui.value.Setting;
import myau.clickgui.value.Value;

public class BooleanValue extends Value {
    private boolean enabled;
    
    public BooleanValue(Object parent, String id, String name, boolean enabled, Supplier<Boolean> visible, Setting setting) {
        super(parent, id, name, visible); this.enabled = enabled;
    }
    public BooleanValue(Object parent, String name, boolean enabled, Setting setting) {
        super(parent, name); this.enabled = enabled;
    }
    public BooleanValue(Object parent, String name, boolean enabled) {
        super(parent, name); this.enabled = enabled;
    }
    public BooleanValue(Object parent, String id, String name, boolean enabled) {
        super(parent, id, name); this.enabled = enabled;
    }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
