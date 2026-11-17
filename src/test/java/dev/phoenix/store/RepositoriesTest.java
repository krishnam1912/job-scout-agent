package dev.phoenix.store;

import dev.phoenix.domain.Application;
import dev.phoenix.domain.Approval;
import dev.phoenix.domain.Ask;
import dev.phoenix.domain.AskStatus;
import dev.phoenix.domain.Job;
import dev.phoenix.domain.JobState;
import dev.phoenix.domain.Referral;
import dev.phoenix.domain.Score;
import dev.phoenix.testsupport.TestStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepositoriesTest {

    private TestStore store;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        store = TestStore.create(dir);
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    @Test
    void aJobRoundTripsWithItsOptionalFields() {
        Job saved = store.insertJob("greenhouse", "acme", "42", "Backend Engineer", JobState.CANDIDATE, null);

        Job loaded = store.jobs.findByKey("greenhouse", "acme", "42").orElseThrow();

        assertThat(loaded).isEqualTo(saved);
        assertThat(loaded.groupId()).isNull();
        assertThat(loaded.prescore()).isEqualTo(50);
        assertThat(store.jobs.findByCompanyAndExtId("acme", "42")).hasSize(1);
        assertThat(store.jobs.findByExtId("42")).hasSize(1);
    }

    @Test
    void theSameSourceCompanyAndExtIdCannotBeInsertedTwice() {
        store.insertJob("greenhouse", "acme", "42", "Backend Engineer", JobState.CANDIDATE, null);

        assertThatThrownBy(() -> store.insertJob("greenhouse", "acme", "42", "Other", JobState.CANDIDATE, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void countsAndStateQueriesFollowTheStoredStates() {
        store.insertJob("a", "acme", "1", "T1", JobState.QUEUED, null);
        store.insertJob("a", "acme", "2", "T2", JobState.QUEUED, null);
        store.insertJob("a", "acme", "3", "T3", JobState.SUBMITTED, null);

        assertThat(store.jobs.countByState()).containsEntry(JobState.QUEUED, 2).containsEntry(JobState.SUBMITTED, 1);
        assertThat(store.jobs.findByStates(List.of(JobState.SUBMITTED))).extracting(Job::extId).containsExactly("3");
        assertThat(store.jobs.findByStates(List.of())).isEmpty();
        assertThat(store.jobs.findByBoardAndStates("a", "acme", List.of(JobState.QUEUED))).hasSize(2);
    }

    @Test
    void atRiskFindsOverdueQueuedAndBlockedJobsAndNearClosingOnes() {
        Instant now = store.clock.instant();
        Job overdue = store.insertJob("a", "acme", "1", "T1", JobState.QUEUED, null);
        store.jobs.updateState(overdue.id(), JobState.QUEUED, now.minus(Duration.ofHours(1)), null);
        Job notYet = store.insertJob("a", "acme", "2", "T2", JobState.QUEUED, null);
        store.jobs.updateState(notYet.id(), JobState.QUEUED, now.plus(Duration.ofDays(1)), null);
        Job closing = store.insertJob("a", "acme", "3", "T3", JobState.SCORED, null);
        store.jobs.updateClosingDate(closing.id(), now.plus(Duration.ofHours(48)), null);
        Job closingButApplied = store.insertJob("a", "acme", "4", "T4", JobState.SUBMITTED, null);
        store.jobs.updateClosingDate(closingButApplied.id(), now.plus(Duration.ofHours(1)), null);

        List<Job> atRisk = store.jobs.findAtRisk(now, now.plus(Duration.ofHours(72)));

        assertThat(atRisk).extracting(Job::extId).containsExactly("1", "3");
    }

    @Test
    void markSeenOnlyMovesLastSeen() {
        Job job = store.insertJob("a", "acme", "1", "T1", JobState.CANDIDATE, null);
        Instant later = store.clock.instant().plus(Duration.ofDays(3));

        store.jobs.markSeen(job.id(), later);

        Job loaded = store.jobs.findById(job.id()).orElseThrow();
        assertThat(loaded.lastSeen()).isEqualTo(later);
        assertThat(loaded.firstSeen()).isEqualTo(job.firstSeen());
    }

    @Test
    void theLatestScorePerJobWins() {
        Job job = store.insertJob("a", "acme", "1", "T1", JobState.SCORED, null);
        Instant at = store.clock.instant();
        store.scores.insert(new Score(0, job.id(), "claude-code", 3.2, 64, "first", "v1", at));
        store.scores.insert(new Score(0, job.id(), "claude-code", 4.1, 82, "second", "v2", at));

        assertThat(store.scores.latestFor(job.id()).orElseThrow().rationale()).isEqualTo("second");
        Map<Long, Score> latest = store.scores.latestByJob(List.of(job.id()));
        assertThat(latest.get(job.id()).fitPct()).isEqualTo(82);
        assertThat(store.scores.latestForAll()).hasSize(1);
        assertThat(store.scores.latestByJob(List.of())).isEmpty();
    }

    @Test
    void fitPercentIsTheScoreOverFiveRounded() {
        assertThat(Score.fitPct(4.4)).isEqualTo(88);
        assertThat(Score.fitPct(3.5)).isEqualTo(70);
        assertThat(Score.fitPct(3.33)).isEqualTo(67);
    }

    @Test
    void anApplicationIsSavedOncePerJobAndUpdatedInPlace() {
        Job job = store.insertJob("a", "acme", "1", "T1", JobState.IN_PROGRESS, null);
        store.applications.save(new Application(0, job.id(), JobState.IN_PROGRESS, 1, null, null, "v3", null, null, null));
        store.applications.save(new Application(0, job.id(), JobState.BLOCKED, 2, "captcha", "needs a human", "v3", null, null, "n"));

        Application loaded = store.applications.findByJob(job.id()).orElseThrow();
        assertThat(store.applications.findAll()).hasSize(1);
        assertThat(loaded.state()).isEqualTo(JobState.BLOCKED);
        assertThat(loaded.attempts()).isEqualTo(2);
        assertThat(loaded.blockerType()).isEqualTo("captcha");
    }

    @Test
    void asksMoveFromOpenToOfferedToAnswered() {
        Job job = store.insertJob("a", "acme", "1", "T1", JobState.BLOCKED, null);
        long id = store.asks.insert(new Ask(0, job.id(), "blocker", "Solve the captcha", null, 5, AskStatus.OPEN,
                null, null, store.clock.instant(), null, null));

        assertThat(store.asks.findByStatuses(List.of(AskStatus.OPEN))).hasSize(1);
        store.asks.markOffered(id, "claude-session");
        assertThat(store.asks.findById(id).orElseThrow().status()).isEqualTo(AskStatus.OFFERED);
        assertThat(store.asks.findOpenForJob(job.id())).hasSize(1);
        store.asks.markAnswered(id, "done", store.clock.instant());

        Ask answered = store.asks.findById(id).orElseThrow();
        assertThat(answered.status()).isEqualTo(AskStatus.ANSWERED);
        assertThat(answered.answer()).isEqualTo("done");
        assertThat(store.asks.findOpenForJob(job.id())).isEmpty();
    }

    @Test
    void approvalsExpireAndCanBeConsumedOnce() {
        Instant now = store.clock.instant();
        long id = store.approvals.insert(new Approval(0, "job:1", "claude-code-chat", "apply 1",
                now, now.plus(Duration.ofHours(24)), null));

        Approval stored = store.approvals.findByScope("job:1").get(0);
        assertThat(stored.id()).isEqualTo(id);
        assertThat(stored.isUsableAt(now.plus(Duration.ofHours(23)))).isTrue();
        assertThat(stored.isUsableAt(now.plus(Duration.ofHours(25)))).isFalse();

        store.approvals.consume(id, now);
        assertThat(store.approvals.findByScope("job:1").get(0).isUsableAt(now)).isFalse();
    }

    @Test
    void referralsAreDetectedAsDuplicates() {
        Referral r = new Referral("Pat Example", "alumni", "acme", "42", "asked", "2026-11-01", null, null);

        assertThat(store.referrals.exists(r)).isFalse();
        store.referrals.insert(r);

        assertThat(store.referrals.exists(r)).isTrue();
        assertThat(store.referrals.findAll()).containsExactly(r);
    }

    @Test
    void sourceHealthCountsConsecutiveFailuresAndResetsOnSuccess() {
        store.health.recordFailure("greenhouse", "acme");
        store.health.recordFailure("greenhouse", "acme");
        assertThat(store.health.find("greenhouse", "acme").orElseThrow().consecutiveFailures()).isEqualTo(2);

        store.health.recordSuccess("greenhouse", "acme", store.clock.instant(), 17);

        SourceHealthRepository.Health health = store.health.find("greenhouse", "acme").orElseThrow();
        assertThat(health.consecutiveFailures()).isZero();
        assertThat(health.lastCount()).isEqualTo(17);
        assertThat(health.lastOk()).isEqualTo(store.clock.instant());
    }

    @Test
    void runsRecordStartAndFinish() {
        long id = store.runs.start("scan", store.clock.instant());
        assertThat(store.runs.latest("scan").orElseThrow().status()).isEqualTo("RUNNING");

        store.runs.finish(id, "OK", store.clock.instant(), "{\"new\":3}");

        RunRepository.Run run = store.runs.latest("scan").orElseThrow();
        assertThat(run.status()).isEqualTo("OK");
        assertThat(run.checkpoint()).isEqualTo("{\"new\":3}");
        assertThat(store.runs.latest("other")).isEmpty();
    }
}
