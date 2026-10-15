package crewx.module.modules.render;

import crewx.module.Module;
import crewx.event.EventTarget;
import crewx.events.Render2DEvent;
import crewx.property.properties.ModeProperty;
import crewx.util.notifications.NotificationManager;
import crewx.util.notifications.NotificationType;

public class Notifications extends Module {
    private static Notifications INSTANCE;
    public final ModeProperty position = new ModeProperty("corner", 3,
            new String[]{"Top Left", "Top Right", "Bottom Left", "Bottom Right"});

    public Notifications() {
        super("Notifications", true);
        INSTANCE = this;
    }

    public static boolean isRightSide() {
        return INSTANCE != null && (INSTANCE.position.getValue() == 1 || INSTANCE.position.getValue() == 3);
    }

    public static boolean isBottom() {
        return INSTANCE != null && INSTANCE.position.getValue() >= 2;
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

    @Override
    public void onDisabled() {
        NotificationManager.getManager().clear();
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (this.isEnabled()) {
            NotificationManager.getManager().updateAndRender();
        }
    }
}
