package crewx.clickgui.clone.core;

/** Simple ARGB color setting for the ClickGUI. */
public class ColorSetting extends Setting {
    private int argb;
    /** When true, the GUI treats this as the focused color editor. */
    public boolean editing;

    public ColorSetting(String name, int argb) {
        super(name);
        this.argb = argb;
    }

    public int get() {
        return argb;
    }

    public void set(int argb) {
        this.argb = argb;
    }

    @Override
    float height(Theme t) {
        return t.colorH;
    }
}
