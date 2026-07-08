package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;

import java.awt.Color;

public class DynamicIsland extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ColorProperty textColor = new ColorProperty("accent-color", new Color(60, 162, 253).getRGB());
    public final BooleanProperty textShadow = new BooleanProperty("shadow", true);

    private final int bgAlpha = 160;

    public DynamicIsland() {
        super("DynamicIsland", true, false);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) return;

        ScaledResolution sr = new ScaledResolution(mc);
        String username = mc.thePlayer.getName();
        int ping = getPing();
        int fps = Minecraft.getDebugFPS();
        String server = getServerIP();

        String text = "CrewX  \u00b7  " + username + "  \u00b7  " + ping + "ms to " + server + "  \u00b7  " + fps + "fps";
        float width = mc.fontRendererObj.getStringWidth(text) + 24f;
        float height = 20f;
        float x = sr.getScaledWidth() / 2f - width / 2f;
        float y = 6f;

        RenderUtil.enableRenderState();
        Gui.drawRect((int) x, (int) y, (int) (x + width), (int) (y + height), new Color(0, 0, 0, bgAlpha).getRGB());
        Gui.drawRect((int) x, (int) y, (int) (x + width), (int) y + 1, new Color(textColor.getValue()).getRGB());
        Gui.drawRect((int) x, (int) (y + height - 1), (int) (x + width), (int) (y + height), new Color(textColor.getValue()).getRGB());
        RenderUtil.disableRenderState();

        int textY = (int) (y + (height - mc.fontRendererObj.FONT_HEIGHT) / 2f);
        int startX = (int) (x + 12);
        int accent = new Color(textColor.getValue()).getRGB();

        drawText("CrewX", startX, textY, accent);
        int p1 = mc.fontRendererObj.getStringWidth("CrewX");
        String mid = "  \u00b7  " + username + "  \u00b7  ";
        drawText(mid, startX + p1, textY, 0xFFFFFFFF);
        int p2 = p1 + mc.fontRendererObj.getStringWidth(mid);
        String pingStr = ping + "ms";
        drawText(pingStr, startX + p2, textY, accent);
        int p3 = p2 + mc.fontRendererObj.getStringWidth(pingStr);
        drawText(" to " + server + "  \u00b7  " + fps + "fps", startX + p3, textY, 0xFFFFFFFF);
    }

    private void drawText(String s, int x, int y, int color) {
        if (textShadow.getValue()) mc.fontRendererObj.drawStringWithShadow(s, x, y, color);
        else mc.fontRendererObj.drawString(s, x, y, color);
    }

    private int getPing() {
        try {
            if (mc.thePlayer == null || mc.getNetHandler() == null) return 0;
            NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getName());
            if (playerInfo != null) return playerInfo.getResponseTime();
        } catch (Exception ignored) {}
        return 0;
    }

    private String getServerIP() {
        try {
            if (mc.theWorld != null) {
                if (mc.isIntegratedServerRunning()) return "SinglePlayer";
                if (mc.getCurrentServerData() != null) return mc.getCurrentServerData().serverIP;
            }
        } catch (Exception ignored) {}
        return "SinglePlayer";
    }
}