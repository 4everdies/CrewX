package crewx.gui.menu;

import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MenuAssetsTest {
    private static BufferedImage read(String name) throws Exception {
        try (InputStream in = MenuAssetsTest.class.getResourceAsStream("/assets/crewx/menu/" + name)) {
            assertNotNull(name + " must be packaged", in);
            return ImageIO.read(in);
        }
    }

    @Test
    public void backgroundLayersArePackagedAndSixteenByNine() throws Exception {
        for (String name : new String[]{"space.jpg", "earth.jpg", "earth_alpha.png", "glare.jpg", "vignette.png"}) {
            BufferedImage image = read(name);
            assertNotNull(name, image);
            assertEquals(name + " aspect", 16.0 / 9.0, image.getWidth() / (double) image.getHeight(), 0.01);
        }
        BufferedImage earth = read("earth.jpg");
        BufferedImage alpha = read("earth_alpha.png");
        assertEquals(earth.getWidth(), alpha.getWidth());
        assertEquals(earth.getHeight(), alpha.getHeight());
    }

    @Test
    public void earthLayerIsTransparentInSpaceAndOpaqueOnThePlanet() throws Exception {
        BufferedImage combined = MenuTextures.withAlpha(read("earth.jpg"), read("earth_alpha.png"), false);
        int w = combined.getWidth();
        int h = combined.getHeight();
        assertTrue("top of the frame is space", (combined.getRGB(w / 2, h / 20) >>> 24) < 10);
        assertTrue("bottom of the frame is the planet", (combined.getRGB(w / 2, h - 5) >>> 24) == 255);
    }

    @Test
    public void vignetteBecomesBlackWithAlpha() throws Exception {
        BufferedImage v = MenuTextures.withAlpha(null, read("vignette.png"), true);
        int corner = v.getRGB(0, 0);
        int centre = v.getRGB(v.getWidth() / 2, v.getHeight() / 2);
        assertEquals(0, corner & 0xFFFFFF);
        assertTrue((corner >>> 24) > (centre >>> 24));
    }

    @Test
    public void iconsArePackaged() throws Exception {
        for (String icon : new String[]{MenuIcons.SINGLEPLAYER, MenuIcons.MULTIPLAYER, MenuIcons.ALTS, MenuIcons.OPTIONS, MenuIcons.QUIT}) {
            BufferedImage image = read(icon);
            assertEquals(icon, 96, image.getWidth());
            assertTrue(icon + " has transparency", image.getColorModel().hasAlpha());
        }
    }

    @Test
    public void clockFormatAndMetrics() throws Exception {
        assertEquals("09:05", MenuClock.format(9, 5));
        assertEquals("15:54", MenuClock.format(15, 54));
        assertEquals("00:00", MenuClock.format(0, 0));
        try (InputStream in = MenuAssetsTest.class.getResourceAsStream("/assets/crewx/menu/clock.txt")) {
            MenuClock.parse(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
        }
        float wide = MenuClock.width("88:88", 80.0F);
        float narrow = MenuClock.width("11:11", 80.0F);
        assertTrue(wide > narrow && narrow > 0.0F);
        assertEquals(2.0F, MenuClock.width("88:88", 160.0F) / wide, 0.01F);
        assertTrue("a 80px clock is a few times wider than tall", wide > 160.0F && wide < 320.0F);
    }
}
