package dev.phoenix.llm;

import com.fasterxml.jackson.databind.JsonNode;

/** A model reply: the raw {@code text}, and {@code json} (parsed and schema-checked) when a schema was requested. */
public record LlmResponse(String text, JsonNode json) {
}
