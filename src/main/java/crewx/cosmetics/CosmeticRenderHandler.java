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
        if (renderer == null || !this.installedRenderers.add(renderer)) return;
        renderer.addLayer(new CosmeticLayer(renderer));
    }
}
