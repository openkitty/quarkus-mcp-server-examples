package io.quarkiverse.mcp.servers.restful;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.ToolCallException;

@ApplicationScoped
public class RestApiClient {

    @Inject
    ObjectMapper mapper;

    /*
     * REST API 的基础地址。
     *
     * 例如：
     * -Drest.base-url=http://127.0.0.1:9998/api/
     */
    @ConfigProperty(name = "rest.base-url")
    Optional<String> baseUrl;

    /*
     * 如果 REST API 使用 Bearer Token，可以配置：
     *
     * -Drest.bearer-token=xxxx
     */
    @ConfigProperty(name = "rest.bearer-token")
    Optional<String> bearerToken;

    /*
     * 是否允许 REST 写操作。
     *
     * false:
     * 只允许 GET
     *
     * true:
     * 允许 POST / PUT / PATCH / DELETE
     */
    @ConfigProperty(name = "rest.write-enabled", defaultValue = "false")
    boolean writeEnabled;

    /*
     * REST 请求超时时间。
     */
    @ConfigProperty(name = "rest.timeout-seconds", defaultValue = "60")
    int timeoutSeconds;

    /*
     * 从 rest-api.json 中加载出来的 API 定义。
     */
    private JsonNode apiDefinition;

    /*
     * Java 17 自带 HTTP Client。
     *
     * 不需要增加第三方 HTTP 依赖。
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /*
     * MCP Server 启动时读取 rest-api.json。
     */
    @PostConstruct
    void loadApiDefinition() {

        try (InputStream input = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("rest-api.json")) {

            if (input == null) {

                throw new IllegalStateException(
                        "rest-api.json was not found in application resources");
            }

            apiDefinition = mapper.readTree(input);

            if (!apiDefinition.has("endpoints")
                    || !apiDefinition.get("endpoints").isArray()) {

                throw new IllegalStateException(
                        "rest-api.json must contain an endpoints array");
            }

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Failed to load rest-api.json: "
                            + e.getMessage(),
                    e);
        }
    }

    /*
     * REST API 整体信息。
     *
     * 相当于 JDBC 的 database_info。
     */
    public String apiInfo() {

        try {

            var result = mapper.createObjectNode();

            result.put(
                    "configured",
                    baseUrl.isPresent()
                            && !baseUrl.get().isBlank());

            result.put(
                    "base_url",
                    baseUrl.orElse(
                            "NOT_CONFIGURED"));

            result.put(
                    "write_enabled",
                    writeEnabled);

            result.put(
                    "timeout_seconds",
                    timeoutSeconds);

            result.put(
                    "authentication",
                    bearerToken.isPresent()
                            && !bearerToken.get().isBlank()
                                    ? "bearer_token"
                                    : "none");

            result.put(
                    "api_name",
                    apiDefinition
                            .path("name")
                            .asText("REST API"));

            result.put(
                    "api_description",
                    apiDefinition
                            .path("description")
                            .asText(""));

            result.put(
                    "endpoint_count",
                    apiDefinition
                            .path("endpoints")
                            .size());

            return mapper.writeValueAsString(
                    result);

        } catch (Exception e) {

            throw new ToolCallException(
                    "Failed to get REST API info: "
                            + e.getMessage(),
                    e);
        }
    }

    /*
     * 返回全部 REST Endpoint。
     *
     * 相当于 JDBC 的 list_tables。
     */
    public String listEndpoints() {

        try {

            return mapper.writeValueAsString(
                    apiDefinition.path(
                            "endpoints"));

        } catch (Exception e) {

            throw new ToolCallException(
                    "Failed to list REST endpoints: "
                            + e.getMessage(),
                    e);
        }
    }

    /*
     * 根据 endpoint 名称查看接口详细信息。
     *
     * 相当于 JDBC 的 describe_table。
     *
     * 例如：
     *
     * update_record
     *
     * 返回：
     *
     * PATCH records/{id}
     */
    public String describeEndpoint(
            String endpointName) {

        try {

            JsonNode endpoint = findEndpointNode(
                    endpointName);

            return mapper.writeValueAsString(
                    endpoint);

        } catch (ToolCallException e) {

            throw e;

        } catch (Exception e) {

            throw new ToolCallException(
                    "Failed to describe REST endpoint: "
                            + e.getMessage(),
                    e);
        }
    }

    /*
     * 推荐 DataAgent 使用的 REST 调用方式。
     *
     * DataAgent 不需要自己决定：
     *
     * method
     * path
     *
     * 只需要传：
     *
     * endpointName
     * pathParams
     * queryParams
     * body
     *
     *
     * 例如：
     *
     * endpointName = update_record
     *
     * pathParams =
     * {"id":"123"}
     *
     * body =
     * {"status":"inactive"}
     *
     *
     * 服务端会自动从 rest-api.json 找到：
     *
     * PATCH records/{id}
     *
     * 然后生成：
     *
     * PATCH records/123
     */
    public String callEndpoint(
            String endpointName,
            String pathParams,
            String queryParams,
            String body) {

        try {

            /*
             * 先根据名字找到 REST Endpoint。
             */
            JsonNode endpoint = findEndpointNode(
                    endpointName);

            String method = endpoint.path(
                    "method")
                    .asText()
                    .toUpperCase();

            String path = endpoint.path(
                    "path")
                    .asText();

            /*
             * rest-api.json 中声明的 path 参数。
             */
            JsonNode declaredPathParams = endpoint.path(
                    "path_params");

            /*
             * DataAgent 实际传进来的 path 参数。
             */
            JsonNode providedPathParams = null;

            if (pathParams != null
                    && !pathParams.isBlank()) {

                providedPathParams = mapper.readTree(
                        pathParams);

                if (!providedPathParams
                        .isObject()) {

                    throw new ToolCallException(
                            "path_params must be a JSON object",
                            null);
                }
            }

            /*
             * 检查并替换：
             *
             * records/{id}
             *
             * →
             *
             * records/123
             */
            if (declaredPathParams.isArray()) {

                for (JsonNode parameter : declaredPathParams) {

                    String parameterName = parameter.path(
                            "name")
                            .asText();

                    boolean required = parameter.path(
                            "required")
                            .asBoolean(false);

                    boolean provided = providedPathParams != null
                            && providedPathParams
                                    .has(parameterName)
                            && !providedPathParams
                                    .get(parameterName)
                                    .isNull();

                    /*
                     * 必填参数没有提供。
                     */
                    if (required && !provided) {

                        throw new ToolCallException(
                                "Missing required path parameter: "
                                        + parameterName,
                                null);
                    }

                    /*
                     * 参数存在就进行 URL 编码并替换。
                     */
                    if (provided) {

                        String value = providedPathParams
                                .get(parameterName)
                                .asText();

                        String encodedValue = URLEncoder.encode(
                                value,
                                StandardCharsets.UTF_8)
                                .replace(
                                        "+",
                                        "%20");

                        path = path.replace(
                                "{"
                                        + parameterName
                                        + "}",
                                encodedValue);
                    }
                }
            }

            /*
             * 如果还有 {xxx}，
             * 说明存在没有被替换的 path 参数。
             */
            if (path.contains("{")
                    || path.contains("}")) {

                throw new ToolCallException(
                        "Some REST path parameters "
                                + "were not provided: "
                                + path,
                        null);
            }

            /*
             * 最终还是复用下面的通用 request()。
             */
            return request(
                    method,
                    path,
                    queryParams,
                    body);

        } catch (ToolCallException e) {

            throw e;

        } catch (Exception e) {

            throw new ToolCallException(
                    "Failed to call REST endpoint: "
                            + e.getMessage(),
                    e);
        }
    }

    /*
     * 根据名称从 rest-api.json 中找到 Endpoint。
     */
    private JsonNode findEndpointNode(
            String endpointName) {

        if (endpointName == null
                || endpointName.isBlank()) {

            throw new ToolCallException(
                    "Endpoint name must not be empty",
                    null);
        }

        JsonNode endpoints = apiDefinition.path(
                "endpoints");

        for (JsonNode endpoint : endpoints) {

            if (endpointName.equals(
                    endpoint.path(
                            "name")
                            .asText())) {

                return endpoint;
            }
        }

        throw new ToolCallException(
                "Unknown REST endpoint: "
                        + endpointName,
                null);
    }

    /*
     * 通用 REST 请求方法。
     *
     * 支持：
     *
     * GET
     * POST
     * PUT
     * PATCH
     * DELETE
     *
     * 这个方法继续保留，
     * 用于兼容原来的 rest_request Tool。
     */
    public String request(
            String method,
            String path,
            String queryParams,
            String body) {

        try {

            /*
             * 必须配置 REST base URL。
             */
            if (baseUrl.isEmpty()
                    || baseUrl.get().isBlank()) {

                throw new ToolCallException(
                        "REST API is not configured. "
                                + "Please start the server with "
                                + "-Drest.base-url=http://host:port/api/",
                        null);
            }

            String httpMethod = method == null
                    ? ""
                    : method.strip()
                            .toUpperCase();

            /*
             * 只允许这些 HTTP Method。
             */
            if (!httpMethod.equals("GET")
                    && !httpMethod.equals("POST")
                    && !httpMethod.equals("PUT")
                    && !httpMethod.equals("PATCH")
                    && !httpMethod.equals("DELETE")) {

                throw new ToolCallException(
                        "Unsupported HTTP method: "
                                + httpMethod,
                        null);
            }

            /*
             * 判断是不是写操作。
             */
            boolean writeOperation = httpMethod.equals("POST")
                    || httpMethod.equals("PUT")
                    || httpMethod.equals("PATCH")
                    || httpMethod.equals("DELETE");

            /*
             * 默认禁止写操作。
             */
            if (writeOperation
                    && !writeEnabled) {

                throw new ToolCallException(
                        "REST write operations are disabled. "
                                + "Start the server with "
                                + "-Drest.write-enabled=true "
                                + "to enable POST/PUT/PATCH/DELETE.",
                        null);
            }

            /*
             * 构造最终 URL。
             */
            URI targetUri = buildUri(
                    path,
                    queryParams);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(
                            targetUri)
                    .timeout(
                            Duration.ofSeconds(
                                    timeoutSeconds))
                    .header(
                            "Accept",
                            "application/json");

            /*
             * 如果配置 Bearer Token，
             * 自动增加 Authorization Header。
             *
             * DataAgent 不需要接触 Token。
             */
            bearerToken
                    .filter(token -> !token.isBlank())
                    .ifPresent(token -> requestBuilder.header(
                            "Authorization",
                            "Bearer "
                                    + token));

            boolean hasBody = body != null
                    && !body.isBlank();

            HttpRequest.BodyPublisher publisher = hasBody
                    ? HttpRequest.BodyPublishers
                            .ofString(
                                    body,
                                    StandardCharsets.UTF_8)
                    : HttpRequest.BodyPublishers
                            .noBody();

            /*
             * 有请求 body 时，
             * 默认认为是 JSON。
             */
            if (hasBody) {

                requestBuilder.header(
                        "Content-Type",
                        "application/json");
            }

            /*
             * 根据 method 生成 HTTP 请求。
             */
            switch (httpMethod) {

                case "GET" ->
                    requestBuilder.GET();

                case "POST" ->
                    requestBuilder.POST(
                            publisher);

                case "PUT" ->
                    requestBuilder.PUT(
                            publisher);

                case "PATCH" ->
                    requestBuilder.method(
                            "PATCH",
                            publisher);

                case "DELETE" ->
                    requestBuilder.method(
                            "DELETE",
                            publisher);

                default ->
                    throw new ToolCallException(
                            "Unsupported HTTP method: "
                                    + httpMethod,
                            null);
            }

            /*
             * 真正发送 HTTP 请求。
             */
            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers
                            .ofString(
                                    StandardCharsets.UTF_8));

            /*
             * 将 REST 返回统一包装成 JSON。
             */
            var result = mapper.createObjectNode();

            result.put(
                    "method",
                    httpMethod);

            result.put(
                    "url",
                    targetUri.toString());

            result.put(
                    "status_code",
                    response.statusCode());

            String responseBody = response.body();

            /*
             * REST 没有 response body。
             */
            if (responseBody == null
                    || responseBody.isBlank()) {

                result.putNull(
                        "body");

            } else {

                /*
                 * REST 返回 JSON：
                 *
                 * 直接作为 JSON 放到 body。
                 *
                 * REST 返回普通文本：
                 *
                 * 当作 String 放进去。
                 */
                try {

                    JsonNode json = mapper.readTree(
                            responseBody);

                    result.set(
                            "body",
                            json);

                } catch (Exception ignored) {

                    result.put(
                            "body",
                            responseBody);
                }
            }

            return mapper.writeValueAsString(
                    result);

        } catch (ToolCallException e) {

            throw e;

        } catch (Exception e) {

            throw new ToolCallException(
                    "REST API request failed: "
                            + e.getMessage(),
                    e);
        }
    }

    /*
     * 构造 REST URL。
     *
     * baseUrl：
     *
     * http://127.0.0.1:9998/api/
     *
     * path：
     *
     * records/123
     *
     * 最终：
     *
     * http://127.0.0.1:9998/api/records/123
     */
    private URI buildUri(
            String path,
            String queryParams)
            throws Exception {

        String configuredBaseUrl = baseUrl.orElseThrow();

        /*
         * Base URL 必须以 / 结尾。
         */
        if (!configuredBaseUrl
                .endsWith("/")) {

            configuredBaseUrl = configuredBaseUrl
                    + "/";
        }

        URI base = URI.create(
                configuredBaseUrl);

        /*
         * 只允许 HTTP / HTTPS。
         */
        if (!"http".equalsIgnoreCase(
                base.getScheme())
                && !"https".equalsIgnoreCase(
                        base.getScheme())) {

            throw new ToolCallException(
                    "rest.base-url must start "
                            + "with http:// or https://",
                    null);
        }

        String relativePath = path == null
                ? ""
                : path.strip();

        /*
         * 防止 /records 导致 resolve
         * 跳出 /api/。
         */
        while (relativePath
                .startsWith("/")) {

            relativePath = relativePath.substring(1);
        }

        URI target = base.resolve(
                relativePath)
                .normalize();

        /*
         * 不允许 DataAgent 借此访问其他服务器。
         */
        if (!sameServer(
                base,
                target)) {

            throw new ToolCallException(
                    "REST request is not allowed "
                            + "to access another server",
                    null);
        }

        /*
         * 不允许通过 ../ 跳出配置的 API 根路径。
         */
        if (!target.getPath()
                .startsWith(
                        base.getPath())) {

            throw new ToolCallException(
                    "REST path is outside "
                            + "the configured base path",
                    null);
        }

        /*
         * 没有 query 参数。
         */
        if (queryParams == null
                || queryParams.isBlank()) {

            return target;
        }

        JsonNode query = mapper.readTree(
                queryParams);

        if (!query.isObject()) {

            throw new ToolCallException(
                    "query_params must be "
                            + "a JSON object",
                    null);
        }

        StringBuilder url = new StringBuilder(
                target.toString());

        boolean first = target.getQuery() == null;

        /*
         * 把：
         *
         * {"page":1,"size":20}
         *
         * 转换成：
         *
         * ?page=1&size=20
         */
        Iterator<Map.Entry<String, JsonNode>> fields = query.fields();

        while (fields.hasNext()) {

            Map.Entry<String, JsonNode> entry = fields.next();

            url.append(
                    first
                            ? "?"
                            : "&");

            first = false;

            url.append(
                    URLEncoder.encode(
                            entry.getKey(),
                            StandardCharsets.UTF_8));

            url.append("=");

            url.append(
                    URLEncoder.encode(
                            entry.getValue()
                                    .asText(),
                            StandardCharsets.UTF_8));
        }

        return URI.create(
                url.toString());
    }

    /*
     * 检查最终 URL 是否还属于同一个服务器。
     */
    private boolean sameServer(
            URI base,
            URI target) {

        if (!base.getScheme()
                .equalsIgnoreCase(
                        target.getScheme())) {

            return false;
        }

        if (base.getHost() == null
                || target.getHost() == null
                || !base.getHost()
                        .equalsIgnoreCase(
                                target.getHost())) {

            return false;
        }

        return effectivePort(base) == effectivePort(target);
    }

    /*
     * 获取实际端口。
     *
     * 如果 URL 没写端口：
     *
     * http → 80
     * https → 443
     */
    private int effectivePort(
            URI uri) {

        if (uri.getPort() != -1) {

            return uri.getPort();
        }

        if ("https".equalsIgnoreCase(
                uri.getScheme())) {

            return 443;
        }

        return 80;
    }
}