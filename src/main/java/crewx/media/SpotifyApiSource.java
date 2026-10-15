package crewx.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Spotify Web API source (OAuth Authorization Code + PKCE, no client secret).
 *
 * Optional complement to {@link SystemMediaSource}: it sees playback on other devices (phone, speaker)
 * and gives exact cover art / explicit flag. Note: a Client ID in Spotify "Development Mode" only works
 * for up to 5 allow-listed accounts; everybody else gets HTTP 403, in which case this source switches
 * itself off and the system source keeps working.
 */
public final class SpotifyApiSource implements MediaSource {
    /** Put the Client ID of the Spotify app you registered here (see docs/media-setup.md). */
    public static final String BUILTIN_CLIENT_ID = "";

    private static final String API = "https://api.spotify.com/v1";
    private static final String TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token";
    private static final String SCOPES = "user-read-currently-playing user-modify-playback-state";
    /** Register ALL of these as Redirect URIs in the Spotify dashboard. */
    public static final int[] CALLBACK_PORTS = {8888, 8889, 8890};
    private static final int TIMEOUT = 8000;

    private final File authFile;
    private final Supplier<String> clientIdSupplier;
    private final Notifier notifier;
    private final ScheduledExecutorService worker = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "CrewX Spotify API");
        thread.setDaemon(true);
        return thread;
    });
    private final JsonParser parser = new JsonParser();

    private volatile Listener listener;
    private volatile boolean running;
    private volatile boolean active;
    private volatile boolean oauthInProgress;
    private volatile boolean blocked;
    private volatile boolean declined;
    private volatile String accessToken;
    private volatile String refreshToken;
    private volatile String tokenClientId = "";
    private volatile long accessTokenExpiresAt;
    private volatile long retryAfterAt;
    private volatile int errorStreak;
    private volatile MediaInfo lastInfo;
    private volatile String status = "Not connected";
    private ScheduledFuture<?> pollTask;

    public interface Notifier {
        void chat(String message);

        void link(String text, String url);
    }

    public SpotifyApiSource(File authFile, Supplier<String> clientIdSupplier, Notifier notifier) {
        this.authFile = authFile;
        this.clientIdSupplier = clientIdSupplier;
        this.notifier = notifier;
        loadTokens();
    }

    @Override
    public String getName() {
        return "Spotify";
    }

    @Override
    public synchronized void start(Listener listener) {
        this.listener = listener;
        this.running = true;
        scheduleNext(0L);
    }

    @Override
    public synchronized void stop() {
        this.running = false;
        this.active = false;
        if (this.pollTask != null) this.pollTask.cancel(false);
        this.pollTask = null;
    }

    /** Only polls the API while active (saves quota when the system source already knows the track). */
    public void setActive(boolean active) {
        if (this.active == active) return;
        this.active = active;
        if (active) scheduleNext(0L);
        else this.lastInfo = null;
    }

    public boolean hasClientId() {
        return !clientId().isEmpty();
    }

    public boolean isLinked() {
        return this.refreshToken != null && !this.refreshToken.isEmpty() && clientId().equals(this.tokenClientId);
    }

    public boolean isBlocked() {
        return this.blocked;
    }

    public boolean wasDeclined() {
        return this.declined;
    }

    public boolean isConnecting() {
        return this.oauthInProgress;
    }

    @Override
    public String getStatus() {
        return this.status;
    }

    private String clientId() {
        String configured = this.clientIdSupplier.get();
        configured = configured == null ? "" : configured.trim();
        return configured.isEmpty() ? BUILTIN_CLIENT_ID.trim() : configured;
    }

    private void setStatus(String text) {
        this.status = text;
        Listener current = this.listener;
        if (current != null) current.onStatus(this, text);
    }

    private synchronized void scheduleNext(long delayMs) {
        if (!this.running) return;
        if (this.pollTask != null) this.pollTask.cancel(false);
        this.pollTask = this.worker.schedule(this::pollOnce, Math.max(0L, delayMs), TimeUnit.MILLISECONDS);
    }

    @Override
    public void refreshNow() {
        scheduleNext(0L);
    }

    // ------------------------------------------------------------------ OAuth

    /** Opens the browser so the user can approve access. Safe to call repeatedly. */
    public synchronized void connect() {
        if (this.oauthInProgress) return;
        final String id = clientId();
        if (id.isEmpty()) {
            setStatus("Spotify login not configured");
            this.notifier.chat("&eSpotify login is not configured in this build. System media (Spotify app, "
                    + "browser, ...) is detected automatically without any login.&r");
            return;
        }
        this.declined = false;
        this.blocked = false;
        this.oauthInProgress = true;
        Thread thread = new Thread(() -> runOAuth(id), "CrewX Spotify OAuth");
        thread.setDaemon(true);
        thread.start();
    }

    private void runOAuth(String id) {
        setStatus("Waiting for browser authorization");
        ServerSocket server = null;
        try {
            int port = -1;
            for (int candidate : CALLBACK_PORTS) {
                try {
                    server = new ServerSocket();
                    server.setReuseAddress(true);
                    server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), candidate), 4);
                    port = candidate;
                    break;
                } catch (IOException busy) {
                    closeQuietly(server);
                    server = null;
                }
            }
            if (server == null) throw new IOException("no free callback port");
            String redirect = "http://127.0.0.1:" + port + "/callback";
            String verifier = HttpUtil.randomUrlSafe(64);
            String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.UTF_8)));
            String state = HttpUtil.randomUrlSafe(32);
            String url = "https://accounts.spotify.com/authorize?response_type=code"
                    + "&client_id=" + HttpUtil.urlEncode(id)
                    + "&scope=" + HttpUtil.urlEncode(SCOPES)
                    + "&code_challenge_method=S256&code_challenge=" + HttpUtil.urlEncode(challenge)
                    + "&state=" + HttpUtil.urlEncode(state)
                    + "&redirect_uri=" + HttpUtil.urlEncode(redirect);
            this.notifier.chat("&eSpotify: opening your browser to authorize...&r");
            if (!HttpUtil.openBrowser(url)) this.notifier.link("&a&nClick here to authorize Spotify", url);

            server.setSoTimeout(1000);
            long deadline = System.currentTimeMillis() + 300000L;
            while (System.currentTimeMillis() < deadline && this.running) {
                Socket socket;
                try {
                    socket = server.accept();
                } catch (java.net.SocketTimeoutException timeout) {
                    continue;
                }
                try {
                    if (handleCallback(socket, id, redirect, verifier, state)) return;
                } finally {
                    closeQuietly(socket);
                }
            }
            setStatus("Authorization timed out");
            this.notifier.chat("&cSpotify: authorization timed out.&r");
        } catch (Exception exception) {
            setStatus("Authorization failed");
            this.notifier.chat("&cSpotify: authorization failed (" + exception.getMessage() + ").&r");
        } finally {
            this.oauthInProgress = false;
            closeQuietly(server);
        }
    }

    /** @return true when the flow is finished (success, denial or error), false to keep listening. */
    private boolean handleCallback(Socket socket, String id, String redirect, String verifier, String state) throws Exception {
        socket.setSoTimeout(8000);
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        String requestLine = reader.readLine();
        String header;
        while ((header = reader.readLine()) != null && !header.isEmpty()) {
            // headers are not needed
        }
        String target = requestLine == null ? "/" : requestLine.split(" ")[1];
        if (!target.startsWith("/callback")) {
            respond(socket, 404, "<html><body>Not found</body></html>");
            return false;
        }
        int question = target.indexOf('?');
        Map<String, String> query = HttpUtil.parseQuery(question >= 0 ? target.substring(question + 1) : "");
        if (!state.equals(query.get("state"))) {
            respond(socket, 400, page("Falha na autorização", "A sessão não corresponde. Tente novamente no jogo."));
            setStatus("Authorization failed");
            return true;
        }
        if (query.containsKey("error")) {
            respond(socket, 200, page("Autorização cancelada", "Você pode fechar esta aba."));
            this.declined = true;
            saveTokens();
            setStatus("Authorization cancelled");
            this.notifier.chat("&cSpotify: authorization cancelled. System media still works.&r");
            return true;
        }
        String code = query.get("code");
        if (code == null || code.isEmpty()) {
            respond(socket, 400, page("Falha na autorização", "O Spotify não enviou o código."));
            setStatus("Authorization failed");
            return true;
        }
        respond(socket, 200, page("Spotify conectado", "Pronto! Pode voltar ao Minecraft e fechar esta aba."));
        Map<String, String> form = new HashMap<>();
        form.put("client_id", id);
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", redirect);
        form.put("code_verifier", verifier);
        HttpUtil.Result result = HttpUtil.request(TOKEN_ENDPOINT, "POST", form, null,
                "application/x-www-form-urlencoded", null, TIMEOUT);
        if (result.code < 200 || result.code >= 300) throw new IOException("token exchange HTTP " + result.code);
        applyTokens(parseObject(result.body), id);
        this.declined = false;
        this.blocked = false;
        this.errorStreak = 0;
        saveTokens();
        setStatus("Connected");
        this.notifier.chat("&aSpotify connected.&r");
        scheduleNext(0L);
        return true;
    }

    private void applyTokens(JsonObject json, String id) throws IOException {
        String access = string(json, "access_token", null);
        if (access == null || access.isEmpty()) throw new IOException("no access token");
        this.accessToken = access;
        this.refreshToken = string(json, "refresh_token", this.refreshToken);
        this.accessTokenExpiresAt = System.currentTimeMillis() + number(json, "expires_in", 3600L) * 1000L - 60000L;
        this.tokenClientId = id;
    }

    private boolean ensureToken() {
        if (this.accessToken != null && !this.accessToken.isEmpty()
                && System.currentTimeMillis() < this.accessTokenExpiresAt) return true;
        return refreshAccessToken();
    }

    private synchronized boolean refreshAccessToken() {
        String id = clientId();
        if (this.refreshToken == null || this.refreshToken.isEmpty() || id.isEmpty()) return false;
        try {
            Map<String, String> form = new HashMap<>();
            form.put("client_id", id);
            form.put("grant_type", "refresh_token");
            form.put("refresh_token", this.refreshToken);
            HttpUtil.Result result = HttpUtil.request(TOKEN_ENDPOINT, "POST", form, null,
                    "application/x-www-form-urlencoded", null, TIMEOUT);
            if (result.code == 400 || result.code == 401) {
                // refresh token revoked / belongs to another Client ID: the user must authorize again
                this.refreshToken = null;
                this.accessToken = null;
                saveTokens();
                return false;
            }
            if (result.code < 200 || result.code >= 300) return false;
            applyTokens(parseObject(result.body), id);
            saveTokens();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public synchronized void logout() {
        this.accessToken = null;
        this.refreshToken = null;
        this.accessTokenExpiresAt = 0L;
        this.lastInfo = null;
        this.blocked = false;
        this.declined = true; // do not auto-open the browser again until the user asks
        saveTokens(); // rewrites the file without tokens but with declined=true
        setStatus("Not connected");
        Listener current = this.listener;
        if (current != null) current.onMedia(this, null);
        this.notifier.chat("&aSpotify disconnected. Local tokens removed.&r");
    }

    // ------------------------------------------------------------------ polling

    private void pollOnce() {
        long next;
        try {
            next = poll();
        } catch (Exception exception) {
            next = failure("Spotify connection error");
        }
        scheduleNext(next);
    }

    private long poll() throws IOException {
        if (!this.running) return 5000L;
        if (!this.active) return 1500L;
        if (!isLinked()) {
            setStatus(hasClientId() ? "Not connected" : "Spotify login not configured");
            return 3000L;
        }
        if (this.blocked) return 30000L;
        long now = System.currentTimeMillis();
        if (now < this.retryAfterAt) return this.retryAfterAt - now;
        if (!ensureToken()) {
            setStatus("Authorization expired");
            if (this.refreshToken == null) {
                Listener current = this.listener;
                if (current != null) current.onMedia(this, null);
                return 10000L;
            }
            return failure("Spotify token refresh failed");
        }
        long requestedAt = System.currentTimeMillis();
        HttpUtil.Result response = HttpUtil.request(API + "/me/player/currently-playing?additional_types=episode",
                "GET", null, null, null, this.accessToken, TIMEOUT);
        if (response.code == 401 && refreshAccessToken()) {
            requestedAt = System.currentTimeMillis();
            response = HttpUtil.request(API + "/me/player/currently-playing?additional_types=episode",
                    "GET", null, null, null, this.accessToken, TIMEOUT);
        }
        long capturedAt = requestedAt + (System.currentTimeMillis() - requestedAt) / 2L;
        switch (response.code) {
            case 200:
                if (response.body.isEmpty()) return publishNothing();
                this.errorStreak = 0;
                return publish(parseObject(response.body), capturedAt);
            case 202:
            case 204:
                this.errorStreak = 0;
                return publishNothing();
            case 429: {
                boolean quota = response.body.contains("QUOTA_EXCEEDED");
                long wait = response.retryAfterSeconds > 0L ? response.retryAfterSeconds * 1000L : (quota ? 60000L : 5000L);
                this.retryAfterAt = System.currentTimeMillis() + wait;
                setStatus(quota ? "Spotify API quota exceeded" : "Spotify rate limit, retrying");
                return wait;
            }
            case 403:
                this.blocked = true;
                this.lastInfo = null;
                Listener current = this.listener;
                if (current != null) current.onMedia(this, null);
                setStatus("This Spotify account is not allowed by the app (Development Mode)");
                this.notifier.chat("&eSpotify: this account is not authorized for the app's API (Development Mode "
                        + "allows only 5 listed users). Using system media instead.&r");
                return 30000L;
            default:
                return failure("Spotify API error " + response.code);
        }
    }

    private long failure(String message) {
        this.errorStreak++;
        setStatus(message);
        MediaInfo last = this.lastInfo;
        if (last != null && this.errorStreak >= 4 && last.playing) {
            // can't confirm playback any more: freeze the card instead of running forever
            MediaInfo frozen = last.toBuilder().playing(false, last.positionNow()).build();
            this.lastInfo = frozen;
            Listener current = this.listener;
            if (current != null) current.onMedia(this, frozen);
        }
        return Math.min(60000L, 3000L << Math.min(5, this.errorStreak));
    }

    private long publishNothing() {
        this.lastInfo = null;
        setStatus("Nothing playing on Spotify");
        Listener current = this.listener;
        if (current != null) current.onMedia(this, null);
        return 10000L;
    }

    private long publish(JsonObject root, long capturedAt) {
        JsonElement itemElement = root.get("item");
        if (itemElement == null || itemElement.isJsonNull() || !itemElement.isJsonObject()) return publishNothing();
        JsonObject item = itemElement.getAsJsonObject();
        String type = string(item, "type", "track");
        MediaInfo.Builder info = MediaInfo.builder();
        info.title = string(item, "name", "");
        JsonObject container;
        if ("episode".equals(type) && item.has("show") && item.get("show").isJsonObject()) {
            container = item.getAsJsonObject("show");
            info.artist = string(container, "publisher", string(container, "name", ""));
            info.album = string(container, "name", "");
        } else {
            container = item.has("album") && item.get("album").isJsonObject() ? item.getAsJsonObject("album") : new JsonObject();
            info.artist = joinArtists(item.has("artists") && item.get("artists").isJsonArray() ? item.getAsJsonArray("artists") : null);
            info.album = string(container, "name", "");
        }
        JsonArray images = item.has("images") && item.get("images").isJsonArray() ? item.getAsJsonArray("images")
                : (container.has("images") && container.get("images").isJsonArray() ? container.getAsJsonArray("images") : null);
        info.artworkUrl = pickImage(images);
        info.key = "spotify:" + string(item, "id", info.title + "|" + info.artist);
        info.durationMs = number(item, "duration_ms", 0L);
        info.positionMs = number(root, "progress_ms", 0L);
        info.capturedAt = capturedAt;
        info.playing = bool(root, "is_playing", false);
        info.explicit = bool(item, "explicit", false);
        info.canSeek = info.durationMs > 0L;
        info.source = "Spotify";
        MediaInfo built = info.build();
        this.lastInfo = built;
        setStatus(built.playing ? "Playing on Spotify" : "Paused on Spotify");
        Listener current = this.listener;
        if (current != null) current.onMedia(this, built);
        if (!built.playing) return 8000L;
        long remaining = built.durationMs - built.positionNow();
        if (built.durationMs > 0L && remaining < 6000L) return Math.max(600L, remaining + 350L);
        return 5000L;
    }

    // ------------------------------------------------------------------ controls

    @Override
    public void control(MediaAction action, long argument) {
        this.worker.execute(() -> {
            try {
                if (!isLinked() || this.blocked || !ensureToken()) return;
                MediaInfo last = this.lastInfo;
                MediaAction effective = action;
                if (action == MediaAction.TOGGLE) {
                    effective = last != null && last.playing ? MediaAction.PAUSE : MediaAction.PLAY;
                }
                String path;
                String method;
                switch (effective) {
                    case PAUSE: path = "/me/player/pause"; method = "PUT"; break;
                    case PLAY: path = "/me/player/play"; method = "PUT"; break;
                    case NEXT: path = "/me/player/next"; method = "POST"; break;
                    case PREVIOUS: path = "/me/player/previous"; method = "POST"; break;
                    case SEEK: path = "/me/player/seek?position_ms=" + Math.max(0L, argument); method = "PUT"; break;
                    default: return;
                }
                HttpUtil.Result result = HttpUtil.request(API + path, method, null, null, null, this.accessToken, TIMEOUT);
                if (result.code == 401 && refreshAccessToken()) {
                    result = HttpUtil.request(API + path, method, null, null, null, this.accessToken, TIMEOUT);
                }
                if (result.code >= 200 && result.code < 300) {
                    if (last != null && (effective == MediaAction.PAUSE || effective == MediaAction.PLAY)) {
                        MediaInfo optimistic = last.toBuilder().playing(effective == MediaAction.PLAY, last.positionNow()).build();
                        this.lastInfo = optimistic;
                        Listener current = this.listener;
                        if (current != null) current.onMedia(this, optimistic);
                    }
                    scheduleNext(effective == MediaAction.NEXT || effective == MediaAction.PREVIOUS ? 700L : 900L);
                } else if (result.code == 403) {
                    this.notifier.chat("&cSpotify: control needs Premium and an active device.&r");
                } else if (result.code == 429) {
                    this.retryAfterAt = System.currentTimeMillis() + Math.max(2, result.retryAfterSeconds) * 1000L;
                }
            } catch (Exception ignored) {
                setStatus("Spotify control error");
            }
        });
    }

    // ------------------------------------------------------------------ storage / json helpers

    private void loadTokens() {
        if (!this.authFile.isFile()) return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(this.authFile), StandardCharsets.UTF_8))) {
            JsonObject json = this.parser.parse(reader).getAsJsonObject();
            this.refreshToken = string(json, "refresh_token", null);
            this.accessToken = string(json, "access_token", null);
            this.accessTokenExpiresAt = number(json, "expires_at", 0L);
            this.tokenClientId = string(json, "client_id", "");
            this.declined = bool(json, "declined", false);
        } catch (Exception ignored) {
            this.status = "Could not read the saved Spotify login";
        }
    }

    private synchronized void saveTokens() {
        try {
            File parent = this.authFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) return;
            JsonObject json = new JsonObject();
            if (this.accessToken != null) json.addProperty("access_token", this.accessToken);
            if (this.refreshToken != null) json.addProperty("refresh_token", this.refreshToken);
            json.addProperty("expires_at", this.accessTokenExpiresAt);
            json.addProperty("client_id", this.tokenClientId);
            json.addProperty("declined", this.declined);
            try (OutputStream output = new FileOutputStream(this.authFile)) {
                output.write(json.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    private JsonObject parseObject(String body) {
        return this.parser.parse(body).getAsJsonObject();
    }

    private static String pickImage(JsonArray images) {
        if (images == null || images.size() == 0) return "";
        String best = "";
        long bestWidth = Long.MAX_VALUE;
        String largest = "";
        long largestWidth = -1L;
        for (JsonElement element : images) {
            if (element == null || !element.isJsonObject()) continue;
            JsonObject image = element.getAsJsonObject();
            String url = string(image, "url", "");
            if (url.isEmpty()) continue;
            long width = number(image, "width", 0L);
            if (width > largestWidth) {
                largestWidth = width;
                largest = url;
            }
            if (width >= 256L && width < bestWidth) {
                bestWidth = width;
                best = url;
            }
        }
        return best.isEmpty() ? largest : best;
    }

    private static String joinArtists(JsonArray artists) {
        if (artists == null || artists.size() == 0) return "";
        StringBuilder value = new StringBuilder();
        for (JsonElement artist : artists) {
            if (artist != null && artist.isJsonObject()) {
                if (value.length() > 0) value.append(", ");
                value.append(string(artist.getAsJsonObject(), "name", ""));
            }
        }
        return value.toString();
    }

    private static String string(JsonObject object, String key, String fallback) {
        try {
            JsonElement element = object.get(key);
            return element == null || element.isJsonNull() ? fallback : element.getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long number(JsonObject object, String key, long fallback) {
        try {
            JsonElement element = object.get(key);
            return element == null || element.isJsonNull() ? fallback : element.getAsLong();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        try {
            JsonElement element = object.get(key);
            return element == null || element.isJsonNull() ? fallback : element.getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static void respond(Socket socket, int code, String html) throws IOException {
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        OutputStream output = socket.getOutputStream();
        output.write(("HTTP/1.1 " + code + (code == 200 ? " OK" : " Error")
                + "\r\nContent-Type: text/html; charset=utf-8\r\nConnection: close\r\nContent-Length: "
                + body.length + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(body);
        output.flush();
    }

    private static String page(String title, String message) {
        return "<html><head><meta charset='utf-8'><title>CrewX</title></head>"
                + "<body style='font-family:Segoe UI,sans-serif;background:#101116;color:#fff;padding:40px'>"
                + "<h2>" + title + "</h2><p>" + message + "</p></body></html>";
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }
}
