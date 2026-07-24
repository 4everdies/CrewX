package myau.util;

public class Translate {
    private float x, y;
    private long lastUpdate;

    public Translate(float x, float y) {
        this.x = x;
        this.y = y;
        this.lastUpdate = System.currentTimeMillis();
    }

    public void interpolate(float targetX, float targetY, float speed) {
        long now = System.currentTimeMillis();
        long delta = now - lastUpdate;
        lastUpdate = now;

        float factor = Math.min(1, delta * speed / 16.666f);
        x += (targetX - x) * factor;
        y += (targetY - y) * factor;
    }

    public float getX() { return x; }
    public float getY() { return y; }
    public void setX(float x) { this.x = x; }
    public void setY(float y) { this.y = y; }
}