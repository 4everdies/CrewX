package myau.clickgui.util;

public class PositionUtils {
    private double x, y, width, height;
    private double scale;
    
    public PositionUtils(double x, double y, double width, double height, double scale) {
        this.x = x; this.y = y; this.width = width; this.height = height; this.scale = scale;
    }
    
    public boolean isInside(int mouseX, int mouseY) {
        return x <= mouseX && x + getWidth() > mouseX && y <= mouseY && y + getHeight() > mouseY;
    }
    public double getX2() { return this.getX() + this.getWidth(); }
    public double getY2() { return this.getY() + this.getHeight(); }
    public double getX() { return x + (x - x * scale); }
    public void setX(double x) { this.x = x; }
    public double getY() { return y + (y - y * scale); }
    public void setY(double y) { this.y = y; }
    public double getRealWidth() { return width * scale; }
    public double getRealHeight() { return height * scale; }
    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }
    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }
    public double getScale() { return scale; }
    public void setScale(double scale) { this.scale = scale; }
}
