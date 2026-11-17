package dev.phoenix.llm;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One model call. {@code schema} (nullable) is a JSON Schema the reply must satisfy; providers enforce it, so
 * callers get either valid JSON or a typed failure.
 */
public record LlmRequest(String systemPrompt, String userText, JsonNode schema, double temperature, int maxTokens) {

    public static final double DEFAULT_TEMPERATURE = 0.0;
    public static final int DEFAULT_MAX_TOKENS = 1024;

    public static LlmRequest of(String systemPrompt, String userText, JsonNode schema) {
        return new LlmRequest(systemPrompt, userText, schema, DEFAULT_TEMPERATURE, DEFAULT_MAX_TOKENS);
    }
}
