package dev.phoenix.discovery;

import dev.phoenix.config.PhoenixHome;
import dev.phoenix.config.PhoenixProperties;
import dev.phoenix.error.ConfigException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscoveryConfigLoaderTest {

    private DiscoveryConfigLoader loader(Path dir, String sources) throws IOException {
        Files.writeString(dir.resolve("sources.json"), sources);
        Files.copy(Path.of("src/test/resources/fixtures/filters.json"), dir.resolve("filters.json"));
        return new DiscoveryConfigLoader(new PhoenixHome(dir),
                new PhoenixProperties(null, "sources.json", "filters.json", null, null, null, null));
    }

    @Test
    void sourcesKeepTheirFileOrderAndFlagsAreRead(@TempDir Path dir) throws IOException {
        DiscoveryConfig config = loader(dir, "{\"_comment\": \"x\", \"greenhouse\": [\"zeta\", \"Alpha\"], \"amazon\": true, \"atlassian\": false}").load();

        assertThat(config.sources().greenhouse()).containsExactly("zeta", "Alpha");
        assertThat(config.sources().amazon()).isTrue();
        assertThat(config.sources().atlassian()).isFalse();
        assertThat(config.amazonEnabled()).isTrue();
        assertThat(config.filters().filters().maxMinYears()).isEqualTo(6);
    }

    @Test
    void localBoardsAreMergedAndTheUnionIsSortedByName(@TempDir Path dir) throws IOException {
        DiscoveryConfigLoader loader = loader(dir, "{\"greenhouse\": [\"zeta\", \"alpha\"]}");
        Files.createDirectories(dir.resolve("profile"));
        Files.writeString(dir.resolve("profile/sources.local.json"), "{\"greenhouse\": [\"Mid\", \"alpha\"]}");

        assertThat(loader.load().sources().greenhouse()).containsExactly("alpha", "Mid", "zeta");
    }

    @Test
    void amazonNeedsBothTheSourceFlagAndAFiltersBlock(@TempDir Path dir) throws IOException {
        DiscoveryConfig off = loader(dir, "{\"amazon\": false}").load();

        assertThat(off.amazonEnabled()).isFalse();
    }

    @Test
    void scanningWithoutSourcesOrFiltersConfiguredIsAUserError() {
        DiscoveryConfigLoader loader = new DiscoveryConfigLoader(new PhoenixHome(Path.of("/nowhere")), PhoenixProperties.defaults());

        assertThatThrownBy(loader::load).isInstanceOf(ConfigException.class).hasMessageContaining("filters");
    }
}
