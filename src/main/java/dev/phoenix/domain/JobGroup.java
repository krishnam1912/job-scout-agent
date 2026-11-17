package dev.phoenix.domain;

import java.time.Instant;

/** Postings that are the same role: one group is shown once in the queue. {@code signature} is the MinHash of the first member's JD. */
public record JobGroup(long id, String company, String normTitle, int[] signature, Instant createdAt) {
}
