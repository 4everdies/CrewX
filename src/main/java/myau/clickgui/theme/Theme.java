package myau.clickgui.theme;

import java.util.LinkedHashMap;

public class Theme {
    private String name;
    private LinkedHashMap<String, Integer> colors = new LinkedHashMap<>();

    public Theme(String name) {
        this.name = name;
        setDefaults();
    }

    public void setDefaults() {
        colors.clear();
        colors.put("Panel Background Color", 0xff1C1C1E);
        colors.put("Panel Screen Backround Color", 0xff2C2C2E);
        colors.put("Panel Screen Scroll Thumb Color", 0x40FFFFFF);
        colors.put("Panel Content Button Color", 0xff2C2C2E);
        colors.put("Panel Module Background", 0xff2C2C2E);
        colors.put("Panel Active Color", 0xff007AFF);
        colors.put("Panel Category Setting Line", 0x303A3A3C);
        colors.put("Panel Other Buttons Background", 0xff3A3A3C);
        colors.put("Panel Cursors Background", 0xffffffff);
        colors.put("Panel Location Box Outline", 0x25aaaaaa);
        colors.put("Panel Slider Background", 0x603A3A3C);
        colors.put("Panel Slider Background Filled", 0xff007AFF);
        colors.put("Panel Slider Cursor", 0xffFFFFFF);
        colors.put("Panel Search Text Color", 0xaaffffff);
        colors.put("Panel Search Cursor Color", -1);
        colors.put("Panel Text Color", 0xffFFFFFF);
        colors.put("Panel Descriptions Color", 0xff8E8E93);
    }

    public int get(String name) { return colors.getOrDefault(name, -1); }
    public void set(String name, int color) { colors.put(name, color); }
}
