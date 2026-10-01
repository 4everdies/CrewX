package crewx.cosmetics;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;

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
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        registered = true;
    }

    @SubscribeEvent
    public void onPlayerRenderPre(RenderPlayerEvent.Pre event) {
        RenderPlayer renderer = event.renderer;
        if (renderer == null || !this.installedRenderers.add(renderer)) return;
        renderer.addLayer(new CosmeticLayer(renderer));
    }
}
