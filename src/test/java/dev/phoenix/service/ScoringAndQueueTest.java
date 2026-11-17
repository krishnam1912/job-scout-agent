package dev.phoenix.service;

import dev.phoenix.domain.Job;
import dev.phoenix.domain.JobState;
import dev.phoenix.domain.ScoreBand;
import dev.phoenix.error.NotFoundException;
import dev.phoenix.error.UserErrorException;
import dev.phoenix.testsupport.Services;
import dev.phoenix.testsupport.TestStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoringAndQueueTest {

    private TestStore store;
    private Services services;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        store = TestStore.create(dir);
        services = new Services(store);
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    @ParameterizedTest
    @CsvSource({"5.0,QUEUE", "3.5,QUEUE", "3.49,NEAR_MISS", "3.0,NEAR_MISS", "2.99,BELOW", "1.0,BELOW"})
    void scoresFallIntoTheSpecBands(double score, ScoreBand band) {
        assertThat(ScoreBand.of(score)).isEqualTo(band);
    }

    @Test
    void scoringStoresTheScoreAndMovesTheJobThroughScoredToItsBand() {
        Job high = store.insertJob("acme", "1", "Backend", JobState.CANDIDATE);
        Job mid = store.insertJob("acme", "2", "Platform", JobState.CANDIDATE);
        Job low = store.insertJob("acme", "3", "Data", JobState.CANDIDATE);

        List<ScoringService.Scored> done = services.scoring.record(List.of(
                new ScoreInput(high.id(), 4.4, "strong fit", "v1"),
                new ScoreInput(mid.id(), 3.2, "ok", "v1"),
                new ScoreInput(low.id(), 2.0, "weak", "v1")), "claude-code");

        assertThat(done).extracting(ScoringService.Scored::state)
                .containsExactly(JobState.QUEUED, JobState.NEAR_MISS, JobState.BELOW);
        assertThat(done.get(0).fitPct()).isEqualTo(88);
        assertThat(store.scores.latestFor(high.id()).orElseThrow().rationale()).isEqualTo("strong fit");
        assertThat(store.events.findByJob(high.id())).extracting(e -> e.to())
                .containsExactly(JobState.SCORED, JobState.QUEUED);
        assertThat(store.jobs.findById(high.id()).orElseThrow().nextActionAt()).isNotNull();
    }

    @Test
    void aBadItemRejectsTheWholeBatchAndStoresNothing() {
        Job good = store.insertJob("acme", "1", "Backend", JobState.CANDIDATE);
        Job queued = store.insertJob("acme", "2", "Platform", JobState.QUEUED);

        assertThatThrownBy(() -> services.scoring.record(List.of(
                new ScoreInput(good.id(), 4.0, "ok", "v1"), new ScoreInput(queued.id(), 4.0, "ok", "v1")), "x"))
                .isInstanceOf(UserErrorException.class).hasMessageContaining("QUEUED");

        assertThat(store.scores.latestFor(good.id())).isEmpty();
        assertThat(store.jobs.findById(good.id()).orElseThrow().state()).isEqualTo(JobState.CANDIDATE);
    }

    @Test
    void invalidInputIsRefusedWithAClearMessage() {
        Job job = store.insertJob("acme", "1", "Backend", JobState.CANDIDATE);

        assertThatThrownBy(() -> services.scoring.record(List.of(new ScoreInput(job.id(), 5.5, "", "v1")), "x"))
                .hasMessageContaining("between 1 and 5");
        assertThatThrownBy(() -> services.scoring.record(List.of(
                new ScoreInput(job.id(), 4, "", "v1"), new ScoreInput(job.id(), 3, "", "v1")), "x"))
                .hasMessageContaining("twice");
        assertThatThrownBy(() -> services.scoring.record(List.of(new ScoreInput(999, 4, "", "v1")), "x"))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> services.scoring.record(List.of(), "x")).isInstanceOf(UserErrorException.class);
    }

    @Test
    void aNearMissCanBeRescoredAfterARubricChange() {
        Job job = store.insertJob("acme", "1", "Backend", JobState.NEAR_MISS);

        List<ScoringService.Scored> done = services.scoring.record(List.of(new ScoreInput(job.id(), 4.0, "new rubric", "v2")), "x");

        assertThat(done.get(0).state()).isEqualTo(JobState.QUEUED);
    }

    private Job queued(String extId, String title, Long group, double score, int prescore) {
        Job job = store.insertJob("greenhouse", "acme", extId, title, JobState.CANDIDATE, group);
        store.jobs.updateScan(job.id(), prescore, group);
        services.scoring.record(List.of(new ScoreInput(job.id(), score, "r", "v1")), "x");
        return job;
    }

    @Test
    void theQueueIsOrderedByFitThenClosingDateThenAge() {
        Job a = queued("1", "A", null, 4.0, 10);
        store.clock.advance(Duration.ofHours(1));
        Job b = queued("2", "B", null, 4.5, 10);
        store.clock.advance(Duration.ofHours(1));
        Job c = queued("3", "C", null, 4.0, 10);
        store.jobs.updateClosingDate(c.id(), store.clock.instant().plus(Duration.ofDays(10)), null);

        List<QueueEntry> queue = services.queue.queue(null, 10);

        assertThat(queue).extracting(e -> e.job().id()).containsExactly(b.id(), c.id(), a.id());
    }

    @Test
    void theQueueShowsOneJobPerGroupTheBestFit() {
        long group = store.groups.insert("acme", "backend", new int[0], store.clock.instant());
        queued("1", "Backend", group, 3.6, 10);
        Job best = queued("2", "Backend", group, 4.2, 10);
        Job other = queued("3", "Other", null, 3.9, 10);

        List<QueueEntry> queue = services.queue.queue(null, 10);

        assertThat(queue).extracting(e -> e.job().id()).containsExactly(best.id(), other.id());
    }

    @Test
    void minFitAndLimitApply() {
        queued("1", "A", null, 3.6, 10);
        Job b = queued("2", "B", null, 4.6, 10);
        queued("3", "C", null, 4.2, 10);

        assertThat(services.queue.queue(90, 10)).extracting(e -> e.job().id()).containsExactly(b.id());
        assertThat(services.queue.queue(null, 2)).hasSize(2);
        assertThat(services.queue.queue(null, 0)).isEmpty();
    }

    @Test
    void jobsThatAreNotQueuedAreNotInTheQueue() {
        store.insertJob("acme", "1", "A", JobState.CANDIDATE);
        store.insertJob("acme", "2", "B", JobState.APPROVED);

        assertThat(services.queue.queue(null, 10)).isEmpty();
    }
}
