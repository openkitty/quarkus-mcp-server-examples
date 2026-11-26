package io.quarkiverse.mcp.servers.dme;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

@ApplicationScoped
public class DmeAvaticaClient {

    private static final Logger LOG = Logger.getLogger(DmeAvaticaClient.class);
    @Inject
    ObjectMapper mapper;

    private Connection getConnection() throws Exception {
        String host = requireEnv("DME_JDBC_HOST");
        String username = requireEnv("DME_JDBC_USERNAME");
        String password = requireEnv("DME_JDBC_PASSWORD");

        Class.forName("org.apache.calcite.avatica.remote.Driver");

        Properties info = new Properties();
        info.setProperty(
                "httpclient_impl",
                AvaticaHttpClient.class.getName());

        info.setProperty("username", username);
        info.setProperty("password", password);
        info.setProperty("ip", host);

        String jdbcUrl = "jdbc:avatica:remote:url=https://"
                + host
                + ":26335/rest/catalogopenapiservice/v1/sql/statements";

        return DriverManager.getConnection(jdbcUrl, info);
    }

    public String readQuery(String sql) {
        if (LOG.isDebugEnabled()) {
            LOG.debugf("[DME SQL] Received read SQL:\n%s", sql);
        }
        validateSql(sql);

        String timeout = getEnvOrDefault("DME_JDBC_TIMEOUT_S", "120");
        String fetchSize = getEnvOrDefault("DME_JDBC_FETCH_SIZE", "1000");

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(Integer.parseInt(timeout));
            stmt.setFetchSize(Integer.parseInt(fetchSize));
            ResultSet rs = stmt.executeQuery(sql);

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                columns.add(metaData.getColumnLabel(i));
            }

            List<Map<String, Object>> rows = new ArrayList<>();

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();

                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnLabel(i);
                    Object value = rs.getObject(i);

                    if (value == null) {
                        row.put(columnName, null);
                    } else {
                        row.put(columnName, value.toString());
                    }
                }

                rows.add(row);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("columns", columns);
            result.put("row_count", rows.size());
            result.put("rows", rows);

            String response = mapper.writeValueAsString(result);

            if (LOG.isDebugEnabled()) {
                LOG.debugf(
                        "[DME SQL] Read query succeeded. sql=%s, row_count=%d, result=%s",
                        sql,
                        rows.size(),
                        response);
            }

            return response;

        } catch (Exception e) {
            throw new RuntimeException(
                    "DME read query failed: " + e.getMessage(),
                    e);
        }
    }

    public String writeQuery(String sql) {
        if (LOG.isDebugEnabled()) {
            LOG.debugf("[DME SQL] Received write SQL:\n%s", sql);
        }
        validateSql(sql);

        String normalized = sql.stripLeading().toUpperCase();

        if (normalized.startsWith("SELECT")) {
            throw new IllegalArgumentException(
                    "SELECT queries are not allowed for writeQuery");
        }

        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            int affectedRows = stmt.executeUpdate();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("affected_rows", affectedRows);

            String response = mapper.writeValueAsString(result);

            if (LOG.isDebugEnabled()) {
                LOG.debugf(
                        "[DME SQL] Write query succeeded. affected_rows=%d, result=%s",
                        affectedRows,
                        response);
            }

            return response;

        } catch (Exception e) {
            throw new RuntimeException(
                    "DME write query failed: " + e.getMessage(),
                    e);
        }
    }

    private static void validateSql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException(
                    "SQL query must not be empty");
        }
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Environment variable "
                            + name
                            + " is not configured");
        }

        return value.trim();
    }

    private static String getEnvOrDefault(
            String name,
            String defaultValue) {

        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return value.trim();
    }
}
