package myau.clickgui.value.settings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import myau.clickgui.value.Value;

public class LocationValue extends Value {
    private double x, y;
    
    public LocationValue(Object parent, String name, double x, double y) { super(parent, name); this.x = x; this.y = y; }
    public LocationValue(Object parent, String id, String name, double x, double y) { super(parent, id, name); this.x = x; this.y = y; }
    
    public void setPosX(double value) { this.x = Math.min(Math.round(Math.max(0, Math.min(1000, value))), 1000); }
    public void setPosY(double value) { this.y = Math.min(Math.round(Math.max(0, Math.min(600, value))), 600); }
    public double getPosX() { return new ScaledResolution(Minecraft.getMinecraft()).getScaledWidth_double() / 1000 * x; }
    public double getPosY() { return new ScaledResolution(Minecraft.getMinecraft()).getScaledHeight_double() / 600 * y; }
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
}
