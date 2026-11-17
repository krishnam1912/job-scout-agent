package dev.phoenix.config;

import java.util.Map;

/** Typed view of {@code phoenix.yml}. Unset optional sections hold their documented defaults. */
public record PhoenixProperties(
        OperatorMode operator,
        String sources,
        String filters,
        String seenFile,
        Legacy legacy,
        Dedupe dedupe,
        Llm llm) {

    public PhoenixProperties {
        operator = operator == null ? OperatorMode.CLAUDE_CODE : operator;
        legacy = legacy == null ? new Legacy(null, null, null, null) : legacy;
        dedupe = dedupe == null ? new Dedupe(null, null) : dedupe;
        llm = llm == null ? new Llm(null, null) : llm;
    }

    public static PhoenixProperties defaults() {
        return new PhoenixProperties(null, null, null, null, null, null, null);
    }

    /** Paths of the legacy files the one-off importer reads; every entry is optional. */
    public record Legacy(String leadsCsv, String applicationsMd, String referralsCsv, String queueDir) {
    }

    public record Dedupe(Double similarityThreshold, Integer companyCooldownDays) {

        public static final double DEFAULT_THRESHOLD = 0.9;
        public static final int DEFAULT_COOLDOWN_DAYS = 60;

        public Dedupe {
            similarityThreshold = similarityThreshold == null ? DEFAULT_THRESHOLD : similarityThreshold;
            companyCooldownDays = companyCooldownDays == null ? DEFAULT_COOLDOWN_DAYS : companyCooldownDays;
        }
    }

    public record Llm(Map<String, Provider> providers, Map<String, String> routing) {

        public Llm {
            providers = providers == null ? Map.of() : Map.copyOf(providers);
            routing = routing == null ? Map.of() : Map.copyOf(routing);
        }
    }

    public record Provider(String type, String baseUrl, String model, String apiKeyEnv) {
    }
}
