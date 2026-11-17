package io.quarkiverse.mcp.servers.dme;

public record DmeColumnDefinition(
        String name,
        String data_type,
        String data_length,
        String element_data_type,
        Boolean is_dominant,
        Boolean is_primary,
        Boolean is_nullable,
        String default_value,
        String description) {
}