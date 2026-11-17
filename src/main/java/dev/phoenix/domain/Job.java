package dev.phoenix.domain;

import java.time.Instant;

/** One posting at one company. {@code company} is the normalised key; nullable fields are unknown or unset. */
public record Job(
        long id,
        String source,
        String company,
        String extId,
        String url,
        String title,
        String location,
        Long groupId,
        JobState state,
        String jdText,
        String jdHash,
        Integer prescore,
        Instant firstSeen,
        Instant lastSeen,
        Instant closedAt,
        Instant closingDate,
        Instant nextActionAt) {

    public Job withState(JobState newState, Instant newNextActionAt, Instant newClosedAt) {
        return new Job(id, source, company, extId, url, title, location, groupId, newState, jdText, jdHash,
                prescore, firstSeen, lastSeen, newClosedAt, closingDate, newNextActionAt);
    }
}
