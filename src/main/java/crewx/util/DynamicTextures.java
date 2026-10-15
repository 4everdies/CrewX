package crewx.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;

/** Releasing textures registered in the {@link TextureManager} (album covers, script images...). */
public final class DynamicTextures {
    private DynamicTextures() {
    }

    /**
     * Frees the GPU texture and the pixel copy a DynamicTexture keeps on the heap. TextureManager.deleteTexture only
     * deletes the GL name: the object stays mapped forever with its pixels and a stale id that OpenGL hands out again
     * to the next new texture. Render thread only.
     */
    public static void release(ResourceLocation location) {
        if (location == null) return;
        TextureManager manager = Minecraft.getMinecraft().getTextureManager();
        ITextureObject texture = manager.getTexture(location);
        if (texture == null || texture == TextureUtil.missingTexture) return;
        if (texture instanceof AbstractTexture) {
            ((AbstractTexture) texture).deleteGlTexture();   // also forgets the id
        } else {
            TextureUtil.deleteTexture(texture.getGlTextureId());
        }
        manager.loadTexture(location, TextureUtil.missingTexture);   // drop the reference to the old pixels
    }
}
