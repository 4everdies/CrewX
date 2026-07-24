package myau.util.notifications;

import java.util.List;

public interface INotificationRenderer {
    void draw(List<INotification> notifications);
}