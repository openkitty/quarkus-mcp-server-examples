package io.quarkiverse.mcp.servers.dme;

import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class AvaticaHttpClient implements org.apache.calcite.avatica.remote.AvaticaHttpClient {

    private static final int DME_PORT = 26335;

    private static volatile String authToken;
    private static volatile String roaRand;
    private static volatile long authRefreshAtMillis = 0L;

    private static final long REFRESH_MARGIN_SECONDS = 60L;
    private static volatile AvaticaHttpClient sharedInstance;
    private final String host;
    private final URI avaticaUri;
    private final URI authUri;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public AvaticaHttpClient(URL url) {
        this.host = url.getHost();

        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("DME host is empty");
        }

        this.avaticaUri = URI.create(url.toString());

        this.authUri = URI.create(
                "https://" + host + ":" + DME_PORT
                        + "/rest/plat/smapp/v1/sessions");

        this.httpClient = createHttpClient();
        sharedInstance = this;
    }

    @Override
    public byte[] send(byte[] request) {
        try {
            String requestJson = new String(
                    request,
                    StandardCharsets.UTF_8);

            JsonNode requestRoot = mapper.readTree(requestJson);

            if ("openConnection".equals(
                    requestRoot.path("request").asText())) {

                System.err.println(
                        "[DME JDBC] openConnection timeout="
                                + requestRoot
                                        .path("info")
                                        .path("timeout")
                                        .asText("<not set>"));
            }
            String token = getAuthToken();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(avaticaUri)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("x-odae-jdbc-client", "jdbc-client")
                    .header("x-auth-token", token)
                    .POST(HttpRequest.BodyPublishers.ofString(new String(request, StandardCharsets.UTF_8)))
                    .build();

            HttpResponse<byte[]> response = httpClient.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() >= 200
                    && response.statusCode() < 300) {
                return response.body();
            }

            throw new RuntimeException(
                    "DME Avatica request failed, HTTP "
                            + response.statusCode()
                            + ": "
                            + new String(
                                    response.body(),
                                    StandardCharsets.UTF_8));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(
                    "DME Avatica request interrupted",
                    e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                    "DME Avatica request failed: "
                            + e.getMessage(),
                    e);
        }
    }

    public static String getSharedAuthToken(String baseUrl)
            throws Exception {

        AvaticaHttpClient client = sharedInstance;

        if (client == null) {
            synchronized (AvaticaHttpClient.class) {
                client = sharedInstance;

                if (client == null) {
                    String normalizedBaseUrl = baseUrl.endsWith("/")
                            ? baseUrl.substring(
                                    0,
                                    baseUrl.length() - 1)
                            : baseUrl;

                    URL url = new URL(
                            normalizedBaseUrl
                                    + "/rest/catalogopenapiservice/v2/open-query/sql");

                    client = new AvaticaHttpClient(url);

                    sharedInstance = client;
                }
            }
        }

        return client.getAuthToken();
    }

    public static String getSharedRoaRand(String baseUrl)
            throws Exception {

        getSharedAuthToken(baseUrl);

        return roaRand;
    }

    public String getAuthToken() throws Exception {
        long now = System.currentTimeMillis();
        String token = authToken;

        // Token存在，并且还没有到刷新时间，直接复用
        if (token != null
                && !token.isBlank()
                && now < authRefreshAtMillis) {
            return token;
        }

        synchronized (AvaticaHttpClient.class) {
            token = authToken;
            now = System.currentTimeMillis();

            // 防止多个线程同时刷新
            if (token != null
                    && !token.isBlank()
                    && now < authRefreshAtMillis) {
                return token;
            }

            String username = requireEnv("DME_JDBC_USERNAME");
            String password = requireEnv("DME_JDBC_PASSWORD");

            authToken = login(username, password);

            return authToken;
        }
    }

    private String login(String username, String password)
            throws Exception {

        ObjectNode body = mapper.createObjectNode();
        body.put("grantType", "password");
        body.put("userName", username);
        body.put("value", password);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(authUri)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")

                .PUT(HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(body),
                        StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(
                        StandardCharsets.UTF_8));

        if (response.statusCode() < 200
                || response.statusCode() >= 300) {
            throw new RuntimeException(
                    "DME authentication failed, HTTP "
                            + response.statusCode()
                            + ": "
                            + response.body());
        }

        JsonNode root = mapper.readTree(response.body());

        String accessSession = root.path("accessSession").asText(null);

        String newRoaRand = root.path("roaRand").asText(null);

        long expires = root.path("expires").asLong(1800L);

        if (accessSession == null || accessSession.isBlank()) {
            throw new IllegalStateException(
                    "DME authentication response does not contain accessSession");
        }

        authToken = accessSession;
        roaRand = newRoaRand;

        long refreshAfterSeconds = Math.max(1L, expires - REFRESH_MARGIN_SECONDS);

        authRefreshAtMillis = System.currentTimeMillis()
                + refreshAfterSeconds * 1000L;

        System.err.println(
                "[DME AUTH] Login success, expires="
                        + expires
                        + "s, refreshAfter="
                        + refreshAfterSeconds
                        + "s");

        return accessSession;
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Environment variable " + name + " is not configured");
        }

        return value;
    }

    private static HttpClient createHttpClient() {
        try {
            TrustManager[] trustAll = new TrustManager[] {
                    new X509TrustManager() {
                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        @Override
                        public void checkClientTrusted(
                                X509Certificate[] chain,
                                String authType) {
                        }

                        @Override
                        public void checkServerTrusted(
                                X509Certificate[] chain,
                                String authType) {
                        }
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(
                    null,
                    trustAll,
                    new SecureRandom());

            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to create DME HTTPS client",
                    e);
        }
    }
}