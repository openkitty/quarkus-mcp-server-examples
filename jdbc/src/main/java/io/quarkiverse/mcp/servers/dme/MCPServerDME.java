package io.quarkiverse.mcp.servers.dme;

import java.util.List;

import jakarta.inject.Inject;

import org.apache.calcite.avatica.AvaticaSqlException;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;

public class MCPServerDME {

    @Inject
    DmeApiClient dmeApiClient;

    @Inject
    DmeAvaticaClient dmeAvaticaClient;

    @Tool(description = "Create a project in Huawei iMaster DME. "
            + "Use this tool when the user asks to create a DME project. "
            + "The project name must contain only English letters, "
            + "numbers and underscores, and must be globally unique.")
    String dme_create_project(
            @ToolArg(description = "Name of the DME project. "
                    + "Only letters, numbers and underscores are allowed. "
                    + "Length must be between 1 and 50 characters.") String name,

            @ToolArg(description = "Optional description of the DME project.", required = false) String description,

            @ToolArg(description = "Optional data entity access unit ID to associate with the Project. "
                    + "Use this when the Project needs access to a data storage unit, "
                    + "for example an Iceberg access unit.", required = false) String access_unit_id) {

        return dmeApiClient.createProject(
                name,
                description,
                access_unit_id);
    }

    @Tool(description = "Create a catalog in Huawei iMaster DME. "
            + "Use this tool when the user asks to create a DME Catalog "
            + "inside an existing Project.")
    String dme_create_catalog(
            @ToolArg(description = "Name of the DME Catalog. "
                    + "Only letters, numbers and underscores are allowed. "
                    + "Length must be between 1 and 64 characters.") String name,

            @ToolArg(description = "Catalog type. Allowed values are: "
                    + "star_rocks, dme_metadata, omni_format_iceberg.") String type,

            @ToolArg(description = "ID of the existing DME Project "
                    + "that this Catalog belongs to.") String project_id,

            @ToolArg(description = "Optional description of the Catalog.", required = false) String description) {

        return dmeApiClient.createCatalog(
                name,
                type,
                project_id,
                description);
    }

    @Tool(description = "Create a schema in Huawei iMaster DME. "
            + "Use this tool when the user asks to create a Schema "
            + "inside an existing DME Catalog.")
    String dme_create_schema(
            @ToolArg(description = "Name of the existing DME Catalog "
                    + "that this Schema belongs to.") String catalog_name,

            @ToolArg(description = "Name of the DME Schema. "
                    + "Only letters, numbers and underscores are allowed. "
                    + "Length must be between 1 and 64 characters.") String name,

            @ToolArg(description = "ID of the DME Project "
                    + "that this Schema belongs to.") String project_id,

            @ToolArg(description = "Optional description of the Schema.", required = false) String description,

            @ToolArg(description = "Optional datasource ID for the Schema storage location. "
                    + "Required when creating an Iceberg Schema.", required = false) String datasource_id,

            @ToolArg(description = "Optional data entity access unit ID for the Schema storage location. "
                    + "Required for an Omni Format Iceberg Schema.", required = false) String storage_unit_id,

            @ToolArg(description = "Optional storage path for the Schema. "
                    + "Required for an Omni Format Iceberg Schema.", required = false) String storage_path) {

        return dmeApiClient.createSchema(
                catalog_name,
                name,
                project_id,
                description,
                datasource_id,
                storage_unit_id,
                storage_path);
    }

    @Tool(description = "Create a table in Huawei iMaster DME. "
            + "Use this tool when the user asks to create a Table "
            + "inside an existing DME Schema. "
            + "The column_list parameter supports multiple columns.")
    String dme_create_table(
            @ToolArg(description = "Name of the existing DME Catalog.") String catalog_name,

            @ToolArg(description = "Name of the existing DME Schema.") String schema_name,

            @ToolArg(description = "Name of the DME Table. "
                    + "Only letters, numbers and underscores are allowed. "
                    + "Length must be between 1 and 64 characters.") String name,

            @ToolArg(description = "ID of the DME Project "
                    + "that this Table belongs to.") String project_id,

            @ToolArg(description = "Optional description of the Table.", required = false) String description,

            @ToolArg(description = "Datasource ID used by the Table storage location.") String datasource_id,

            @ToolArg(description = "Optional data entity access unit ID. "
                    + "Required when creating an Iceberg Table.", required = false) String storage_unit_id,

            @ToolArg(description = "Optional storage path of the Table. "
                    + "For Iceberg, the path must be located under "
                    + "the selected storage unit path.", required = false) String storage_path,

            @ToolArg(description = "Columns of the Table. "
                    + "Each column must contain name and data_type. "
                    + "Optional fields include data_length, description, "
                    + "is_primary, is_nullable, default_value, "
                    + "element_data_type and is_dominant.") List<DmeColumnDefinition> column_list) {

        return dmeApiClient.createTable(
                catalog_name,
                schema_name,
                name,
                project_id,
                description,
                datasource_id,
                storage_unit_id,
                storage_path,
                column_list);
    }

    @Tool(description = "Execute a SELECT SQL query against Huawei iMaster DME through the Avatica JDBC service. "
            + "Use this tool to read actual data from DME managed tables. "
            + "The SQL should normally use a fully qualified table name: catalog.schema.table.")
    String dme_read_query(
            @ToolArg(description = "SELECT SQL query to execute against DME.") String query) {

        try {
            return dmeAvaticaClient.readQuery(query);
        } catch (RuntimeException e) {
            Throwable cause = e;

            while (cause != null) {
                if (cause instanceof AvaticaSqlException avaticaException) {

                    if (avaticaException.getErrorCode() == 400) {
                        var exceptions = avaticaException.getStackTraces();

                        if (exceptions != null && !exceptions.isEmpty()) {
                            String message = exceptions.get(exceptions.size() - 1);

                            throw new McpException(
                                    message,
                                    JsonRpcErrorCodes.INTERNAL_ERROR);
                        }
                    }

                    break;
                }

                cause = cause.getCause();
            }

            throw e;
        }
    }

    @Tool(description = "Execute an INSERT, UPDATE or DELETE SQL statement against Huawei iMaster DME "
            + "through the Avatica JDBC service. "
            + "Use this tool to modify actual table data. "
            + "Do not use this tool for SELECT queries.")
    String dme_write_query(
            @ToolArg(description = "INSERT, UPDATE or DELETE SQL statement to execute against DME.") String query) {

        return dmeAvaticaClient.writeQuery(query);
    }
}
