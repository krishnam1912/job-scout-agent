package dev.phoenix.lifecycle;

import dev.phoenix.domain.JobState;

import java.time.Duration;
import java.time.Instant;

/** When a job next needs attention, derived from its state alone so it can be recomputed at every transition. */
public final class NextActionPolicy {

    static final Duration QUEUED_REVIEW = Duration.ofDays(2);
    static final Duration BLOCKED_RETRY = Duration.ofDays(1);
    static final Duration SUBMITTED_GHOST_CHECK = Duration.ofDays(21);
    static final Duration URGENT_WINDOW = Duration.ofHours(72);

    /** @return null when nothing is due (terminal states and states that wait for an explicit call) */
    public Instant nextActionAt(JobState state, Instant now, Instant closingDate) {
        if (state.isTerminal()) {
            return null;
        }
        if (isUrgent(state, now, closingDate)) {
            return now;
        }
        return switch (state) {
            case QUEUED -> now.plus(QUEUED_REVIEW);
            case BLOCKED -> now.plus(BLOCKED_RETRY);
            case SUBMITTED -> now.plus(SUBMITTED_GHOST_CHECK);
            default -> null;
        };
    }

    /** A posting that closes within 72 hours and has not been applied to yet. */
    public boolean isUrgent(JobState state, Instant now, Instant closingDate) {
        return closingDate != null && state.isPreApplication() && !closingDate.isAfter(now.plus(URGENT_WINDOW));
    }
}
