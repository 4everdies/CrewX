package crewx.gui;

import crewx.module.ModuleCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

/** Draws the shared category pictograms used by the Click GUI and HUD ArrayList. */
public final class CategoryIconRenderer {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final Map<ModuleCategory, ResourceLocation> TEXTURES =
            new EnumMap<ModuleCategory, ResourceLocation>(ModuleCategory.class);
    private static final Map<ModuleCategory, Boolean> LOAD_ATTEMPTED =
            new EnumMap<ModuleCategory, Boolean>(ModuleCategory.class);

    private CategoryIconRenderer() {
    }

    public static void draw(ModuleCategory category, float x, float y, float size, int color) {
        if (category == null || size <= 0.0F) return;
        ResourceLocation texture = loadTexture(category);
        if (texture == null) return;
        int pixelSize = Math.max(1, Math.round(size));
        long timed = crewx.util.HitchMonitor.start();
        GlStateManager.pushMatrix();
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F,
                (color & 255) / 255.0F, (color >>> 24) / 255.0F);
        MC.getTextureManager().bindTexture(texture);
        Gui.drawModalRectWithCustomSizedTexture(Math.round(x), Math.round(y), 0.0F, 0.0F,
                pixelSize, pixelSize, (float) pixelSize, (float) pixelSize);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
        crewx.util.HitchMonitor.section("icons", timed);
    }

    /** GL names of the category icons loaded so far (for the ClickGUI texture self-check). */
    static void collectTextureIds(java.util.Set<Integer> out) {
        for (ResourceLocation location : TEXTURES.values()) {
            net.minecraft.client.renderer.texture.ITextureObject texture = MC.getTextureManager().getTexture(location);
            if (texture != null) out.add(texture.getGlTextureId());
        }
    }

    private static ResourceLocation loadTexture(ModuleCategory category) {
        if (Boolean.TRUE.equals(LOAD_ATTEMPTED.get(category))) return TEXTURES.get(category);
        LOAD_ATTEMPTED.put(category, Boolean.TRUE);
        String name = category.name().toLowerCase(java.util.Locale.ROOT);
        String resource = "/assets/crewx/icons/gui/" + name + ".png";
        try (InputStream stream = CategoryIconRenderer.class.getResourceAsStream(resource)) {
            if (stream == null) return null;
            BufferedImage image = ImageIO.read(stream);
            if (image == null) return null;
            DynamicTexture object = new DynamicTexture(image);
            ResourceLocation texture = MC.getTextureManager().getDynamicTextureLocation(
                    "crewx_category_icon_" + name, object);
            crewx.util.TextureGuard.watch(texture, object, image.getWidth(), image.getHeight(), true);
            MC.getTextureManager().bindTexture(texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            TEXTURES.put(category, texture);
            return texture;
        } catch (Exception ignored) {
            return null;
        }
    }
}
