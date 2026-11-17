package dev.phoenix.entry.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.phoenix.error.UserErrorException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Typed, validated access to a tool call's arguments; every problem is a user error naming the argument. */
public final class ToolArgs {

    private final JsonNode node;

    public ToolArgs(JsonNode node) {
        this.node = node == null || node.isNull() ? JsonNodeFactory.instance.objectNode() : node;
    }

    public long requireLong(String name) {
        JsonNode v = node.get(name);
        if (v == null || !v.canConvertToLong() || !v.isIntegralNumber()) {
            throw new UserErrorException("'" + name + "' is required and must be an integer");
        }
        return v.asLong();
    }

    public Optional<Long> optLong(String name) {
        JsonNode v = node.get(name);
        if (v == null || v.isNull()) {
            return Optional.empty();
        }
        return Optional.of(requireLong(name));
    }

    public int optInt(String name, int defaultValue) {
        return optLong(name).map(Long::intValue).orElse(defaultValue);
    }

    public double requireDouble(String name) {
        JsonNode v = node.get(name);
        if (v == null || !v.isNumber()) {
            throw new UserErrorException("'" + name + "' is required and must be a number");
        }
        return v.asDouble();
    }

    public String requireString(String name) {
        return optString(name).filter(s -> !s.isBlank())
                .orElseThrow(() -> new UserErrorException("'" + name + "' is required and must be a non-empty string"));
    }

    public Optional<String> optString(String name) {
        JsonNode v = node.get(name);
        if (v == null || v.isNull()) {
            return Optional.empty();
        }
        if (!v.isTextual()) {
            throw new UserErrorException("'" + name + "' must be a string");
        }
        return Optional.of(v.asText());
    }

    public boolean optBoolean(String name, boolean defaultValue) {
        JsonNode v = node.get(name);
        if (v == null || v.isNull()) {
            return defaultValue;
        }
        if (!v.isBoolean()) {
            throw new UserErrorException("'" + name + "' must be true or false");
        }
        return v.asBoolean();
    }

    public List<Long> optLongList(String name) {
        JsonNode v = node.get(name);
        if (v == null || v.isNull()) {
            return List.of();
        }
        if (!v.isArray()) {
            throw new UserErrorException("'" + name + "' must be an array of integers");
        }
        List<Long> out = new ArrayList<>();
        for (JsonNode item : v) {
            if (!item.isIntegralNumber()) {
                throw new UserErrorException("'" + name + "' must contain only integers");
            }
            out.add(item.asLong());
        }
        return out;
    }

    /** Each element of the array argument as its own {@link ToolArgs}. */
    public List<ToolArgs> requireObjects(String name) {
        JsonNode v = node.get(name);
        if (v == null || !v.isArray() || v.isEmpty()) {
            throw new UserErrorException("'" + name + "' is required and must be a non-empty array");
        }
        List<ToolArgs> out = new ArrayList<>();
        for (JsonNode item : v) {
            if (!item.isObject()) {
                throw new UserErrorException("'" + name + "' must contain objects");
            }
            out.add(new ToolArgs(item));
        }
        return out;
    }
}
