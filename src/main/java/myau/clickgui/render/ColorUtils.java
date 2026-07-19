package myau.clickgui.render;

import java.awt.Color;
import myau.clickgui.value.settings.ColorValue;

public class ColorUtils {
    public static int mix(int first, int second, double range) {
        int red1 = (first >> 16 & 255);
        int green1 = (first >> 8 & 255);
        int blue1 = (first & 255);
        int red2 = (second >> 16 & 255);
        int green2 = (second >> 8 & 255);
        int blue2 = (second & 255);
        int diffR = (int) (red1 - (red1 - red2) / 1 * range);
        int diffG = (int) (green1 - (green1 - green2) / 1 * range);
        int diffB = (int) (blue1 - (blue1 - blue2) / 1 * range);
        if (diffR > 255) diffR = 255; if (diffR < 0) diffR = 0;
        if (diffG > 255) diffG = 255; if (diffG < 0) diffG = 0;
        if (diffB > 255) diffB = 255; if (diffB < 0) diffB = 0;
        return new Color(diffR, diffG, diffB).getRGB();
    }
    
    public static int setAlphaForColor(int color, int alpha) {
        int red = (color >> 16 & 255);
        int green = (color >> 8 & 255);
        int blue = (color & 255);
        return new Color(red, green, blue, Math.min(Math.round(alpha * 2.5f), 255)).getRGB();
    }
    
    public static int rainbow(long timeMilis, double offset, double speed, double saturation) {
        return Color.HSBtoRGB((float)((timeMilis*speed + offset) % 360F) / 360F, (float)saturation, 1);
    }
    
    public static int getColor(ColorValue color, long timeMilis) {
        switch(color.getMode().toLowerCase()) {
            case "rainbow": return setAlphaForColor(rainbow(timeMilis, 1, 1, 1), color.getAlpha());
            case "gradient": return setAlphaForColor(mix(color.getColor1(), color.getColor2(), (Math.cos(Math.toRadians(timeMilis))+1) / 2), color.getAlpha());
            default: return setAlphaForColor(color.getColor1(), color.getAlpha());
        }
    }
}
