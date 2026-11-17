package dev.phoenix.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.phoenix.http.HttpFetchException;
import dev.phoenix.http.HttpFetcher;

import java.util.Map;
import java.util.function.Function;

/**
 * Chat-completions client for any OpenAI-compatible server: Ollama and LM Studio on localhost, or OpenAI itself.
 * With a schema it first asks the server to enforce it ({@code json_schema}); servers that refuse fall back to
 * {@code json_object} with the schema in the prompt. Either way the reply is validated here, and an invalid reply
 * is sent back with the validation error at most {@value #MAX_RETRIES} times.
 */
public final class OpenAiCompatibleProvider implements LlmProvider {

    static final int MAX_RETRIES = 2;
    private static final int STATUS_BAD_REQUEST = 400;
    private static final int STATUS_UNPROCESSABLE = 422;

    private enum Format { NONE, JSON_SCHEMA, JSON_OBJECT }

    private final String name;
    private final String baseUrl;
    private final String model;
    private final String apiKeyEnv;
    private final Function<String, String> env;
    private final HttpFetcher http;
    private final ObjectMapper mapper;
    private final JsonSchemaGuard guard;

    /**
     * @param apiKeyEnv name of the environment variable holding the API key, blank for none; the key is read per
     *                  call and never logged or stored
     */
    public OpenAiCompatibleProvider(String name, String baseUrl, String model, String apiKeyEnv,
                                    Function<String, String> env, HttpFetcher http, ObjectMapper mapper,
                                    JsonSchemaGuard guard) {
        this.name = name;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.apiKeyEnv = apiKeyEnv == null ? "" : apiKeyEnv;
        this.env = env;
        this.http = http;
        this.mapper = mapper;
        this.guard = guard;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        Format format = request.schema() == null ? Format.NONE : Format.JSON_SCHEMA;
        ArrayNode retryTurns = mapper.createArrayNode();
        String lastProblem = null;
        int validationFailures = 0;
        while (true) {
            String reply;
            try {
                reply = send(request, format, retryTurns);
            } catch (HttpFetchException e) {
                if (format == Format.JSON_SCHEMA && (e.status() == STATUS_BAD_REQUEST || e.status() == STATUS_UNPROCESSABLE)) {
                    format = Format.JSON_OBJECT;
                    continue;
                }
                throw new LlmException("Provider '" + name + "' failed: " + e.getMessage(), e);
            }
            if (request.schema() == null) {
                return new LlmResponse(reply, null);
            }
            JsonSchemaGuard.Verdict verdict = guard.check(request.schema(), reply);
            if (verdict.valid()) {
                return new LlmResponse(reply, verdict.json());
            }
            lastProblem = verdict.problem();
            if (validationFailures++ >= MAX_RETRIES) {
                throw new LlmException("Provider '" + name + "' gave no valid reply after " + (MAX_RETRIES + 1)
                        + " attempts; last problem: " + lastProblem);
            }
            retryTurns.add(message("assistant", reply));
            retryTurns.add(message("user", "That reply was rejected: " + lastProblem
                    + ". Reply again with only valid JSON that matches the schema."));
        }
    }

    private String send(LlmRequest request, Format format, ArrayNode retryTurns) {
        String body;
        try {
            body = mapper.writeValueAsString(buildBody(request, format, retryTurns));
        } catch (JsonProcessingException e) {
            throw new LlmException("Cannot build the request for provider '" + name + "'", e);
        }
        String response = http.postJson(baseUrl + "/chat/completions", headers(), body);
        try {
            JsonNode content = mapper.readTree(response).path("choices").path(0).path("message").path("content");
            if (!content.isTextual()) {
                throw new LlmException("Provider '" + name + "' returned no message content");
            }
            return content.asText();
        } catch (JsonProcessingException e) {
            throw new LlmException("Provider '" + name + "' returned something that is not JSON", e);
        }
    }

    private Map<String, String> headers() {
        if (apiKeyEnv.isBlank()) {
            return Map.of();
        }
        String key = env.apply(apiKeyEnv);
        if (key == null || key.isBlank()) {
            throw new LlmException("Provider '" + name + "' needs the environment variable " + apiKeyEnv + ", which is not set");
        }
        return Map.of("Authorization", "Bearer " + key);
    }

    private ObjectNode buildBody(LlmRequest request, Format format, ArrayNode retryTurns) throws JsonProcessingException {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", request.temperature());
        body.put("max_tokens", request.maxTokens());
        ArrayNode messages = body.putArray("messages");
        String system = request.systemPrompt() == null ? "" : request.systemPrompt();
        if (format == Format.JSON_OBJECT) {
            system += "\nReply with a single JSON object that matches this JSON schema: "
                    + mapper.writeValueAsString(request.schema());
        }
        if (!system.isBlank()) {
            messages.add(message("system", system));
        }
        messages.add(message("user", request.userText()));
        messages.addAll(retryTurns);
        if (format == Format.JSON_SCHEMA) {
            ObjectNode schemaFormat = body.putObject("response_format");
            schemaFormat.put("type", "json_schema");
            ObjectNode spec = schemaFormat.putObject("json_schema");
            spec.put("name", "reply");
            spec.put("strict", true);
            spec.set("schema", request.schema());
        } else if (format == Format.JSON_OBJECT) {
            body.putObject("response_format").put("type", "json_object");
        }
        return body;
    }

    private ObjectNode message(String role, String content) {
        ObjectNode message = mapper.createObjectNode();
        message.put("role", role);
        message.put("content", content);
        return message;
    }
}
