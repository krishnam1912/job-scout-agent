package dev.phoenix.entry.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.phoenix.channel.AskService;
import dev.phoenix.domain.AskStatus;
import dev.phoenix.entry.JsonViews;
import dev.phoenix.error.UserErrorException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class ListAsksTool implements McpTool {

    private static final JsonNode SCHEMA = Schemas.parse("""
            {"type": "object", "properties": {
               "status": {"type": "string", "enum": ["open", "offered", "answered"], "description": "Default: everything still waiting (open and offered)."}}}""");

    private final AskService asks;
    private final JsonViews views;
    private final ObjectMapper mapper;

    public ListAsksTool(AskService asks, JsonViews views, ObjectMapper mapper) {
        this.asks = asks;
        this.views = views;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "list_asks";
    }

    @Override
    public String description() {
        return "The asks inbox: questions the engine needs the user to answer (blocked applications, missing answers). "
                + "Relay them to the user, highest priority first, and record their reply with answer_ask.";
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public JsonNode call(ToolArgs args) {
        List<AskStatus> statuses = args.optString("status").map(ListAsksTool::parse).map(List::of).orElse(List.of());
        ObjectNode out = mapper.createObjectNode();
        out.set("asks", views.array(asks.list(statuses).stream().map(views::ask).toList()));
        return out;
    }

    private static AskStatus parse(String text) {
        try {
            return AskStatus.valueOf(text.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new UserErrorException("'status' must be open, offered or answered");
        }
    }
}
