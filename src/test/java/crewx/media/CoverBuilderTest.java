package crewx.media;

import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CoverBuilderTest {
    @Test
    public void spotifyLinuxCoverUrlPointsToTheImageCdn() {
        assertEquals("https://i.scdn.co/image/ab67616d0000b273abc",
                CoverBuilder.normalizeUrl("https://open.spotify.com/image/ab67616d0000b273abc"));
        assertEquals("https://i.scdn.co/image/x", CoverBuilder.normalizeUrl("https://i.scdn.co/image/x"));
    }

    @Test
    public void loadsVersionedWindowsCoverFileAndBuildsTexture() throws Exception {
        File file = File.createTempFile("cover", ".img.3");
        file.deleteOnExit();
        BufferedImage source = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(source, "jpg", file);
        BufferedImage built = CoverBuilder.buildRounded(CoverBuilder.load("file:" + file.getAbsolutePath()));
        assertEquals(CoverBuilder.TEXTURE_SIZE, built.getWidth());
        assertEquals(CoverBuilder.TEXTURE_SIZE, built.getHeight());
    }

    @Test
    public void removesOnlySpotifyTileBrandingAndKeepsSquareCover() {
        BufferedImage tile = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 236; y++) {
            for (int x = 32; x < 268; x++) tile.setRGB(x, y, Color.MAGENTA.getRGB());
        }
        for (int y = 250; y < 280; y++) {
            for (int x = 60; x < 220; x++) tile.setRGB(x, y, Color.WHITE.getRGB());
        }
        BufferedImage cover = CoverBuilder.extractSpotifyArtwork(tile);
        assertEquals(236, cover.getWidth());
        assertEquals(236, cover.getHeight());
        assertEquals(Color.MAGENTA.getRGB(), cover.getRGB(118, 118));
        assertEquals(300, CoverBuilder.extractSpotifyArtwork(new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB)).getWidth());
    }

    @Test
    public void roundedCoverHasTransparentCorners() {
        BufferedImage source = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
        BufferedImage cover = CoverBuilder.buildRounded(source);
        assertEquals(0, (cover.getRGB(0, 0) >>> 24) & 255);
        assertTrue(((cover.getRGB(cover.getWidth() / 2, 0) >>> 24) & 255) > 200);
    }
}
