package myau.accountmanager.utils;

import com.mojang.authlib.Agent;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import java.net.Proxy;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlayerSkinCache {
    private static final long PROFILE_CACHE_TTL_MS = 30L * 60L * 1000L;

    private static final class CachedProfile {
        private final GameProfile profile;
        private final long expiresAtMs;

        private CachedProfile(GameProfile profile, long expiresAtMs) {
            this.profile = profile;
            this.expiresAtMs = expiresAtMs;
        }

        private boolean isExpired(long now) {
            return now >= expiresAtMs;
        }
    }

    private static final Map<String, ResourceLocation> SKINS = new ConcurrentHashMap<String, ResourceLocation>();
    private static final Map<String, UUID> UUIDS = new ConcurrentHashMap<String, UUID>();
    private static final Map<String, CachedProfile> PROFILES = new ConcurrentHashMap<String, CachedProfile>();
    private static final Set<String> LOOKUP_IN_FLIGHT = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Set<String> LOOKUP_FAILED = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final GameProfileRepository PROFILE_REPOSITORY = new YggdrasilAuthenticationService(Proxy.NO_PROXY, UUID.randomUUID().toString()).createProfileRepository();
    private static final ExecutorService LOOKUP_EXECUTOR = Executors.newFixedThreadPool(3);

    private PlayerSkinCache() {
    }

    public static ResourceLocation getSkin(String username, NetworkPlayerInfo playerInfo) {
        String normalized = normalize(username);
        if (normalized.isEmpty()) {
            return DefaultPlayerSkin.getDefaultSkin(EntityPlayer.getOfflineUUID("Steve"));
        }

        if (playerInfo != null && playerInfo.getGameProfile() != null) {
            GameProfile profile = playerInfo.getGameProfile();
            UUID uuid = profile.getId();
            if (uuid != null) {
                UUIDS.put(normalized, uuid);
            }
//            if (profile.getName() != null && Moonlight.playerRelationsManager != null) {
//                Moonlight.playerRelationsManager.refreshDisplayName(profile.getName());
//            }
            ResourceLocation location = playerInfo.getLocationSkin();
            if (location != null) {
                SKINS.put(normalized, location);
                return location;
            }
        }

        ResourceLocation cached = SKINS.get(normalized);
        if (cached != null) {
            return cached;
        }

        if (!LOOKUP_FAILED.contains(normalized)) {
            requestSkin(normalized, username);
        }

        UUID fallbackUuid = UUIDS.get(normalized);
        if (fallbackUuid == null) {
            fallbackUuid = EntityPlayer.getOfflineUUID(username);
            UUIDS.put(normalized, fallbackUuid);
        }
        return DefaultPlayerSkin.getDefaultSkin(fallbackUuid);
    }

    private static void requestSkin(final String normalized, final String username) {
        long now = System.currentTimeMillis();
        CachedProfile cachedProfile = PROFILES.get(normalized);
        if (cachedProfile != null && !cachedProfile.isExpired(now)) {
            if (cachedProfile.profile == null) {
                LOOKUP_FAILED.add(normalized);
                return;
            }
            resolveSkinFromProfile(normalized, cachedProfile.profile);
            return;
        }

        if (!LOOKUP_IN_FLIGHT.add(normalized)) {
            return;
        }

        LOOKUP_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final GameProfile[] holder = new GameProfile[1];
                    PROFILE_REPOSITORY.findProfilesByNames(new String[]{username}, Agent.MINECRAFT, new ProfileLookupCallback() {
                        @Override
                        public void onProfileLookupSucceeded(GameProfile profile) {
                            holder[0] = profile;
                        }

                        @Override
                        public void onProfileLookupFailed(GameProfile profile, Exception exception) {
                        }
                    });

                    GameProfile profile = holder[0];
                    if (profile == null) {
                        cacheProfile(normalized, null);
                        LOOKUP_FAILED.add(normalized);
                        return;
                    }

                    Minecraft minecraft = Minecraft.getMinecraft();
                    GameProfile filledProfile = minecraft.getSessionService().fillProfileProperties(profile, false);
                    if (filledProfile == null) {
                        cacheProfile(normalized, null);
                        LOOKUP_FAILED.add(normalized);
                        return;
                    }

                    cacheProfile(normalized, filledProfile);
                    resolveSkinFromProfile(normalized, filledProfile);
                }
                catch (Exception ignored) {
                    cacheProfile(normalized, null);
                    LOOKUP_FAILED.add(normalized);
                }
                finally {
                    LOOKUP_IN_FLIGHT.remove(normalized);
                }
            }
        });
    }

    private static void resolveSkinFromProfile(final String normalized, final GameProfile profile) {
        if (profile == null) {
            LOOKUP_FAILED.add(normalized);
            return;
        }

        if (profile.getId() != null) {
            UUIDS.put(normalized, profile.getId());
        }
//        if (profile.getName() != null && Moonlight.playerRelationsManager != null) {
//            Moonlight.playerRelationsManager.refreshDisplayName(profile.getName());
//        }

        Minecraft minecraft = Minecraft.getMinecraft();
        Map<Type, MinecraftProfileTexture> textures = minecraft.getSkinManager().loadSkinFromCache(profile);
        final MinecraftProfileTexture skinTexture = textures == null ? null : textures.get(Type.SKIN);
        if (skinTexture == null) {
            LOOKUP_FAILED.add(normalized);
            return;
        }

        minecraft.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                ResourceLocation location = Minecraft.getMinecraft().getSkinManager().loadSkin(skinTexture, Type.SKIN);
                if (location != null) {
                    SKINS.put(normalized, location);
                    LOOKUP_FAILED.remove(normalized);
                }
                else {
                    LOOKUP_FAILED.add(normalized);
                }
            }
        });
    }

    private static void cacheProfile(String normalized, GameProfile profile) {
        PROFILES.put(normalized, new CachedProfile(profile, System.currentTimeMillis() + PROFILE_CACHE_TTL_MS));
    }

    private static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
