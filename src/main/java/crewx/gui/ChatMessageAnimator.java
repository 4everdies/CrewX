package crewx.gui;

import net.minecraft.client.gui.ChatLine;

import java.util.Map;
import java.util.WeakHashMap;

public final class ChatMessageAnimator {
    private static final Map<ChatLine, Long> ARRIVAL_TIMES = new WeakHashMap<>();
    private static final long DURATION_NANOS = 220_000_000L;
    private static final float RISE_PIXELS = 2.5F;

    private ChatMessageAnimator() {
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
