package myau.util.notifications;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class NotificationManager {
    private static NotificationManager instance = new NotificationManager();

    private List<INotification> notifications = new CopyOnWriteArrayList<>();
    private NotificationRenderer renderer = new NotificationRenderer();

    public List<INotification> getNotifications() {
        return notifications;
    }

    private NotificationManager() {
        instance = this;
    }

    public static NotificationManager getManager() {
        return instance;
    }

    public void post(String header, String subtext) {
        post(header, subtext, 2500);
    }

    public void post(String header, String subtext, NotificationType type) {
        post(header, subtext, 2500, type);
    }

    public void post(String header, String subtext, long displayTime) {
        post(header, subtext, displayTime, NotificationType.INFO);
    }

    public void post(String header, String subtext, long displayTime, NotificationType type) {
        if (!notifications.isEmpty()) {
            for (INotification not : notifications) {
                if (type != NotificationType.WARNING && type != NotificationType.OKAY
                        && not.getHeader().startsWith(header) && not.getType().equals(type)) {
                    if (!not.getSubtext().equals(subtext)) {
                        not.setStart(System.currentTimeMillis());
                        not.setDisplayTime(displayTime);
                    }
                    try {
                        if (not.getHeader().length() > header.length()
                                && not.getHeader().substring(header.length()).startsWith(" (")
                                && not.getHeader().substring(header.length()).endsWith(")")) {
                            String[] parts = not.getHeader().split(" \\(");
                            int current = Integer.parseInt(parts[1].replace(")", "")) + 1;
                            not.setHeader(parts[0] + " (" + current + ")");
                        } else {
                            not.setHeader(header + " (2)");
                        }
                        not.setSubtext(subtext);
                        return;
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        this.notifications.add(new Notification(header, subtext, displayTime, type));
    }

    public void updateAndRender() {
        if (notifications.isEmpty()) return;
        renderer.draw(notifications);
    }
}