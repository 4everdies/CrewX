package crewx.cosmetics;

import net.minecraft.client.renderer.entity.RenderPlayer;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class CosmeticRenderHandler {
    public static final CosmeticRenderHandler INSTANCE = new CosmeticRenderHandler();
    private final Set<RenderPlayer> installedRenderers = Collections.newSetFromMap(new WeakHashMap<RenderPlayer, Boolean>());
    private static boolean registered;

    private CosmeticRenderHandler() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
    }

    public void onPlayerRenderPre(RenderPlayer renderer) {
        if (renderer == null) return;
        synchronized (this.installedRenderers) {
            if (this.installedRenderers.contains(renderer)) return;
            try {
                if (renderer.addLayer(new CosmeticLayer(renderer))) {
                    this.installedRenderers.add(renderer);
                }
            } catch (Throwable ignored) {
                // Retry on the next render instead of permanently losing the accessory layer.
            }
        }
    }
}
