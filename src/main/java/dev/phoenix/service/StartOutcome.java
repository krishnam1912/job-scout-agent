package dev.phoenix.service;

import dev.phoenix.domain.Job;

/** Result of asking to start an application: started, or the reason it may not. */
public record StartOutcome(boolean started, String reason, Job job) {

    static StartOutcome refused(Job job, String reason) {
        return new StartOutcome(false, reason, job);
    }

    static StartOutcome started(Job job) {
        return new StartOutcome(true, "started", job);
    }
}
