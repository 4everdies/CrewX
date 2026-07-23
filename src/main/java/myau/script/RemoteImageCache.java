package myau.script;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Controlled asynchronous image loader exposed to Lua. It accepts only HTTP(S),
 * blocks local/private network targets, limits redirects and payload size, then
 * creates the DynamicTexture on Minecraft's render thread.
 */
public final class RemoteImageCache {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final int MAX_DIMENSION = 4096;
    private static final int MAX_REDIRECTS = 3;

    private static final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private static final ExecutorService executor = Executors.newFixedThreadPool(2, new ThreadFactory() {
        private int id;

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "CrewX-Image-" + (++id));
            thread.setDaemon(true);
            return thread;
        }
    });

    private RemoteImageCache() {
    }

    private enum State {
        LOADING,
        READY,
        ERROR
    }

    private static final class Entry {
        final String key;
        final String url;
        volatile State state = State.LOADING;
        volatile ResourceLocation texture;
        volatile String error;

        Entry(String key, String url) {
            this.key = key;
            this.url = url;
        }
    }

    public static boolean fetch(String rawKey, String rawUrl) {
        final String key = normalizeKey(rawKey);
        if (key == null || rawUrl == null || rawUrl.length() > 2048) {
            return false;
        }

        final URL url;
        try {
            url = validateUrl(new URL(rawUrl));
        } catch (Exception exception) {
            Entry bad = new Entry(key, String.valueOf(rawUrl));
            bad.state = State.ERROR;
            bad.error = cleanError(exception);
            entries.put(key, bad);
            return false;
        }

        Entry current = entries.get(key);
        if (current != null && current.url.equals(url.toString())
                && (current.state == State.LOADING || current.state == State.READY)) {
            return true;
        }

        Entry entry = new Entry(key, url.toString());
        Entry previous = entries.put(key, entry);
        deleteTexture(previous);
        executor.submit(() -> load(entry, url));
        return true;
    }

    public static String status(String rawKey) {
        String key = normalizeKey(rawKey);
        if (key == null) return "missing";
        Entry entry = entries.get(key);
        if (entry == null) {
            return "missing";
        }
        return entry.state.name().toLowerCase(Locale.ROOT);
    }

    public static String error(String rawKey) {
        String key = normalizeKey(rawKey);
        if (key == null) return "";
        Entry entry = entries.get(key);
        return entry == null || entry.error == null ? "" : entry.error;
    }

    public static ResourceLocation texture(String rawKey) {
        String key = normalizeKey(rawKey);
        if (key == null) return null;
        Entry entry = entries.get(key);
        return entry != null && entry.state == State.READY ? entry.texture : null;
    }

    public static boolean remove(String rawKey) {
        String key = normalizeKey(rawKey);
        if (key == null) return false;
        Entry removed = entries.remove(key);
        deleteTexture(removed);
        return removed != null;
    }

    public static void clear() {
        for (Entry entry : entries.values()) {
            deleteTexture(entry);
        }
        entries.clear();
    }

    private static void load(Entry entry, URL initialUrl) {
        try {
            byte[] bytes = download(initialUrl);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new IllegalArgumentException("unsupported or invalid image");
            }
            if (image.getWidth() <= 0 || image.getHeight() <= 0
                    || image.getWidth() > MAX_DIMENSION || image.getHeight() > MAX_DIMENSION) {
                throw new IllegalArgumentException("image dimensions exceed " + MAX_DIMENSION + "px");
            }

            mc.addScheduledTask(() -> {
                if (entries.get(entry.key) != entry) {
                    return;
                }
                try {
                    entry.texture = mc.getTextureManager().getDynamicTextureLocation(
                            "lua_" + entry.key,
                            new DynamicTexture(image));
                    entry.state = State.READY;
                } catch (Throwable throwable) {
                    entry.error = cleanError(throwable);
                    entry.state = State.ERROR;
                }
            });
        } catch (Throwable throwable) {
            entry.error = cleanError(throwable);
            entry.state = State.ERROR;
        }
    }

    private static byte[] download(URL initialUrl) throws Exception {
        URL current = initialUrl;
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            current = validateUrl(current);
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "CrewX-LuaImages/1.0");
            connection.setRequestProperty("Accept", "image/png,image/jpeg,image/gif,image/*;q=0.8");

            int code = connection.getResponseCode();
            if (code >= 300 && code < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || redirect == MAX_REDIRECTS) {
                    throw new IllegalArgumentException("too many or invalid redirects");
                }
                current = new URL(current, location);
                continue;
            }
            if (code < 200 || code >= 300) {
                connection.disconnect();
                throw new IllegalArgumentException("HTTP " + code);
            }

            String type = connection.getContentType();
            if (type != null && !type.toLowerCase(Locale.ROOT).startsWith("image/")) {
                connection.disconnect();
                throw new IllegalArgumentException("response is not an image");
            }
            int declared = connection.getContentLength();
            if (declared > MAX_BYTES) {
                connection.disconnect();
                throw new IllegalArgumentException("image exceeds 4 MiB");
            }

            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream(Math.max(1024, declared))) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    total += read;
                    if (total > MAX_BYTES) {
                        throw new IllegalArgumentException("image exceeds 4 MiB");
                    }
                    output.write(buffer, 0, read);
                }
                return output.toByteArray();
            } finally {
                connection.disconnect();
            }
        }
        throw new IllegalArgumentException("redirect failure");
    }

    private static URL validateUrl(URL url) throws Exception {
        String protocol = url.getProtocol().toLowerCase(Locale.ROOT);
        if (!"http".equals(protocol) && !"https".equals(protocol)) {
            throw new IllegalArgumentException("only http/https images are allowed");
        }
        if (url.getUserInfo() != null) {
            throw new IllegalArgumentException("URL credentials are not allowed");
        }
        String host = url.getHost();
        if (host == null || host.isEmpty() || "localhost".equalsIgnoreCase(host)
                || host.toLowerCase(Locale.ROOT).endsWith(".local")) {
            throw new IllegalArgumentException("invalid image host");
        }
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                throw new IllegalArgumentException("private/local image hosts are blocked");
            }
        }
        return url;
    }

    private static String normalizeKey(String key) {
        if (key == null) {
            return null;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        return normalized.isEmpty() || normalized.length() > 64 ? null : normalized;
    }

    private static void deleteTexture(Entry entry) {
        if (entry == null || entry.texture == null) {
            return;
        }
        ResourceLocation location = entry.texture;
        mc.addScheduledTask(() -> {
            try {
                mc.getTextureManager().deleteTexture(location);
            } catch (Throwable ignored) {
            }
        });
    }

    private static String cleanError(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = throwable.getClass().getSimpleName();
        }
        return message.length() > 180 ? message.substring(0, 180) : message;
    }
}
