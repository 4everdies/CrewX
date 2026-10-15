package crewx.gui.menu;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/** White menu icons (person, people, alts, options, quit) tinted at draw time. */
public final class MenuIcons {
    public static final String SINGLEPLAYER = "icon_singleplayer.png";
    public static final String MULTIPLAYER = "icon_multiplayer.png";
    public static final String ALTS = "icon_alts.png";
    public static final String OPTIONS = "icon_options.png";
    public static final String QUIT = "icon_quit.png";

    private MenuIcons() {
    }

    public static void draw(String icon, float centerX, float centerY, float size, int argb) {
        MenuTextures.Tex tex = MenuTextures.get(icon);
        if (tex == null || (argb >>> 24) == 0) return;
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.disableAlpha();
        MenuTextures.color(argb);
        MenuTextures.draw(tex, centerX - size / 2.0F, centerY - size / 2.0F, size, size);
        GlStateManager.enableAlpha();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
