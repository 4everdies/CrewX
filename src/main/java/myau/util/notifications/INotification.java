package myau.util.notifications;

public interface INotification {
    String getHeader();
    String getSubtext();
    long getStart();
    long getDisplayTime();
    NotificationType getType();
    float getX();
    float getTarX();
    void setHeader(String header);
    void setSubtext(String subtext);
    void setStart(long start);
    void setDisplayTime(long displayTime);
    void setX(int x);
    void setTarX(int x);
    void setY(int y);
    long checkTime();
    float getY();
}