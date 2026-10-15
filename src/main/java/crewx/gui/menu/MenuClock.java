package crewx.gui.menu;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;

/**
 * The big clock of the main menu. Digits come from a pre-rendered atlas (Inter Display Bold), so they stay
 * sharp at any size and drawing is one quad per character; the text is rebuilt only when the minute changes.
 */
public final class MenuClock {
    private static final String ATLAS = "clock.png";
    private static final String CHARS = "0123456789:";
    private static final float TRACKING = -0.02F;

    private static final int[] CELL_X = new int[CHARS.length()];
    private static final int[] CELL_W = new int[CHARS.length()];
    private static final float[] ADVANCE = new float[CHARS.length()];
    private static int cellHeight;
    private static int capTop;
    private static int capBottom;
    private static int pad;
    private static boolean metricsLoaded;
    private static boolean metricsFailed;

    private static long cachedMinute = Long.MIN_VALUE;
    private static String cachedText = "";

    private MenuClock() {
    }

    /** "HH:mm" in the system's local time, recomputed once per minute. */
    public static String currentText() {
        long minute = System.currentTimeMillis() / 60000L;
        if (minute != cachedMinute) {
            cachedMinute = minute;
            Calendar now = Calendar.getInstance();
            cachedText = format(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
        }
        return cachedText;
    }

    static String format(int hour, int minute) {
        return (hour < 10 ? "0" : "") + hour + ":" + (minute < 10 ? "0" : "") + minute;
    }

    /** Width of {@code text} when the digits are {@code capHeight} tall; 0 while loading. */
    public static float width(String text, float capHeight) {
        if (!loadMetrics()) return 0.0F;
        float scale = capHeight / (capBottom - capTop);
        float w = 0.0F;
        for (int i = 0; i < text.length(); i++) {
            int index = CHARS.indexOf(text.charAt(i));
            if (index < 0) continue;
            w += (ADVANCE[index] + TRACKING * cellHeight) * scale;
        }
        return Math.max(0.0F, w - TRACKING * cellHeight * scale);
    }

    /** Draws {@code text} centred on {@code centerX}; {@code top} is the top of the digits. */
    public static boolean draw(String text, float centerX, float top, float capHeight, int argb) {
        MenuTextures.Tex atlas = MenuTextures.get(ATLAS);
        if (atlas == null || !loadMetrics()) return false;
        float scale = capHeight / (capBottom - capTop);
        float cursor = centerX - width(text, capHeight) / 2.0F;
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.disableAlpha();
        for (int pass = 0; pass < 2; pass++) {
            // soft shadow first, then the digits
            float dy = pass == 0 ? capHeight * 0.025F : 0.0F;
            int color = pass == 0 ? ((argb >>> 24) * 40 / 255) << 24 : argb;
            MenuTextures.color(color);
            float x = cursor;
            for (int i = 0; i < text.length(); i++) {
                int index = CHARS.indexOf(text.charAt(i));
                if (index < 0) continue;
                float u0 = CELL_X[index] / (float) atlas.width;
                float u1 = (CELL_X[index] + CELL_W[index]) / (float) atlas.width;
                MenuTextures.draw(atlas, x - pad * scale, top - capTop * scale + dy, CELL_W[index] * scale,
                        cellHeight * scale, u0, 0.0, u1, 1.0);
                x += (ADVANCE[index] + TRACKING * cellHeight) * scale;
            }
        }
        GlStateManager.enableAlpha();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        return true;
    }

    private static boolean loadMetrics() {
        if (metricsLoaded) return true;
        if (metricsFailed) return false;
        try (InputStream in = MenuClock.class.getResourceAsStream("/assets/crewx/menu/clock.txt")) {
            if (in == null) throw new IllegalStateException("clock.txt missing");
            parse(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
            metricsLoaded = true;
        } catch (Exception failure) {
            metricsFailed = true;
        }
        return metricsLoaded;
    }

    static void parse(BufferedReader reader) throws Exception {
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\\s+");
            switch (f[0]) {
                case "height": cellHeight = Integer.parseInt(f[1]); break;
                case "captop": capTop = Integer.parseInt(f[1]); break;
                case "capbottom": capBottom = Integer.parseInt(f[1]); break;
                case "pad": pad = Integer.parseInt(f[1]); break;
                case "ascent": break;
                default:
                    int index = CHARS.indexOf(f[0].charAt(0));
                    if (f[0].length() == 1 && index >= 0) {
                        CELL_X[index] = Integer.parseInt(f[1]);
                        CELL_W[index] = Integer.parseInt(f[2]);
                        ADVANCE[index] = Float.parseFloat(f[3]);
                    }
            }
        }
        if (cellHeight <= 0 || capBottom <= capTop) throw new IllegalStateException("bad clock metrics");
    }
}
