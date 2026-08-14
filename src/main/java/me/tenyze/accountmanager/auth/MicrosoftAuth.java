package me.tenyze.accountmanager.auth;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import me.tenyze.accountmanager.utils.SSLUtils;
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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/*
 * This file is derived from https://github.com/tenyze/AccountManager.
 * Originally licensed under the GNU LGPL.
 *
 * This modified version is licensed under the GNU GPL v3.
 */
// Based on Auth Me (https://github.com/axieum/authme)
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
            //
        }

        return HttpClients.createDefault();
    }

    // A reusable Apache HTTP request config
    private static final RequestConfig REQUEST_CONFIG = RequestConfig
            .custom()
            .setConnectionRequestTimeout(30_000)
            .setConnectTimeout(30_000)
            .setSocketTimeout(30_000)
            .build();

    // Account Manager
    public static String CLIENT_ID = "42a60a84-599d-44b2-a7c6-b00cdef1d6a2";
    public static String SCOPE = "XboxLive.signin XboxLive.offline_access";

    // 25565 + 10
    private static final int PORT = 25575;

    public static URI getMSAuthLink(String state) {
        try {
            // Build a Microsoft login url
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
                // Prepare a temporary HTTP server we can listen for the OAuth2 callback on
                HttpServer server = HttpServer.create(
                        new InetSocketAddress(PORT), 0
                );

                // Track when a request has been handled
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<String> authCode = new AtomicReference<>(null),
                        errorMsg = new AtomicReference<>(null);

                server.createContext("/callback", exchange -> {
                    // Parse the query parameters
                    Map<String, String> query = URLEncodedUtils
                            .parse(
                                    exchange.getRequestURI().toString().replaceAll("/callback\\?", ""),
                                    StandardCharsets.UTF_8
                            )
                            .stream()
                            .collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue));

                    // Check the returned parameter values
                    if (!state.equals(query.get("state"))) {
                        // The "state" does not match what we sent
                        errorMsg.set(
                                String.format("State mismatch! Expected '%s' but got '%s'.", state, query.get("state"))
                        );
                    } else if (query.containsKey("code")) {
                        // Successfully matched the auth code
                        authCode.set(query.get("code"));
                    } else if (query.containsKey("error")) {
                        // Otherwise, try to find an error description
                        errorMsg.set(String.format("%s: %s", query.get("error"), query.get("error_description")));
                    }

                    // Send a response informing that the browser may now be closed
                    InputStream stream = MicrosoftAuth.class.getResourceAsStream("/callback.html");
                    byte[] response = stream != null ? IOUtils.toByteArray(stream) : new byte[0];
                    exchange.getResponseHeaders().add("Content-Type", "text/html");
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                    exchange.getResponseBody().close();

                    // Let the caller thread know that the request has been handled
                    latch.countDown();
                });

                try {
                    // Start the HTTP server (http://localhost:25575/callback)
                    server.start();

                    // Wait for the server to stop and return the auth code
                    latch.await();

                    // If present, return
                    return Optional.ofNullable(authCode.get())
                            .filter(code -> !StringUtils.isBlank(code))
                            // Otherwise, throw an exception with the error description (if present)
                            .orElseThrow(() -> new Exception(
                                    Optional.ofNullable(errorMsg.get())
                                            .orElse("There was no auth code or error description present.")
                            ));
                } finally {
                    // Always release the server
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
                // Build a new HTTP request
                HttpPost request = new HttpPost(URI.create("https://login.live.com/oauth20_token.srf"));
                request.setConfig(REQUEST_CONFIG);
                request.setHeader("Content-Type", "application/x-www-form-urlencoded");
                request.setEntity(new UrlEncodedFormEntity(
                        Arrays.asList(
                                new BasicNameValuePair("client_id", CLIENT_ID),
                                new BasicNameValuePair("grant_type", "authorization_code"),
                                new BasicNameValuePair("code", authCode),
                                // We must provide the exact redirect URI that was used to obtain the auth code
                                new BasicNameValuePair(
                                        "redirect_uri", String.format("http://localhost:%d/callback", PORT)
                                )
                        ),
                        "UTF-8"
                ));

                // Send the request on the HTTP client
                HttpResponse res = client.execute(request);

                // Attempt to parse the response body as JSON and extract the access and refresh tokens
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

                // Return an immutable mapping of the access and refresh tokens
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
                // Build a new HTTP request
                HttpPost request = new HttpPost(URI.create("https://login.live.com/oauth20_token.srf"));
                request.setConfig(REQUEST_CONFIG);
                request.setHeader("Content-Type", "application/x-www-form-urlencoded");
                request.setEntity(new UrlEncodedFormEntity(
                        Arrays.asList(
                                new BasicNameValuePair("client_id", CLIENT_ID),
                                new BasicNameValuePair("grant_type", "refresh_token"),
                                new BasicNameValuePair("refresh_token", msToken),
                                // We must provide the exact redirect URI that was used to obtain the auth code
                                CLIENT_ID.equals("00000000402b5328") ? new BasicNameValuePair(
                                        "scope", SCOPE
                                ) : new BasicNameValuePair(
                                        "redirect_uri", String.format("http://localhost:%d/callback", PORT)
                                )
                        ),
                        "UTF-8"
                ));

                // Send the request on the HTTP client
                HttpResponse res = client.execute(request);

                // Attempt to parse the response body as JSON and extract the access and refresh tokens
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

                // Return an immutable mapping of the access and refresh tokens
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
                // Build a new HTTP request
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

                // Send the request on the HTTP client
                HttpResponse res = client.execute(request);

                // Attempt to parse the response body as JSON and extract the access token
                JsonObject json = res.getStatusLine().getStatusCode() == 200
                        ? new JsonParser().parse(EntityUtils.toString(res.getEntity())).getAsJsonObject()
                        : new JsonObject();
                // If present, return
                return Optional.ofNullable(json.get("Token"))
                        .map(JsonElement::getAsString)
                        .filter(token -> !StringUtils.isBlank(token))
                        // Otherwise, throw an exception with the error description (if present)
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
                // Build a new HTTP request
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

                // Send the request on the HTTP client
                HttpResponse res = client.execute(request);

                // Attempt to parse the response body as JSON and extract the access token and user hash
                JsonObject json = res.getStatusLine().getStatusCode() == 200
                        ? new JsonParser().parse(EntityUtils.toString(res.getEntity())).getAsJsonObject()
                        : new JsonObject();
                return Optional.ofNullable(json.get("Token"))
                        .map(JsonElement::getAsString)
                        .filter(token -> !StringUtils.isBlank(token))
                        // If present, extract the user hash and return
                        .map(token -> {
                            // Extract the user hash
                            String uhs = json.get("DisplayClaims").getAsJsonObject()
                                    .get("xui").getAsJsonArray()
                                    .get(0).getAsJsonObject()
                                    .get("uhs").getAsString();

                            // Return an immutable mapping of the token and user hash
                            Map<String, String> result = new HashMap<>();
                            result.put("Token", token);
                            result.put("uhs", uhs);
                            return result;
                        })
                        // Otherwise, throw an exception with the error description (if present)
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
                // Build a new HTTP request
                HttpPost request = new HttpPost(URI.create("https://api.minecraftservices.com/authentication/login_with_xbox"));
                request.setConfig(REQUEST_CONFIG);
                request.setHeader("Content-Type", "application/json");
                request.setEntity(new StringEntity(
                        String.format("{\"identityToken\": \"XBL3.0 x=%s;%s\"}", userHash, xstsToken)
                ));

                // Send the request on the HTTP client
                HttpResponse res = client.execute(request);

                // Attempt to parse the response body as JSON and extract the access token
                JsonObject json = new JsonParser().parse(EntityUtils.toString(res.getEntity())).getAsJsonObject();

                // If present, return
                return Optional.ofNullable(json.get("access_token"))
                        .map(JsonElement::getAsString)
                        .filter(token -> !StringUtils.isBlank(token))
                        // Otherwise, throw an exception with the error description (if present)
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
                // Build a new HTTP request
                HttpGet request = new HttpGet(URI.create("https://api.minecraftservices.com/minecraft/profile"));
                request.setConfig(REQUEST_CONFIG);
                request.setHeader("Authorization", "Bearer " + mcToken);

                // Send the request on the HTTP client
                HttpResponse res = client.execute(request);

                // Attempt to parse the response body as JSON and extract the profile
                JsonObject json = new JsonParser().parse(EntityUtils.toString(res.getEntity())).getAsJsonObject();
                return Optional.ofNullable(json.get("id"))
                        .map(JsonElement::getAsString)
                        .filter(uuid -> !StringUtils.isBlank(uuid))
                        // If present, build a new session and return
                        .map(uuid -> new Session(
                                json.get("name").getAsString(),
                                uuid,
                                mcToken,
                                Session.Type.MOJANG.toString()
                        ))
                        // Otherwise, throw an exception with the error description (if present)
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

    /**
     * 统一的 Token 登录入口（重写版，参考 Elara MicrosoftOAuthTranslation）。
     * 使用 HttpURLConnection 同步实现，避免 Apache HttpClient 的 SSL 问题。
     *
     * 自动识别 token 类型：
     *   1) 以 "eyJ" 开头 / 长度 > 500 → 直接作为 Minecraft Access Token，调用 profile API
     *   2) 否则 → 作为 Microsoft Refresh Token，走 MS -> XBL -> XSTS -> MC 完整流程
     *
     * 返回 Map 包含: username, uuid, accessToken, refreshToken, sessionType
     */
    public static CompletableFuture<Map<String, String>> loginWithToken(String rawToken, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            if (rawToken == null || rawToken.trim().isEmpty()) {
                throw new CompletionException("Token is empty", null);
            }
            String cleanToken = rawToken.trim();

            try {
                // Case 1: 直接是 Minecraft Access Token
                if (cleanToken.startsWith("eyJ") || cleanToken.length() > 500) {
                    return loginWithAccessToken(cleanToken);
                }
                // Case 2: Microsoft Refresh Token
                return loginWithRefreshToken(cleanToken);
            } catch (Exception e) {
                throw new CompletionException(e.getMessage(), e);
            }
        }, executor);
    }

    // ---- 以下为同步实现，参考 Elara MicrosoftOAuthTranslation ----

    private static final String TOKEN_CLIENT_ID = "9fbc7315-7200-4b2b-a655-bb38c865da17";
    // Azure AD client secret removed for public repo. Microsoft login disabled.
    private static final String TOKEN_CLIENT_SECRET = "";
    private static final Gson tokenGson = new Gson();

    /**
     * 用 Minecraft Access Token 直接登录（打 profile API）。
     */
    private static Map<String, String> loginWithAccessToken(String accessToken) throws Exception {
        String profileJson = httpGet("https://api.minecraftservices.com/minecraft/profile",
                "Authorization", "Bearer " + accessToken);

        JsonObject profile = tokenGson.fromJson(profileJson, JsonObject.class);
        if (profile == null || !profile.has("name") || !profile.has("id")) {
            throw new Exception("Invalid profile response: " + profileJson);
        }

        Map<String, String> result = new HashMap<>();
        result.put("username", profile.get("name").getAsString());
        result.put("uuid", formatDashUuid(profile.get("id").getAsString()));
        result.put("accessToken", accessToken);
        result.put("refreshToken", "");
        result.put("sessionType", "mojang");
        return result;
    }

    /**
     * 用 Microsoft Refresh Token 走完整 OAuth 流程。
     * 参考 Elara MicrosoftOAuthTranslation.login()。
     */
    private static Map<String, String> loginWithRefreshToken(String refreshToken) throws Exception {
        // Step 1: Refresh Microsoft access token
        String tokenRequestBody = "client_id=" + TOKEN_CLIENT_ID +
                "&client_secret=" + URLEncoder.encode(TOKEN_CLIENT_SECRET, StandardCharsets.UTF_8.name()) +
                "&refresh_token=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8.name()) +
                "&grant_type=refresh_token";

        String tokenResponse = httpPostForm("https://login.live.com/oauth20_token.srf", tokenRequestBody);
        JsonObject tokenJson = tokenGson.fromJson(tokenResponse, JsonObject.class);

        if (tokenJson == null || !tokenJson.has("access_token")) {
            String err = (tokenJson != null && tokenJson.has("error_description"))
                    ? tokenJson.get("error_description").getAsString()
                    : "Failed to refresh Microsoft token";
            throw new Exception(err);
        }

        String msAccessToken = tokenJson.get("access_token").getAsString();
        String newRefreshToken = tokenJson.has("refresh_token")
                ? tokenJson.get("refresh_token").getAsString() : refreshToken;

        // Step 2: Xbox Live authentication
        String xblPayload = "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\","
                + "\"RpsTicket\":\"d=" + msAccessToken + "\"},"
                + "\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}";
        String xblResponse = httpPostJson("https://user.auth.xboxlive.com/user/authenticate", xblPayload);
        JsonObject xblJson = tokenGson.fromJson(xblResponse, JsonObject.class);

        if (xblJson == null || !xblJson.has("Token")) {
            throw new Exception("XBL authentication failed: " + xblResponse);
        }
        String xblToken = xblJson.get("Token").getAsString();
        String uhs = xblJson.getAsJsonObject("DisplayClaims")
                .getAsJsonArray("xui").get(0).getAsJsonObject()
                .get("uhs").getAsString();

        // Step 3: XSTS authentication
        String xstsPayload = "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\"" + xblToken + "\"]},"
                + "\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}";
        String xstsResponse = httpPostJson("https://xsts.auth.xboxlive.com/xsts/authorize", xstsPayload);
        JsonObject xstsJson = tokenGson.fromJson(xstsResponse, JsonObject.class);

        if (xstsJson == null || !xstsJson.has("Token")) {
            // 检查 Xbox 账号相关错误
            if (xstsResponse != null && xstsResponse.contains("2148916233")) {
                throw new Exception("No Xbox account associated with this Microsoft account");
            } else if (xstsResponse != null && xstsResponse.contains("2148916235")) {
                throw new Exception("Xbox Live is not available in your country");
            } else if (xstsResponse != null && xstsResponse.contains("2148916238")) {
                throw new Exception("Adult verification required for this account");
            }
            throw new Exception("XSTS authentication failed: " + xstsResponse);
        }
        String xstsToken = xstsJson.get("Token").getAsString();

        // Step 4: Minecraft authentication
        String mcPayload = "{\"identityToken\":\"XBL3.0 x=" + uhs + ";" + xstsToken + "\"}";
        String mcResponse = httpPostJson("https://api.minecraftservices.com/authentication/login_with_xbox", mcPayload);
        JsonObject mcJson = tokenGson.fromJson(mcResponse, JsonObject.class);

        if (mcJson == null || !mcJson.has("access_token")) {
            throw new Exception("Minecraft authentication failed: " + mcResponse);
        }
        String mcAccessToken = mcJson.get("access_token").getAsString();

        // Step 5: Get profile
        String profileResponse = httpGet("https://api.minecraftservices.com/minecraft/profile",
                "Authorization", "Bearer " + mcAccessToken);
        JsonObject profileJson = tokenGson.fromJson(profileResponse, JsonObject.class);

        if (profileJson == null || !profileJson.has("name") || !profileJson.has("id")) {
            throw new Exception("Failed to get Minecraft profile: " + profileResponse);
        }

        Map<String, String> result = new HashMap<>();
        result.put("username", profileJson.get("name").getAsString());
        result.put("uuid", formatDashUuid(profileJson.get("id").getAsString()));
        result.put("accessToken", mcAccessToken);
        result.put("refreshToken", newRefreshToken);
        result.put("sessionType", "mojang");
        return result;
    }

    // ---- HTTP 工具方法 ----

    private static String httpPostForm(String urlStr, String body) throws Exception {
        return httpPost(urlStr, body, "application/x-www-form-urlencoded");
    }

    private static String httpPostJson(String urlStr, String body) throws Exception {
        return httpPost(urlStr, body, "application/json");
    }

    private static String httpPost(String urlStr, String body, String contentType) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Content-Type", contentType);
            conn.setRequestProperty("Accept", "application/json");

            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(out.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(out);
            }

            int code = conn.getResponseCode();
            InputStream stream = code < 400 ? conn.getInputStream() : conn.getErrorStream();
            if (stream == null) return null;
            return readAll(stream);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String httpGet(String urlStr, String... headers) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Accept", "application/json");
            for (int i = 0; i + 1 < headers.length; i += 2) {
                conn.setRequestProperty(headers[i], headers[i + 1]);
            }

            int code = conn.getResponseCode();
            InputStream stream = code == 200 ? conn.getInputStream() : conn.getErrorStream();
            if (stream == null) return null;
            String body = readAll(stream);
            if (code != 200) {
                throw new Exception("HTTP " + code + ": " + body);
            }
            return body;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readAll(InputStream stream) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(line);
            }
        }
        return sb.toString();
    }

    /**
     * 把不带横线的 32 字符 UUID 转成 36 字符带横线格式。
     */
    private static String formatDashUuid(String uuid) {
        if (uuid == null) return "";
        String u = uuid.trim().replace("-", "");
        if (u.length() != 32) return uuid;
        return u.substring(0, 8) + "-" + u.substring(8, 12) + "-" + u.substring(12, 16) + "-"
                + u.substring(16, 20) + "-" + u.substring(20, 32);
    }
}
