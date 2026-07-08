package myau.ui;

import java.util.concurrent.atomic.AtomicInteger;

public interface Component {
    void draw(AtomicInteger offset);
    void update(int mousePosX, int mousePosY);
    void mouseDown(int x, int y, int button);
    void mouseReleased(int x, int y, int button);
    void keyTyped(char chatTyped, int keyCode);
    void setComponentStartAt(int newOffsetY);
    int getHeight();
    boolean isVisible();
}