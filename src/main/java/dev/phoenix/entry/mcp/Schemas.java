package dev.phoenix.entry.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Parses the JSON Schema text blocks the tools declare, failing loudly at startup if one is malformed. */
final class Schemas {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Schemas() {
    }

    static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid tool schema: " + e.getOriginalMessage(), e);
        }
    }
}
