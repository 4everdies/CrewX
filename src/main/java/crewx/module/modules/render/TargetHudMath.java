package crewx.module.modules.render;

/** Pure logic behind the TargetHUD (no Minecraft types) so it can be unit-tested. */
final class TargetHudMath {
    /** Health difference (in HP) that still counts as an even fight. */
    static final float DRAW_MARGIN = 1.0F;

    private TargetHudMath() {
    }

    /** 'W' when we have clearly more health than the target, 'L' when clearly less, 'D' otherwise. */
    static char verdict(float ownHealth, float targetHealth) {
        float diff = ownHealth - targetHealth;
        if (diff > DRAW_MARGIN) return 'W';
        if (diff < -DRAW_MARGIN) return 'L';
        return 'D';
    }

    /**
     * Moves the shown health towards the real one: losses slide down smoothly (the "draining" bar),
     * frame-rate independent; gains (regen/heal) catch up faster.
     */
    static float animateHealth(float shown, float real, float dtSeconds) {
        if (Float.isNaN(shown)) return real;
        float speed = real < shown ? 6.0F : 10.0F;
        float next = shown + (real - shown) * (1.0F - (float) Math.exp(-dtSeconds * speed));
        return Math.abs(next - real) < 0.01F ? real : next;
    }

    /** 0..1 width of the bar. */
    static float fraction(float health, float maxHealth) {
        if (maxHealth <= 0.0F) return 0.0F;
        return Math.max(0.0F, Math.min(1.0F, health / maxHealth));
    }
}
