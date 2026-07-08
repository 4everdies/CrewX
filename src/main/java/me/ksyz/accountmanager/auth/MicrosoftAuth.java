package me.ksyz.accountmanager.auth;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import me.ksyz.accountmanager.utils.SSLUtils;
import net.minecraft.util.Session;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.conn.ssl.BrowserCompatHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public final class MicrosoftAuth {
    private static CloseableHttpClient createTrustedHttpClient() {
        try {
            SSLConnectionSocketFactory sf = new SSLConnectionSocketFactory(
                    SSLUtils.getSSLContext().getSocketFactory(),
                    new String[]{"TLSv1.2"},
                    null,
                    new BrowserCompatHostnameVerifier()
            );
            return HttpClientBuilder.create()
                    .setSSLSocketFactory(sf)
                    .build();
        } catch (Exception ignored) {

        }

        return HttpClients.createDefault();
    }

    private static final RequestConfig REQUEST_CONFIG = RequestConfig
            .custom()
            .setConnectionRequestTimeout(30_000)
            .setConnectTimeout(30_000)
            .setSocketTimeout(30_000)
            .build();

    public static String CLIENT_ID = "42a60a84-599d-44b2-a7c6-b00cdef1d6a2";
    public static String SCOPE = "XboxLive.signin XboxLive.offline_access";

    private static final int PORT = 25575;

    public static URI getMSAuthLink(String state) {
        try {

            URIBuilder uriBuilder = new URIBuilder("https://login.live.com/oauth20_authorize.srf")
                    .addParameter("client_id", CLIENT_ID)
                    .addParameter("response_type", "code")
                    .addParameter("redirect_uri", String.format("http://localhost:%d/callback", PORT))
                    .addParameter("scope", SCOPE)
                    .addParameter("state", state)
                    .addParameter("prompt", "select_account");
            return uriBuilder.build();
        } catch (Exception e) {
            return null;
        }
    }

    public static CompletableFuture<String> acquireMSAuthCode(String state, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {

                HttpServer server = HttpServer.create(
                        new InetSocketAddress(PORT), 0
                );

                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<String> authCode = new AtomicReference<>(null),
                        errorMsg = new AtomicReference<>(null);

                server.createContext("/callback", exchange -> {

                    Map<String, String> query = URLEncodedUtils
                            .parse(
                                    exchange.getRequestURI().toString().replaceAll("/callback\\?", ""),
                                    StandardCharsets.UTF_8
                            )
                            .stream()
                            .collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue));

                    if (!state.equals(query.get("state"))) {

                        errorMsg.set(
                                String.format("State mismatch! Expected '%s' but got '%s'.", state, query.get("state"))
                        );
                    } else if (query.containsKey("code")) {

                        authCode.set(query.get("code"));
                    } else if (query.containsKey("error")) {

                        errorMsg.set(String.format("%s: %s", query.get("error"), query.get("error_description")));
                    }

                    InputStream stream = MicrosoftAuth.class.getResourceAsStream("/callback.html");
                    byte[] response = stream != null ? IOUtils.toByteArray(stream) : new byte[0];
                    exchange.getResponseHeaders().add("Content-Type", "text/html");
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                    exchange.getResponseBody().close();

                    latch.countDown();
                });

                try {

                    server.start();

                    latch.await();

                    return Optional.ofNullable(authCode.get())
                            .filter(code -> !StringUtils.isBlank(code))

                            .orElseThrow(() -> new Exception(
                                    Optional.ofNullable(errorMsg.get())
                                            .orElse("There was no auth code or error description present.")
                            ));
                } finally {

                    server.stop(2);
                }
            } catch (InterruptedException e) {
                throw new CancellationException("Microsoft auth code acquisition was cancelled!");
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire Microsoft auth code!", e);
            }
        }, executor);
    }

    public static CompletableFuture<Map<String, String>> acquireMSAccessTokens(String authCode, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try (CloseableHttpClient client = createTrustedHttpClient()) {

                HttpPost request = new HttpPost(URI.create("https://login.live.com/oauth20_token.srf"));
                request.setConfig(REQUEST_CONFIG);
                request.setHeader("Content-Type", "application/x-www-form-urlencoded");
                request.setEntity(new UrlEncodedFormEntity(
                        Arrays.asList(
                                new BasicNameValuePair("client_id", CLIENT_ID),
                                new BasicNameValuePair("grant_type", "authorization_code"),
                                new BasicNameValuePair("code", authCode),

                                new BasicNameValuePair(
                                        "redirect_uri", String.format("http://localhost:%d/callback", PORT)
                                )
                        ),
                        "UTF-8"
                ));

                HttpResponse res = client.execute(request);

                JsonObject json = new JsonParser().parse(EntityUtils.toString(res.getEntity())).getAsJsonObject();
                String accessToken = Optional.ofNullable(json.get("access_token"))
                        .map(JsonElement::getAsString)
                        .filter(token -> !StringUtils.isBlank(token))
                        .orElseThrow(() -> new Exception(json.has("error") ?
                                String.format("%s: %s", json.get("error").getAsString(), json.get("error_description").getAsString()) :
                                "There was no Microsoft access token or error description present."
                        ));
                String refreshToken = Optional.ofNullable(json.get("refresh_token"))
                        .map(JsonElement::getAsString)
                        .filter(token -> !StringUtils.isBlank(token))
                        .orElseThrow(() -> new Exception(json.has("error") ?
                                String.format("%s: %s", json.get("error").getAsString(), json.get("error_description").getAsString()) :
                                "There was no Microsoft refresh token or error description present."
                        ));

                Map<String, String> result = new HashMap<>();
                result.put("access_token", accessToken);
                result.put("refresh_token", refreshToken);
                return result;
            } catch (InterruptedException e) {
                throw new CancellationException("Microsoft access tokens acquisition was cancelled!");
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire Microsoft access tokens!", e);
            }
        }, executor);
    }

    public static CompletableFuture<Map<String, String>> refreshMSAccessTokens(String msToken, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try (CloseableHttpClient client = createTrustedHttpClient()) {

                HttpPost request = new HttpPost(URI.create("https://login.live.com/oauth20_token.srf"));
                request.setConfig(REQUEST_CONFIG);
                request.setHeader("Content-Type", "application/x-www-form-urlencoded");
                request.setEntity(new UrlEncodedFormEntity(
                        Arrays.asList(
                                new BasicNameValuePair("client_id", CLIENT_ID),
                                new BasicNameValuePair("grant_type", "refresh_token"),
                                new BasicNameValuePair("refresh_token", msToken),

                                CLIENT_ID.equals("00000000402b5328") ? new BasicNameValuePair(
                                        "scope", SCOPE
                                ) : new BasicNameValuePair(
                                        "redirect_uri", String.format("http://localhost:%d/callback", PORT)
                                )
                        ),
                        "UTF-8"
                ));

                HttpResponse res = client.execute(request);

                JsonObject json = new JsonParser().parse(EntityUtils.toString(res.getEntity())).getAsJsonObject();
                String accessToken = Optional.ofNullable(json.get("access_token"))
                        .map(JsonElement::getAsString)
                        .filter(token -> !StringUtils.isBlank(token))
                        .orElseThrow(() -> new Exception(json.has("error") ?
                                String.format("%s: %s", json.get("error").getAsString(), json.get("error_description").getAsString()) :
                                "There was no Microsoft access token or error description present."
                        ));
                String refreshToken = Optional.ofNullable(json.get("refresh_token"))
                        .map(JsonElement::getAsString)
                        .filter(token -> !StringUtils.isBlank(token))
                        .orElseThrow(() -> new Exception(json.has("error") ?
                                String.format("%s: %s", json.get("error").getAsString(), json.get("error_description").getAsString()) :
                                "There was no Microsoft refresh token or error description present."
                        ));

                Map<String, String> result = new HashMap<>();
                result.put("access_token", accessToken);
                result.put("refresh_token", refreshToken);
                return result;
            } catch (InterruptedException e) {
                throw new CancellationException("Microsoft access tokens acquisition was cancelled!");
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire Microsoft access tokens!", e);
            }
        }, executor);
    }

    public static CompletableFuture<String> acquireXboxAccessToken(String accessToken, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try (CloseableHttpClient client = createTrustedHttpClient()) {

                HttpPost request = new HttpPost(URI.create("https://user.auth.xboxlive.com/user/authenticate"));
                JsonObject entity = new JsonObject();
                JsonObject properties = new JsonObject();
                properties.addProperty("AuthMethod", "RPS");
                properties.addProperty("SiteName", "user.auth.xboxlive.com");
                properties.addProperty("RpsTicket", CLIENT_ID.equals("00000000402b5328") ? accessToken : String.format("d=%s", accessToken));
                entity.add("Properties", properties);
                entity.addProperty("RelyingParty", "http://auth.xboxlive.com");
                entity.addProperty("TokenType", "JWT");
                request.setConfig(REQUEST_CONFIG);
                request.setHeader("Content-Type", "application/json");
                request.setEntity(new StringEntity(entity.toString()));

                HttpResponse res = client.execute(request);

                JsonObject json = res.getStatusLine().getStatusCode() == 200
                        ? new JsonParser().parse(EntityUtils.toString(res.getEntity())).getAsJsonObject()
                        : new JsonObject();

                return Optional.ofNullable(json.get("Token"))
                        .map(JsonElement::getAsString)
                        .filter(token -> !StringUtils.isBlank(token))

                        .orElseThrow(() -> new Exception(json.has("XErr") ?
                                String.format("%s: %s", json.get("XErr").getAsString(), json.get("Message").getAsString()) :
                                "There was no access token or error description present."
                        ));
            } catch (InterruptedException e) {
                throw new CancellationException("Xbox Live access token acquisition was cancelled!");
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire Xbox Live access token!", e);
            }
        }, executor);
    }

    public static CompletableFuture<Map<String, String>> acquireXboxXstsToken(String accessToken, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try (CloseableHttpClient client = createTrustedHttpClient()) {

                HttpPost request = new HttpPost("https://xsts.auth.xboxlive.com/xsts/authorize");
                JsonObject entity = new JsonObject();
                JsonObject properties = new JsonObject();
                JsonArray userTokens = new JsonArray();
                userTokens.add(new JsonPrimitive(accessToken));
                properties.addProperty("SandboxId", "RETAIL");
                properties.add("UserTokens", userTokens);
                entity.add("Properties", properties);
                entity.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
                entity.addProperty("TokenType", "JWT");
                request.setConfig(REQUEST_CONFIG);
                request.setHeader("Content-Type", "application/json");
                request.setEntity(new StringEntity(entity.toString()));

                HttpResponse res = client.execute(request);

                JsonObject json = res.getStatusLine().getStatusCode() == 200
                        ? new JsonParser().parse(EntityUtils.toString(res.getEntity())).getAsJsonObject()
                        : new JsonObject();
                return Optional.ofNullable(json.get("Token"))
                        .map(JsonElement::getAsString)
                        .filter(token -> !StringUtils.isBlank(token))

                        .map(token -> {

                            String uhs = json.get("DisplayClaims").getAsJsonObject()
                                    .get("xui").getAsJsonArray()
                                    .get(0).getAsJsonObject()
                                    .get("uhs").getAsString();

                            Map<String, String> result = new HashMap<>();
                            result.put("Token", token);
                            result.put("uhs", uhs);
                            return result;
                        })

                        .orElseThrow(() -> new Exception(json.has("XErr") ?
                                String.format("%s: %s", json.get("XErr").getAsString(), json.get("Message").getAsString()) :
                                "There was no access token or error description present."
                        ));
            } catch (InterruptedException e) {
                throw new CancellationException("Xbox Live XSTS token acquisition was cancelled!");
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire Xbox Live XSTS token!", e);
            }
        }, executor);
    }

    public static CompletableFuture<String> acquireMCAccessToken(String xstsToken, String userHash, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try (CloseableHttpClient client = createTrustedHttpClient()) {

                HttpPost request = new HttpPost(URI.create("https://api.minecraftservices.com/authentication/login_with_xbox"));
                request.setConfig(REQUEST_CONFIG);
                request.setHeader("Content-Type", "application/json");
                request.setEntity(new StringEntity(
                        String.format("{\"identityToken\": \"XBL3.0 x=%s;%s\"}", userHash, xstsToken)
                ));

                HttpResponse res = client.execute(request);

                JsonObject json = new JsonParser().parse(EntityUtils.toString(res.getEntity())).getAsJsonObject();

                return Optional.ofNullable(json.get("access_token"))
                        .map(JsonElement::getAsString)
                        .filter(token -> !StringUtils.isBlank(token))

                        .orElseThrow(() -> new Exception(json.has("error") ?
                                String.format("%s: %s", json.get("error").getAsString(), json.get("errorMessage").getAsString()) :
                                "There was no access token or error description present."
                        ));
            } catch (InterruptedException e) {
                throw new CancellationException("Minecraft access token acquisition was cancelled!");
            } catch (Exception e) {
                throw new CompletionException("Unable to acquire Minecraft access token!", e);
            }
        }, executor);
    }

    public static CompletableFuture<Session> login(String mcToken, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try (CloseableHttpClient client = createTrustedHttpClient()) {

                HttpGet request = new HttpGet(URI.create("https://api.minecraftservices.com/minecraft/profile"));
                request.setConfig(REQUEST_CONFIG);
                request.setHeader("Authorization", "Bearer " + mcToken);

                HttpResponse res = client.execute(request);

                JsonObject json = new JsonParser().parse(EntityUtils.toString(res.getEntity())).getAsJsonObject();
                return Optional.ofNullable(json.get("id"))
                        .map(JsonElement::getAsString)
                        .filter(uuid -> !StringUtils.isBlank(uuid))

                        .map(uuid -> new Session(
                                json.get("name").getAsString(),
                                uuid,
                                mcToken,
                                Session.Type.MOJANG.toString()
                        ))

                        .orElseThrow(() -> new Exception(json.has("error") ?
                                String.format("%s: %s", json.get("error").getAsString(), json.get("errorMessage").getAsString()) :
                                "There was no profile or error description present."
                        ));
            } catch (InterruptedException e) {
                throw new CancellationException("Minecraft profile fetching was cancelled!");
            } catch (Exception e) {
                throw new CompletionException("Unable to fetch Minecraft profile!", e);
            }
        }, executor);
    }
}