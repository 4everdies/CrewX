package crewx.gui;

import net.minecraft.client.gui.ChatLine;

import java.util.Map;
import java.util.WeakHashMap;

public final class ChatMessageAnimator {
    private static final Map<ChatLine, Long> ARRIVAL_TIMES = new WeakHashMap<>();
    private static final long DURATION_NANOS = 260_000_000L;
    private static final float RISE_PIXELS = 4.0F;

    private ChatMessageAnimator() {
    }

    /** 0..1 eased progress of a new line's entrance (1 = settled). */
    public static float getProgress(ChatLine line, int currentCounter) {
        if (line == null) return 1.0F;
        int age = currentCounter - line.getUpdatedCounter();
        if (age < 0 || age > 10) return 1.0F;
        long now = System.nanoTime();
        Long started = ARRIVAL_TIMES.get(line);
        if (started == null) {
            started = now;
            ARRIVAL_TIMES.put(line, started);
        }
        float t = Math.max(0.0F, Math.min(1.0F, (float) (now - started) / (float) DURATION_NANOS));
        float inverse = 1.0F - t;
        return 1.0F - inverse * inverse * inverse;
    }

    /** New lines fade in while they rise into place; returns the colour with the faded alpha. */
    public static int fadeColor(int color, float progress) {
        if (progress >= 1.0F) return color;
        int alpha = color >>> 24;
        if (alpha == 0) alpha = 255;
        // FontRenderer treats alpha < 4 as opaque, so never go below it
        int faded = Math.max(4, Math.round(alpha * progress));
        return faded << 24 | color & 0xFFFFFF;
    }

    public static float getRiseOffset(ChatLine line, int currentCounter) {
        if (line == null) return 0.0F;
        int age = currentCounter - line.getUpdatedCounter();
        if (age < 0 || age > 10) return 0.0F;

        long now = System.nanoTime();
        Long started = ARRIVAL_TIMES.get(line);
        if (started == null) {
            started = now;
            ARRIVAL_TIMES.put(line, started);
        }

        float progress = (float) (now - started) / (float) DURATION_NANOS;
        if (progress >= 1.0F) return 0.0F;
        return RISE_PIXELS * (1.0F - Math.max(0.0F, progress));
    }
}
