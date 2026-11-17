package dev.phoenix.domain;

import java.util.EnumSet;
import java.util.Set;

/** Every state a job can be in. The legal moves between states live in {@code lifecycle.Transitions}. */
public enum JobState {
    DISCOVERED, FILTERED_OUT, CANDIDATE, SCORED, NEAR_MISS, BELOW, QUEUED, APPROVED, IN_PROGRESS, SUBMITTED,
    BLOCKED, FAILED, SUBMIT_UNKNOWN, SKIPPED, OA, INTERVIEW, REJECTED, OFFER, GHOSTED, CLOSED, EXPIRED;

    private static final Set<JobState> TERMINAL =
            EnumSet.of(FILTERED_OUT, SKIPPED, REJECTED, OFFER, GHOSTED, CLOSED, EXPIRED);

    /** Approved or later: the job (or its group) is already being worked or was worked. */
    private static final Set<JobState> ENGAGED = EnumSet.of(APPROVED, IN_PROGRESS, SUBMITTED, BLOCKED, FAILED,
            SUBMIT_UNKNOWN, OA, INTERVIEW, REJECTED, OFFER, GHOSTED);

    /** States in which an application was actually submitted. */
    private static final Set<JobState> APPLIED = EnumSet.of(SUBMITTED, OA, INTERVIEW, REJECTED, OFFER, GHOSTED);

    /** States before any application work starts; only these may be closed by the posting disappearing. */
    private static final Set<JobState> PRE_APPLICATION =
            EnumSet.of(DISCOVERED, CANDIDATE, SCORED, NEAR_MISS, BELOW, QUEUED, APPROVED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean isEngaged() {
        return ENGAGED.contains(this);
    }

    public boolean isApplied() {
        return APPLIED.contains(this);
    }

    public boolean isPreApplication() {
        return PRE_APPLICATION.contains(this);
    }
}
