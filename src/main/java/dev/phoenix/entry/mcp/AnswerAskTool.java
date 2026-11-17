package dev.phoenix.entry.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.phoenix.channel.AskService;
import dev.phoenix.domain.Ask;
import org.springframework.stereotype.Component;

@Component
public class AnswerAskTool implements McpTool {

    private static final JsonNode SCHEMA = Schemas.parse("""
            {"type": "object", "required": ["ask_id", "answer"], "properties": {
               "ask_id": {"type": "integer"}, "answer": {"type": "string", "description": "The user's answer, in their words."}}}""");

    private final AskService asks;
    private final ObjectMapper mapper;

    public AnswerAskTool(AskService asks, ObjectMapper mapper) {
        this.asks = asks;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "answer_ask";
    }

    @Override
    public String description() {
        return "Record the user's answer to an ask from list_asks. Only pass what the user actually said.";
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public JsonNode call(ToolArgs args) {
        Ask answered = asks.answer(args.requireLong("ask_id"), args.requireString("answer"));
        ObjectNode out = mapper.createObjectNode();
        out.put("ask_id", answered.id());
        out.put("status", answered.status().name());
        return out;
    }
}
