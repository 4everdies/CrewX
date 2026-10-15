package crewx.gui.menu;

/**
 * Mouse-driven "camera" for the space background (pure maths, unit-tested). The camera eases towards the
 * cursor with inertia and drifts back to the centre when the cursor leaves the window; every layer is moved
 * by its own depth so near layers travel further than far ones.
 */
public final class ParallaxCamera {
    /** Higher = follows the cursor faster. ~3 gives a soft, weighty glide. */
    static final float FOLLOW_RATE = 3.0F;
    /** Largest shift of a depth-1 layer, as a fraction of the screen size. */
    static final float SHIFT_X = 0.024F;
    static final float SHIFT_Y = 0.032F;
    /** Extra zoom when the cursor is at the very top. */
    static final float MAX_ZOOM = 0.05F;
    /** Each layer is drawn this much larger than the screen so its edges never show. */
    static final float MARGIN = 1.10F;

    private float x;
    private float y;

    public float x() {
        return this.x;
    }

    public float y() {
        return this.y;
    }

    /**
     * @param targetX cursor -1 (left) .. 1 (right)
     * @param targetY cursor -1 (top) .. 1 (bottom)
     */
    public void update(float targetX, float targetY, boolean inside, float dtSeconds) {
        float tx = inside ? clamp(targetX) : 0.0F;
        float ty = inside ? clamp(targetY) : 0.0F;
        float k = 1.0F - (float) Math.exp(-Math.max(0.0F, dtSeconds) * FOLLOW_RATE);
        this.x += (tx - this.x) * k;
        this.y += (ty - this.y) * k;
        if (Math.abs(tx - this.x) < 1.0e-4F) this.x = tx;
        if (Math.abs(ty - this.y) < 1.0e-4F) this.y = ty;
    }

    /** Looking up (cursor at the top) gently pushes the camera towards the planet. */
    public float zoom() {
        return 1.0F + MAX_ZOOM * Math.max(0.0F, -this.y);
    }

    /**
     * Screen rectangle {x, y, w, h} of a layer with the given aspect ratio: it covers the screen (with a margin),
     * is shifted by depth and zoomed around an anchor given as a fraction of the layer (e.g. the horizon).
     */
    public float[] layer(float screenW, float screenH, float aspect, float depth, float zoomAmount,
                         float anchorFx, float anchorFy, float[] out) {
        float w = Math.max(screenW, screenH * aspect) * MARGIN;
        float h = w / aspect;
        float rx = (screenW - w) / 2.0F - this.x * depth * screenW * SHIFT_X;
        float ry = (screenH - h) / 2.0F - this.y * depth * screenH * SHIFT_Y;
        float z = 1.0F + (zoom() - 1.0F) * zoomAmount;
        float ax = rx + w * anchorFx;
        float ay = ry + h * anchorFy;
        out[0] = ax + (rx - ax) * z;
        out[1] = ay + (ry - ay) * z;
        out[2] = w * z;
        out[3] = h * z;
        return out;
    }

    private static float clamp(float v) {
        return Math.max(-1.0F, Math.min(1.0F, v));
    }
}
