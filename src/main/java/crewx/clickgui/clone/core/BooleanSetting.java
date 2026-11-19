package crewx.clickgui.clone.core;

public class BooleanSetting extends Setting {
    private boolean value;
    /** Posição animada do interruptor 0..1 (uso interno). */
    float anim = -1f;

    public BooleanSetting(String name, boolean value) {
        super(name);
        this.value = value;
    }

    public boolean get() {
        return value;
    }

    public void set(boolean v) {
        this.value = v;
    }

    public void toggle() {
        set(!value);
    }

    @Override
    float height(Theme t) {
        return t.boolH;
    }
}
