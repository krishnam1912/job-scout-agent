package dev.phoenix.config;

import dev.phoenix.error.ConfigException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoenixConfigLoaderTest {

    private final PhoenixConfigLoader loader = new PhoenixConfigLoader();

    private PhoenixProperties load(Path dir, String yaml) throws IOException {
        Files.writeString(dir.resolve("phoenix.yml"), yaml);
        return loader.load(new PhoenixHome(dir));
    }

    @Test
    void aMissingFileMeansDefaults(@TempDir Path dir) {
        PhoenixProperties props = loader.load(new PhoenixHome(dir));

        assertThat(props.operator()).isEqualTo(OperatorMode.CLAUDE_CODE);
        assertThat(props.dedupe().similarityThreshold()).isEqualTo(0.9);
        assertThat(props.dedupe().companyCooldownDays()).isEqualTo(60);
        assertThat(props.llm().providers()).isEmpty();
    }

    @Test
    void aFullFileIsParsed(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("sources.json"), "{}");
        Files.writeString(dir.resolve("filters.json"), "{}");
        Files.createDirectories(dir.resolve("queue"));

        PhoenixProperties props = load(dir, """
                operator: standalone
                sources: sources.json
                filters: filters.json
                legacy:
                  queue-dir: queue
                dedupe:
                  similarity-threshold: 0.8
                  company-cooldown-days: 30
                llm:
                  providers:
                    local: {type: openai-compatible, base-url: "http://localhost:11434/v1", model: "m", api-key-env: ""}
                  routing: {extract: local}
                """);

        assertThat(props.operator()).isEqualTo(OperatorMode.STANDALONE);
        assertThat(props.sources()).isEqualTo("sources.json");
        assertThat(props.legacy().queueDir()).isEqualTo("queue");
        assertThat(props.dedupe().similarityThreshold()).isEqualTo(0.8);
        assertThat(props.llm().routing()).containsEntry("extract", "local");
    }

    @Test
    void aReferencedFileThatIsMissingFailsFast(@TempDir Path dir) {
        assertThatThrownBy(() -> load(dir, "sources: nope.json\n"))
                .isInstanceOf(ConfigException.class).hasMessageContaining("sources").hasMessageContaining("nope.json");
    }

    @Test
    void invalidYamlIsReportedWithTheFileName(@TempDir Path dir) {
        assertThatThrownBy(() -> load(dir, "operator: [unclosed"))
                .isInstanceOf(ConfigException.class).hasMessageContaining("phoenix.yml");
    }

    @Test
    void anUnknownKeyIsRejected(@TempDir Path dir) {
        assertThatThrownBy(() -> load(dir, "sourcez: x\n")).isInstanceOf(ConfigException.class);
    }

    @Test
    void anUnknownOperatorIsRejected(@TempDir Path dir) {
        assertThatThrownBy(() -> load(dir, "operator: robot\n")).isInstanceOf(ConfigException.class);
    }

    @Test
    void aThresholdOutsideZeroToOneIsRejected(@TempDir Path dir) {
        assertThatThrownBy(() -> load(dir, "dedupe:\n  similarity-threshold: 1.5\n"))
                .isInstanceOf(ConfigException.class).hasMessageContaining("similarity-threshold");
    }

    @Test
    void routingToAnUnknownProviderIsRejected(@TempDir Path dir) {
        assertThatThrownBy(() -> load(dir, "llm:\n  routing: {extract: ghost}\n"))
                .isInstanceOf(ConfigException.class).hasMessageContaining("ghost");
    }

    @Test
    void routingAnUnknownTaskIsRejected(@TempDir Path dir) {
        String yaml = """
                llm:
                  providers:
                    local: {type: openai-compatible, base-url: "http://x/v1", model: "m"}
                  routing: {dance: local}
                """;

        assertThatThrownBy(() -> load(dir, yaml)).isInstanceOf(ConfigException.class).hasMessageContaining("dance");
    }

    @Test
    void anUnsupportedProviderTypeIsRejected(@TempDir Path dir) {
        String yaml = """
                llm:
                  providers:
                    odd: {type: carrier-pigeon, base-url: "http://x/v1", model: "m"}
                """;

        assertThatThrownBy(() -> load(dir, yaml)).isInstanceOf(ConfigException.class).hasMessageContaining("carrier-pigeon");
    }
}
