package myau.clickgui.value.settings;

import java.awt.Color;
import java.util.function.Supplier;
import myau.clickgui.value.Value;

public class ColorValue extends Value {
    private int color1, color2, alpha = 100;
    private String mode = "Static";
    private String[] modes = new String[] { "Static", "Gradient", "Rainbow", "Astolfo" };
    private double offset = 1, speed = 1;
    
    public ColorValue(Object parent, String id, String name, int color1, int color2) {
        super(parent, id, name); this.color1 = color1; this.color2 = color2;
    }
    public ColorValue(Object parent, String name, int color1, int color2) {
        super(parent, name); this.color1 = color1; this.color2 = color2;
    }
    public int getColor1() { return color1; }
    public void setColor1(int color1) { this.color1 = color1; }
    public int getColor2() { return color2; }
    public void setColor2(int color2) { this.color2 = color2; }
    public int getAlpha() { return alpha; }
    public void setAlpha(int alpha) { this.alpha = alpha; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String[] getModes() { return modes; }
    public double getOffset() { return offset; }
    public void setOffset(double offset) { this.offset = offset; }
    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed; }
    public Color getColor(int milis) {
        float r = (float)(color1 >> 16 & 255) / 255f;
        float g = (float)(color1 >> 8 & 255) / 255f;
        float b = (float)(color1 & 255) / 255f;
        return new Color(r, g, b);
    }
}
