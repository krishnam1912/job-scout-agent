package dev.phoenix;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** The whole application, wiring included, driven through the command line against a temp home. */
class PhoenixApplicationTest {

    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    private PrintStream originalOut;
    private PrintStream originalErr;

    @BeforeEach
    void captureStreams() {
        originalOut = System.out;
        originalErr = System.err;
        System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    private int run(Path home, String... args) {
        String[] withHome = Stream.concat(Stream.of("--home", home.toString()), Stream.of(args)).toArray(String[]::new);
        return PhoenixApplication.run(withHome);
    }

    private String out() {
        return stdout.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return stderr.toString(StandardCharsets.UTF_8);
    }

    @Test
    void statusOnAFreshHomeCreatesTheDatabaseAndExitsZero(@TempDir Path home) {
        int exit = run(home, "status");

        assertThat(exit).isZero();
        assertThat(out()).contains("# Status").contains("No jobs yet.");
        assertThat(home.resolve("state/phoenix.db")).exists();
        assertThat(err()).as("nothing but warnings may reach stderr").doesNotContain("INFO");
    }

    @Test
    void theHomeCanBeGivenWithAnEqualsSign(@TempDir Path home) {
        assertThat(PhoenixApplication.run(new String[] {"--home=" + home, "asks"})).isZero();
        assertThat(out()).contains("No asks.");
    }

    @Test
    void anInvalidConfigFailsFastWithOneLineAndExitOne(@TempDir Path home) throws IOException {
        Files.writeString(home.resolve("phoenix.yml"), "sources: missing.json\n");

        int exit = run(home, "status");

        assertThat(exit).isEqualTo(1);
        assertThat(err()).contains("missing.json");
        assertThat(home.resolve("state")).doesNotExist();
    }

    @Test
    void anIllegalTransitionIsAUserErrorWithExitOne(@TempDir Path home) {
        assertThat(run(home, "transition", "1", "SKIPPED")).isEqualTo(1);
        assertThat(err()).contains("Job 1 not found");
        assertThat(run(home, "transition", "1", "NOWHERE")).isEqualTo(1);
        assertThat(err()).contains("Unknown state");
    }

    @Test
    void anUnknownOptionIsAUserErrorToo(@TempDir Path home) {
        assertThat(run(home, "scan", "--nonsense")).isEqualTo(1);
        assertThat(err()).contains("Unknown option");
    }

    @Test
    void scanningWithoutConfiguredSourcesSaysWhatIsMissing(@TempDir Path home) {
        assertThat(run(home, "scan", "--dry-run")).isEqualTo(1);
        assertThat(err()).contains("'filters' is required");
    }

    @Test
    void llmTestWithoutARoutedProviderSaysSo(@TempDir Path home) {
        assertThat(run(home, "llm-test", "--text", "hello")).isEqualTo(1);
        assertThat(err()).contains("No provider is routed to 'extract'");
    }

    @Test
    void importExportAndQueueWorkEndToEnd(@TempDir Path home) throws IOException {
        Path legacy = home.resolve("legacy");
        try (Stream<Path> files = Files.walk(Path.of("src/test/resources/fixtures/legacy"))) {
            for (Path p : files.toList()) {
                Path target = legacy.resolve(Path.of("src/test/resources/fixtures/legacy").relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(p, target);
                }
            }
        }
        Files.writeString(home.resolve("phoenix.yml"), """
                seen-file: legacy/seen.txt
                legacy:
                  leads-csv: legacy/leads.csv
                  applications-md: legacy/applications.md
                  referrals-csv: legacy/referrals.csv
                  queue-dir: legacy/queue
                """);

        assertThat(run(home, "import-legacy")).as("unparsed rows make the import exit 1").isEqualTo(1);
        assertThat(out()).contains("11 created").contains("Unparsed rows (not imported");
        stdout.reset();

        assertThat(run(home, "import-legacy")).isEqualTo(1);
        assertThat(out()).contains("0 created, 11 already imported");
        stdout.reset();

        assertThat(run(home, "queue")).isZero();
        assertThat(out()).contains("acme: Platform Engineer").contains("soylent");
        stdout.reset();

        assertThat(run(home, "export")).isZero();
        assertThat(home.resolve("exports/leads.csv")).exists();
        assertThat(Files.readString(home.resolve("exports/STATUS.md"))).contains("SUBMITTED 4");
        assertThat(Files.readAllLines(home.resolve("exports/STATUS.md")).size()).isLessThanOrEqualTo(25);
        stdout.reset();

        assertThat(run(home, "job", "1")).isZero();
        assertThat(out()).contains("\"state\" : ").contains("\"events\"");
    }
}
