package myau.accountmanager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.util.ResourceLocation;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/** maded by moonlight. */
public final class MushProfileService {
    private static final String API_URL = "https://mush.com.br/api/player/";
    private static final String HEAD_URL = "https://mc-heads.net/avatar/";
    private static final Map<String, Profile> CACHE = new ConcurrentHashMap<String, Profile>();
    private static final Map<String, ResourceLocation> HEADS = new ConcurrentHashMap<String, ResourceLocation>();
    private static final Map<String, Boolean> LOOKUPS_IN_FLIGHT = new ConcurrentHashMap<String, Boolean>();
    private static final Map<String, TimedPunishments> PUNISHMENT_CACHE = new ConcurrentHashMap<String, TimedPunishments>();
    private static final Map<String, Boolean> PUNISHMENT_LOOKUPS_IN_FLIGHT = new ConcurrentHashMap<String, Boolean>();
    private static final long PUNISHMENT_CACHE_TTL = 300000L;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "crewx-mush-profile");
            thread.setDaemon(true);
            return thread;
        }
    });

    private MushProfileService() {
    }

    public interface Callback {
        void onResult(Profile profile);
    }

    public interface StatsCallback {
        void onResult(Stats stats);
    }

    public interface TagsCallback {
        void onResult(List<String> tags);
    }

    public interface PunishmentsCallback {
        void onResult(Punishments punishments);
    }

    public static void lookup(final String username, final Callback callback) {
        final String normalized = normalize(username);
        if (normalized.isEmpty()) {
            dispatch(callback, null);
            return;
        }

        Profile cached = CACHE.get(normalized);
        if (cached != null) {
            dispatch(callback, cached);
            return;
        }

        if (LOOKUPS_IN_FLIGHT.putIfAbsent(normalized, Boolean.TRUE) != null) {
            return;
        }

        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                Profile profile = null;
                try {
                    profile = requestProfile(username);
                    if (profile != null) {
                        CACHE.put(normalized, profile);
                    }
                }
                catch (Exception ignored) {
                }
                finally {
                    LOOKUPS_IN_FLIGHT.remove(normalized);
                }
                dispatch(callback, profile);
            }
        });
    }

    public static ResourceLocation getHeadTexture(String hash) {
        String normalized = hash == null ? "" : hash.trim().toLowerCase();
        if (!normalized.matches("[a-z0-9_-]{8,128}")) {
            return null;
        }

        ResourceLocation existing = HEADS.get(normalized);
        if (existing != null) {
            return existing;
        }

        ResourceLocation location = new ResourceLocation("crewx", "mush_heads/" + normalized);
        try {
            Minecraft.getMinecraft().getTextureManager().loadTexture(
                    location,
                    new ThreadDownloadImageData(null, HEAD_URL + normalized, null, null)
            );
            HEADS.put(normalized, location);
            return location;
        }
        catch (Exception ignored) {
            return null;
        }
    }

    public static void fetchStats(final String username, final StatsCallback callback) {
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                Stats stats = null;
                try {
                    stats = requestStats(username);
                }
                catch (Exception ignored) {
                }
                dispatchStats(callback, stats);
            }
        });
    }

    public static void fetchTags(final String username, final TagsCallback callback) {
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                List<String> tags = new ArrayList<String>();
                try {
                    JsonObject root = requestPlayer(username);
                    JsonObject response = root == null ? null : getObject(root, "response");
                    JsonObject source = response == null ? root : response;
                    JsonObject account = source == null ? null : getObject(source, "account");
                    JsonElement value = account == null ? null : account.get("tags");
                    if (value == null && source != null) value = source.get("tags");
                    if (value != null && value.isJsonArray()) {
                        for (JsonElement entry : value.getAsJsonArray()) {
                            if (entry.isJsonPrimitive()) tags.add(entry.getAsString());
                            else if (entry.isJsonObject()) {
                                JsonObject tag = entry.getAsJsonObject();
                                String name = getString(tag, "name");
                                if (name.isEmpty()) name = getString(tag, "tag");
                                if (!name.isEmpty()) tags.add(name);
                            }
                        }
                    }
                }
                catch (Exception ignored) {
                }
                dispatchTags(callback, tags);
            }
        });
    }

    public static void fetchPunishments(final String username, final PunishmentsCallback callback) {
        final String normalized = normalize(username);
        if (normalized.isEmpty()) {
            dispatchPunishments(callback, null);
            return;
        }

        TimedPunishments cached = PUNISHMENT_CACHE.get(normalized);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.fetchedAt < PUNISHMENT_CACHE_TTL) {
            dispatchPunishments(callback, cached.value);
            return;
        }
        if (PUNISHMENT_LOOKUPS_IN_FLIGHT.putIfAbsent(normalized, Boolean.TRUE) != null) {
            return;
        }

        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                Punishments punishments = null;
                try {
                    JsonObject root = requestPlayer(username);
                    JsonObject response = root == null ? null : getObject(root, "response");
                    JsonObject source = response == null ? root : response;
                    if (source != null) {
                        punishments = new Punishments(
                                getNonNegativeInt(source, "ban_blacklist_count"),
                                getNonNegativeInt(source, "mute_blacklist_count")
                        );
                        PUNISHMENT_CACHE.put(normalized, new TimedPunishments(punishments, System.currentTimeMillis()));
                    }
                }
                catch (Exception ignored) {
                }
                finally {
                    PUNISHMENT_LOOKUPS_IN_FLIGHT.remove(normalized);
                }
                dispatchPunishments(callback, punishments);
            }
        });
    }

    private static Profile requestProfile(String username) throws Exception {
        JsonObject root = requestPlayer(username);
        if (root == null) {
            return null;
        }

        JsonObject response = getObject(root, "response");
        if (response == null) {
            response = root;
        }
        JsonObject account = getObject(response, "account");
        if (account == null) {
            account = response;
        }
        JsonObject skin = getObject(response, "skin");

        String resolvedName = getString(account, "username");
        String uuid = getString(account, "unique_id");
        if (uuid.isEmpty()) {
            uuid = getString(account, "uuid");
        }
        String hash = skin == null ? "" : getString(skin, "hash");
        boolean slim = skin != null && skin.has("slim") && skin.get("slim").getAsBoolean();
        return uuid.isEmpty() ? null : new Profile(resolvedName, uuid, hash, slim);
    }

    private static Stats requestStats(String username) throws Exception {
        JsonObject root = requestPlayer(username);
        if (root == null) {
            return null;
        }
        JsonObject response = getObject(root, "response");
        JsonObject source = response == null ? root : response;
        List<StatsCategory> categories = new ArrayList<StatsCategory>();
        List<Stat> general = new ArrayList<Stat>();
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            if ("stats".equals(entry.getKey()) && entry.getValue().isJsonObject()) {
                addGameCategories(entry.getValue().getAsJsonObject(), categories);
                continue;
            }
            if (entry.getValue().isJsonObject()) {
                List<Stat> values = new ArrayList<Stat>();
                flatten(entry.getValue().getAsJsonObject(), "", values);
                categories.add(new StatsCategory(formatName(entry.getKey()), values));
            }
            else {
                general.add(new Stat(entry.getKey(), formatName(entry.getKey()), format(entry.getValue(), entry.getKey())));
            }
        }
        if (!general.isEmpty()) {
            categories.add(0, new StatsCategory("General", general));
        }
        return new Stats(categories);
    }

    private static JsonObject requestPlayer(String username) throws Exception {
        String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8.name());
        HttpURLConnection connection = (HttpURLConnection) new URL(API_URL + encoded).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Accept", "application/json");

        try {
            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                return null;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            JsonElement parsed = new JsonParser().parse(reader);
            reader.close();
            if (!parsed.isJsonObject()) {
                return null;
            }

            JsonObject root = parsed.getAsJsonObject();
            if (root.has("success") && !root.get("success").getAsBoolean()) {
                return null;
            }
            return root;
        }
        finally {
            connection.disconnect();
        }
    }

    private static void dispatch(final Callback callback, final Profile profile) {
        if (callback == null) {
            return;
        }
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                callback.onResult(profile);
            }
        });
    }

    private static void dispatchStats(final StatsCallback callback, final Stats stats) {
        if (callback == null) {
            return;
        }
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                callback.onResult(stats);
            }
        });
    }

    private static void dispatchTags(final TagsCallback callback, final List<String> tags) {
        if (callback == null) return;
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                callback.onResult(tags);
            }
        });
    }

    private static void dispatchPunishments(final PunishmentsCallback callback, final Punishments punishments) {
        if (callback == null) return;
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                callback.onResult(punishments);
            }
        });
    }

    private static int getNonNegativeInt(JsonObject object, String name) {
        JsonElement element = object.get(name);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return 0;
        }
        try {
            return Math.max(0, element.getAsInt());
        }
        catch (Exception ignored) {
            return 0;
        }
    }

    private static void flatten(JsonObject object, String prefix, List<Stat> values) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            JsonElement value = entry.getValue();
            if (value.isJsonObject()) {
                flatten(value.getAsJsonObject(), key, values);
            }
            else {
                values.add(new Stat(key, formatName(displayKey(key)), format(value, key)));
            }
        }
    }

    private static void addGameCategories(JsonObject gameStats, List<StatsCategory> categories) {
        for (Map.Entry<String, JsonElement> entry : gameStats.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            List<Stat> values = new ArrayList<Stat>();
            String prefix = "stats." + entry.getKey();
            flatten(entry.getValue().getAsJsonObject(), prefix, values);
            categories.add(new StatsCategory(formatCategoryName(entry.getKey()), values));
        }
    }

    private static String format(JsonElement value, String key) {
        if (value == null || value.isJsonNull()) {
            return "null";
        }
        if (value.isJsonArray()) {
            StringBuilder formatted = new StringBuilder();
            for (JsonElement element : value.getAsJsonArray()) {
                if (formatted.length() > 0) {
                    formatted.append(", ");
                }
                formatted.append(element.isJsonPrimitive() ? element.getAsString() : element.toString());
            }
            return formatted.toString();
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
            return value.getAsBoolean() ? "Yes" : "No";
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            long number = value.getAsLong();
            if ("first_login".equals(key) || "last_login".equals(key)) {
                return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ROOT).format(new Date(number));
            }
            if (key.startsWith("play_time.") || key.startsWith("stats.play_time.")) {
                return formatDuration(number * 1000L);
            }
            if (key.endsWith("_best_time") || key.endsWith("_total_time")) {
                return formatDuration(number);
            }
            if (key.endsWith("fkdr")) {
                return String.format(Locale.ROOT, "%.2f", number / 100.0D);
            }
        }
        return value.getAsString();
    }

    private static String formatDuration(long milliseconds) {
        long totalSeconds = Math.max(0L, milliseconds / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = totalSeconds % 3600L / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.ROOT, "%dh %02dm", hours, minutes);
        }
        if (minutes > 0L) {
            return String.format(Locale.ROOT, "%dm %02ds", minutes, seconds);
        }
        return String.format(Locale.ROOT, "%ds", seconds);
    }

    private static String formatName(String key) {
        String words = key.replace('.', ' ').replace('_', ' ');
        StringBuilder formatted = new StringBuilder();
        for (String word : words.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            if (formatted.length() > 0) {
                formatted.append(' ');
            }
            if ("uuid".equalsIgnoreCase(word) || "id".equalsIgnoreCase(word) || "xp".equalsIgnoreCase(word)
                    || "fkdr".equalsIgnoreCase(word)) {
                formatted.append(word.toUpperCase(Locale.ROOT));
            }
            else {
                formatted.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return formatted.toString();
    }

    private static String formatCategoryName(String key) {
        if ("bedwars".equals(key)) return "Bed Wars";
        if ("bridgepractice".equals(key)) return "Bridge Practice";
        if ("hungergames".equals(key)) return "Hunger Games";
        if ("play_time".equals(key)) return "Play Time";
        if ("skywars_r1".equals(key)) return "Sky Wars";
        return formatName(key);
    }

    private static String displayKey(String key) {
        if (!key.startsWith("stats.")) {
            return key;
        }
        int firstDot = key.indexOf('.', "stats.".length());
        return firstDot == -1 ? key : key.substring(firstDot + 1);
    }

    private static JsonObject getObject(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String getString(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    private static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    public static final class Profile {
        private final String username;
        private final String uuid;
        private final String skinHash;
        private final boolean slim;

        private Profile(String username, String uuid, String skinHash, boolean slim) {
            this.username = username == null ? "" : username;
            this.uuid = uuid;
            this.skinHash = skinHash == null ? "" : skinHash;
            this.slim = slim;
        }

        public String getUsername() {
            return username;
        }

        public String getUuid() {
            return uuid;
        }

        public String getSkinHash() {
            return skinHash;
        }

        public boolean isSlim() {
            return slim;
        }
    }

    public static final class Punishments {
        private final int bans;
        private final int mutes;

        private Punishments(int bans, int mutes) {
            this.bans = bans;
            this.mutes = mutes;
        }

        public int getBans() {
            return bans;
        }

        public int getMutes() {
            return mutes;
        }
    }

    private static final class TimedPunishments {
        private final Punishments value;
        private final long fetchedAt;

        private TimedPunishments(Punishments value, long fetchedAt) {
            this.value = value;
            this.fetchedAt = fetchedAt;
        }
    }

    public static final class Stats {
        private final List<StatsCategory> categories;

        private Stats(List<StatsCategory> categories) {
            this.categories = categories;
        }

        public List<StatsCategory> getCategories() {
            return categories;
        }

        public String getValue(String id) {
            for (StatsCategory category : categories) {
                for (Stat stat : category.getValues()) {
                    if (stat.getId().equals(id)) {
                        return stat.getValue();
                    }
                }
            }
            return null;
        }
    }

    public static final class StatsCategory {
        private final String name;
        private final List<Stat> values;

        private StatsCategory(String name, List<Stat> values) {
            this.name = name;
            this.values = values;
        }

        public String getName() {
            return name;
        }

        public List<Stat> getValues() {
            return values;
        }
    }

    public static final class Stat {
        private final String id;
        private final String name;
        private final String value;

        private Stat(String id, String name, String value) {
            this.id = id;
            this.name = name;
            this.value = value;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }
    }
}
