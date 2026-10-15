package crewx.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps the client's own images (category icons, font atlas, capes, server icons...) intact.
 *
 * If anything deletes the GL name of one of them (another mod, a resource reload, a driver that drops textures
 * when the game reconnects or the window changes), the next bind re-creates the name empty and the image is drawn
 * as a plain square; or the name is handed to another texture and the wrong picture shows up. After such moments
 * every watched texture is checked (GL name alive, still the size it was uploaded with, not shared with another
 * watched one) and a broken one is uploaded again from the pixels the DynamicTexture keeps, under a fresh name and
 * the same ResourceLocation, so every place that binds it by location is fixed at once. The old name is never
 * deleted: it may belong to someone else by now. Render thread only.
 */
public final class TextureGuard {
    private static final Map<ResourceLocation, Entry> WATCHED = new LinkedHashMap<ResourceLocation, Entry>();
    private static int repairs;

    private static final class Entry {
        DynamicTexture texture;
        final int width;
        final int height;
        final boolean linear;

        Entry(DynamicTexture texture, int width, int height, boolean linear) {
            this.texture = texture;
            this.width = width;
            this.height = height;
            this.linear = linear;
        }
    }

    private TextureGuard() {
    }

    /** Watches a texture registered at {@code location}; returns the location for chaining. */
    public static ResourceLocation watch(ResourceLocation location, DynamicTexture texture, int width, int height, boolean linear) {
        if (location != null && texture != null && width > 0 && height > 0) {
            WATCHED.put(location, new Entry(texture, width, height, linear));
        }
        return location;
    }

    public static void forget(ResourceLocation location) {
        if (location != null) WATCHED.remove(location);
    }

    /** Number of textures repaired since the game started (for diagnostics). */
    public static int repairs() {
        return repairs;
    }

    /** Checks soon (e.g. the ClickGUI opened). */
    public static void requestCheck() {
        scheduleChecks(System.currentTimeMillis());
    }

    private static Object lastWorld;
    private static Object lastPack;
    private static int lastWidth;
    private static int lastHeight;
    private static final long[] PENDING = new long[2];

    private static void scheduleChecks(long now) {
        PENDING[0] = now + 500L;
        PENDING[1] = now + 3000L;   // a second look once loading has settled
    }

    /**
     * Called once per frame. Checks only right after the moments textures get lost - joining or leaving a world or
     * server, a server resource pack being applied or removed, the window being resized - and not on a timer: the
     * check has to ask the driver for each texture's size, which makes the CPU wait for the GPU.
     */
    public static void tick() {
        if (WATCHED.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        long now = System.currentTimeMillis();
        Object world = mc.theWorld;
        Object pack = mc.getResourcePackRepository() == null ? null : mc.getResourcePackRepository().getResourcePackInstance();
        if (world != lastWorld || pack != lastPack || mc.displayWidth != lastWidth || mc.displayHeight != lastHeight) {
            lastWorld = world;
            lastPack = pack;
            lastWidth = mc.displayWidth;
            lastHeight = mc.displayHeight;
            scheduleChecks(now);
        }
        boolean due = false;
        for (int i = 0; i < PENDING.length; i++) {
            if (PENDING[i] != 0L && now >= PENDING[i]) {
                PENDING[i] = 0L;
                due = true;
            }
        }
        if (!due) return;
        try {
            check();
        } catch (Throwable ignored) {
            // never let a diagnostic break a frame
        }
    }

    private static void check() {
        TextureManager manager = Minecraft.getMinecraft().getTextureManager();
        if (manager == null) return;
        List<ResourceLocation> broken = null;
        Map<Integer, ResourceLocation> owners = new HashMap<Integer, ResourceLocation>();
        int activeUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            for (Map.Entry<ResourceLocation, Entry> watched : WATCHED.entrySet()) {
                Entry entry = watched.getValue();
                ITextureObject current = manager.getTexture(watched.getKey());
                boolean bad;
                if (current == null || current == TextureUtil.missingTexture) {
                    bad = true;                                   // unregistered behind our back
                } else if (current != entry.texture) {
                    continue;                                     // replaced on purpose by its owner: not ours to judge
                } else {
                    int id = current.getGlTextureId();
                    ResourceLocation other = owners.put(id, watched.getKey());
                    bad = other != null || !GL11.glIsTexture(id);
                    if (!bad) {
                        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
                        bad = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH) != entry.width
                                || GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT) != entry.height;
                    }
                    if (other != null) {
                        if (broken == null) broken = new ArrayList<ResourceLocation>();
                        if (!broken.contains(other)) broken.add(other);
                    }
                }
                if (bad) {
                    if (broken == null) broken = new ArrayList<ResourceLocation>();
                    if (!broken.contains(watched.getKey())) broken.add(watched.getKey());
                }
            }
        } finally {
            // the driver goes back to how it was; GlStateManager's cache never saw these binds
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
            GL13.glActiveTexture(activeUnit);
        }
        if (broken == null) return;
        for (ResourceLocation location : broken) repair(manager, location, WATCHED.get(location));
    }

    private static void repair(TextureManager manager, ResourceLocation location, Entry entry) {
        if (entry == null) return;
        int[] pixels = entry.texture.getTextureData();
        if (pixels == null || pixels.length < entry.width * entry.height) return;
        DynamicTexture fresh = new DynamicTexture(entry.width, entry.height);
        System.arraycopy(pixels, 0, fresh.getTextureData(), 0, entry.width * entry.height);
        fresh.updateDynamicTexture();
        if (entry.linear) {
            net.minecraft.client.renderer.GlStateManager.bindTexture(fresh.getGlTextureId());
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        }
        manager.loadTexture(location, fresh);   // same location: everything that binds it by name gets the fixed one
        entry.texture = fresh;
        repairs++;
        crewx.util.ClientLog.log("TextureGuard: " + location + " had lost its image on the GPU, uploaded again");
    }
}
