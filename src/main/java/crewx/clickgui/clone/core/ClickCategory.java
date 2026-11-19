package crewx.clickgui.clone.core;

import java.util.ArrayList;
import java.util.List;

public final class ClickCategory {
    public final String name;
    /** Caractere do glifo na fonte de ícones (catfont.ttf). */
    public final String glyph;
    public final List<ClickModule> modules = new ArrayList<ClickModule>();

    public ClickCategory(String name, String glyph, ClickModule... mods) {
        this.name = name;
        this.glyph = glyph;
        for (ClickModule m : mods) modules.add(m);
    }
}
