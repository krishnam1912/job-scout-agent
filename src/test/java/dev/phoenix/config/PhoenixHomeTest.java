package dev.phoenix.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PhoenixHomeTest {

    private static final Path USER_HOME = Path.of("/home/someone");

    @Test
    void anExplicitHomeBeatsTheEnvironment() {
        PhoenixHome home = PhoenixHome.resolve("/data/a", Map.of("PHOENIX_HOME", "/data/b"), USER_HOME);

        assertThat(home.root()).isEqualTo(Path.of("/data/a"));
    }

    @Test
    void theEnvironmentBeatsTheDefault() {
        PhoenixHome home = PhoenixHome.resolve(null, Map.of("PHOENIX_HOME", "/data/b"), USER_HOME);

        assertThat(home.root()).isEqualTo(Path.of("/data/b"));
    }

    @Test
    void theDefaultIsDotPhoenixInTheUserHome() {
        PhoenixHome home = PhoenixHome.resolve(" ", Map.of(), USER_HOME);

        assertThat(home.root()).isEqualTo(USER_HOME.resolve(".phoenix"));
        assertThat(home.dbFile()).isEqualTo(USER_HOME.resolve(".phoenix/state/phoenix.db"));
        assertThat(home.exportsDir()).isEqualTo(USER_HOME.resolve(".phoenix/exports"));
        assertThat(home.logFile()).isEqualTo(USER_HOME.resolve(".phoenix/logs/phoenix.log"));
    }

    @Test
    void relativeConfiguredPathsResolveAgainstTheHomeAndAbsoluteOnesStay() {
        PhoenixHome home = new PhoenixHome(Path.of("/data/a"));

        assertThat(home.resolvePath("state/seen.txt")).isEqualTo(Path.of("/data/a/state/seen.txt"));
        assertThat(home.resolvePath("/elsewhere/x.json")).isEqualTo(Path.of("/elsewhere/x.json"));
    }
}
