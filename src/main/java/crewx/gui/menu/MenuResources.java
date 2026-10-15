package crewx.gui.menu;

import crewx.event.EventTarget;
import crewx.events.Render2DEvent;

/**
 * Frees the menu textures (~25 MB of GPU memory) while playing: Render2DEvent only fires in game, so once the
 * menus have not been drawn for a while their textures are released; they reload in the background next time.
 */
public final class MenuResources {
    private static final long IDLE_MS = 15000L;
    private long lastCheck;

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        crewx.util.TextureGuard.tick();   // also covers builds where the per-frame mixin hook is not present
        long now = System.currentTimeMillis();
        if (now - this.lastCheck < 2000L) return;
        this.lastCheck = now;
        MenuTextures.releaseIdle(IDLE_MS);
    }
}
