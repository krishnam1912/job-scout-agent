package dev.phoenix.llm;

import dev.phoenix.error.UserErrorException;

import java.util.Arrays;
import java.util.Locale;

/** The kinds of judgment work that can be routed to a model; the lower-case name is the key in phoenix.yml. */
public enum LlmTask {
    EXTRACT, SCORE, ANSWER;

    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static LlmTask parse(String text) {
        return Arrays.stream(values()).filter(t -> t.configKey().equalsIgnoreCase(text == null ? "" : text.strip()))
                .findFirst().orElseThrow(() -> new UserErrorException("Unknown task '" + text
                        + "' (expected extract, score or answer)"));
    }
}
