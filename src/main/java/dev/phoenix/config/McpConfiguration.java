package dev.phoenix.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.phoenix.entry.JsonViews;
import dev.phoenix.entry.mcp.McpTool;
import dev.phoenix.entry.mcp.McpToolCatalog;
import dev.phoenix.entry.mcp.SdkMcpServer;
import dev.phoenix.entry.mcp.ToolInvoker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** The MCP server, with the tool set decided by the operator mode. */
@Configuration(proxyBeanMethods = false)
public class McpConfiguration {

    private static final String VERSION = "0.1.0";

    @Bean
    JsonViews jsonViews(ObjectMapper mapper) {
        return new JsonViews(mapper);
    }

    @Bean
    McpToolCatalog mcpToolCatalog(PhoenixProperties properties, List<McpTool> tools) {
        return new McpToolCatalog(properties.operator(), tools);
    }

    @Bean
    ToolInvoker toolInvoker(ObjectMapper mapper) {
        return new ToolInvoker(mapper);
    }

    @Bean
    SdkMcpServer sdkMcpServer(McpToolCatalog catalog, ToolInvoker invoker, ObjectMapper mapper) {
        return new SdkMcpServer(catalog, invoker, mapper, VERSION);
    }
}
