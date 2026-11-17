package dev.phoenix.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.util.Set;
import java.util.stream.Collectors;

/** Checks that a model's reply is JSON and satisfies the schema the caller asked for. */
public final class JsonSchemaGuard {

    /** The parsed reply, or the reason it was rejected (one of the two is null). */
    public record Verdict(JsonNode json, String problem) {
        public boolean valid() {
            return problem == null;
        }
    }

    private final ObjectMapper mapper;
    private final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    public JsonSchemaGuard(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Verdict check(JsonNode schema, String reply) {
        JsonNode parsed;
        try {
            parsed = mapper.readTree(reply);
        } catch (JsonProcessingException e) {
            return new Verdict(null, "the reply is not valid JSON: " + e.getOriginalMessage());
        }
        if (parsed == null || parsed.isMissingNode()) {
            return new Verdict(null, "the reply is empty");
        }
        JsonSchema compiled = factory.getSchema(schema);
        Set<ValidationMessage> errors = compiled.validate(parsed);
        if (errors.isEmpty()) {
            return new Verdict(parsed, null);
        }
        return new Verdict(null, "the reply does not match the schema: "
                + errors.stream().map(ValidationMessage::getMessage).sorted().collect(Collectors.joining("; ")));
    }
}
