package dev.phoenix.service;

/** One score from the operator: the 1-5 rubric result, why, and which rubric version produced it. */
public record ScoreInput(long jobId, double score, String rationale, String rubricVersion) {
}
