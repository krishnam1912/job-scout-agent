package dev.phoenix.dedupe;

import dev.phoenix.domain.Application;
import dev.phoenix.domain.Job;
import dev.phoenix.domain.JobState;
import dev.phoenix.testsupport.TestStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CooldownGuardTest {

    private TestStore store;
    private CooldownGuard guard;
    private long group;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        store = TestStore.create(dir);
        guard = new CooldownGuard(store.jobs, store.applications, 0.9, 60, store.clock);
        group = store.groups.insert("acme", "backend engineer", new int[0], store.clock.instant());
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    private void recordSubmission(Job job, Instant at) {
        store.applications.save(new Application(0, job.id(), JobState.SUBMITTED, 1, null, null, null, at, null, null));
    }

    @Test
    void aLoneJobIsAllowed() {
        Job job = store.insertJob("greenhouse", "acme", "1", "Backend Engineer", JobState.QUEUED, group);

        assertThat(guard.check(job).allowed()).isTrue();
    }

    @Test
    void aGroupMemberAtApprovedOrLaterBlocksTheJob() {
        Job engaged = store.insertJob("greenhouse", "acme", "1", "Backend Engineer", JobState.APPROVED, group);
        Job other = store.insertJob("lever", "acme", "2", "Backend Engineer", JobState.QUEUED, group);

        GuardResult result = guard.check(other);

        assertThat(result.verdict()).isEqualTo(GuardVerdict.BLOCKED_BY_GROUP);
        assertThat(result.blockingJobId()).isEqualTo(engaged.id());
    }

    @Test
    void aGroupMemberThatIsOnlyQueuedDoesNotBlock() {
        store.insertJob("greenhouse", "acme", "1", "Backend Engineer", JobState.QUEUED, group);
        Job other = store.insertJob("lever", "acme", "2", "Backend Engineer", JobState.QUEUED, group);

        assertThat(guard.check(other).allowed()).isTrue();
    }

    @Test
    void aSimilarTitleAppliedToRecentlyBlocksEvenOutsideTheGroup() {
        Job applied = store.insertJob("greenhouse", "acme", "1", "Backend Engineer", JobState.SUBMITTED, null);
        recordSubmission(applied, store.clock.instant().minus(Duration.ofDays(10)));
        Job candidate = store.insertJob("lever", "acme", "2", "Backend Engineer (Payments)", JobState.QUEUED, null);
        Job nearlyIdentical = store.insertJob("lever", "acme", "3", "Backend Engineers", JobState.QUEUED, null);

        assertThat(guard.check(candidate).allowed()).isTrue();
        GuardResult result = guard.check(nearlyIdentical);
        assertThat(result.verdict()).isEqualTo(GuardVerdict.BLOCKED_BY_COOLDOWN);
        assertThat(result.blockingJobId()).isEqualTo(applied.id());
    }

    @Test
    void anApplicationOlderThanTheCooldownNoLongerBlocks() {
        Job applied = store.insertJob("greenhouse", "acme", "1", "Backend Engineer", JobState.SUBMITTED, null);
        recordSubmission(applied, store.clock.instant().minus(Duration.ofDays(61)));
        Job candidate = store.insertJob("lever", "acme", "2", "Backend Engineer", JobState.QUEUED, null);

        assertThat(guard.check(candidate).allowed()).isTrue();
    }

    @Test
    void applicationsAtOtherCompaniesNeverBlock() {
        Job applied = store.insertJob("greenhouse", "globex", "1", "Backend Engineer", JobState.SUBMITTED, null);
        recordSubmission(applied, store.clock.instant().minus(Duration.ofDays(1)));
        Job candidate = store.insertJob("lever", "acme", "2", "Backend Engineer", JobState.QUEUED, null);

        assertThat(guard.check(candidate).allowed()).isTrue();
    }
}
