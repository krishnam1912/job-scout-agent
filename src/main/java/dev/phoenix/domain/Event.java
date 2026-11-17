package dev.phoenix.domain;

import java.time.Instant;

/** An append-only lifecycle record. {@code from} is null for a job's first event. */
public record Event(long id, long jobId, JobState from, JobState to, String actor, String note, Instant at) {
}
