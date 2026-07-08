package myau.module.modules;

import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Notifications extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static Notifications INSTANCE;

    public final ModeProperty position = new ModeProperty("position", 0, new String[]{"BOTTOM_RIGHT", "TOP_RIGHT", "BOTTOM_LEFT", "TOP_LEFT"});
    public final IntProperty duration = new IntProperty("duration-ms", 2000, 500, 8000);
    public final IntProperty anim = new IntProperty("anim-ms", 250, 0, 1000);
    public final BooleanProperty shadow = new BooleanProperty("shadow", true);

    private final List<Notice> notices = new ArrayList<>();

    public Notifications() {
        super("Notifications", true);
        INSTANCE = this;
    }

    // Toggle-style notification: title becomes "<title> enabled" / "<title> disabled".
    // Passing an empty/null message keeps the notice as a single line (fix #3).
    public static void push(String title, String message, boolean enabled) {
        if (INSTANCE == null || !INSTANCE.isEnabled()) return;
        String body = message == null ? "" : message;
        INSTANCE.notices.add(new Notice(title, body, enabled, false, System.currentTimeMillis()));
    }

    // Raw notification: title is shown exactly as given (no " enabled"/" disabled" suffix).
    // Used by HackerDetector to render "Hacker Detected" + "<player> - <cheat>".
    public static void pushRaw(String title, String message) {
        if (INSTANCE == null || !INSTANCE.isEnabled()) return;
        String body = message == null ? "" : message;
        INSTANCE.notices.add(new Notice(title, body, true, true, System.currentTimeMillis()));
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) return;
        long now = System.currentTimeMillis();
        long dur = this.duration.getValue();
        long ani = Math.max(1, this.anim.getValue());

        Iterator<Notice> it = notices.iterator();
        while (it.hasNext()) {
            if (now - it.next().start > dur + ani * 2) it.remove();
        }

        ScaledResolution sr = new ScaledResolution(mc);
        int sw = sr.getScaledWidth();
        int sh = sr.getScaledHeight();
        int mode = this.position.getValue();
        boolean right = mode == 0 || mode == 1;
        boolean bottom = mode == 0 || mode == 2;

        int gap = 4;
        int stackOffset = 0;
        List<Notice> snapshot = new ArrayList<>(notices);
        if (bottom) java.util.Collections.reverse(snapshot);
        for (Notice n : snapshot) {
            long age = now - n.start;
            float slide;
            if (age < ani) slide = age / (float) ani;
            else if (age > dur + ani) slide = Math.max(0f, 1f - (age - dur - ani) / (float) ani);
            else slide = 1f;
            if (slide <= 0.001f) continue;

            String head = n.raw ? n.title : n.title + (n.enabled ? " enabled" : " disabled");
            String body = n.message == null ? "" : n.message;
            boolean hasBody = !body.isEmpty();

            int textW = mc.fontRendererObj.getStringWidth(head);
            if (hasBody) textW = Math.max(textW, mc.fontRendererObj.getStringWidth(body));
            int w = textW + 14;
            int h = hasBody ? 26 : 16;
            int fullX = right ? sw - w - 6 : 6;
            int offX = (int) ((1f - slide) * (w + 12));
            int x = right ? fullX + offX : fullX - offX;
            int y = bottom ? sh - h - 6 - stackOffset : 6 + stackOffset;

            int accent;
            if (n.raw) accent = new Color(240, 170, 40).getRGB(); // hacker-detected: amber
            else accent = n.enabled ? new Color(60, 200, 90).getRGB() : new Color(220, 70, 70).getRGB();

            RenderUtil.enableRenderState();
            Gui.drawRect(x, y, x + w, y + h, new Color(0, 0, 0, 180).getRGB());
            Gui.drawRect(x, y, x + 2, y + h, accent);
            RenderUtil.disableRenderState();

            int titleY = hasBody ? y + 5 : y + (h - 8) / 2;
            if (this.shadow.getValue()) {
                mc.fontRendererObj.drawStringWithShadow(head, x + 6, titleY, 0xFFFFFFFF);
                if (hasBody) mc.fontRendererObj.drawStringWithShadow(body, x + 6, y + 15, 0xFFB0B0B0);
            } else {
                mc.fontRendererObj.drawString(head, x + 6, titleY, 0xFFFFFFFF);
                if (hasBody) mc.fontRendererObj.drawString(body, x + 6, y + 15, 0xFFB0B0B0);
            }
            stackOffset += h + gap;
        }
    }

    private static class Notice {
        final String title;
        final String message;
        final boolean enabled;
        final boolean raw;
        final long start;

        Notice(String title, String message, boolean enabled, boolean raw, long start) {
            this.title = title;
            this.message = message;
            this.enabled = enabled;
            this.raw = raw;
            this.start = start;
        }
    }
}
