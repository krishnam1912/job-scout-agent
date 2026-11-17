package dev.phoenix.lifecycle;

import dev.phoenix.domain.JobState;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static dev.phoenix.domain.JobState.*;

/** The single table of allowed state changes. Everything that moves a job asks here. */
public final class Transitions {

    private static final Map<JobState, Set<JobState>> ALLOWED = new EnumMap<>(JobState.class);

    static {
        allow(DISCOVERED, FILTERED_OUT, CANDIDATE);
        allow(CANDIDATE, SCORED);
        allow(SCORED, NEAR_MISS, BELOW, QUEUED);
        allow(NEAR_MISS, SCORED);
        allow(BELOW, SCORED);
        allow(QUEUED, APPROVED, SKIPPED);
        allow(APPROVED, IN_PROGRESS, SKIPPED, QUEUED); // QUEUED: the approval lapsed unused
        allow(IN_PROGRESS, SUBMITTED, BLOCKED, FAILED, SUBMIT_UNKNOWN, SKIPPED);
        allow(BLOCKED, IN_PROGRESS, SKIPPED);
        allow(FAILED, IN_PROGRESS, SKIPPED);
        allow(SUBMIT_UNKNOWN, SUBMITTED, IN_PROGRESS);
        allow(SUBMITTED, OA, INTERVIEW, REJECTED, GHOSTED);
        allow(OA, INTERVIEW, REJECTED);
        allow(INTERVIEW, OFFER, REJECTED);
        // any non-terminal state may also end because the posting closed or expired
        for (JobState s : JobState.values()) {
            if (!s.isTerminal()) {
                allow(s, CLOSED, EXPIRED);
            }
        }
    }

    private Transitions() {
    }

    private static void allow(JobState from, JobState... to) {
        ALLOWED.computeIfAbsent(from, k -> EnumSet.noneOf(JobState.class)).addAll(Set.of(to));
    }

    public static boolean isAllowed(JobState from, JobState to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }
}
