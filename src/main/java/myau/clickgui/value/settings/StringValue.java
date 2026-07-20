package myau.clickgui.value.settings;

import myau.clickgui.value.Value;

public class StringValue extends Value {
    private String value;

    public StringValue(Object parent, String id, String name, String value) {
        super(parent, id, name);
        this.value = value == null ? "" : value;
    }

    public StringValue(Object parent, String name, String value) {
        super(parent, name);
        this.value = value == null ? "" : value;
    }

    public String getValue() {
        return this.value;
    }

    public void setValue(String value) {
        this.value = value == null ? "" : value;
    }
}
