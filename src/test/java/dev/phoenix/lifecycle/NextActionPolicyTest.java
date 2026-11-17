package dev.phoenix.lifecycle;

import dev.phoenix.domain.JobState;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NextActionPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");
    private final NextActionPolicy policy = new NextActionPolicy();

    @Test
    void queuedIsReviewedAfterTwoDays() {
        assertThat(policy.nextActionAt(JobState.QUEUED, NOW, null)).isEqualTo(NOW.plus(Duration.ofDays(2)));
    }

    @Test
    void blockedIsRetriedAfterOneDay() {
        assertThat(policy.nextActionAt(JobState.BLOCKED, NOW, null)).isEqualTo(NOW.plus(Duration.ofDays(1)));
    }

    @Test
    void submittedBecomesAGhostCandidateAfterTwentyOneDays() {
        assertThat(policy.nextActionAt(JobState.SUBMITTED, NOW, null)).isEqualTo(NOW.plus(Duration.ofDays(21)));
    }

    @Test
    void terminalStatesHaveNothingDue() {
        for (JobState s : JobState.values()) {
            if (s.isTerminal()) {
                assertThat(policy.nextActionAt(s, NOW, null)).as(s.name()).isNull();
            }
        }
    }

    @Test
    void statesThatWaitForAnExplicitCallHaveNothingDue() {
        assertThat(policy.nextActionAt(JobState.IN_PROGRESS, NOW, null)).isNull();
        assertThat(policy.nextActionAt(JobState.INTERVIEW, NOW, null)).isNull();
    }

    @Test
    void closingWithin72HoursIsUrgentNow() {
        Instant closing = NOW.plus(Duration.ofHours(72));
        assertThat(policy.nextActionAt(JobState.QUEUED, NOW, closing)).isEqualTo(NOW);
        assertThat(policy.isUrgent(JobState.QUEUED, NOW, closing)).isTrue();
    }

    @Test
    void closingBeyond72HoursIsNotUrgent() {
        Instant closing = NOW.plus(Duration.ofHours(73));
        assertThat(policy.isUrgent(JobState.QUEUED, NOW, closing)).isFalse();
        assertThat(policy.nextActionAt(JobState.QUEUED, NOW, closing)).isEqualTo(NOW.plus(Duration.ofDays(2)));
    }

    @Test
    void anAlreadyAppliedJobIsNeverUrgent() {
        Instant closing = NOW.plus(Duration.ofHours(1));
        assertThat(policy.isUrgent(JobState.SUBMITTED, NOW, closing)).isFalse();
        assertThat(policy.nextActionAt(JobState.SUBMITTED, NOW, closing)).isEqualTo(NOW.plus(Duration.ofDays(21)));
    }
}
