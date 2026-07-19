package myau.clickgui.util;

public abstract class Button {
    public abstract void drawScreen(int mouseX, int mouseY);
    public abstract void onClick(int mouseX, int mouseY, int button);
    public abstract void onRelease(int mouseX, int mouseY, int button);
    public abstract void onKey(int key, char ch);
}
