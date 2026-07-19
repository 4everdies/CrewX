package myau.clickgui.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.lwjgl.input.Mouse;

public class ScrollBar {
    public double scroll;
    public double max, visibleSize;
    private double temp;
    public PositionUtils track = new PositionUtils(0,0,6,100,1), thumb = new PositionUtils(0,0,6,0,1);
    private boolean dragging;
    private ScrollType type = ScrollType.VERTICAL;
    
    public ScrollBar(double visibleSize, double max, double width, double height) {
        this.max = max; track.setWidth(width); track.setHeight(height); this.visibleSize = visibleSize;
    }
    public ScrollBar(double visibleSize, double max, double width, double height, ScrollType type) {
        this.max = max; this.type = type; track.setWidth(width); track.setHeight(height); this.visibleSize = visibleSize;
    }
    
    public void update(int mouse) {
        if(max <= 0) {
            thumb.setHeight(0);
            thumb.setWidth(0);
            return;
        }
        if(type == ScrollType.HORIZONTAL) {
            thumb.setY(this.track.getY());
            thumb.setWidth(this.visibleSize / this.max * track.getWidth());
            thumb.setHeight(track.getHeight());
            thumb.setX(this.track.getX()+(scroll / max * track.getWidth()));
            if(this.visibleSize / this.max >= 1) thumb.setWidth(0);
        } else {
            thumb.setX(this.track.getX());
            thumb.setY(this.track.getY()+(scroll / max * track.getHeight()));
            thumb.setWidth(track.getWidth());
            thumb.setHeight(this.visibleSize / this.max * track.getHeight());
            if(this.visibleSize / this.max >= 1) thumb.setHeight(0);
        }
        double trackStartingPosition = ((type == ScrollType.HORIZONTAL) ? track.getX() : track.getY()) + ((type == ScrollType.HORIZONTAL) ? thumb.getWidth()/2 : thumb.getHeight()/2);
        double trackSize = ((type == ScrollType.HORIZONTAL) ? track.getWidth() : track.getHeight()) - ((type == ScrollType.HORIZONTAL) ? thumb.getWidth(): thumb.getHeight());
        double diff = Math.min(trackSize, Math.max(0, mouse - trackStartingPosition));
        if (dragging) {
            scroll = roundToPlace(((diff / trackSize) * (max-visibleSize)), (int) trackSize);
        }
    }
    
    public void onClick(int mouseX, int mouseY, int button) {
        if(track.isInside(mouseX, mouseY)) dragging = true;
    }
    public void onRelease(int mouseX, int mouseY, int button) { dragging = false; }
    
    public void scrollMouseInput() {
        if (visibleSize < max) {
            float g = Mouse.getEventDWheel();
            double maxScrollY = max - visibleSize;
            double size = Mouse.getDWheel() / 30;
            if (size != 0) temp += size;
            if (Math.round(temp) != 0) {
                temp = (((temp) * (4 - 1)) / (4));
                scroll -= temp;
                if (scroll < 0) scroll = 0;
                else if (scroll > maxScrollY) scroll = maxScrollY;
            } else { temp = 0; }
        } else { scroll = 0; }
    }
    
    private double roundToPlace(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
    
    public enum ScrollType { VERTICAL, HORIZONTAL }
}
