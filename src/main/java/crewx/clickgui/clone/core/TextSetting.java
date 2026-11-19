package crewx.clickgui.clone.core;

/** Single-line text setting editable from the ClickGUI. */
public class TextSetting extends Setting {
    private String value;
    public boolean editing;

    public TextSetting(String name, String value) {
        super(name);
        this.value = value == null ? "" : value;
    }

    public String get() {
        return value;
    }

    public void set(String v) {
        this.value = v == null ? "" : v;
    }

    @Override
    float height(Theme t) {
        return t.textH;
    }
}
