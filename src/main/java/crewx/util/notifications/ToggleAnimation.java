package crewx.util.notifications;

/**
 * Timing of the Jello toggle notification (pure, unit-tested): the card slides in, the switch knob travels
 * to its new side (right = enabled, left = disabled), and at the end of its time the card slides out.
 */
public final class ToggleAnimation {
    static final long SLIDE_MS = 260L;
    static final long KNOB_DELAY_MS = 140L;
    static final long KNOB_MS = 280L;

    private ToggleAnimation() {
    }

    /** 0 = hidden off-screen, 1 = fully in. */
    public static float appear(long elapsedMs, long displayMs) {
        if (elapsedMs < 0L) return 0.0F;
        if (elapsedMs >= displayMs) return 1.0F - ease((elapsedMs - displayMs) / (float) SLIDE_MS);
        return ease(elapsedMs / (float) SLIDE_MS);
    }

    /** True once the slide-out finished: the notification can be removed. */
    public static boolean finished(long elapsedMs, long displayMs) {
        return elapsedMs >= displayMs + SLIDE_MS;
    }

    /** Knob position 0 (left / off) .. 1 (right / on). It starts on the old side and moves to the new one. */
    public static float knob(long elapsedMs, boolean enabled) {
        float t = ease((elapsedMs - KNOB_DELAY_MS) / (float) KNOB_MS);
        return enabled ? t : 1.0F - t;
    }

    static float ease(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return 1.0F - (float) Math.pow(1.0F - t, 3.0D);
    }
}
