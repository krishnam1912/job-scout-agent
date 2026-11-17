package dev.phoenix.legacy;

import dev.phoenix.config.PhoenixHome;
import dev.phoenix.config.PhoenixProperties;
import dev.phoenix.domain.Application;
import dev.phoenix.domain.Job;
import dev.phoenix.domain.JobState;
import dev.phoenix.domain.Score;
import dev.phoenix.lifecycle.NextActionPolicy;
import dev.phoenix.testsupport.TestStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyImporterTest {

    private TestStore store;
    private LegacyImporter importer;
    private Path legacyDir;

    @BeforeEach
    void setUp(@TempDir Path dir) throws IOException {
        store = TestStore.create(dir);
        legacyDir = dir.resolve("legacy");
        copyTree(Path.of("src/test/resources/fixtures/legacy"), legacyDir);
        PhoenixProperties props = new PhoenixProperties(null, null, null, "legacy/seen.txt",
                new PhoenixProperties.Legacy("legacy/leads.csv", "legacy/applications.md", "legacy/referrals.csv",
                        "legacy/queue"), null, null);
        LegacyJobWriter writer = new LegacyJobWriter(store.jobs, store.scores, store.applications, store.events,
                new NextActionPolicy(), store.clock);
        importer = new LegacyImporter(new PhoenixHome(dir), props, writer, store.referrals, store.tx);
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> paths = Files.walk(from)) {
            for (Path p : paths.toList()) {
                Path target = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(p, target);
                }
            }
        }
    }

    private Job job(String company, String extId) {
        return store.jobs.findByKey("legacy", company, extId).orElseThrow();
    }

    @Test
    void importsEveryFileAndReconcilesTheCounts() {
        ImportReport report = importer.run();

        assertThat(report.files()).extracting(ImportReport.FileCount::file, ImportReport.FileCount::rowsRead, ImportReport.FileCount::rowsUsed)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("seen.txt", 4, 3),
                        org.assertj.core.groups.Tuple.tuple("queue/", 4, 3),
                        org.assertj.core.groups.Tuple.tuple("leads.csv", 7, 5),
                        org.assertj.core.groups.Tuple.tuple("applications.md", 6, 4),
                        org.assertj.core.groups.Tuple.tuple("referrals.csv", 2, 1));
        assertThat(report.jobsCreated()).isEqualTo(11);
        assertThat(report.jobsUnchanged()).isZero();
        assertThat(report.createdByState()).containsExactlyInAnyOrderEntriesOf(Map.of(
                JobState.SUBMITTED, 4, JobState.QUEUED, 3, JobState.BELOW, 2, JobState.SKIPPED, 1, JobState.BLOCKED, 1));
        assertThat(report.syntheticIds()).isEqualTo(2);
        assertThat(report.referralsCreated()).isEqualTo(1);
        assertThat(report.clean()).isFalse();
        assertThat(report.unparsed()).extracting(RowIssue::toString).containsExactlyInAnyOrder(
                "seen.txt:5: expected company:id",
                "queue/2026-01-02.md:8: row has no usable score",
                "leads.csv:7: unrecognised status 'Teleported'",
                "leads.csv:8: expected 13 columns, found 3",
                "applications.md:14: unrecognised status 'Mystery status'",
                "applications.md:18: row has no date",
                "referrals.csv:3: row has no name");
        assertThat(report.render()).contains("11 created").contains("SUBMITTED 4").contains("applications.md:18");
    }

    @Test
    void anApplicationsRowBeatsTheLeadsStatusAndFieldsAreMergedAcrossFiles() {
        importer.run();

        Job merged = job("acme", "1001");
        assertThat(merged.state()).isEqualTo(JobState.SUBMITTED);
        assertThat(merged.title()).isEqualTo("Backend Engineer II");
        assertThat(merged.location()).isEqualTo("Bengaluru");
        assertThat(merged.url()).isEqualTo("https://jobs.example.com/acme/1001");
        assertThat(merged.firstSeen()).isEqualTo(java.time.Instant.parse("2026-01-05T00:00:00Z"));
        Score score = store.scores.latestFor(merged.id()).orElseThrow();
        assertThat(score.score()).isEqualTo(4.4);
        assertThat(score.rationale()).isEqualTo("Java and Kafka match");
        Application application = store.applications.findByJob(merged.id()).orElseThrow();
        assertThat(application.state()).isEqualTo(JobState.SUBMITTED);
        assertThat(application.submittedAt()).isNotNull();
        assertThat(application.note()).contains("applications row").contains("legacy status: Applied 2026-01-05")
                .contains("gaps: No Go");
        assertThat(store.events.findByJob(merged.id())).singleElement().satisfies(e -> {
            assertThat(e.from()).isNull();
            assertThat(e.to()).isEqualTo(JobState.SUBMITTED);
            assertThat(e.actor()).isEqualTo("legacy-import");
        });
    }

    @Test
    void statesComeFromTheStatusRulesAndScoreBands() {
        importer.run();

        assertThat(job("acme", "1002").state()).isEqualTo(JobState.QUEUED);
        assertThat(job("acme", "1002").nextActionAt()).isNotNull();
        assertThat(job("globex", "2001").state()).isEqualTo(JobState.BELOW);
        assertThat(job("globex", "2002").state()).isEqualTo(JobState.SKIPPED);
        assertThat(job("initech", "3001").state()).isEqualTo(JobState.BLOCKED);
        assertThat(store.applications.findByJob(job("initech", "3001").id()).orElseThrow().blockerType()).isEqualTo("legacy");
        assertThat(job("soylent", "7007").state()).isEqualTo(JobState.QUEUED);
        assertThat(job("hooli", "R-12345").state()).isEqualTo(JobState.SUBMITTED);
    }

    @Test
    void aSeenOnlyIdBecomesAPlaceholderThatDedupes() {
        importer.run();

        Job placeholder = job("acme", "9999");
        assertThat(placeholder.state()).isEqualTo(JobState.BELOW);
        assertThat(placeholder.title()).contains("title unknown");
        assertThat(store.scores.latestFor(placeholder.id())).isEmpty();
    }

    @Test
    void aCompanyWithManyWordsIsRecognisedAndOnlyTheFirstOfSeveralIdsIsUsed() {
        importer.run();

        Job wayne = job("wayneenterprises", "4004");
        assertThat(wayne.state()).isEqualTo(JobState.SUBMITTED);
        assertThat(wayne.title()).isEqualTo("Backend Dev (Pune)");
        assertThat(store.jobs.findByCompanyAndExtId("wayneenterprises", "4005")).isEmpty();
    }

    @Test
    void rowsWithoutAnIdGetAStableMadeUpOne() {
        importer.run();

        List<Job> madeUp = store.jobs.findAll().stream().filter(j -> j.extId().startsWith("legacy-")).toList();
        assertThat(madeUp).extracting(Job::company).containsExactlyInAnyOrder("acme", "soylent");
    }

    @Test
    void aSecondRunAddsNothing() {
        importer.run();
        long jobs = store.jobs.findAll().size();
        long events = store.jobs.findAll().stream().mapToLong(j -> store.events.findByJob(j.id()).size()).sum();

        ImportReport second = importer.run();

        assertThat(second.jobsCreated()).isZero();
        assertThat(second.jobsUnchanged()).isEqualTo(11);
        assertThat(second.referralsCreated()).isZero();
        assertThat(second.referralsUnchanged()).isEqualTo(1);
        assertThat(store.jobs.findAll()).hasSize((int) jobs);
        assertThat(store.jobs.findAll().stream().mapToLong(j -> store.events.findByJob(j.id()).size()).sum()).isEqualTo(events);
        assertThat(store.referrals.findAll()).hasSize(1);
    }

    @Test
    void theLegacyFilesAreNeverModified() throws IOException {
        String before = fingerprint();

        importer.run();

        assertThat(fingerprint()).isEqualTo(before);
    }

    @Test
    void importedReferralsKeepTheirColumns() {
        importer.run();

        assertThat(store.referrals.findAll()).singleElement().satisfies(r -> {
            assertThat(r.name()).isEqualTo("Pat Example");
            assertThat(r.company()).isEqualTo("Acme");
            assertThat(r.jobRef()).isEqualTo("1001");
            assertThat(r.status()).isEqualTo("pending");
            assertThat(r.lastContact()).isEqualTo("2026-01-04");
            assertThat(r.followUp()).isEqualTo("2026-01-11");
            assertThat(r.note()).isEqualTo("message sent: yes; warm intro");
        });
    }

    @Test
    void aMissingLeadsColumnIsAUserError() throws IOException {
        Files.writeString(legacyDir.resolve("leads.csv"), "Date found,Company,Role\n2026-01-01,Acme,Dev\n");

        org.assertj.core.api.Assertions.assertThatThrownBy(importer::run)
                .isInstanceOf(dev.phoenix.error.UserErrorException.class).hasMessageContaining("Job ID");
        assertThat(store.jobs.findAll()).as("nothing imported when a file cannot be read").isEmpty();
    }

    private String fingerprint() throws IOException {
        try (Stream<Path> paths = Files.walk(legacyDir)) {
            return paths.filter(Files::isRegularFile).sorted().map(p -> {
                try {
                    return p.getFileName() + ":" + Files.readString(p).hashCode() + ":" + Files.getLastModifiedTime(p);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }).collect(Collectors.joining("\n"));
        }
    }
}
