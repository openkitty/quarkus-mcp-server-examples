package io.quarkiverse.mcp.servers.restful;

import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;

public class MCPServerREST {

    @Inject
    RestApiClient restApiClient;

    @Tool(description = "Check whether the REST MCP tools are available.")
    String rest_ping() {

        return """
                {
                  "status": "ok",
                  "message": "REST MCP tools are available"
                }
                """;
    }

    @Tool(description = "Get information about the configured REST API service. "
            + "Use this tool before calling REST APIs to check the API name, "
            + "base URL, endpoint count and whether write operations are enabled.")
    String rest_api_info() {

        return restApiClient.apiInfo();
    }

    @Tool(description = "List all REST API endpoints available to DataAgent. "
            + "Use this tool to discover which REST operations can be performed, "
            + "including their names, HTTP methods, paths and descriptions.")
    String rest_list_endpoints() {

        return restApiClient.listEndpoints();
    }

    @Tool(description = "Describe one REST API endpoint in detail. "
            + "This returns its HTTP method, path, path parameters, "
            + "query parameters, request body definition and example. "
            + "Use an endpoint name returned by rest_list_endpoints.")
    String rest_describe_endpoint(

            @ToolArg(description = "Endpoint name returned by rest_list_endpoints. "
                    + "For example: get_record, create_record, update_record or delete_record.") String endpoint_name) {

        return restApiClient.describeEndpoint(
                endpoint_name);
    }

    @Tool(description = "Call a REST API endpoint by its configured endpoint name. "
            + "This is the recommended REST calling tool for DataAgent. "
            + "The HTTP method and path are automatically loaded from rest-api.json. "
            + "Use rest_list_endpoints and rest_describe_endpoint to discover endpoint names "
            + "and required parameters.")
    String rest_call_endpoint(

            @ToolArg(description = "Endpoint name defined in rest-api.json. "
                    + "For example: get_record, create_record, update_record or delete_record.") String endpoint_name,

            @ToolArg(description = "Optional REST path parameters as a JSON object. "
                    + "For example: {\"id\":\"123\"}", required = false) String path_params,

            @ToolArg(description = "Optional REST query parameters as a JSON object. "
                    + "For example: {\"page\":1,\"size\":20}", required = false) String query_params,

            @ToolArg(description = "Optional request body. "
                    + "Normally this should be a JSON string for POST, PUT or PATCH.", required = false) String body) {

        return restApiClient.callEndpoint(
                endpoint_name,
                path_params,
                query_params,
                body);
    }

    @Tool(description = "Call the configured REST API service directly using an HTTP method and path. "
            + "Supports GET, POST, PUT, PATCH and DELETE. "
            + "The path must be relative to the configured REST base URL. "
            + "This is a lower-level REST tool. "
            + "Prefer rest_call_endpoint when the REST endpoint is defined in rest-api.json.")
    String rest_request(

            @ToolArg(description = "HTTP method. Supported values: GET, POST, PUT, PATCH, DELETE") String method,

            @ToolArg(description = "Relative REST API path. "
                    + "For example: records/123 or records") String path,

            @ToolArg(description = "Optional query parameters as a JSON object. "
                    + "For example: {\"page\":1,\"size\":20}", required = false) String query_params,

            @ToolArg(description = "Optional HTTP request body. "
                    + "Normally this should be a JSON string for POST, PUT or PATCH.", required = false) String body) {

        return restApiClient.request(
                method,
                path,
                query_params,
                body);
    }
}