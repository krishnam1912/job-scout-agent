package dev.phoenix.service;

import dev.phoenix.channel.AskService;
import dev.phoenix.dedupe.CooldownGuard;
import dev.phoenix.dedupe.GuardResult;
import dev.phoenix.domain.Application;
import dev.phoenix.domain.Ask;
import dev.phoenix.domain.Job;
import dev.phoenix.domain.JobState;
import dev.phoenix.error.NotFoundException;
import dev.phoenix.error.UserErrorException;
import dev.phoenix.lifecycle.LifecycleService;
import dev.phoenix.store.ApplicationRepository;
import dev.phoenix.store.JobRepository;
import dev.phoenix.store.TxRunner;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The application lifecycle after approval: starting an attempt (only with a valid approval and a clean
 * already-applied check), recording how it ended and tracking what the employer did next. Nothing here submits
 * anything; the operator does the work and reports back.
 */
public class ApplicationService {

    private static final int BLOCKER_ASK_PRIORITY = 5;
    private static final Set<JobState> STARTABLE = Set.of(JobState.APPROVED, JobState.BLOCKED, JobState.FAILED);

    private final JobRepository jobs;
    private final ApplicationRepository applications;
    private final ApprovalService approvals;
    private final CooldownGuard guard;
    private final LifecycleService lifecycle;
    private final AskService asks;
    private final TxRunner tx;
    private final Clock clock;

    public ApplicationService(JobRepository jobs, ApplicationRepository applications, ApprovalService approvals,
                              CooldownGuard guard, LifecycleService lifecycle, AskService asks, TxRunner tx, Clock clock) {
        this.jobs = jobs;
        this.applications = applications;
        this.approvals = approvals;
        this.guard = guard;
        this.lifecycle = lifecycle;
        this.asks = asks;
        this.tx = tx;
        this.clock = clock;
    }

    public StartOutcome start(long jobId, String actor) {
        return start(jobId, actor, false);
    }

    /**
     * @param verifiedNotSubmitted only for a SUBMIT_UNKNOWN job: the operator checked the ATS dashboard or the
     *        confirmation email and the earlier attempt did not go through. Without it such a job is never restarted,
     *        because a blind retry could apply twice.
     */
    public StartOutcome start(long jobId, String actor, boolean verifiedNotSubmitted) {
        return tx.inTx(() -> {
            Job job = jobs.findById(jobId).orElseThrow(() -> new NotFoundException("Job", jobId));
            if (job.state() == JobState.SUBMIT_UNKNOWN && !verifiedNotSubmitted) {
                return StartOutcome.refused(job, "the last attempt may have gone through: check the ATS dashboard or "
                        + "confirmation email first; record it as submitted if it did, otherwise start again with "
                        + "verified_not_submitted=true");
            }
            if (!STARTABLE.contains(job.state()) && job.state() != JobState.SUBMIT_UNKNOWN) {
                return StartOutcome.refused(job, "job is " + job.state() + "; it must be approved first");
            }
            if (approvals.usableApproval(jobId).isEmpty()) {
                return StartOutcome.refused(job, "no valid approval (missing, used or expired); ask the user again");
            }
            GuardResult verdict = guard.check(job);
            if (!verdict.allowed()) {
                return StartOutcome.refused(job, verdict.verdict() + ": " + verdict.reason());
            }
            Job started = lifecycle.transition(jobId, JobState.IN_PROGRESS, actor, "application started");
            Application previous = applications.findByJob(jobId).orElse(null);
            int attempts = previous == null ? 1 : previous.attempts() + 1;
            applications.save(new Application(0, jobId, JobState.IN_PROGRESS, attempts, null, null,
                    previous == null ? null : previous.resumeVersion(), null, null,
                    previous == null ? null : previous.note()));
            return StartOutcome.started(started);
        });
    }

    /** @return the job after the change, and the ask that was opened when the attempt was blocked */
    public Reported record(ResultReport report, String actor) {
        if (report.outcome() == ApplicationOutcome.BLOCKED && (report.blockerType() == null || report.blockerType().isBlank())) {
            throw new UserErrorException("A blocked application needs a blocker_type");
        }
        return tx.inTx(() -> {
            Job job = lifecycle.transition(report.jobId(), report.outcome().state(), actor, describe(report));
            Application previous = applications.findByJob(report.jobId()).orElse(null);
            applications.save(afterReport(previous, report, clock.instant()));
            if (report.outcome() == ApplicationOutcome.SUBMITTED) {
                approvals.consume(report.jobId());
            }
            Optional<Ask> ask = report.outcome() == ApplicationOutcome.BLOCKED
                    ? Optional.of(openBlockerAsk(job, report)) : Optional.empty();
            return new Reported(job, ask.orElse(null));
        });
    }

    /** Records what the employer did after submission (OA, interview, rejection, offer). */
    public Job markOutcome(long jobId, PostSubmitOutcome outcome, String actor, String note) {
        return tx.inTx(() -> {
            Job job = lifecycle.transition(jobId, outcome.state(), actor, note);
            applications.findByJob(jobId).ifPresent(a -> applications.save(new Application(a.id(), jobId, outcome.state(),
                    a.attempts(), a.blockerType(), a.blockerDetail(), a.resumeVersion(), a.submittedAt(),
                    a.confirmationRef(), a.note())));
            return job;
        });
    }

    /** Result of {@link #record}: {@code openedAsk} is null unless the attempt was blocked. */
    public record Reported(Job job, Ask openedAsk) {
    }

    /** The application row after an attempt ended: blocker fields only while blocked, submit time and reference once submitted. */
    private static Application afterReport(Application previous, ResultReport report, Instant now) {
        boolean blocked = report.outcome() == ApplicationOutcome.BLOCKED;
        boolean submitted = report.outcome() == ApplicationOutcome.SUBMITTED;
        int attempts = previous == null ? 0 : previous.attempts();
        String resume = previous == null ? null : previous.resumeVersion();
        Instant submittedAt = submitted ? now : previous == null ? null : previous.submittedAt();
        String reference = submitted ? report.confirmationRef() : previous == null ? null : previous.confirmationRef();
        String note = !blocked && report.detail() != null ? report.detail() : previous == null ? null : previous.note();
        return new Application(0, report.jobId(), report.outcome().state(), attempts,
                blocked ? report.blockerType() : null, blocked ? report.detail() : null, resume, submittedAt, reference, note);
    }

    private Ask openBlockerAsk(Job job, ResultReport report) {
        String prompt = "Blocked applying to \"" + job.title() + "\" at " + job.company() + ": " + report.blockerType()
                + (report.detail() == null || report.detail().isBlank() ? "" : " - " + report.detail());
        return asks.create(job.id(), "blocker", prompt, null, BLOCKER_ASK_PRIORITY);
    }

    private static String describe(ResultReport report) {
        StringBuilder note = new StringBuilder(report.outcome().name().toLowerCase(Locale.ROOT));
        if (report.blockerType() != null && !report.blockerType().isBlank()) {
            note.append(": ").append(report.blockerType());
        }
        if (report.detail() != null && !report.detail().isBlank()) {
            note.append(" - ").append(report.detail());
        }
        return note.toString();
    }
}
