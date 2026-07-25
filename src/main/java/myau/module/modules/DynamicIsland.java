package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class DynamicIsland extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final int BOX_HEIGHT = 20;
    private static final float PAD_LEFT = 11.0F;
    private static final float PAD_RIGHT = 12.0F;
    private static final float STATUS_RADIUS = 2.5F;
    private static final float STATUS_SPACE = STATUS_RADIUS * 2.0F + 7.0F;
    private static final float DOT_SPACE = 11.0F;
    private static final int DIM_TEXT = 0xFF9BA0AD;

    public final ModeProperty colorMode = new ModeProperty(
            "color", 0, new String[]{"HUD Theme", "Custom"}
    );
    public final ColorProperty textColor = new ColorProperty(
            "accent-color", new Color(60, 162, 253).getRGB() & 0xFFFFFF,
            () -> this.colorMode.getValue() == 1
    );
    public final PercentProperty backgroundAlpha = new PercentProperty("background-alpha", 72);
    public final IntProperty curve = new IntProperty("curve", 10, 0, 10);
    public final IntProperty offsetY = new IntProperty("offset-y", 6, 0, 80);
    public final BooleanProperty glow = new BooleanProperty("glow", true);
    public final BooleanProperty outline = new BooleanProperty("outline", true);
    public final BooleanProperty textShadow = new BooleanProperty("shadow", true);
    public final BooleanProperty showUsername = new BooleanProperty("username", true);
    public final BooleanProperty showServer = new BooleanProperty("server", true);
    public final BooleanProperty showPing = new BooleanProperty("ping", true);
    public final BooleanProperty showFps = new BooleanProperty("fps", true);

    private float animatedWidth = -1.0F;
    private long lastFrame = 0L;

    public DynamicIsland() {
        super("DynamicIsland", true, false);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) return;

        ScaledResolution sr = new ScaledResolution(mc);
        long now = System.currentTimeMillis();

        List<Part> parts = buildParts();
        if (parts.isEmpty()) return;

        float content = STATUS_SPACE;
        for (int i = 0; i < parts.size(); i++) content += parts.get(i).width;

        float target = content + PAD_LEFT + PAD_RIGHT;
        float step = Math.min(1.0F, (now - lastFrame) / 1000.0F * 9.0F);
        lastFrame = now;
        if (animatedWidth <= 0.0F) {
            animatedWidth = target;
        } else {
            animatedWidth += (target - animatedWidth) * step;
        }

        float width = animatedWidth;
        float height = BOX_HEIGHT;
        float x = Math.round(sr.getScaledWidth() / 2.0F - width / 2.0F);
        float y = this.offsetY.getValue();
        float radius = Math.min(this.curve.getValue(), height / 2.0F);
        float centerY = y + height / 2.0F;
        Color accent = accentAt(0.0D);
        int accentRGB = accent.getRGB();
        int alpha = Math.round(255.0F * this.backgroundAlpha.getValue() / 100.0F);
        if (alpha > 255) alpha = 255;
        if (alpha < 0) alpha = 0;

        RenderUtil.enableRenderState();

        if (this.glow.getValue()) {
            for (int i = 4; i >= 1; i--) {
                int a = 4 + (4 - i) * 5;
                drawRoundedRect(x - i, y - i, x + width + i, y + height + i, radius + i, withAlpha(accentRGB, a));
            }
        }

        drawRoundedGradient(x, y, x + width, y + height, radius,
                argb(alpha, 30, 31, 38), argb(Math.min(255, alpha + 20), 11, 11, 15));

        if (this.outline.getValue()) {
            drawRoundedOutline(x, y, x + width, y + height, radius, withAlpha(accentRGB, 165), 1.0F);
            drawRoundedOutline(x + 1.0F, y + 1.0F, x + width - 1.0F, y + height - 1.0F,
                    Math.max(0.0F, radius - 1.0F), 0x14FFFFFF, 1.0F);
        }

        float pulse = 0.65F + 0.35F * (float) Math.sin(now / 380.0);
        drawCircle(x + PAD_LEFT + STATUS_RADIUS, centerY, STATUS_RADIUS + 1.8F, withAlpha(accentRGB, (int) (55 * pulse)));
        drawCircle(x + PAD_LEFT + STATUS_RADIUS, centerY, STATUS_RADIUS, withAlpha(accentRGB, 235));

        float cursor = x + PAD_LEFT + STATUS_SPACE;
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            if (part.dot) {
                drawCircle(cursor + DOT_SPACE / 2.0F, centerY, 1.3F, withAlpha(accentRGB, 130));
            }
            cursor += part.width;
        }

        RenderUtil.disableRenderState();

        float textY = y + (height - 8.0F) / 2.0F;
        cursor = x + PAD_LEFT + STATUS_SPACE;
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            if (!part.dot) {
                if (part.logo) {
                    drawLogo(part.text, cursor, textY);
                } else {
                    drawText(part.text, cursor, textY, part.color);
                }
            }
            cursor += part.width;
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private List<Part> buildParts() {
        List<Part> parts = new ArrayList<Part>();
        int accentRGB = accentAt(0.0D).getRGB();

        parts.add(Part.text("CrewX", accentRGB, true));

        if (this.showUsername.getValue()) {
            parts.add(Part.dot());
            parts.add(Part.text(mc.thePlayer.getName(), 0xFFFFFFFF, false));
        }
        if (this.showServer.getValue()) {
            parts.add(Part.dot());
            parts.add(Part.text(getServerIP(), 0xFFE2E4EA, false));
        }
        if (this.showPing.getValue()) {
            parts.add(Part.dot());
            parts.add(Part.text(String.valueOf(getPing()), accentRGB, false));
            parts.add(Part.text("ms", DIM_TEXT, false));
        }
        if (this.showFps.getValue()) {
            parts.add(Part.dot());
            parts.add(Part.text(String.valueOf(Minecraft.getDebugFPS()), accentRGB, false));
            parts.add(Part.text("fps", DIM_TEXT, false));
        }
        return parts;
    }

    private static final class Part {
        final String text;
        final int color;
        final boolean dot;
        final boolean logo;
        final float width;

        private Part(String text, int color, boolean dot, boolean logo, float width) {
            this.text = text;
            this.color = color;
            this.dot = dot;
            this.logo = logo;
            this.width = width;
        }

        static Part text(String text, int color, boolean logo) {
            return new Part(text, color, false, logo, mc.fontRendererObj.getStringWidth(text));
        }

        static Part dot() {
            return new Part(null, 0, true, false, DOT_SPACE);
        }
    }

    private Color accentAt(double offset) {
        if (this.colorMode.getValue() == 0) {
            Module module = Myau.moduleManager.modules.get(HUD.class);
            if (module instanceof HUD) {
                return ((HUD) module).getColor(System.currentTimeMillis(), offset);
            }
        }
        return new Color(this.textColor.getValue() & 0xFFFFFF);
    }

    private void drawLogo(String text, float x, float y) {
        float cursor = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            drawText(ch, cursor, y, accentAt(i * 0.45D).getRGB());
            cursor += mc.fontRendererObj.getStringWidth(ch);
        }
    }

    private void drawText(String text, float x, float y, int color) {
        if (this.textShadow.getValue()) {
            mc.fontRendererObj.drawStringWithShadow(text, x, y, color);
        } else {
            mc.fontRendererObj.drawString(text, (int) x, (int) y, color);
        }
    }
    private static int argb(int alpha, int red, int green, int blue) {
        return (alpha & 0xFF) << 24 | (red & 0xFF) << 16 | (green & 0xFF) << 8 | (blue & 0xFF);
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha & 0xFF) << 24 | (color & 0xFFFFFF);
    }

    private static int mixColor(int first, int second, float progress) {
        if (progress < 0.0F) progress = 0.0F;
        if (progress > 1.0F) progress = 1.0F;
        int a1 = first >>> 24 & 0xFF, r1 = first >> 16 & 0xFF, g1 = first >> 8 & 0xFF, b1 = first & 0xFF;
        int a2 = second >>> 24 & 0xFF, r2 = second >> 16 & 0xFF, g2 = second >> 8 & 0xFF, b2 = second & 0xFF;
        return (int) (a1 + (a2 - a1) * progress) << 24
                | (int) (r1 + (r2 - r1) * progress) << 16
                | (int) (g1 + (g2 - g1) * progress) << 8
                | (int) (b1 + (b2 - b1) * progress);
    }

    private static void drawRoundedRect(float x1, float y1, float x2, float y2, float radius, int color) {
        drawRoundedGradient(x1, y1, x2, y2, radius, color, color);
    }

    private static void drawRoundedGradient(float x1, float y1, float x2, float y2, float radius, int top, int bottom) {
        radius = Math.min(radius, Math.min((x2 - x1) / 2.0F, (y2 - y1) / 2.0F));
        if (radius < 0.0F) radius = 0.0F;

        float h = Math.max(1.0F, y2 - y1);
        int steps = Math.max(4, (int) radius + 3);

        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        RenderUtil.setColor(mixColor(top, bottom, 0.5F));
        GL11.glVertex2f((x1 + x2) / 2.0F, (y1 + y2) / 2.0F);
        arc(x1 + radius, y1 + radius, radius, Math.PI, steps, y1, h, top, bottom);
        arc(x2 - radius, y1 + radius, radius, -Math.PI / 2.0, steps, y1, h, top, bottom);
        arc(x2 - radius, y2 - radius, radius, 0.0, steps, y1, h, top, bottom);
        arc(x1 + radius, y2 - radius, radius, Math.PI / 2.0, steps, y1, h, top, bottom);
        RenderUtil.setColor(top);
        GL11.glVertex2f(x1, y1 + radius);
        GL11.glEnd();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.resetColor();
    }

    private static void arc(float cx, float cy, float r, double start, int steps,
                            float y1, float h, int top, int bottom) {
        for (int i = 0; i <= steps; i++) {
            double angle = start + (Math.PI / 2.0) * ((double) i / steps);
            float vx = (float) (cx + Math.cos(angle) * r);
            float vy = (float) (cy + Math.sin(angle) * r);
            RenderUtil.setColor(mixColor(top, bottom, (vy - y1) / h));
            GL11.glVertex2f(vx, vy);
        }
    }

    private static void drawRoundedOutline(float x1, float y1, float x2, float y2,
                                           float radius, int color, float lineWidth) {
        radius = Math.min(radius, Math.min((x2 - x1) / 2.0F, (y2 - y1) / 2.0F));
        if (radius < 0.0F) radius = 0.0F;
        int steps = Math.max(4, (int) radius + 3);

        RenderUtil.setColor(color);
        GL11.glLineWidth(lineWidth);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        plainArc(x1 + radius, y1 + radius, radius, Math.PI, steps);
        plainArc(x2 - radius, y1 + radius, radius, -Math.PI / 2.0, steps);
        plainArc(x2 - radius, y2 - radius, radius, 0.0, steps);
        plainArc(x1 + radius, y2 - radius, radius, Math.PI / 2.0, steps);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1.0F);
        GlStateManager.resetColor();
    }

    private static void plainArc(float cx, float cy, float r, double start, int steps) {
        for (int i = 0; i <= steps; i++) {
            double angle = start + (Math.PI / 2.0) * ((double) i / steps);
            GL11.glVertex2f((float) (cx + Math.cos(angle) * r), (float) (cy + Math.sin(angle) * r));
        }
    }

    private static void drawCircle(float cx, float cy, float radius, int color) {
        if (radius <= 0.0F) return;
        RenderUtil.setColor(color);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(cx, cy);
        int steps = 24;
        for (int i = 0; i <= steps; i++) {
            double angle = Math.PI * 2.0 * i / steps;
            GL11.glVertex2f((float) (cx + Math.cos(angle) * radius), (float) (cy + Math.sin(angle) * radius));
        }
        GL11.glEnd();
        GlStateManager.resetColor();
    }

    private int getPing() {
        try {
            if (mc.thePlayer == null || mc.getNetHandler() == null) return 0;
            NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getName());
            if (playerInfo != null) return playerInfo.getResponseTime();
        } catch (Exception ignored) {
        }
        return 0;
    }

    private String getServerIP() {
        try {
            if (mc.theWorld != null) {
                if (mc.isIntegratedServerRunning()) return "SinglePlayer";
                if (mc.getCurrentServerData() != null) return mc.getCurrentServerData().serverIP;
            }
        } catch (Exception ignored) {
        }
        return "SinglePlayer";
    }
}
