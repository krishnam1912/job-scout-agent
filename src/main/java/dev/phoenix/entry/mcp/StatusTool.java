package dev.phoenix.entry.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import dev.phoenix.entry.JsonViews;
import dev.phoenix.service.StatusService;
import org.springframework.stereotype.Component;

@Component
public class StatusTool implements McpTool {

    private static final JsonNode SCHEMA = Schemas.parse("{\"type\": \"object\", \"properties\": {}}");

    private final StatusService status;
    private final JsonViews views;

    public StatusTool(StatusService status, JsonViews views) {
        this.status = status;
        this.views = views;
    }

    @Override
    public String name() {
        return "status";
    }

    @Override
    public String description() {
        return "Pipeline snapshot: job counts per state, open asks, jobs at risk (queued over 2 days, blocked over 1 day, "
                + "closing within 72 hours) and the last scan.";
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public JsonNode call(ToolArgs args) {
        return views.status(status.report());
    }
}
