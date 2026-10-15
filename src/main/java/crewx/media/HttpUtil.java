package crewx.media;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Small HTTP / OS helpers shared by the media sources. */
public final class HttpUtil {
    public static final String USER_AGENT = "CrewX/1.0 (https://github.com/4everdies/CrewX)";

    private HttpUtil() {
    }

    public static final class Result {
        public final int code;
        public final String body;
        public final long retryAfterSeconds;

        Result(int code, String body, long retryAfterSeconds) {
            this.code = code;
            this.body = body == null ? "" : body;
            this.retryAfterSeconds = retryAfterSeconds;
        }
    }

    public static Result request(String url, String method, Map<String, String> form, String body,
                                 String contentType, String bearerToken, int timeoutMs) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "application/json");
            if (bearerToken != null && !bearerToken.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
            }
            if (contentType != null) connection.setRequestProperty("Content-Type", contentType);
            byte[] payload = null;
            if (form != null) {
                StringBuilder data = new StringBuilder();
                for (Map.Entry<String, String> item : form.entrySet()) {
                    if (data.length() > 0) data.append('&');
                    data.append(urlEncode(item.getKey())).append('=').append(urlEncode(item.getValue()));
                }
                payload = data.toString().getBytes(StandardCharsets.UTF_8);
            } else if (body != null) {
                payload = body.getBytes(StandardCharsets.UTF_8);
            } else if ("PUT".equals(method) || "POST".equals(method)) {
                payload = new byte[0];
            }
            if (payload != null) {
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(payload.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(payload);
                }
            }
            int code = connection.getResponseCode();
            long retryAfter = 0L;
            String retry = connection.getHeaderField("Retry-After");
            if (retry != null) {
                try {
                    retryAfter = Long.parseLong(retry.trim());
                } catch (NumberFormatException ignored) {
                }
            }
            InputStream input = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String text = "";
            if (input != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                    StringBuilder output = new StringBuilder();
                    char[] buffer = new char[4096];
                    int read;
                    while ((read = reader.read(buffer)) != -1) output.append(buffer, 0, read);
                    text = output.toString();
                }
            }
            return new Result(code, text, retryAfter);
        } finally {
            connection.disconnect();
        }
    }

    public static Result get(String url, int timeoutMs) throws IOException {
        return request(url, "GET", null, null, null, null, timeoutMs);
    }

    public static byte[] download(String url, int timeoutMs, int maxBytes) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            if (connection.getResponseCode() >= 400) throw new IOException("HTTP " + connection.getResponseCode());
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(64 * 1024);
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    if (output.size() > maxBytes) throw new IOException("Image too large");
                }
                return output.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }

    public static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception ignored) {
            return value;
        }
    }

    public static Map<String, String> parseQuery(String query) {
        Map<String, String> values = new HashMap<>();
        if (query == null || query.isEmpty()) return values;
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            try {
                values.put(URLDecoder.decode(parts[0], "UTF-8"),
                        parts.length > 1 ? URLDecoder.decode(parts[1], "UTF-8") : "");
            } catch (Exception ignored) {
            }
        }
        return values;
    }

    public static String randomUrlSafe(int length) {
        final char[] chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~".toCharArray();
        SecureRandom random = new SecureRandom();
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) result.append(chars[random.nextInt(chars.length)]);
        return result.toString();
    }

    public static String sha1(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format(Locale.ROOT, "%02x", b));
            return hex.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(text.hashCode());
        }
    }

    public static String osName() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    }

    /** Opens the default browser. Returns false if nothing worked (caller should show a clickable link). */
    public static boolean openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return true;
            }
        } catch (Exception ignored) {
        }
        try {
            String os = osName();
            ProcessBuilder builder;
            if (os.contains("win")) builder = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
            else if (os.contains("mac")) builder = new ProcessBuilder("open", url);
            else builder = new ProcessBuilder("xdg-open", url);
            builder.start();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
