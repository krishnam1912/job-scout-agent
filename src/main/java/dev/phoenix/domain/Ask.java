package dev.phoenix.domain;

import java.time.Instant;

/** A question the engine needs a human to answer (a blocker, a missing answer, a decision). */
public record Ask(long id, Long jobId, String kind, String prompt, String options, int priority, AskStatus status,
                  String answer, String channel, Instant createdAt, Instant dueAt, Instant answeredAt) {
}
