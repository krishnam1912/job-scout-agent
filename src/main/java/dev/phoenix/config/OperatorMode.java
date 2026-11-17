package dev.phoenix.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Who makes the judgment calls. {@code CLAUDE_CODE}: the user's Claude chat is the operator and the engine
 * trusts approvals typed there. {@code STANDALONE}: no chat operator, so the engine itself must route work to
 * configured LLM providers and never exposes an approval tool to an LLM.
 */
public enum OperatorMode {
    CLAUDE_CODE("claude-code"),
    STANDALONE("standalone");

    private final String configValue;

    OperatorMode(String configValue) {
        this.configValue = configValue;
    }

    @JsonValue
    public String configValue() {
        return configValue;
    }

    @JsonCreator
    public static OperatorMode fromConfig(String value) {
        return Arrays.stream(values())
                .filter(m -> m.configValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown operator '" + value + "' (expected claude-code or standalone)"));
    }
}
