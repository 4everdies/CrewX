package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;

import java.util.Random;

public class Fakelag extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Random RANDOM = new Random();

    public final IntProperty minDelay = new IntProperty("min-delay", 100, 0, 2000);
    public final IntProperty maxDelay = new IntProperty("max-delay", 200, 0, 2000);

    private int currentDelay = 0;
    private long lastRoll = 0L;

    public Fakelag() {
        super("Fakelag", false);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;
        long now = System.currentTimeMillis();
        if (now - lastRoll >= currentDelay || currentDelay == 0) {
            int min = Math.min(minDelay.getValue(), maxDelay.getValue());
            int max = Math.max(minDelay.getValue(), maxDelay.getValue());
            currentDelay = max <= min ? min : min + RANDOM.nextInt(max - min + 1);
            lastRoll = now;
        }
        Myau.lagManager.setDelay(currentDelay / 50);
    }

    @Override
    public void onDisabled() {
        Myau.lagManager.setDelay(0);
        currentDelay = 0;
        lastRoll = 0L;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{minDelay.getValue() + "-" + maxDelay.getValue() + "ms"};
    }
}