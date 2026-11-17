package dev.phoenix.service;

import dev.phoenix.domain.Ask;
import dev.phoenix.domain.Job;
import dev.phoenix.domain.JobState;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** A snapshot of the pipeline. {@code lastScanAt} and {@code lastScanStatus} are null before the first scan. */
public record StatusReport(Instant generatedAt, Map<JobState, Integer> counts, List<Ask> openAsks, List<Job> atRisk,
                           Instant lastScanAt, String lastScanStatus) {

    public StatusReport {
        counts = Map.copyOf(counts);
        openAsks = List.copyOf(openAsks);
        atRisk = List.copyOf(atRisk);
    }
}
