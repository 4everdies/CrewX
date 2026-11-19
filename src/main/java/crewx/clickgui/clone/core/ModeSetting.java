package crewx.clickgui.clone.core;

/** Mode setting with expandable dropdown (left click opens, pick an option). */
public class ModeSetting extends Setting {
    public final String[] modes;
    private int index;
    /** Whether the option list is open. */
    public boolean expanded;
    /** Animation 0..1 for the dropdown. */
    float anim;

    public ModeSetting(String name, int index, String... modes) {
        super(name);
        this.modes = modes;
        this.index = Math.max(0, Math.min(modes.length - 1, index));
    }

    public String get() {
        int i = getIndex();
        if (modes.length == 0) return "";
        if (i < 0 || i >= modes.length) i = 0;
        return modes[i];
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int i) {
        if (modes.length == 0) return;
        int n = modes.length;
        index = ((i % n) + n) % n;
    }

    public void cycle(int dir) {
        if (modes.length == 0) return;
        int n = modes.length;
        index = ((index + dir) % n + n) % n;
    }

    public void setExpanded(boolean v, boolean instant) {
        expanded = v;
        if (instant) anim = expanded ? 1f : 0f;
    }

    @Override
    float height(Theme t) {
        float base = t.modeH;
        if (anim > 0.001f) {
            base += (modes.length * t.modeOptionH + 4f) * anim;
        }
        return base;
    }
}
