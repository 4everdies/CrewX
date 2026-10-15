package crewx.util.notifications;

import crewx.clickgui.render.RoundedUtils;
import crewx.gui.ClientFont;
import crewx.module.modules.render.Notifications;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Jello-style notifications: a dark rounded card with a small switch icon, a bold title ("Enabled" /
 * "Disabled") and the module name underneath. On a toggle the switch knob slides to its new side; the card
 * slides in from the screen edge and back out when its time is over.
 */
public class NotificationRenderer implements INotificationRenderer {
    private static final float MARGIN = 8.0F;
    private static final float BOX_HEIGHT = 30.0F;
    private static final float SPACING = 4.0F;
    private static final float ICON_AREA = 28.0F;
    private static final int PANEL = 0x24272D;
    private static final int TITLE = 0xFFFFFF;
    private static final int SUBTITLE = 0xB3B6BD;
    private static final int SUCCESS = 0x54C98A;
    private static final int FAILURE = 0xE6656B;
    private static final int NOTICE = 0xE7B853;
    private static final int INFO = 0x5E98F5;

    @Override
    public void draw(List<INotification> notifications) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);
        int screenWidth = resolution.getScaledWidth();
        int screenHeight = resolution.getScaledHeight();
        boolean right = Notifications.isRightSide();
        boolean bottom = Notifications.isBottom();
        float maxTextWidth = Math.max(40.0F, screenWidth - MARGIN * 2.0F - ICON_AREA - 12.0F);
        long now = System.currentTimeMillis();

        float slotY = bottom ? screenHeight - MARGIN - BOX_HEIGHT : MARGIN;
        // at the bottom the newest card takes the lowest slot and pushes the older ones up
        List<INotification> order = new java.util.ArrayList<INotification>(notifications);
        if (bottom) java.util.Collections.reverse(order);
        for (INotification value : order) {
            Notification notification = (Notification) value;
            long elapsed = now - notification.getStart();
            long display = Math.max(1L, notification.getDisplayTime());
            if (ToggleAnimation.finished(elapsed, display)) {
                notifications.remove(notification);
                continue;
            }
            String header = trim(notification.getHeader(), maxTextWidth);
            String subtext = trim(notification.getSubtext(), maxTextWidth);
            boolean enabledToggle = "Enabled".equalsIgnoreCase(header);
            boolean toggle = enabledToggle || "Disabled".equalsIgnoreCase(header);
            float textWidth = Math.max(ClientFont.getStringWidthFloat(header) + 1.0F, ClientFont.getStringWidthFloat(subtext));
            float boxWidth = Math.max(116.0F, ICON_AREA + textWidth + 12.0F);

            float appear = ToggleAnimation.appear(elapsed, display);
            float hiddenX = right ? screenWidth + 2.0F : -boxWidth - 2.0F;
            float shownX = right ? screenWidth - MARGIN - boxWidth : MARGIN;
            float x = hiddenX + (shownX - hiddenX) * appear;
            // vertical slot glides when a card above/below goes away; a new card starts in its own slot
            if (!notification.placed) {
                notification.placed = true;
                notification.translate.setX(x);
                notification.translate.setY(slotY);
            }
            notification.translate.interpolate(x, slotY, 0.35F);
            float y = notification.translate.getY();

            for (int i = 3; i >= 1; i--) {
                RoundedUtils.drawRoundedRect(x - i, y - i + 1.0F, boxWidth + i * 2.0F, BOX_HEIGHT + i * 2.0F,
                        alpha(0x000000, 0.06F * appear), 6.0F + i);
            }
            RoundedUtils.drawRoundedRect(x, y, boxWidth, BOX_HEIGHT, alpha(PANEL, 0.94F * appear), 6.0F);

            float iconCx = x + ICON_AREA / 2.0F + 1.0F;
            float iconCy = y + BOX_HEIGHT / 2.0F;
            if (toggle) {
                drawSwitch(iconCx, iconCy, ToggleAnimation.knob(elapsed, enabledToggle), appear);
            } else {
                int accent = getColor(notification.getType());
                RoundedUtils.drawRoundedRect(iconCx - 5.0F, iconCy - 5.0F, 10.0F, 10.0F, alpha(accent, appear), 5.0F);
                drawText("!", iconCx - ClientFont.getStringWidthFloat("!") / 2.0F, iconCy - ClientFont.getHeight() / 2.0F + 0.5F,
                        alpha(0x15161A, appear), 0.0F);
            }

            float textX = x + ICON_AREA + 2.0F;
            drawText(header, textX, y + 5.0F, alpha(TITLE, appear), 0.35F);
            drawText(subtext, textX, y + 16.5F, alpha(SUBTITLE, appear), 0.0F);

            slotY += bottom ? -(BOX_HEIGHT + SPACING) : BOX_HEIGHT + SPACING;
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
    }

    /** The little lever: a short line with a round knob at {@code knob} 0 (left) .. 1 (right). */
    private static void drawSwitch(float cx, float cy, float knob, float appear) {
        float half = 6.0F;
        float lineH = 1.6F;
        float knobR = 3.2F;
        float knobX = cx - half + knob * half * 2.0F;
        // track: brighter on the side the knob came from looks like the "string" of the lever in the reference
        RoundedUtils.drawRoundedRect(cx - half - knobR, cy - lineH / 2.0F, (half + knobR) * 2.0F, lineH,
                alpha(0xFFFFFF, 0.85F * appear), lineH / 2.0F);
        RoundedUtils.drawRoundedRect(knobX - knobR, cy - knobR, knobR * 2.0F, knobR * 2.0F,
                alpha(0xFFFFFF, appear), knobR);
        RoundedUtils.drawRoundedRect(knobX - knobR + 1.3F, cy - knobR + 1.3F, (knobR - 1.3F) * 2.0F, (knobR - 1.3F) * 2.0F,
                alpha(PANEL, appear), knobR - 1.3F);
    }

    private static void drawText(String text, float x, float y, int color, float bold) {
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        if ((color >>> 24) < 8) return;
        final int c = color;
        ClientFont.drawStringStyled(text, x, y, offset -> c, bold, 0, 0.0F);
    }

    private static int alpha(int rgb, float a) {
        int value = Math.round(Math.max(0.0F, Math.min(1.0F, a)) * 255.0F);
        return (value << 24) | (rgb & 0xFFFFFF);
    }

    private String trim(String text, float maxWidth) {
        if (text == null) return "";
        if (ClientFont.getStringWidthFloat(text) <= maxWidth) return text;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (ClientFont.getStringWidthFloat(builder.toString() + text.charAt(i) + "...") > maxWidth) break;
            builder.append(text.charAt(i));
        }
        return builder.append("...").toString();
    }

    private int getColor(NotificationType type) {
        if (type == NotificationType.WARNING) return FAILURE;
        if (type == NotificationType.OKAY) return SUCCESS;
        if (type == NotificationType.NOTIFY) return NOTICE;
        return INFO;
    }
}
