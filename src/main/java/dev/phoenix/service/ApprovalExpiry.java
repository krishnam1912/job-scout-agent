package dev.phoenix.service;

import dev.phoenix.domain.Job;
import dev.phoenix.domain.JobState;
import dev.phoenix.lifecycle.LifecycleService;
import dev.phoenix.store.ApprovalRepository;
import dev.phoenix.store.JobRepository;
import dev.phoenix.store.TxRunner;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Returns APPROVED jobs whose approval expired unused to QUEUED. Without this, a job approved in a batch that never
 * reached it would sit in APPROVED forever: out of the queue, impossible to start and invisible in the digest, which
 * is exactly the kind of silently lost opportunity the engine exists to prevent.
 */
public class ApprovalExpiry {

    static final String ACTOR = "engine";

    private final JobRepository jobs;
    private final ApprovalRepository approvals;
    private final LifecycleService lifecycle;
    private final TxRunner tx;
    private final Clock clock;

    public ApprovalExpiry(JobRepository jobs, ApprovalRepository approvals, LifecycleService lifecycle, TxRunner tx,
                          Clock clock) {
        this.jobs = jobs;
        this.approvals = approvals;
        this.lifecycle = lifecycle;
        this.tx = tx;
        this.clock = clock;
    }

    /** @return how many jobs were put back in the queue */
    public int releaseLapsed() {
        return tx.inTx(() -> {
            Instant now = clock.instant();
            int released = 0;
            for (Job job : jobs.findByStates(List.of(JobState.APPROVED))) {
                boolean covered = approvals.findByScope(ApprovalService.scope(job.id())).stream()
                        .anyMatch(a -> a.isUsableAt(now));
                if (!covered) {
                    lifecycle.transition(job.id(), JobState.QUEUED, ACTOR, "approval expired unused; back in the queue");
                    released++;
                }
            }
            return released;
        });
    }
}
