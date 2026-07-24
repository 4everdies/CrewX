package myau.util.notifications;

public class NotificationType {
    public static final NotificationType INFO = new NotificationType("INFO");
    public static final NotificationType WARNING = new NotificationType("WARNING");
    public static final NotificationType NOTIFY = new NotificationType("NOTIFY");
    public static final NotificationType OKAY = new NotificationType("OKAY");
    public static final NotificationType SPOTIFY = new NotificationType("SPOTIFY");

    public final String name;

    public NotificationType(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }
}