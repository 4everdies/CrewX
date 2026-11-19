package crewx.clickgui.clone.core;

/** Clickable action row (button) inside a module. */
public class ActionSetting extends Setting {
    private Runnable action;
    private int runs;

    public ActionSetting(String name, Runnable action) {
        super(name);
        this.action = action == null ? new Runnable() { public void run() { } } : action;
    }

    public void run() {
        runs++;
        if (action != null) action.run();
    }

    public int getRuns() {
        return runs;
    }

    @Override
    float height(Theme t) {
        return t.boolH;
    }
}
