package myau.clickgui.value.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import myau.clickgui.value.Value;

public class CategoryValue extends Value {
    private List<Value> settings = new ArrayList<>();
    
    public CategoryValue(Object parent, String name) { super(parent, name); }
    public CategoryValue(Object parent, String id, String name) { super(parent, id, name); }
    
    public void addSettings(Value... values) { settings.addAll(Arrays.asList(values)); }
    public List<Value> getSettings() { return settings; }
    public void setSettings(List<Value> settings) { this.settings = settings; }
}
