package myau.util.notifications;

import myau.util.Translate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

public class Notification implements INotification {
    private String header, subtext;
    private long start, displayTime;
    private NotificationType type;
    private float x, tarX, y;
    public Translate translate;

    protected Notification(String header, String subtext, long displayTime, NotificationType type) {
        this.header = header;
        this.subtext = subtext;
        this.start = System.currentTimeMillis();
        this.displayTime = displayTime;
        this.type = type;
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());

        this.y = sr.getScaledHeight();
        this.x = sr.getScaledWidth();
        float headerWidth = Minecraft.getMinecraft().fontRendererObj.getStringWidth(header);
        float subWidth = Minecraft.getMinecraft().fontRendererObj.getStringWidth(subtext);
        this.tarX = sr.getScaledWidth() - 25 - Math.max(headerWidth, subWidth);
        this.translate = new Translate(x, y);
    }

    @Override public long checkTime() { return System.currentTimeMillis(); }
    @Override public String getHeader() { return header; }
    @Override public String getSubtext() { return subtext; }
    @Override public long getStart() { return start; }
    @Override public long getDisplayTime() { return displayTime; }
    @Override public NotificationType getType() { return type; }
    @Override public float getX() { return x; }
    @Override public float getTarX() { return tarX; }
    @Override public void setSubtext(String subtext) { this.subtext = subtext; }
    @Override public void setHeader(String header) { this.header = header; }
    @Override public void setStart(long start) { this.start = start; }
    @Override public void setDisplayTime(long displayTime) { this.displayTime = displayTime; }
    @Override public void setX(int x) { this.x = x; }
    @Override public void setTarX(int x) { this.tarX = x; }
    @Override public void setY(int y) { this.y = y; }
    @Override public float getY() { return y; }
}