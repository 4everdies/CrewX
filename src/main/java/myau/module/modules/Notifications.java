package myau.module.modules;

import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.util.notifications.NotificationManager;
import myau.util.notifications.NotificationType;

public class Notifications extends Module {
    private static Notifications INSTANCE;

    public Notifications() {
        super("Notifications", true);
        INSTANCE = this;
    }

    public static void push(String title, String message, boolean enabled) {
        if (INSTANCE == null || !INSTANCE.isEnabled()) return;
        NotificationManager.getManager().post(
                title,
                message == null ? "" : message,
                2500,
                enabled ? NotificationType.OKAY : NotificationType.WARNING
        );
    }

    public static void pushRaw(String title, String message) {
        if (INSTANCE == null || !INSTANCE.isEnabled()) return;
        NotificationManager.getManager().post(
                title,
                message == null ? "" : message,
                3000,
                NotificationType.NOTIFY
        );
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!this.isEnabled()) return;
        NotificationManager.getManager().updateAndRender();
    }
}