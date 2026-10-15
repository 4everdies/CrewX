package crewx.media;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;

/** Loads cover art from http(s) / file locations and bakes smooth rounded corners into it. */
public final class CoverBuilder {
    public static final int TEXTURE_SIZE = 192;
    public static final float RADIUS_RATIO = 0.18F;

    private CoverBuilder() {
    }

    public static BufferedImage load(String url) throws IOException {
        if (url == null || url.isEmpty()) throw new IOException("empty url");
        BufferedImage image;
        if (url.startsWith("file://")) {
            String clean = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
            image = ImageIO.read(new File(URI.create(clean)));
        } else if (url.startsWith("file:")) {
            String path = url.substring(5);
            int query = path.indexOf('?');
            if (query >= 0) path = path.substring(0, query);
            image = ImageIO.read(new File(path));
        } else {
            image = ImageIO.read(new ByteArrayInputStream(HttpUtil.download(normalizeUrl(url), 6000, 6000000)));
        }
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) throw new IOException("unreadable image");
        return image;
    }

    /**
     * Spotify's Linux client reports covers as https://open.spotify.com/image/&lt;id&gt;, a page that does not
     * serve the picture; the same id lives on the i.scdn.co image CDN.
     */
    static String normalizeUrl(String url) {
        String prefix = "https://open.spotify.com/image/";
        if (url.startsWith(prefix)) return "https://i.scdn.co/image/" + url.substring(prefix.length());
        return url;
    }

    /** Windows Spotify sometimes wraps the square cover in a black 300px tile with its wordmark below. */
    static BufferedImage extractSpotifyArtwork(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width != height || width < 100) return source;
        int side = Math.round(width * 0.787F);
        int margin = (width - side) / 2;
        int bottom = Math.round(height * 0.82F);
        int darkSides = 0;
        int sideSamples = 0;
        for (int y = 0; y < side; y += 4) {
            for (int x = 0; x < margin; x += 4) {
                if (isDark(source.getRGB(x, y))) darkSides++;
                if (isDark(source.getRGB(width - 1 - x, y))) darkSides++;
                sideSamples += 2;
            }
        }
        int darkBottom = 0;
        int lightBottom = 0;
        int bottomSamples = 0;
        for (int y = bottom; y < height; y += 2) {
            for (int x = 0; x < width; x += 2) {
                int rgb = source.getRGB(x, y);
                if (isDark(rgb)) darkBottom++;
                if (isLightNeutral(rgb)) lightBottom++;
                bottomSamples++;
            }
        }
        // Requiring both black side gutters and a white mark on a black footer avoids cropping normal albums.
        if (darkSides * 100 < sideSamples * 90 || darkBottom * 100 < bottomSamples * 70
                || lightBottom * 100 < bottomSamples * 3) return source;
        return source.getSubimage(margin, 0, side, side);
    }

    private static boolean isDark(int rgb) {
        return ((rgb >> 16) & 255) < 35 && ((rgb >> 8) & 255) < 35 && (rgb & 255) < 35;
    }

    private static boolean isLightNeutral(int rgb) {
        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = rgb & 255;
        return r > 180 && g > 180 && b > 180 && Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) < 35;
    }

    /** Centre-crops to a square, downsamples in steps (no aliasing) and masks rounded corners. */
    public static BufferedImage buildRounded(BufferedImage source) {
        final int target = TEXTURE_SIZE;
        int side = Math.min(source.getWidth(), source.getHeight());
        int cropX = (source.getWidth() - side) / 2;
        int cropY = (source.getHeight() - side) / 2;
        BufferedImage current = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D crop = current.createGraphics();
        crop.drawImage(source.getSubimage(cropX, cropY, side, side), 0, 0, null);
        crop.dispose();
        int size = side;
        while (size / 2 >= target) {
            int next = size / 2;
            BufferedImage half = new BufferedImage(next, next, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = half.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(current, 0, 0, next, next, null);
            g.dispose();
            current = half;
            size = next;
        }
        BufferedImage result = new BufferedImage(target, target, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        float arc = target * RADIUS_RATIO * 2.0F;
        g.setClip(new RoundRectangle2D.Float(0.0F, 0.0F, target, target, arc, arc));
        g.drawImage(current, 0, 0, target, target, null);
        g.dispose();
        return result;
    }
}
