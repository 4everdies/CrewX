package myau.accountmanager.auth;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/*
 * This file is derived from https://github.com/ksyzov/AccountManager.
 * Originally licensed under the GNU LGPL.
 *
 * This modified version is licensed under the GNU GPL v3.
 */
public class Account {
    public enum Type {
        MICROSOFT,
        CRACKED,
        COOKIE;

        public static Type fromStorage(String value, String refreshToken) {
            if (value != null) {
                try {
                    return Type.valueOf(value.toUpperCase());
                }
                catch (IllegalArgumentException ignored) {
                }
            }
            return "offline".equals(refreshToken) ? CRACKED : MICROSOFT;
        }
    }

    private String refreshToken;
    private String accessToken;
    private String username;
    private long unban;
    private String clientId;
    private String scope;
    private String uuid;
    private Type type;
    private String skinHash;
    private boolean skinSlim;

    public Account(String refreshToken, String accessToken, String username, String clientId, String scope) {
        this(refreshToken, accessToken, username, 0L, clientId, scope, "", Type.fromStorage(null, refreshToken), "", false);
    }

    public Account(String refreshToken, String accessToken, String username, long unban, String clientId, String scope) {
        this(refreshToken, accessToken, username, unban, clientId, scope, "", Type.fromStorage(null, refreshToken), "", false);
    }

    public Account(String refreshToken, String accessToken, String username, long unban, String clientId, String scope,
                   String uuid, Type type, String skinHash, boolean skinSlim) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.username = username;
        this.unban = unban;
        this.clientId = clientId;
        this.scope = scope;
        this.uuid = uuid == null ? "" : uuid;
        this.type = type == null ? Type.fromStorage(null, refreshToken) : type;
        this.skinHash = skinHash == null ? "" : skinHash;
        this.skinSlim = skinSlim;
    }

    public static Account cracked(String username) {
        return new Account("offline", "0", username, 0L, "", "", offlineUuid(username), Type.CRACKED, "", false);
    }

    public static Account cookie(String username, String uuid, String token) {
        return new Account("cookie", token, username, 0L, "", "", uuid, Type.COOKIE, "", false);
    }

    public String getClientId() {
        return clientId;
    }

    public String getScope() {
        return scope;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getUsername() {
        return username;
    }

    public long getUnban() {
        return unban;
    }

    public String getUuid() {
        if (uuid.isEmpty() && isCracked()) {
            return offlineUuid(username);
        }
        return uuid;
    }

    public Type getType() {
        return type;
    }

    public String getSkinHash() {
        return skinHash;
    }

    public boolean isSkinSlim() {
        return skinSlim;
    }

    public boolean isCracked() {
        return type == Type.CRACKED;
    }

    public boolean isCookie() {
        return type == Type.COOKIE;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setUnban(long unban) {
        this.unban = unban;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid == null ? "" : uuid;
    }

    public void setSkin(String skinHash, boolean skinSlim) {
        this.skinHash = skinHash == null ? "" : skinHash;
        this.skinSlim = skinSlim;
    }

    public static String offlineUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
    }
}
