package dev.phoenix.lifecycle;

import dev.phoenix.domain.JobState;
import dev.phoenix.error.UserErrorException;

/** The requested state change is not in the transition table. */
public class IllegalTransitionException extends UserErrorException {

    private final JobState from;
    private final JobState to;

    public IllegalTransitionException(long jobId, JobState from, JobState to) {
        super("Job " + jobId + ": illegal transition " + from + " -> " + to);
        this.from = from;
        this.to = to;
    }

    public JobState from() {
        return from;
    }

    public JobState to() {
        return to;
    }
}
