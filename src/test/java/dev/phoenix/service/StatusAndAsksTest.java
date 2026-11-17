package dev.phoenix.service;

import dev.phoenix.channel.AskService;
import dev.phoenix.channel.NotificationChannel;
import dev.phoenix.domain.Ask;
import dev.phoenix.domain.AskStatus;
import dev.phoenix.domain.Job;
import dev.phoenix.domain.JobState;
import dev.phoenix.error.NotFoundException;
import dev.phoenix.error.UserErrorException;
import dev.phoenix.testsupport.Services;
import dev.phoenix.testsupport.TestStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatusAndAsksTest {

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

    @Test
    void anAskIsOfferedThroughTheChannelAndAnsweredOnce() {
        Ask ask = services.asks.create(null, "question", "Which resume?", "[\"a\",\"b\"]", 3);

        assertThat(ask.status()).isEqualTo(AskStatus.OFFERED);
        assertThat(ask.channel()).isEqualTo("claude-session");
        assertThat(services.asks.list(List.of())).extracting(Ask::id).containsExactly(ask.id());

        Ask answered = services.asks.answer(ask.id(), "a");

        assertThat(answered.status()).isEqualTo(AskStatus.ANSWERED);
        assertThat(answered.answer()).isEqualTo("a");
        assertThat(services.asks.list(List.of())).isEmpty();
        assertThat(services.asks.list(List.of(AskStatus.ANSWERED))).hasSize(1);
        assertThatThrownBy(() -> services.asks.answer(ask.id(), "b")).isInstanceOf(UserErrorException.class);
        assertThatThrownBy(() -> services.asks.answer(999, "b")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> services.asks.answer(ask.id(), " ")).isInstanceOf(UserErrorException.class);
    }

    @Test
    void anAskStaysOpenWhenNoChannelTakesIt() {
        NotificationChannel refusing = new NotificationChannel() {
            @Override
            public String id() {
                return "refusing";
            }

            @Override
            public boolean deliver(Ask ask) {
                return false;
            }
        };
        AskService asks = new AskService(store.asks, List.of(refusing), store.tx, store.clock);

        assertThat(asks.create(null, "question", "Anyone there?", null, 0).status()).isEqualTo(AskStatus.OPEN);
    }

    @Test
    void everyChannelGetsTheAskAndAllThatAcceptedAreRecorded() {
        NotificationChannel first = new RecordingChannel("one");
        NotificationChannel second = new RecordingChannel("two");
        AskService asks = new AskService(store.asks, List.of(first, second), store.tx, store.clock);

        Ask ask = asks.create(null, "question", "Hello?", null, 0);

        assertThat(ask.channel()).isEqualTo("one,two");
    }

    private record RecordingChannel(String id) implements NotificationChannel {
        @Override
        public boolean deliver(Ask ask) {
            return true;
        }
    }

    @Test
    void asksAreListedHighestPriorityFirst() {
        services.asks.create(null, "q", "low", null, 1);
        Ask high = services.asks.create(null, "q", "high", null, 9);

        assertThat(services.asks.list(List.of()).get(0).id()).isEqualTo(high.id());
    }

    @Test
    void statusCountsStatesListsAsksAndFindsJobsAtRisk() {
        store.insertJob("acme", "1", "A", JobState.QUEUED);
        Job overdue = store.insertJob("acme", "2", "B", JobState.QUEUED);
        store.jobs.updateState(overdue.id(), JobState.QUEUED, store.clock.instant().minus(Duration.ofHours(2)), null);
        store.insertJob("acme", "3", "C", JobState.SUBMITTED);
        services.asks.create(null, "q", "Need you", null, 1);
        long run = store.runs.start("scan", store.clock.instant());
        store.runs.finish(run, "OK", store.clock.instant(), "{}");

        StatusReport report = services.status.report();

        assertThat(report.counts()).containsEntry(JobState.QUEUED, 2).containsEntry(JobState.SUBMITTED, 1);
        assertThat(report.openAsks()).hasSize(1);
        assertThat(report.atRisk()).extracting(Job::id).containsExactly(overdue.id());
        assertThat(report.lastScanStatus()).isEqualTo("OK");
        assertThat(report.lastScanAt()).isEqualTo(store.clock.instant());
    }

    @Test
    void statusBeforeAnyScanHasNoLastScan() {
        StatusReport report = services.status.report();

        assertThat(report.lastScanAt()).isNull();
        assertThat(report.lastScanStatus()).isNull();
        assertThat(report.counts()).isEmpty();
    }

    @Test
    void jobDetailCollectsScoreApplicationEventsAndGroupMembers() {
        long group = store.groups.insert("acme", "backend", new int[0], store.clock.instant());
        Job job = store.insertJob("greenhouse", "acme", "1", "Backend", JobState.CANDIDATE, group);
        Job sibling = store.insertJob("lever", "acme", "2", "Backend", JobState.CANDIDATE, group);
        services.scoring.record(List.of(new ScoreInput(job.id(), 4.0, "good", "v1")), "x");

        JobDetail detail = services.jobQuery.get(job.id());

        assertThat(detail.score().fitPct()).isEqualTo(80);
        assertThat(detail.application()).isNull();
        assertThat(detail.events()).hasSize(2);
        assertThat(detail.groupMembers()).extracting(Job::id).containsExactly(sibling.id());
        assertThatThrownBy(() -> services.jobQuery.get(999)).isInstanceOf(NotFoundException.class);
    }
}
