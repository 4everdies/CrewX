package crewx.util;

import net.minecraft.client.Minecraft;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Optional stutter diagnosis (".hitches"). While enabled, the time of every CrewX event handler on the client
 * thread is added up per frame; when a frame takes much longer than usual, the chat shows how long it took, how
 * much of it was CrewX (and its slowest handlers) and how much was garbage collection - so a hitch can be pinned
 * on the client, on GC pauses or on Minecraft itself. Disabled it costs one boolean check per event.
 */
public final class HitchMonitor {
    private static final long REPORT_COOLDOWN_MS = 2000L;
    private static volatile boolean enabled;
    /** Handler -> nanoseconds this frame. Keyed by the handler object itself: no strings built per call. */
    private static final Map<Object, long[]> FRAME = new java.util.IdentityHashMap<Object, long[]>();
    private static long frameStart;
    private static double averageMs = -1.0;
    private static long gcTimeAtFrameStart;
    private static long lastReport;
    private static int hitches;
    private static int frames;

    private HitchMonitor() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        FRAME.clear();
        SECTIONS.clear();
        frameStart = 0L;
        averageMs = -1.0;
        hitches = 0;
        frames = 0;
    }

    public static String summary() {
        return String.format("%d frames watched, %d hitches, normal frame %.1f ms", frames, hitches, Math.max(0.0, averageMs));
    }

    /** Client thread, once per frame (start of rendering). */
    public static void frame() {
        if (!enabled) return;
        long now = System.nanoTime();
        long gc = gcTimeMs();
        if (frameStart != 0L) {
            double ms = (now - frameStart) / 1.0e6;
            frames++;
            if (averageMs < 0.0) averageMs = ms;
            boolean hitch = frames > 30 && ms > Math.max(averageMs * 2.5, averageMs + 25.0);
            if (hitch) {
                hitches++;
                report(ms, gc - gcTimeAtFrameStart);
            } else {
                averageMs += (ms - averageMs) * 0.05;   // the "normal" frame time ignores the spikes
            }
        }
        for (long[] total : FRAME.values()) total[0] = 0L;   // keep the entries: no garbage per frame
        for (long[] total : SECTIONS.values()) total[0] = 0L;
        frameStart = now;
        gcTimeAtFrameStart = gc;
    }

    /** Named parts of the frame (HUD list, text, rounded panels...), to tell what inside a slow handler was slow. */
    private static final Map<String, long[]> SECTIONS = new java.util.IdentityHashMap<String, long[]>();

    /** Big phases of a frame, timed from mixins: game tick, world, HUD/menus and handing the frame to the driver. */
    public static final int TICK = 0;
    public static final int WORLD = 1;
    public static final int OVERLAY = 2;
    public static final int SWAP = 3;
    private static final String[] PHASE_NAMES = {"game tick (logic, packets)", "world render", "HUD render", "frame swap (driver/GPU)"};
    private static final long[] PHASE_START = new long[4];

    public static void beginPhase(int phase) {
        if (enabled) PHASE_START[phase] = System.nanoTime();
    }

    public static void endPhase(int phase) {
        long started = PHASE_START[phase];
        if (started == 0L) return;
        PHASE_START[phase] = 0L;
        section(PHASE_NAMES[phase], started);
    }

    /** Start of a timed section: 0 while the monitor is off, so callers pay one boolean check. */
    public static long start() {
        return enabled ? System.nanoTime() : 0L;
    }

    /** Adds the time since {@code start} to section {@code name} (a constant string); client thread only. */
    public static void section(String name, long start) {
        if (start == 0L || !enabled || !Minecraft.getMinecraft().isCallingFromMinecraftThread()) return;
        long[] total = SECTIONS.get(name);
        if (total == null) SECTIONS.put(name, total = new long[1]);
        total[0] += System.nanoTime() - start;
    }

    /** Adds the duration of one event handler (client thread only). */
    public static void handler(Object handler, long nanos) {
        if (!enabled || !Minecraft.getMinecraft().isCallingFromMinecraftThread()) return;
        long[] total = FRAME.get(handler);
        if (total == null) FRAME.put(handler, total = new long[1]);
        total[0] += nanos;
    }

    private static void report(double frameMs, long gcMs) {
        long now = System.currentTimeMillis();
        if (now - lastReport < REPORT_COOLDOWN_MS) return;
        lastReport = now;
        long crewx = 0L;
        String[] top = new String[3];
        long[] topNanos = new long[3];
        for (Map.Entry<Object, long[]> entry : FRAME.entrySet()) {
            long nanos = entry.getValue()[0];
            crewx += nanos;
            for (int i = 0; i < 3; i++) {
                if (nanos > topNanos[i]) {
                    for (int j = 2; j > i; j--) {
                        topNanos[j] = topNanos[j - 1];
                        top[j] = top[j - 1];
                    }
                    topNanos[i] = nanos;
                    top[i] = String.valueOf(entry.getKey());
                    break;
                }
            }
        }
        StringBuilder slowest = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            if (top[i] == null) break;
            if (slowest.length() > 0) slowest.append(", ");
            slowest.append(top[i]).append(String.format(" %.1fms", topNanos[i] / 1.0e6));
        }
        String verdict = gcMs >= frameMs * 0.5 ? "&eGC pause"
                : crewx / 1.0e6 >= frameMs * 0.5 ? "&cCrewX" : "&7Minecraft (world/chunks/rendering)";
        ChatUtil.sendFormatted(String.format("&8[&bHitch&8] &f%.0fms&7 (normal %.0fms) - CrewX %.1fms, GC %dms - mostly %s&r",
                frameMs, averageMs, crewx / 1.0e6, gcMs, verdict));
        if (slowest.length() > 0) ChatUtil.sendFormatted("&8[&bHitch&8] &7slowest: &f" + slowest + "&r");
        List<Map.Entry<String, long[]>> parts = new java.util.ArrayList<Map.Entry<String, long[]>>();
        for (Map.Entry<String, long[]> entry : SECTIONS.entrySet()) if (entry.getValue()[0] >= 500_000L) parts.add(entry);
        if (!parts.isEmpty()) {
            parts.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
            StringBuilder inside = new StringBuilder();
            for (Map.Entry<String, long[]> entry : parts) {
                if (inside.length() > 0) inside.append(", ");
                inside.append(entry.getKey()).append(String.format(" %.1fms", entry.getValue()[0] / 1.0e6));
            }
            ChatUtil.sendFormatted("&8[&bHitch&8] &7parts: &f" + inside + "&r");
        }
    }

    private static long gcTimeMs() {
        long total = 0L;
        List<GarbageCollectorMXBean> beans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean bean : beans) total += Math.max(0L, bean.getCollectionTime());
        return total;
    }
}
