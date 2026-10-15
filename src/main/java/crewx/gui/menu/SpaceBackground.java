package crewx.gui.menu;

import crewx.gui.GuiRender;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

/**
 * The space background of the menus: far stars, the Earth with its atmosphere and the rising sun, each a
 * pre-rendered texture moved by the {@link ParallaxCamera} with its own depth. Per frame this is four
 * textured quads and a few gradients - no shaders, no allocation.
 */
public final class SpaceBackground {
    private static final String SPACE = "space.jpg";
    private static final String EARTH = "earth";
    private static final String GLARE = "glare.jpg";
    private static final String VIGNETTE = "vignette";
    private static final float ASPECT = 16.0F / 9.0F;
    /** Where the top of the horizon sits inside the earth layer (matches the renderer script). */
    private static final float HORIZON_X = 0.5F;
    private static final float HORIZON_Y = 0.47F;

    private static final ParallaxCamera CAMERA = new ParallaxCamera();
    private static final float[] RECT = new float[4];
    private static long lastFrame;
    private static float fadeIn;

    private SpaceBackground() {
    }

    public static void render(int width, int height) {
        long now = System.nanoTime();
        float dt = lastFrame == 0L ? 0.0F : Math.min(0.1F, (now - lastFrame) / 1.0e9F);
        // coming back to a menu after a while: start from the current cursor without a long glide
        if (lastFrame != 0L && now - lastFrame > 1_000_000_000L) dt = 0.0F;
        lastFrame = now;
        updateCamera(dt);

        // deep space base (also what is visible while the textures are still loading)
        GuiRender.gradientV(0.0, 0.0, width, height, 0xFF04060C, 0xFF010103);

        MenuTextures.Tex space = MenuTextures.get(SPACE);
        MenuTextures.Tex earth = MenuTextures.get(EARTH);
        MenuTextures.Tex glare = MenuTextures.get(GLARE);
        MenuTextures.Tex vignette = MenuTextures.get(VIGNETTE);
        if (space == null || earth == null || glare == null) {
            // not uploaded yet, or released while playing (MenuResources) and reloading in the background:
            // draw only the base and fade the layers in again once they are back
            fadeIn = 0.0F;
            return;
        }
        fadeIn = Math.min(1.0F, fadeIn + dt * 2.2F);
        if (fadeIn <= 0.0F) return;
        float alpha = fadeIn * fadeIn * (3.0F - 2.0F * fadeIn);

        GlStateManager.pushMatrix();
        GlStateManager.disableAlpha();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        // far: stars barely move
        CAMERA.layer(width, height, ASPECT, 0.28F, 0.25F, 0.5F, 0.5F, RECT);
        MenuTextures.color(alpha(alpha));
        MenuTextures.draw(space, RECT[0], RECT[1], RECT[2], RECT[3]);

        // the sun is far behind the planet: a bit less parallax than the limb, so the two shift slightly
        GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, 1, 0);
        CAMERA.layer(width, height, ASPECT, 0.62F, 0.7F, HORIZON_X, HORIZON_Y, RECT);
        GlStateManager.color(alpha, alpha, alpha, 1.0F);
        MenuTextures.draw(glare, RECT[0], RECT[1], RECT[2], RECT[3]);

        // near: the planet, zooming around the horizon when the cursor goes up
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        CAMERA.layer(width, height, ASPECT, 1.0F, 1.0F, HORIZON_X, HORIZON_Y, RECT);
        MenuTextures.color(alpha(alpha));
        MenuTextures.draw(earth, RECT[0], RECT[1], RECT[2], RECT[3]);

        // sunlight blooms over the limb (second, softer pass of the glare on top of the planet)
        GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, 1, 0);
        CAMERA.layer(width, height, ASPECT, 0.62F, 0.7F, HORIZON_X, HORIZON_Y, RECT);
        GlStateManager.color(alpha * 0.55F, alpha * 0.55F, alpha * 0.55F, 1.0F);
        MenuTextures.draw(glare, RECT[0], RECT[1], RECT[2], RECT[3]);

        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        if (vignette != null) {
            MenuTextures.color(alpha(alpha));
            MenuTextures.draw(vignette, 0.0, 0.0, width, height);
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.popMatrix();

        // keeps text and buttons readable over the bright limb and the planet's surface
        GuiRender.gradientV(0.0, height * 0.55, width, height * 0.45 + 1.0, 0x00000000, GuiRender.alpha(0x8C000000, alpha));
    }

    private static int alpha(float a) {
        return Math.round(Math.max(0.0F, Math.min(1.0F, a)) * 255.0F) << 24 | 0xFFFFFF;
    }

    private static void updateCamera(float dt) {
        int dw = Math.max(1, Display.getWidth());
        int dh = Math.max(1, Display.getHeight());
        boolean inside = Mouse.isCreated() && Mouse.isInsideWindow();
        float mx = inside ? Mouse.getX() / (float) dw * 2.0F - 1.0F : 0.0F;
        float my = inside ? 1.0F - Mouse.getY() / (float) dh * 2.0F : 0.0F;
        CAMERA.update(mx, my, inside, dt);
    }

    /** Starts loading the textures early (e.g. when the client starts) so the first menu shows them at once. */
    public static void preload() {
        MenuTextures.preload(SPACE, EARTH, GLARE, VIGNETTE);
    }
}
