package dev.phoenix.service;

import dev.phoenix.dedupe.CooldownGuard;
import dev.phoenix.dedupe.GuardResult;
import dev.phoenix.domain.Approval;
import dev.phoenix.domain.Job;
import dev.phoenix.domain.JobState;
import dev.phoenix.error.UserErrorException;
import dev.phoenix.lifecycle.LifecycleService;
import dev.phoenix.service.ApprovalOutcome.Refusal;
import dev.phoenix.store.ApprovalRepository;
import dev.phoenix.store.JobRepository;
import dev.phoenix.store.TxRunner;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Turns the user's go-ahead into approval records and moves the covered jobs from QUEUED to APPROVED. The user's
 * words are stored verbatim as evidence. Every approval is per job, so a rule such as {@code fit>=80} is expanded
 * at the moment it is given and cannot silently cover jobs queued later. A rule covers at most one batch
 * ({@value #MAX_BATCH} jobs, best fit first). Blocked or failed jobs, and jobs whose approval lapsed, can be
 * approved again; approving a job that already holds a valid approval changes nothing.
 */
public class ApprovalService {

    public static final Duration TTL = Duration.ofHours(24);
    private static final String SCOPE_PREFIX = "job:";
    public static final int MAX_BATCH = 10;
    private static final Set<JobState> APPROVABLE =
            EnumSet.of(JobState.QUEUED, JobState.APPROVED, JobState.BLOCKED, JobState.FAILED);

    private final JobRepository jobs;
    private final ApprovalRepository approvals;
    private final QueueService queue;
    private final CooldownGuard guard;
    private final LifecycleService lifecycle;
    private final TxRunner tx;
    private final Clock clock;

    public ApprovalService(JobRepository jobs, ApprovalRepository approvals, QueueService queue, CooldownGuard guard,
                           LifecycleService lifecycle, TxRunner tx, Clock clock) {
        this.jobs = jobs;
        this.approvals = approvals;
        this.queue = queue;
        this.guard = guard;
        this.lifecycle = lifecycle;
        this.tx = tx;
        this.clock = clock;
    }

    /**
     * @param jobIds explicit jobs (may be empty when a rule is given)
     * @param rule optional standing rule, expanded against the current queue
     * @param userWords the user's message, verbatim
     * @param via how the approval reached the engine, e.g. {@code claude-chat}
     */
    public ApprovalOutcome approve(List<Long> jobIds, ApprovalRule rule, String userWords, String via, String actor) {
        if (userWords == null || userWords.isBlank()) {
            throw new UserErrorException("An approval needs the user's own words as evidence");
        }
        Set<Long> targets = new LinkedHashSet<>(jobIds);
        if (rule != null) {
            queue.queue(rule.minFit(), MAX_BATCH).forEach(e -> targets.add(e.job().id()));
        }
        if (targets.isEmpty()) {
            throw new UserErrorException("Nothing to approve: give job_ids or a rule that matches queued jobs");
        }
        return tx.inTx(() -> {
            List<Long> approved = new ArrayList<>();
            List<Refusal> refused = new ArrayList<>();
            for (long id : targets) {
                Optional<String> problem = approveOne(id, userWords, via, actor);
                if (problem.isPresent()) {
                    refused.add(new Refusal(id, problem.get()));
                } else {
                    approved.add(id);
                }
            }
            return new ApprovalOutcome(approved, refused);
        });
    }

    /** @return the approval that currently covers the job, if any */
    public Optional<Approval> usableApproval(long jobId) {
        Instant now = clock.instant();
        return approvals.findByScope(scope(jobId)).stream().filter(a -> a.isUsableAt(now)).findFirst();
    }

    public void consume(long jobId) {
        usableApproval(jobId).ifPresent(a -> approvals.consume(a.id(), clock.instant()));
    }

    private Optional<String> approveOne(long id, String userWords, String via, String actor) {
        Optional<Job> found = jobs.findById(id);
        if (found.isEmpty()) {
            return Optional.of("job not found");
        }
        Job job = found.get();
        if (!APPROVABLE.contains(job.state())) {
            return Optional.of("job is " + job.state() + "; only queued, blocked, failed or lapsed approved jobs can be approved");
        }
        if (usableApproval(id).isPresent()) {
            return Optional.empty();
        }
        GuardResult verdict = guard.check(job);
        if (!verdict.allowed()) {
            return Optional.of(verdict.verdict() + ": " + verdict.reason());
        }
        Instant now = clock.instant();
        approvals.insert(new Approval(0, scope(id), via, userWords, now, now.plus(TTL), null));
        if (job.state() == JobState.QUEUED) {
            lifecycle.transition(id, JobState.APPROVED, actor, "approved: " + userWords);
        }
        return Optional.empty();
    }

    static String scope(long jobId) {
        return SCOPE_PREFIX + jobId;
    }
}
