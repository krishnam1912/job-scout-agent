package dev.phoenix.domain;

/** Where a 1-5 rubric score sends a job: at least 3.5 is queued, 3.0 to below 3.5 is a near miss, the rest is below. */
public enum ScoreBand {
    QUEUE(JobState.QUEUED), NEAR_MISS(JobState.NEAR_MISS), BELOW(JobState.BELOW);

    public static final double MIN_SCORE = 1.0;
    public static final double MAX_SCORE = 5.0;
    public static final double QUEUE_THRESHOLD = 3.5;
    public static final double NEAR_MISS_THRESHOLD = 3.0;

    private final JobState state;

    ScoreBand(JobState state) {
        this.state = state;
    }

    public JobState state() {
        return state;
    }

    public static ScoreBand of(double score) {
        if (score >= QUEUE_THRESHOLD) {
            return QUEUE;
        }
        return score >= NEAR_MISS_THRESHOLD ? NEAR_MISS : BELOW;
    }
}
