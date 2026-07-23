package myau.clickgui.value.settings;

import myau.clickgui.value.Value;

/** A clickable, stateless action row used by the modern ClickGUI. */
public class ActionValue extends Value {
    private final Runnable action;

    public ActionValue(Object parent, String id, String name, Runnable action) {
        super(parent, id, name);
        this.action = action;
    }

    public void press() {
        if (this.action != null) {
            this.action.run();
        }
    }
}
