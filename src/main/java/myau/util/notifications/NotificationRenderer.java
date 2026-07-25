package myau.util.notifications;

import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.List;

public class NotificationRenderer implements INotificationRenderer {

    private ResourceLocation WARNING = new ResourceLocation("textures/warning.png");
    private ResourceLocation NOTIFY = new ResourceLocation("textures/notify.png");
    private ResourceLocation OKAY = new ResourceLocation("textures/okay.png");
    private ResourceLocation INFO = new ResourceLocation("textures/info.png");
    private static final int MARGIN = 4;
    private static final int BOX_HEIGHT = 23;
    private static final int SPACING = 24;
    private static final int PADDING = 25;

    @Override
    public void draw(List<INotification> notifications) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        final int screenWidth = sr.getScaledWidth();
        final int screenHeight = sr.getScaledHeight();
        final int maxTextWidth = Math.max(16, screenWidth - MARGIN * 2 - PADDING);
        float y = screenHeight - MARGIN - notifications.size() * SPACING;
        if (y < MARGIN) y = MARGIN;
        for (INotification notification : notifications) {
            Notification not = (Notification) notification;
            String header = trim(mc, not.getHeader(), maxTextWidth);
            String subtext = trim(mc, not.getSubtext(), maxTextWidth);
            int headerWidth = mc.fontRendererObj.getStringWidth(header);
            int subWidth = mc.fontRendererObj.getStringWidth(subtext);
            float boxW = Math.max(headerWidth, subWidth) + PADDING;
            boolean leaving = not.checkTime() >= not.getDisplayTime() + not.getStart();
            float targetX = leaving ? screenWidth : screenWidth - MARGIN - boxW;
            not.setTarX((int) targetX);
            not.translate.interpolate(targetX, y, 0.3f);
            float x = not.translate.getX();
            float boxY = not.translate.getY();
            int accent = getColor(not.getType());
            GL11.glPushMatrix();
            RenderUtil.enableRenderState();
            RenderUtil.drawRect(x, boxY, x + boxW, boxY + BOX_HEIGHT, new Color(0, 0, 0, 200).getRGB());
            RenderUtil.drawRect(x, boxY, x + 2, boxY + BOX_HEIGHT, accent);
            RenderUtil.disableRenderState();
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.enableAlpha();
            GlStateManager.pushMatrix();
            switch (not.getType().name) {
                case "NOTIFY": mc.getTextureManager().bindTexture(NOTIFY); break;
                case "WARNING": mc.getTextureManager().bindTexture(WARNING); break;
                case "INFO": mc.getTextureManager().bindTexture(INFO); break;
                case "OKAY": mc.getTextureManager().bindTexture(OKAY); break;
            }
            GlStateManager.translate(x + 2, boxY + 2.5f, 0);
            Gui.drawModalRectWithCustomSizedTexture(0, 0, 0, 0, 18, 18, 18, 18);
            GlStateManager.popMatrix();
            mc.fontRendererObj.drawStringWithShadow(header, x + 22, boxY + 2, -1);
            mc.fontRendererObj.drawStringWithShadow(subtext, x + 22, boxY + 12, 0xFFB0B0B0);
            GlStateManager.disableBlend();
            GlStateManager.disableAlpha();
            RenderUtil.enableRenderState();
            double percent = Math.min(1, Math.max(0,
                    (double) (System.currentTimeMillis() - not.getStart()) / not.getDisplayTime()));
            RenderUtil.drawRect(x, boxY + 21, x + boxW, boxY + BOX_HEIGHT, new Color(0, 0, 0, 45).getRGB());
            RenderUtil.drawRect(x, boxY + 21, x + (float) (boxW * percent), boxY + BOX_HEIGHT, accent);
            RenderUtil.disableRenderState();
            GL11.glPopMatrix();
            if (leaving && not.translate.getX() >= screenWidth - 1) {
                notifications.remove(notification);
            }
            y += SPACING;
        }
    }
    private String trim(Minecraft mc, String text, int maxWidth) {
        if (text == null) return "";
        if (mc.fontRendererObj.getStringWidth(text) <= maxWidth) return text;

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (mc.fontRendererObj.getStringWidth(builder.toString() + text.charAt(i) + "...") > maxWidth) break;
            builder.append(text.charAt(i));
        }
        return builder.append("...").toString();
    }

    private int getColor(NotificationType type) {
        if (type == NotificationType.INFO) return new Color(64, 131, 214).getRGB();
        if (type == NotificationType.NOTIFY) return new Color(242, 206, 87).getRGB();
        if (type == NotificationType.WARNING) return new Color(226, 74, 74).getRGB();
        if (type == NotificationType.OKAY) return new Color(65, 252, 65).getRGB();
        return -1;
    }
}
