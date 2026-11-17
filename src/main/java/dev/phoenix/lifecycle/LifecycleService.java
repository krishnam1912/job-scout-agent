package dev.phoenix.lifecycle;

import dev.phoenix.domain.Job;
import dev.phoenix.domain.JobState;
import dev.phoenix.error.NotFoundException;
import dev.phoenix.store.EventRepository;
import dev.phoenix.store.JobRepository;
import dev.phoenix.store.TxRunner;

import java.time.Clock;
import java.time.Instant;

/** The only code path that changes {@code jobs.state}: validates, updates, logs and reschedules in one transaction. */
public class LifecycleService {

    private final JobRepository jobs;
    private final EventRepository events;
    private final NextActionPolicy policy;
    private final TxRunner tx;
    private final Clock clock;

    public LifecycleService(JobRepository jobs, EventRepository events, NextActionPolicy policy, TxRunner tx, Clock clock) {
        this.jobs = jobs;
        this.events = events;
        this.policy = policy;
        this.tx = tx;
        this.clock = clock;
    }

    /**
     * @throws IllegalTransitionException when the table does not allow {@code from -> to}
     * @throws NotFoundException when the job does not exist
     */
    public Job transition(long jobId, JobState to, String actor, String note) {
        return tx.inTx(() -> {
            Job job = jobs.findById(jobId).orElseThrow(() -> new NotFoundException("Job", jobId));
            if (!Transitions.isAllowed(job.state(), to)) {
                throw new IllegalTransitionException(jobId, job.state(), to);
            }
            Instant now = clock.instant();
            Instant next = policy.nextActionAt(to, now, job.closingDate());
            Instant closedAt = to == JobState.CLOSED ? now : job.closedAt();
            jobs.updateState(jobId, to, next, closedAt);
            events.append(jobId, job.state(), to, actor, note, now);
            return job.withState(to, next, closedAt);
        });
    }
}
