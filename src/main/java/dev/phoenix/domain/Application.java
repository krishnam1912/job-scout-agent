package dev.phoenix.domain;

import java.time.Instant;

public record Application(long id, long jobId, JobState state, int attempts, String blockerType,
                          String blockerDetail, String resumeVersion, Instant submittedAt,
                          String confirmationRef, String note) {
}
