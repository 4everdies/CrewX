package myau.clickgui.value;

public class Setting<T> {
    protected T parent;
    public Setting(T parent) { this.parent = parent; }
    public T getParent() { return parent; }
    public void onEnable() {}
    public void onDisable() {}
}
