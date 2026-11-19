package crewx.clickgui.clone.mc;

import crewx.clickgui.clone.core.FontId;

import java.awt.Font;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

/** Fontes TTF do classpath /fonts/ — tamanhos ampliados para legibilidade. */
public final class Fonts {
    private static final Map<FontId, Font> MAP = new EnumMap<FontId, Font>(FontId.class);

    static {
        Font nunito = load("nunito-semibold.ttf");
        Font icons = load("catfont.ttf");
        MAP.put(FontId.MODULE, nunito.deriveFont(14.5f));
        MAP.put(FontId.HEADER, nunito.deriveFont(15f));
        MAP.put(FontId.LABEL, nunito.deriveFont(13.5f));
        MAP.put(FontId.SMALL, nunito.deriveFont(12f));
        MAP.put(FontId.ICON, icons.deriveFont(14f));
    }

    private Fonts() { }

    public static Font get(FontId id) {
        return MAP.get(id);
    }

    public static void set(FontId id, Font font) {
        MAP.put(id, font);
    }

    private static Font load(String name) {
        InputStream in = null;
        try {
            in = Fonts.class.getResourceAsStream("/fonts/" + name);
            if (in == null) {
                File f = new File("resources/fonts/" + name);
                if (f.isFile()) in = new FileInputStream(f);
            }
            if (in != null) return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (Exception e) {
            System.err.println("[clickgui] falha ao carregar fonte " + name + ": " + e);
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) { }
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, 14);
    }
}
