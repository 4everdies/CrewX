package crewx.gui.menu;

/**
 * Positions of the main-menu elements for a given screen size (pure maths, unit-tested). Proportions follow
 * the reference: a large clock high up, the profile above a row of three round buttons near the bottom,
 * and two small round buttons (options, quit) in the top-right corner. Everything scales with the screen
 * height and shrinks the clock first when the screen is short.
 */
public final class MainMenuLayout {
    public final float clockTop;
    public final float clockCap;
    public final float avatarSize;
    public final float profileCenterY;
    public final float buttonRadius;
    public final float buttonCenterY;
    public final float buttonSpacing;
    public final float labelY;
    public final float smallRadius;
    public final float smallCenterY;
    public final float[] smallCenterX = new float[2];
    public final float screenW;
    public final float screenH;

    public MainMenuLayout(float width, float height, float fontHeight) {
        this(width, height, fontHeight, 0.0F);
    }

    /** @param minSpacing smallest centre-to-centre distance of the round buttons (labels must not touch) */
    public MainMenuLayout(float width, float height, float fontHeight, float minSpacing) {
        this.screenW = width;
        this.screenH = height;
        float bottomMargin = Math.max(8.0F, height * 0.06F);
        this.buttonRadius = clamp(height * 0.026F, 9.0F, 17.0F);
        this.labelY = height - bottomMargin - fontHeight;
        this.buttonCenterY = this.labelY - 5.0F - this.buttonRadius;
        this.buttonSpacing = Math.max(this.buttonRadius * 3.8F, minSpacing);
        this.avatarSize = clamp(height * 0.05F, 16.0F, 34.0F);
        this.profileCenterY = this.buttonCenterY - this.buttonRadius - Math.max(12.0F, height * 0.066F) - this.avatarSize / 2.0F;

        float top = height * 0.13F;
        float cap = clamp(height * 0.15F, 22.0F, 130.0F);
        float limit = this.profileCenterY - this.avatarSize / 2.0F - Math.max(10.0F, height * 0.05F);
        if (top + cap > limit) {
            top = Math.max(8.0F, Math.min(top, height * 0.08F));
            cap = Math.max(14.0F, limit - top);
        }
        this.clockTop = top;
        this.clockCap = cap;

        // options / quit: a bit smaller than the main buttons, but comfortable to hit
        this.smallRadius = clamp(this.buttonRadius * 0.88F, 9.0F, 14.5F);
        float margin = 10.0F + this.smallRadius * 0.15F;
        this.smallCenterY = margin + this.smallRadius;
        for (int i = 0; i < 2; i++) {
            this.smallCenterX[i] = width - margin - this.smallRadius - i * (this.smallRadius * 2.0F + 9.0F);
        }
    }

    /** Centre x of the i-th of {@code count} main buttons. */
    public float buttonCenterX(int index, int count) {
        return this.screenW / 2.0F + (index - (count - 1) / 2.0F) * this.buttonSpacing;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
