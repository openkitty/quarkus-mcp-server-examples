package io.quarkiverse.mcp.servers.dme;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import jakarta.enterprise.context.ApplicationScoped;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@ApplicationScoped
public class DmeApiClient {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String createProject(String name, String description) {
        return createProject(name, description, null);
    }

    public String createProject(
            String name,
            String description,
            String accessUnitId) {
        try {
            validateProjectName(name);

            String baseUrl = getConfig(
                    "dme.base-url",
                    "DME_BASE_URL",
                    null);

            if (baseUrl == null || baseUrl.isBlank()) {

                return error("DME base URL is not configured");
            }

            String accessSession = AvaticaHttpClient.getSharedAuthToken(baseUrl);

            String currentRoaRand = AvaticaHttpClient.getSharedRoaRand(baseUrl);

            String url = removeTrailingSlash(baseUrl)
                    + "/rest/catalogdatasourceservice/v1/projects";

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("name", name);

            if (description != null && !description.isBlank()) {
                requestBody.put("description", description);
            }

            if (accessUnitId != null && !accessUnitId.isBlank()) {
                requestBody.putArray("access_unit_list")
                        .addObject()
                        .put("id", accessUnitId);
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .header("X-Auth-Token", accessSession)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpClient client = createHttpClient();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

            ObjectNode result = objectMapper.createObjectNode();
            result.put("success",
                    response.statusCode() >= 200
                            && response.statusCode() < 300);
            result.put("method", "POST");
            result.put("url", url);
            result.put("status_code", response.statusCode());

            String responseBody = response.body();

            try {
                JsonNode jsonBody = objectMapper.readTree(responseBody);
                result.set("body", jsonBody);
            } catch (Exception ignored) {
                result.put("body", responseBody);
            }

            return objectMapper.writeValueAsString(result);

        } catch (Exception e) {
            return error(
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public String createCatalog(
            String name,
            String type,
            String projectId,
            String description) {

        try {
            validateCatalogName(name);
            validateCatalogType(type);
            validateProjectId(projectId);

            String baseUrl = getConfig(
                    "dme.base-url",
                    "DME_BASE_URL",
                    null);

            String accessSession = AvaticaHttpClient.getSharedAuthToken(baseUrl);

            String currentRoaRand = AvaticaHttpClient.getSharedRoaRand(baseUrl);

            if (baseUrl == null || baseUrl.isBlank()) {
                return error("DME base URL is not configured");
            }

            String url = removeTrailingSlash(baseUrl)
                    + "/rest/datacatalogmgmtservice/v1/datacatalog/catalogs";

            ObjectNode requestBody = objectMapper.createObjectNode();

            requestBody.put("name", name);
            requestBody.put("type", type);
            requestBody.put("project_id", projectId);

            if (description != null && !description.isBlank()) {
                requestBody.put("description", description);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .header("X-Auth-Token", accessSession)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpClient client = createHttpClient();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

            ObjectNode result = objectMapper.createObjectNode();

            result.put(
                    "success",
                    response.statusCode() >= 200
                            && response.statusCode() < 300);

            result.put("method", "POST");
            result.put("url", url);
            result.put("status_code", response.statusCode());

            String responseBody = response.body();

            if (responseBody == null || responseBody.isBlank()) {
                result.putNull("body");
            } else {
                try {
                    JsonNode jsonBody = objectMapper.readTree(responseBody);
                    result.set("body", jsonBody);
                } catch (Exception ignored) {
                    result.put("body", responseBody);
                }
            }

            return objectMapper.writeValueAsString(result);

        } catch (Exception e) {
            return error(
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public String createSchema(
            String catalogName,
            String name,
            String projectId,
            String description) {

        return createSchema(
                catalogName,
                name,
                projectId,
                description,
                null,
                null,
                null);
    }

    public String createSchema(
            String catalogName,
            String name,
            String projectId,
            String description,
            String datasourceId,
            String storageUnitId,
            String storagePath) {

        try {
            validateCatalogName(catalogName);
            validateSchemaName(name);
            validateProjectId(projectId);

            if ((storageUnitId != null && !storageUnitId.isBlank())
                    || (storagePath != null && !storagePath.isBlank())) {

                if (datasourceId == null || datasourceId.isBlank()) {
                    throw new IllegalArgumentException(
                            "datasource_id is required when storage_unit_id "
                                    + "or storage_path is provided.");
                }
            }

            String baseUrl = getConfig(
                    "dme.base-url",
                    "DME_BASE_URL",
                    null);

            String accessSession = AvaticaHttpClient.getSharedAuthToken(baseUrl);

            String currentRoaRand = AvaticaHttpClient.getSharedRoaRand(baseUrl);

            if (baseUrl == null || baseUrl.isBlank()) {
                return error("DME base URL is not configured");
            }

            String url = removeTrailingSlash(baseUrl)
                    + "/rest/datacatalogmgmtservice/v1/datacatalog/catalogs/"
                    + catalogName
                    + "/schemas";

            ObjectNode requestBody = objectMapper.createObjectNode();

            requestBody.put("name", name);
            requestBody.put("project_id", projectId);

            if (description != null && !description.isBlank()) {
                requestBody.put("description", description);
            }

            if (datasourceId != null && !datasourceId.isBlank()) {
                ObjectNode storageLocation = requestBody.putObject("storage_location");

                storageLocation.put("datasource_id", datasourceId);

                if (storageUnitId != null && !storageUnitId.isBlank()) {
                    storageLocation.put(
                            "storage_unit_id",
                            storageUnitId);
                }

                if (storagePath != null && !storagePath.isBlank()) {
                    storageLocation.put(
                            "storage_path",
                            storagePath);
                }
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .header("X-Auth-Token", accessSession)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpClient client = createHttpClient();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

            ObjectNode result = objectMapper.createObjectNode();

            result.put(
                    "success",
                    response.statusCode() >= 200
                            && response.statusCode() < 300);

            result.put("method", "POST");
            result.put("url", url);
            result.put("status_code", response.statusCode());

            String responseBody = response.body();

            if (responseBody == null || responseBody.isBlank()) {
                result.putNull("body");
            } else {
                try {
                    JsonNode jsonBody = objectMapper.readTree(responseBody);
                    result.set("body", jsonBody);
                } catch (Exception ignored) {
                    result.put("body", responseBody);
                }
            }

            return objectMapper.writeValueAsString(result);

        } catch (Exception e) {
            return error(
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public String createTable(
            String catalogName,
            String schemaName,
            String name,
            String projectId,
            String description,
            String datasourceId,
            String storageUnitId,
            String storagePath,
            List<DmeColumnDefinition> columnList) {

        try {
            validateCatalogName(catalogName);
            validateSchemaName(schemaName);
            validateTableName(name);
            validateProjectId(projectId);

            if (datasourceId == null || datasourceId.isBlank()) {
                throw new IllegalArgumentException(
                        "datasource_id is required for Table storage_location.");
            }

            if (columnList == null
                    || columnList.isEmpty()
                    || columnList.size() > 1000) {
                throw new IllegalArgumentException(
                        "column_list must contain between 1 and 1000 columns.");
            }

            for (DmeColumnDefinition column : columnList) {

                if (column == null) {
                    throw new IllegalArgumentException(
                            "column_list must not contain null columns.");
                }

                if (column.name() == null
                        || !column.name()
                                .matches("^[a-zA-Z0-9_]{1,50}$")) {

                    throw new IllegalArgumentException(
                            "Each column must contain a valid name "
                                    + "using letters, numbers and underscores, "
                                    + "with length 1-50.");
                }

                if (column.data_type() == null
                        || column.data_type().isBlank()
                        || column.data_type().length() > 16) {

                    throw new IllegalArgumentException(
                            "Each column must contain a valid data_type "
                                    + "with length 1-16.");
                }
            }

            String baseUrl = getConfig(
                    "dme.base-url",
                    "DME_BASE_URL",
                    null);

            String accessSession = AvaticaHttpClient.getSharedAuthToken(baseUrl);

            String currentRoaRand = AvaticaHttpClient.getSharedRoaRand(baseUrl);

            if (baseUrl == null || baseUrl.isBlank()) {
                return error("DME base URL is not configured");
            }

            String url = removeTrailingSlash(baseUrl)
                    + "/rest/datacatalogmgmtservice/v1/datacatalog/catalogs/"
                    + catalogName
                    + "/schemas/"
                    + schemaName
                    + "/tables";

            ObjectNode requestBody = objectMapper.createObjectNode();

            requestBody.put("name", name);
            requestBody.put("project_id", projectId);
            requestBody.put("lake_type", "native");

            if (description != null && !description.isBlank()) {
                requestBody.put("description", description);
            }

            ObjectNode storageLocation = requestBody.putObject("storage_location");

            storageLocation.put(
                    "datasource_id",
                    datasourceId);

            if (storageUnitId != null && !storageUnitId.isBlank()) {
                storageLocation.put(
                        "storage_unit_id",
                        storageUnitId);
            }

            if (storagePath != null && !storagePath.isBlank()) {
                storageLocation.put(
                        "storage_path",
                        storagePath);
            }

            var columnArray = requestBody.putArray("column_list");

            for (DmeColumnDefinition column : columnList) {

                ObjectNode columnNode = columnArray.addObject();

                columnNode.put(
                        "name",
                        column.name());

                columnNode.put(
                        "data_type",
                        column.data_type());

                if (column.data_length() != null
                        && !column.data_length().isBlank()) {
                    columnNode.put(

                            "data_length",
                            column.data_length());
                }

                if (column.element_data_type() != null
                        && !column.element_data_type().isBlank()) {
                    columnNode.put(
                            "element_data_type",
                            column.element_data_type());
                }

                if (column.is_dominant() != null) {
                    columnNode.put(
                            "is_dominant",
                            column.is_dominant());
                }

                if (column.is_primary() != null) {
                    columnNode.put(
                            "is_primary",
                            column.is_primary());
                }

                if (column.is_nullable() != null) {
                    columnNode.put(
                            "is_nullable",
                            column.is_nullable());
                }

                if (column.default_value() != null
                        && !column.default_value().isBlank()) {
                    columnNode.put(
                            "default_value",
                            column.default_value());
                }

                if (column.description() != null
                        && !column.description().isBlank()) {
                    columnNode.put(
                            "description",
                            column.description());
                }
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .header("X-Auth-Token", accessSession)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpClient client = createHttpClient();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

            ObjectNode result = objectMapper.createObjectNode();

            result.put(
                    "success",
                    response.statusCode() >= 200
                            && response.statusCode() < 300);

            result.put("method", "POST");
            result.put("url", url);
            result.put("status_code", response.statusCode());

            String responseBody = response.body();

            if (responseBody == null || responseBody.isBlank()) {
                result.putNull("body");
            } else {
                try {
                    JsonNode jsonBody = objectMapper.readTree(responseBody);
                    result.set("body", jsonBody);
                } catch (Exception ignored) {
                    result.put("body", responseBody);
                }
            }

            return objectMapper.writeValueAsString(result);

        } catch (Exception e) {
            return error(
                    e.getClass().getSimpleName()
                            + ": "
                            + e.getMessage());
        }
    }

    private void validateTableName(String name) {
        if (name == null || !name.matches("^[a-zA-Z0-9_]{1,64}$")) {
            throw new IllegalArgumentException(
                    "Table name must contain only letters, numbers "
                            + "and underscores, with length 1-64.");
        }
    }

    private void validateSchemaName(String name) {
        if (name == null || !name.matches("^[a-zA-Z0-9_]{1,64}$")) {
            throw new IllegalArgumentException(
                    "Schema name must contain only letters, numbers "
                            + "and underscores, with length 1-64.");
        }
    }

    private void validateCatalogName(String name) {
        if (name == null || !name.matches("^[a-zA-Z0-9_]{1,64}$")) {
            throw new IllegalArgumentException(
                    "Catalog name must contain only letters, numbers "
                            + "and underscores, with length 1-64.");
        }
    }

    private void validateCatalogType(String type) {
        if (type == null
                || !(type.equals("star_rocks")
                        || type.equals("dme_metadata")
                        || type.equals("omni_format_iceberg"))) {

            throw new IllegalArgumentException(
                    "Catalog type must be one of: "
                            + "star_rocks, dme_metadata, "
                            + "omni_format_iceberg.");
        }
    }

    private void validateProjectId(String projectId) {
        if (projectId == null
                || projectId.isBlank()
                || projectId.length() > 36) {

            throw new IllegalArgumentException(
                    "Project ID must be between 1 and 36 characters.");
        }
    }

    private void validateProjectName(String name) {
        if (name == null || !name.matches("^[a-zA-Z0-9_]{1,50}$")) {
            throw new IllegalArgumentException(
                    "Project name must contain only letters, numbers "
                            + "and underscores, with length 1-50.");
        }
    }

    private HttpClient createHttpClient() throws Exception {
        boolean insecureTls = Boolean.parseBoolean(
                getConfig(
                        "dme.insecure-tls",
                        "DME_INSECURE_TLS",
                        "false"));

        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20));

        if (insecureTls) {
            TrustManager[] trustAll = new TrustManager[] {
                    new X509TrustManager() {
                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        @Override
                        public void checkClientTrusted(
                                X509Certificate[] certs,
                                String authType) {
                        }

                        @Override
                        public void checkServerTrusted(
                                X509Certificate[] certs,
                                String authType) {
                        }
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAll, new SecureRandom());

            SSLParameters sslParameters = new SSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm("");

            builder.sslContext(sslContext);
            builder.sslParameters(sslParameters);
        }

        return builder.build();
    }

    private String getConfig(
            String systemProperty,
            String envName,
            String defaultValue) {

        String value = System.getProperty(systemProperty);

        if (value == null || value.isBlank()) {
            value = System.getenv(envName);
        }

        if (value == null || value.isBlank()) {
            value = defaultValue;
        }

        return value;
    }

    private String removeTrailingSlash(String value) {
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String error(String message) {
        try {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("success", false);
            result.put("error", message);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"Unknown error\"}";
        }
    }
}
