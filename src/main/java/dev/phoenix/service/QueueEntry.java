package dev.phoenix.service;

import dev.phoenix.domain.Job;
import dev.phoenix.domain.Score;

/** A queued job with the score that put it there. */
public record QueueEntry(Job job, Score score) {

    public int fitPct() {
        return score.fitPct();
    }
}
