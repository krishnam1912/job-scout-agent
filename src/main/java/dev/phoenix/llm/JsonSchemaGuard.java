package dev.phoenix.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;

import java.util.List;
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
    private final SchemaRegistry registry = SchemaRegistry.withDialect(Dialects.getDraft202012());

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
        Schema compiled = registry.getSchema(schema);
        List<Error> errors = compiled.validate(parsed);
        if (errors.isEmpty()) {
            return new Verdict(parsed, null);
        }
        return new Verdict(null, "the reply does not match the schema: "
                + errors.stream().map(Error::getMessage).sorted().collect(Collectors.joining("; ")));
    }
}
