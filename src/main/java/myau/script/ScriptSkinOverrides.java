package myau.script;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cosmetic skin overrides controlled by Lua scripts.
 *
 * The map stores only a reference to a texture already managed by
 * RemoteImageCache. Nothing is uploaded and no server-visible profile data is
 * changed.
 */
public final class ScriptSkinOverrides {
    private static final Map<UUID, Override> overrides = new ConcurrentHashMap<>();

    private ScriptSkinOverrides() {
    }

    private static final class Override {
        final LuaScript owner;
        final String textureKey;
        final String model;

        Override(LuaScript owner, String textureKey, String model) {
            this.owner = owner;
            this.textureKey = textureKey;
            this.model = model;
        }
    }

    public static boolean set(LuaScript owner, EntityPlayer player, String textureKey, String model) {
        if (player == null || textureKey == null || textureKey.trim().isEmpty()) {
            return false;
        }
        String normalizedModel = normalizeModel(model);
        overrides.put(player.getUniqueID(), new Override(owner, textureKey, normalizedModel));
        return true;
    }

    public static boolean clear(EntityPlayer player) {
        return player != null && overrides.remove(player.getUniqueID()) != null;
    }

    public static void clearAll() {
        overrides.clear();
    }

    public static void clearOwnedBy(LuaScript owner) {
        if (owner == null) return;
        for (Map.Entry<UUID, Override> entry : overrides.entrySet()) {
            if (entry.getValue().owner == owner) {
                overrides.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    public static String getTextureKey(EntityPlayer player) {
        if (player == null) return "";
        Override override = overrides.get(player.getUniqueID());
        return override == null ? "" : override.textureKey;
    }

    public static ResourceLocation getTexture(AbstractClientPlayer player) {
        if (player == null) return null;
        Override override = overrides.get(player.getUniqueID());
        return override == null ? null : RemoteImageCache.texture(override.textureKey);
    }

    public static String getModel(AbstractClientPlayer player) {
        if (player == null) return null;
        Override override = overrides.get(player.getUniqueID());
        return override == null ? null : override.model;
    }

    private static String normalizeModel(String model) {
        if (model == null) return "default";
        String normalized = model.trim().toLowerCase(Locale.ROOT);
        return "slim".equals(normalized) || "alex".equals(normalized) ? "slim" : "default";
    }
}