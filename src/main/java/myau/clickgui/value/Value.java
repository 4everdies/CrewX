package myau.clickgui.value;

import java.util.function.Supplier;

public class Value<T> {
    private String name, id;
    public Object parent;
    private Supplier<Boolean> isVisible = () -> true;
    
    public Value(Object parent, String id, String name, Supplier<Boolean> visible) {
        this.id = id; this.name = name; this.isVisible = visible; this.parent = parent;
    }
    public Value(Object parent, String name) { this.id = name; this.name = name; this.parent = parent; }
    public Value(Object parent, String id, String name) { this.id = id; this.name = name; this.parent = parent; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Object getParent() { return parent; }
    public void setParent(Object parent) { this.parent = parent; }
    public Supplier<Boolean> getVisibility() { return isVisible; }
    public void setIsVisible(Supplier<Boolean> isVisible) { this.isVisible = isVisible; }
}
