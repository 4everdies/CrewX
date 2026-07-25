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

    @Override
    public void draw(List<INotification> notifications) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        float y = sr.getScaledHeight() - (notifications.size() * 24);

        for (INotification notification : notifications) {
            Notification not = (Notification) notification;

            String header = not.getHeader();
            String subtext = not.getSubtext();

            float headerWidth = mc.fontRendererObj.getStringWidth(header);
            float subWidth = mc.fontRendererObj.getStringWidth(subtext);
            double tarX = not.getTarX() >= sr.getScaledWidth()
                    ? not.getTarX()
                    : sr.getScaledWidth() - 23 - Math.max(headerWidth, subWidth);

            not.translate.interpolate((float) (tarX + 3), y, 0.3f);

            float x = not.translate.getX();
            float boxW = Math.max(headerWidth, subWidth) + 23;

            GL11.glPushMatrix();

            RenderUtil.enableRenderState();
            RenderUtil.drawRect(x, not.translate.getY(), x + boxW, not.translate.getY() + 23, new Color(0, 0, 0, 200).getRGB());
            int accent = getColor(not.getType());
            RenderUtil.drawRect(x, not.translate.getY(), x + 2, not.translate.getY() + 23, accent);
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
            GlStateManager.translate(x + 2, not.translate.getY() + 2.5f, 0);
            Gui.drawModalRectWithCustomSizedTexture(0, 0, 0, 0, 18, 18, 18, 18);
            GlStateManager.popMatrix();

            mc.fontRendererObj.drawStringWithShadow(header, x + 22, not.translate.getY() + 2, -1);
            mc.fontRendererObj.drawStringWithShadow(subtext, x + 22, not.translate.getY() + 12, 0xFFB0B0B0);

            GlStateManager.disableBlend();
            GlStateManager.disableAlpha();

            RenderUtil.enableRenderState();
            double percent = Math.min(1, Math.max(0, (double) (System.currentTimeMillis() - not.getStart()) / not.getDisplayTime()));
            RenderUtil.drawRect(x, not.translate.getY() + 21, x + boxW, not.translate.getY() + 23, new Color(0, 0, 0, 45).getRGB());
            RenderUtil.drawRect(x, not.translate.getY() + 21, x + (float) (boxW * percent), not.translate.getY() + 23, accent);
            RenderUtil.disableRenderState();

            GL11.glPopMatrix();

            if (not.checkTime() >= not.getDisplayTime() + not.getStart()) {
                not.setTarX(sr.getScaledWidth() + 1);
                if (not.translate.getX() >= sr.getScaledWidth()) {
                    notifications.remove(notification);
                }
            }
            y += 24;
        }
    }

    private int getColor(NotificationType type) {
        if (type == NotificationType.INFO) return new Color(64, 131, 214).getRGB();
        if (type == NotificationType.NOTIFY) return new Color(242, 206, 87).getRGB();
        if (type == NotificationType.WARNING) return new Color(226, 74, 74).getRGB();
        if (type == NotificationType.OKAY) return new Color(65, 252, 65).getRGB();
        return -1;
    }
}