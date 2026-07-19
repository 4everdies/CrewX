package myau.clickgui.util;

public class DragUtils {
    private boolean dragging;
    private double DRAGX, DRAGY;
    private int dragButton;
    private PositionUtils position;
    
    public DragUtils(int dragButton, PositionUtils position) {
        this.dragButton = dragButton;
        this.position = position;
    }
    
    public void update(int mouseX, int mouseY) {
        if(!dragging) return;
        position.setX(mouseX - DRAGX);
        position.setY(mouseY - DRAGY);
    }
    
    public void onClick(int mouseX, int mouseY, int button) {
        if(button == dragButton && position.isInside(mouseX, mouseY)) {
            dragging = true;
            DRAGX = mouseX - position.getX();
            DRAGY = mouseY - position.getY();
        }
    }
    public void onRelease() { dragging = false; }
    public boolean isDragging() { return dragging; }
    public void setDragging(boolean dragging) { this.dragging = dragging; }
    public double getDRAGX() { return DRAGX; }
    public void setDRAGX(double dRAGX) { DRAGX = dRAGX; }
    public double getDRAGY() { return DRAGY; }
    public void setDRAGY(double dRAGY) { DRAGY = dRAGY; }
    public int getDragButton() { return dragButton; }
    public void setDragButton(int dragButton) { this.dragButton = dragButton; }
    public PositionUtils getPosition() { return position; }
    public void setPosition(PositionUtils position) { this.position = position; }
}
