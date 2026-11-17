package dev.phoenix.discovery;

import dev.phoenix.discovery.PostingEvaluator.Evaluated;
import dev.phoenix.domain.Job;

import java.util.List;

/** Fetched postings split into those the engine already knows and those that are new, with their verdicts. */
record Classification(int fetched, List<Job> known, List<Evaluated> fresh) {

    List<Evaluated> passing() {
        return fresh.stream().filter(e -> e.filter().passed()).toList();
    }
}
