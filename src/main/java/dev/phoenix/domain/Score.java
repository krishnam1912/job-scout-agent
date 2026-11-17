package dev.phoenix.domain;

import java.time.Instant;

public record Score(long id, long jobId, String scorer, double score, int fitPct, String rationale,
                    String rubricVersion, Instant scoredAt) {

    /** Fit % is the 1-5 rubric score over 5, rounded. */
    public static int fitPct(double score) {
        return (int) Math.round(score / 5.0 * 100.0);
    }
}
