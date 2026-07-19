package myau.clickgui.theme;

public class ThemeManager {
    private Theme activeTheme = new Theme("Dark");
    
    public int get(String name) { return activeTheme.get(name); }
    public Theme getActiveTheme() { return activeTheme; }
}
