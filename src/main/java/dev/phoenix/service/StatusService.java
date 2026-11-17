package dev.phoenix.service;

import dev.phoenix.domain.AskStatus;
import dev.phoenix.store.AskRepository;
import dev.phoenix.store.JobRepository;
import dev.phoenix.store.RunRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Builds the pipeline snapshot: counts per state, open asks, jobs at risk and the last scan. */
public class StatusService {

    private static final Duration CLOSING_HORIZON = Duration.ofHours(72);

    private final JobRepository jobs;
    private final AskRepository asks;
    private final RunRepository runs;
    private final Clock clock;

    public StatusService(JobRepository jobs, AskRepository asks, RunRepository runs, Clock clock) {
        this.jobs = jobs;
        this.asks = asks;
        this.runs = runs;
        this.clock = clock;
    }

    public StatusReport report() {
        Instant now = clock.instant();
        Optional<RunRepository.Run> lastScan = runs.latest("scan");
        return new StatusReport(now, jobs.countByState(), asks.findByStatuses(List.of(AskStatus.OPEN, AskStatus.OFFERED)),
                jobs.findAtRisk(now, now.plus(CLOSING_HORIZON)),
                lastScan.map(RunRepository.Run::startedAt).orElse(null),
                lastScan.map(RunRepository.Run::status).orElse(null));
    }
}
