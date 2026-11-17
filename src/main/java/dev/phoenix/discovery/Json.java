package dev.phoenix.discovery;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/** Null-tolerant JSON reads: feeds omit fields freely, and a missing field means "empty", not an error. */
final class Json {

    private Json() {
    }

    /** The field as text; "" when missing or null. */
    static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    /** The field as an array; an empty list when missing or not an array. */
    static List<JsonNode> list(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        List<JsonNode> out = new ArrayList<>();
        if (value != null && value.isArray()) {
            value.forEach(out::add);
        }
        return out;
    }

    /** The field as an object node, or null. */
    static JsonNode object(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isObject() ? value : null;
    }

    /** JavaScript-style truthiness for flags such as isRemote. */
    static boolean truthy(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return false;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        return !value.asText().isEmpty() && !value.asText().equals("0");
    }
}
