package dev.phoenix.lifecycle;

import dev.phoenix.domain.Event;
import dev.phoenix.domain.Job;
import dev.phoenix.domain.JobState;
import dev.phoenix.error.NotFoundException;
import dev.phoenix.store.EventRepository;
import dev.phoenix.testsupport.TestStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LifecycleServiceTest {

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
    void transitionUpdatesStateWritesAnEventAndSchedulesTheNextAction() {
        Job job = store.insertJob("acme", "1", "Backend Engineer", JobState.SCORED);

        Job moved = store.lifecycle.transition(job.id(), JobState.QUEUED, "claude-code", "fit 4.2");

        assertThat(moved.state()).isEqualTo(JobState.QUEUED);
        Job stored = store.jobs.findById(job.id()).orElseThrow();
        assertThat(stored.state()).isEqualTo(JobState.QUEUED);
        assertThat(stored.nextActionAt()).isEqualTo(store.clock.instant().plus(Duration.ofDays(2)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        List<Event> events = store.events.findByJob(job.id());
        assertThat(events).singleElement().satisfies(e -> {
            assertThat(e.from()).isEqualTo(JobState.SCORED);
            assertThat(e.to()).isEqualTo(JobState.QUEUED);
            assertThat(e.actor()).isEqualTo("claude-code");
            assertThat(e.note()).isEqualTo("fit 4.2");
        });
    }

    @Test
    void anIllegalTransitionThrowsAndChangesNothing() {
        Job job = store.insertJob("acme", "1", "Backend Engineer", JobState.CANDIDATE);

        assertThatThrownBy(() -> store.lifecycle.transition(job.id(), JobState.SUBMITTED, "x", null))
                .isInstanceOfSatisfying(IllegalTransitionException.class, e -> {
                    assertThat(e.from()).isEqualTo(JobState.CANDIDATE);
                    assertThat(e.to()).isEqualTo(JobState.SUBMITTED);
                });

        assertThat(store.jobs.findById(job.id()).orElseThrow().state()).isEqualTo(JobState.CANDIDATE);
        assertThat(store.events.findByJob(job.id())).isEmpty();
    }

    @Test
    void anUnknownJobIsReportedAsNotFound() {
        assertThatThrownBy(() -> store.lifecycle.transition(999, JobState.QUEUED, "x", null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void closingRecordsWhenAndClearsTheNextAction() {
        Job job = store.insertJob("acme", "1", "Backend Engineer", JobState.QUEUED);

        store.lifecycle.transition(job.id(), JobState.CLOSED, "scanner", "posting gone");

        Job stored = store.jobs.findById(job.id()).orElseThrow();
        assertThat(stored.closedAt()).isNotNull();
        assertThat(stored.nextActionAt()).isNull();
    }

    @Test
    void aJobWithACloseDateInsideTheWindowIsDueImmediately() {
        Instant closing = store.clock.instant().plus(Duration.ofHours(24));
        Job job = store.insertJob("acme", "1", "Backend Engineer", JobState.SCORED);
        store.setClosingDate(job.id(), closing);

        store.lifecycle.transition(job.id(), JobState.QUEUED, "x", null);

        assertThat(store.jobs.findById(job.id()).orElseThrow().nextActionAt())
                .isEqualTo(store.clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
    }

    @Test
    void ifTheEventCannotBeWrittenTheStateChangeIsRolledBack() {
        EventRepository failing = new EventRepository(store.jdbc) {
            @Override
            public void append(long jobId, JobState from, JobState to, String actor, String note, Instant at) {
                throw new IllegalStateException("disk full");
            }
        };
        LifecycleService service = new LifecycleService(store.jobs, failing, store.policy, store.tx, store.clock);
        Job job = store.insertJob("acme", "1", "Backend Engineer", JobState.SCORED);

        assertThatThrownBy(() -> service.transition(job.id(), JobState.QUEUED, "x", null))
                .isInstanceOf(IllegalStateException.class);

        assertThat(store.jobs.findById(job.id()).orElseThrow().state()).isEqualTo(JobState.SCORED);
    }
}
