package crewx.gui.menu;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Images of the menus (space background, clock digits, icons). Each one is decoded once on a background
 * thread, uploaded to the GPU on the render thread and its pixels are dropped from the heap right away;
 * {@link #releaseIdle} frees the GPU copies while the menus are not on screen (e.g. in game).
 */
public final class MenuTextures {
    private static final String ROOT = "/assets/crewx/menu/";
    private static final Map<String, Tex> TEXTURES = new HashMap<String, Tex>();
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "CrewX menu textures");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY + 1);
        return thread;
    });

    public static final class Tex {
        final String name;
        volatile BufferedImage pending;
        volatile boolean failed;
        int id = -1;
        public int width;
        public int height;
        long lastUsed;

        Tex(String name) {
            this.name = name;
        }
    }

    private MenuTextures() {
    }

    /** Render thread. Returns null until the texture is ready (callers draw a fallback meanwhile). */
    public static Tex get(String name) {
        Tex tex = TEXTURES.get(name);
        if (tex == null) {
            tex = new Tex(name);
            TEXTURES.put(name, tex);
            final Tex loading = tex;
            LOADER.execute(() -> decode(loading));
        }
        tex.lastUsed = System.currentTimeMillis();
        if (tex.id < 0 && tex.pending != null) upload(tex);
        return tex.id >= 0 ? tex : null;
    }

    /** Starts decoding without drawing anything yet (avoids a pop-in on the first frame). */
    public static void preload(String... names) {
        for (String name : names) get(name);
    }

    public static boolean ready(String... names) {
        for (String name : names) {
            Tex tex = TEXTURES.get(name);
            if (tex == null || tex.id < 0) return false;
        }
        return true;
    }

    /** Render thread: frees the GPU memory of textures not drawn for {@code idleMs}. */
    public static void releaseIdle(long idleMs) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Tex>> it = TEXTURES.entrySet().iterator();
        while (it.hasNext()) {
            Tex tex = it.next().getValue();
            if (tex.id >= 0 && now - tex.lastUsed > idleMs) {
                TextureUtil.deleteTexture(tex.id);
                tex.id = -1;
                it.remove();
            }
        }
    }

    private static void decode(Tex tex) {
        try {
            BufferedImage image;
            if (tex.name.equals("earth")) {
                image = withAlpha(read("earth.jpg"), read("earth_alpha.png"), false);
            } else if (tex.name.equals("vignette")) {
                image = withAlpha(null, read("vignette.png"), true);
            } else {
                image = read(tex.name);
            }
            tex.pending = image;
        } catch (Throwable failure) {
            tex.failed = true;
        }
    }

    private static BufferedImage read(String file) throws Exception {
        try (InputStream in = MenuTextures.class.getResourceAsStream(ROOT + file)) {
            if (in == null) throw new IllegalStateException("missing " + file);
            BufferedImage image = ImageIO.read(in);
            if (image == null) throw new IllegalStateException("unreadable " + file);
            return image;
        }
    }

    /** Colour from {@code rgb} (or black) with the alpha taken from a grayscale mask. */
    static BufferedImage withAlpha(BufferedImage rgb, BufferedImage mask, boolean black) {
        int w = mask.getWidth();
        int h = mask.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[w];
        int[] alpha = new int[w];
        for (int y = 0; y < h; y++) {
            mask.getRGB(0, y, w, 1, alpha, 0, w);
            if (!black) rgb.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                int a = alpha[x] & 0xFF;
                row[x] = a << 24 | (black ? 0 : row[x] & 0xFFFFFF);
            }
            out.setRGB(0, y, w, 1, row, 0, w);
        }
        return out;
    }

    private static void upload(Tex tex) {
        BufferedImage image = tex.pending;
        tex.pending = null;
        int id = TextureUtil.glGenTextures();
        TextureUtil.uploadTextureImageAllocate(id, image, true, true);
        tex.width = image.getWidth();
        tex.height = image.getHeight();
        tex.id = id;
    }

    /** Draws (part of) a texture; colour/alpha come from the current GL colour. */
    public static void draw(Tex tex, double x, double y, double w, double h, double u0, double v0, double u1, double v1) {
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(tex.id);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(x, y + h, 0.0D).tex(u0, v1).endVertex();
        wr.pos(x + w, y + h, 0.0D).tex(u1, v1).endVertex();
        wr.pos(x + w, y, 0.0D).tex(u1, v0).endVertex();
        wr.pos(x, y, 0.0D).tex(u0, v0).endVertex();
        tessellator.draw();
    }

    public static void draw(Tex tex, double x, double y, double w, double h) {
        draw(tex, x, y, w, h, 0.0D, 0.0D, 1.0D, 1.0D);
    }

    public static void color(int argb) {
        GlStateManager.color((argb >> 16 & 255) / 255.0F, (argb >> 8 & 255) / 255.0F,
                (argb & 255) / 255.0F, (argb >>> 24 & 255) / 255.0F);
    }
}
