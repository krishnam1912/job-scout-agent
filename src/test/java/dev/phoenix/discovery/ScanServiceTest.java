package dev.phoenix.discovery;

import dev.phoenix.domain.Job;
import dev.phoenix.domain.JobState;
import dev.phoenix.testsupport.ScanHarness;
import dev.phoenix.testsupport.TestStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScanServiceTest {

    private static final String BOARD_URL = "https://boards-api.greenhouse.io/v1/boards/acme/jobs?content=true";
    private static final String JD = "Java backend services with Kafka for payment systems. "
            + "You will own design reviews, on-call rotations, capacity planning and incident follow-ups for the platform.";

    private TestStore store;
    private ScanHarness harness;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        store = TestStore.create(dir);
        harness = new ScanHarness(store, dir, "{\"greenhouse\": [\"acme\"]}");
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    private static String job(String id, String title, String location, String jd) {
        return "{\"id\": " + id + ", \"title\": \"" + title + "\", \"location\": {\"name\": \"" + location
                + "\"}, \"absolute_url\": \"https://boards.example.com/acme/" + id + "\", \"content\": \"" + jd + "\"}";
    }

    private void board(String... jobs) {
        harness.http.on(BOARD_URL, "{\"jobs\": [" + String.join(",", jobs) + "]}");
    }

    private ScanResult scan(boolean dryRun) {
        return harness.scan.scan(new ScanRequest(10, null, dryRun));
    }

    @Test
    void newPostingsBecomeCandidatesOrAreFilteredOutWithAReason() {
        board(job("1001", "SDE II Backend", "Bengaluru", JD),
                job("1002", "Engineering Manager", "Bengaluru", JD),
                job("1003", "Backend Engineer", "Berlin", JD));

        ScanResult result = scan(false);

        assertThat(result.candidates()).singleElement().satisfies(c -> {
            assertThat(c.extId()).isEqualTo("1001");
            assertThat(c.jobId()).isNotNull();
            assertThat(c.company()).isEqualTo("acme");
            assertThat(c.prescore()).isPositive();
            assertThat(c.jdSummary()).contains("Java backend");
        });
        assertThat(result.stats().fetched()).isEqualTo(3);
        assertThat(result.stats().filteredByReason()).containsEntry("title", 1).containsEntry("location", 1);
        assertThat(store.jobs.findByKey("greenhouse", "acme", "1001").orElseThrow().state()).isEqualTo(JobState.CANDIDATE);
        Job filtered = store.jobs.findByKey("greenhouse", "acme", "1002").orElseThrow();
        assertThat(filtered.state()).isEqualTo(JobState.FILTERED_OUT);
        assertThat(filtered.jdText()).as("filtered-out jobs keep no description").isNull();
        assertThat(store.events.findByJob(filtered.id())).extracting(e -> e.to())
                .containsExactly(JobState.DISCOVERED, JobState.FILTERED_OUT);
        assertThat(store.events.findByJob(filtered.id()).get(1).note()).isEqualTo("filtered: title");
        assertThat(store.runs.latest("scan").orElseThrow().status()).isEqualTo("OK");
    }

    @Test
    void aRescanOnlyRefreshesLastSeenAndOffersTheSameUnscoredCandidate() {
        board(job("1001", "SDE II Backend", "Bengaluru", JD));
        scan(false);
        Job first = store.jobs.findByKey("greenhouse", "acme", "1001").orElseThrow();
        store.clock.advance(Duration.ofDays(1));

        ScanResult second = scan(false);

        Job again = store.jobs.findByKey("greenhouse", "acme", "1001").orElseThrow();
        assertThat(again.id()).isEqualTo(first.id());
        assertThat(again.lastSeen()).isAfter(first.lastSeen());
        assertThat(again.firstSeen()).isEqualTo(first.firstSeen());
        assertThat(second.stats().alreadyKnown()).isEqualTo(1);
        assertThat(second.stats().newPostings()).isZero();
        assertThat(second.candidates()).extracting(Candidate::extId).containsExactly("1001");
        assertThat(store.jobs.findAll()).hasSize(1);
        assertThat(store.events.findByJob(first.id())).hasSize(2);
    }

    @Test
    void aScoredJobIsNotOfferedAgain() {
        board(job("1001", "SDE II Backend", "Bengaluru", JD));
        scan(false);
        Job job = store.jobs.findByKey("greenhouse", "acme", "1001").orElseThrow();
        store.lifecycle.transition(job.id(), JobState.SCORED, "test", null);

        assertThat(scan(false).candidates()).isEmpty();
    }

    @Test
    void aDryRunWritesNothingButReturnsTheSameCandidates() {
        board(job("1001", "SDE II Backend", "Bengaluru", JD), job("1002", "Engineering Manager", "Pune", JD));

        ScanResult dry = scan(true);

        assertThat(dry.candidates()).extracting(Candidate::extId).containsExactly("1001");
        assertThat(dry.candidates().get(0).jobId()).isNull();
        assertThat(store.jobs.findAll()).isEmpty();
        assertThat(store.groups.findByCompany("acme")).isEmpty();
        assertThat(store.runs.latest("scan")).isEmpty();
        assertThat(store.health.find("greenhouse", "acme")).isEmpty();
        assertThat(scan(false).candidates()).extracting(Candidate::extId).containsExactly("1001");
    }

    @Test
    void postingsAlreadyKnownFromAnotherSourceAreNotNew() {
        store.insertJob("legacy", "acme", "1001", "Imported role", JobState.SUBMITTED);
        board(job("1001", "SDE II Backend", "Bengaluru", JD), job("1002", "SDE II Backend", "Bengaluru", JD + " More."));

        ScanResult result = scan(false);

        assertThat(result.stats().alreadyKnown()).isEqualTo(1);
        assertThat(result.candidates()).extracting(Candidate::extId).containsExactly("1002");
    }

    @Test
    void aLongIdSeenAtAnyCompanyCountsAsKnown() {
        store.insertJob("legacy", "someoneelse", "1234567", "Imported role", JobState.BELOW);
        board(job("1234567", "SDE II Backend", "Bengaluru", JD), job("12345", "SDE II Backend", "Bengaluru", JD + " More."));
        store.insertJob("legacy", "someoneelse", "12345", "Short id", JobState.BELOW);

        ScanResult result = scan(false);

        assertThat(result.candidates()).extracting(Candidate::extId).containsExactly("12345");
    }

    @Test
    void severalRequisitionsOfTheSameRoleShowOnce() {
        board(job("2001", "SDE II Backend", "Bengaluru", JD), job("2002", "SDE II Backend", "Bengaluru", JD),
                job("2003", "SDE II Backend", "Bengaluru", JD));

        ScanResult result = scan(false);

        assertThat(result.candidates()).extracting(Candidate::extId).containsExactly("2003");
        List<Job> all = store.jobs.findAll();
        assertThat(all).extracting(Job::groupId).doesNotContainNull().containsOnly(all.get(0).groupId());
    }

    @Test
    void aNewPostingOfARoleThatIsAlreadyEngagedNeverResurfaces() {
        board(job("2001", "SDE II Backend", "Bengaluru", JD));
        scan(false);
        Job first = store.jobs.findByKey("greenhouse", "acme", "2001").orElseThrow();
        store.lifecycle.transition(first.id(), JobState.SCORED, "test", null);
        store.lifecycle.transition(first.id(), JobState.QUEUED, "test", null);
        board(job("2001", "SDE II Backend", "Bengaluru", JD), job("2002", "SDE II Backend", "Bengaluru", JD));

        ScanResult result = scan(false);

        assertThat(result.candidates()).isEmpty();
        assertThat(result.stats().duplicatesHidden()).isEqualTo(1);
        Job dup = store.jobs.findByKey("greenhouse", "acme", "2002").orElseThrow();
        assertThat(dup.state()).isEqualTo(JobState.FILTERED_OUT);
        assertThat(dup.groupId()).isEqualTo(first.groupId());
    }

    @Test
    void aPostingMissingFromTwoSuccessfulScansIsClosed() {
        board(job("3001", "SDE II Backend", "Bengaluru", JD), job("3002", "SDE II Platform", "Pune", JD + " Platform."));
        scan(false);
        store.clock.advance(Duration.ofDays(1));
        board(job("3001", "SDE II Backend", "Bengaluru", JD));
        scan(false);
        Job gone = store.jobs.findByKey("greenhouse", "acme", "3002").orElseThrow();
        assertThat(gone.state()).as("one miss is not enough").isEqualTo(JobState.CANDIDATE);
        store.clock.advance(Duration.ofDays(1));

        ScanResult third = scan(false);

        Job closed = store.jobs.findByKey("greenhouse", "acme", "3002").orElseThrow();
        assertThat(closed.state()).isEqualTo(JobState.CLOSED);
        assertThat(closed.closedAt()).isNotNull();
        assertThat(third.stats().closed()).isEqualTo(1);
        assertThat(store.events.findByJob(closed.id()).get(2).actor()).isEqualTo("scanner");
        assertThat(store.jobs.findByKey("greenhouse", "acme", "3001").orElseThrow().state()).isEqualTo(JobState.CANDIDATE);
    }

    @Test
    void jobsAlreadyBeingAppliedToAreNeverClosedByTheirPostingDisappearing() {
        board(job("3001", "SDE II Backend", "Bengaluru", JD));
        scan(false);
        Job job = store.jobs.findByKey("greenhouse", "acme", "3001").orElseThrow();
        store.lifecycle.transition(job.id(), JobState.SCORED, "t", null);
        store.lifecycle.transition(job.id(), JobState.QUEUED, "t", null);
        store.lifecycle.transition(job.id(), JobState.APPROVED, "t", null);
        store.lifecycle.transition(job.id(), JobState.IN_PROGRESS, "t", null);
        board();
        for (int i = 0; i < 3; i++) {
            store.clock.advance(Duration.ofDays(1));
            scan(false);
        }

        assertThat(store.jobs.findById(job.id()).orElseThrow().state()).isEqualTo(JobState.IN_PROGRESS);
    }

    @Test
    void aFailingBoardIsRecordedAndNeverClosesItsJobs() {
        board(job("3001", "SDE II Backend", "Bengaluru", JD));
        scan(false);
        harness.http.failOn(BOARD_URL, 503);
        for (int i = 0; i < 3; i++) {
            store.clock.advance(Duration.ofDays(1));
            ScanResult result = scan(false);
            assertThat(result.stats().failedBoards()).isEqualTo(1);
        }

        assertThat(store.jobs.findByKey("greenhouse", "acme", "3001").orElseThrow().state()).isEqualTo(JobState.CANDIDATE);
        assertThat(store.health.find("greenhouse", "acme").orElseThrow().consecutiveFailures()).isEqualTo(3);
        assertThat(store.runs.latest("scan").orElseThrow().status()).isEqualTo("OK");
    }

    @Test
    void theCompanyOptionLimitsWhichBoardsAreFetched() {
        board(job("1001", "SDE II Backend", "Bengaluru", JD));

        ScanResult result = harness.scan.scan(new ScanRequest(10, "nomatch", false));

        assertThat(result.candidates()).isEmpty();
        assertThat(harness.http.requests()).isEmpty();
    }

    @Test
    void aFailureInTheMiddleOfTheWriteLeavesNoPartialScan() {
        board(job("1001", "SDE II Backend", "Bengaluru", JD));
        store.insertJob("greenhouse", "acme", "preexisting", "Old", JobState.CLOSED);
        store.jdbc.sql("CREATE TRIGGER fail_on_candidate BEFORE UPDATE OF state ON jobs WHEN NEW.state = 'CANDIDATE' "
                + "BEGIN SELECT RAISE(ABORT, 'forced failure'); END").update();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> scan(false)).hasMessageContaining("forced failure");

        assertThat(store.jobs.findByKey("greenhouse", "acme", "1001")).isEmpty();
        assertThat(store.runs.latest("scan").orElseThrow().status()).isEqualTo("FAILED");
    }
}
