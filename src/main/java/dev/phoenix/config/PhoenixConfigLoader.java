package dev.phoenix.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.phoenix.error.ConfigException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Function;

/** Reads and validates {@code phoenix.yml}; a missing file means all defaults. */
public final class PhoenixConfigLoader {

    private static final Set<String> ROUTED_TASKS = Set.of("extract", "score", "answer");
    private static final String OPENAI_COMPATIBLE = "openai-compatible";

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory())
            .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    public PhoenixProperties load(PhoenixHome home) {
        Path file = home.configFile();
        if (!Files.exists(file)) {
            return PhoenixProperties.defaults();
        }
        PhoenixProperties props;
        try {
            props = yaml.readValue(file.toFile(), PhoenixProperties.class);
        } catch (IOException | IllegalArgumentException e) {
            throw new ConfigException("Invalid " + file + ": " + e.getMessage(), e);
        }
        if (props == null) {
            return PhoenixProperties.defaults();
        }
        validate(props, home);
        return props;
    }

    private void validate(PhoenixProperties props, PhoenixHome home) {
        Function<String, Path> resolve = home::resolvePath;
        requireFile("sources", props.sources(), resolve);
        requireFile("filters", props.filters(), resolve);
        requireFile("seen-file", props.seenFile(), resolve);
        requireFile("legacy.leads-csv", props.legacy().leadsCsv(), resolve);
        requireFile("legacy.applications-md", props.legacy().applicationsMd(), resolve);
        requireFile("legacy.referrals-csv", props.legacy().referralsCsv(), resolve);
        requireDirectory("legacy.queue-dir", props.legacy().queueDir(), resolve);
        validateDedupe(props.dedupe());
        validateLlm(props.llm());
    }

    private void requireFile(String key, String configured, Function<String, Path> resolve) {
        if (configured == null || configured.isBlank()) {
            return;
        }
        Path p = resolve.apply(configured);
        if (!Files.isRegularFile(p)) {
            throw new ConfigException("phoenix.yml: '" + key + "' points to a missing file: " + p);
        }
    }

    private void requireDirectory(String key, String configured, Function<String, Path> resolve) {
        if (configured == null || configured.isBlank()) {
            return;
        }
        Path p = resolve.apply(configured);
        if (!Files.isDirectory(p)) {
            throw new ConfigException("phoenix.yml: '" + key + "' points to a missing directory: " + p);
        }
    }

    private void validateDedupe(PhoenixProperties.Dedupe d) {
        if (d.similarityThreshold() < 0 || d.similarityThreshold() > 1) {
            throw new ConfigException("phoenix.yml: dedupe.similarity-threshold must be between 0 and 1");
        }
        if (d.companyCooldownDays() < 0) {
            throw new ConfigException("phoenix.yml: dedupe.company-cooldown-days must not be negative");
        }
    }

    private void validateLlm(PhoenixProperties.Llm llm) {
        llm.providers().forEach((name, p) -> {
            if (!OPENAI_COMPATIBLE.equals(p.type())) {
                throw new ConfigException("phoenix.yml: llm provider '" + name + "' has unsupported type '"
                        + p.type() + "' (supported: " + OPENAI_COMPATIBLE + ")");
            }
            if (p.baseUrl() == null || p.baseUrl().isBlank() || p.model() == null || p.model().isBlank()) {
                throw new ConfigException("phoenix.yml: llm provider '" + name + "' needs base-url and model");
            }
        });
        llm.routing().forEach((task, provider) -> {
            if (!ROUTED_TASKS.contains(task)) {
                throw new ConfigException("phoenix.yml: unknown llm routing task '" + task
                        + "' (expected one of " + ROUTED_TASKS + ")");
            }
            if (!llm.providers().containsKey(provider)) {
                throw new ConfigException("phoenix.yml: llm routing '" + task + "' references unknown provider '"
                        + provider + "'");
            }
        });
    }
}
