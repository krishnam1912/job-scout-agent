package dev.phoenix.service;

import dev.phoenix.domain.Job;
import dev.phoenix.domain.JobState;
import dev.phoenix.domain.Score;
import dev.phoenix.domain.ScoreBand;
import dev.phoenix.error.NotFoundException;
import dev.phoenix.error.UserErrorException;
import dev.phoenix.lifecycle.LifecycleService;
import dev.phoenix.store.JobRepository;
import dev.phoenix.store.ScoreRepository;
import dev.phoenix.store.TxRunner;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Stores the operator's scores and moves each job to the state its band demands. All or nothing per call. */
public class ScoringService {

    /** What happened to one scored job. */
    public record Scored(long jobId, JobState state, int fitPct) {
    }

    private static final Set<JobState> SCORABLE = Set.of(JobState.CANDIDATE, JobState.NEAR_MISS, JobState.BELOW);

    private final JobRepository jobs;
    private final ScoreRepository scores;
    private final LifecycleService lifecycle;
    private final TxRunner tx;
    private final Clock clock;

    public ScoringService(JobRepository jobs, ScoreRepository scores, LifecycleService lifecycle, TxRunner tx, Clock clock) {
        this.jobs = jobs;
        this.scores = scores;
        this.lifecycle = lifecycle;
        this.tx = tx;
        this.clock = clock;
    }

    /**
     * @param scorer who judged (for the audit trail), e.g. {@code claude-code}
     * @throws UserErrorException when any item is invalid; nothing is stored then
     */
    public List<Scored> record(List<ScoreInput> items, String scorer) {
        if (items.isEmpty()) {
            throw new UserErrorException("No scores given");
        }
        return tx.inTx(() -> {
            validate(items);
            return items.stream().map(item -> apply(item, scorer)).toList();
        });
    }

    private void validate(List<ScoreInput> items) {
        Set<Long> seen = new HashSet<>();
        for (ScoreInput item : items) {
            if (!seen.add(item.jobId())) {
                throw new UserErrorException("Job " + item.jobId() + " appears twice in one request");
            }
            if (Double.isNaN(item.score()) || item.score() < ScoreBand.MIN_SCORE || item.score() > ScoreBand.MAX_SCORE) {
                throw new UserErrorException("Job " + item.jobId() + ": score must be between 1 and 5, got " + item.score());
            }
            Job job = jobs.findById(item.jobId()).orElseThrow(() -> new NotFoundException("Job", item.jobId()));
            if (!SCORABLE.contains(job.state())) {
                throw new UserErrorException("Job " + job.id() + " is " + job.state()
                        + "; only CANDIDATE, NEAR_MISS or BELOW jobs can be scored");
            }
        }
    }

    private Scored apply(ScoreInput item, String scorer) {
        int fit = Score.fitPct(item.score());
        scores.insert(new Score(0, item.jobId(), scorer, item.score(), fit, item.rationale(), item.rubricVersion(),
                clock.instant()));
        String note = "score " + item.score() + " (" + fit + "%)";
        lifecycle.transition(item.jobId(), JobState.SCORED, scorer, note);
        JobState target = ScoreBand.of(item.score()).state();
        lifecycle.transition(item.jobId(), target, scorer, note);
        return new Scored(item.jobId(), target, fit);
    }
}
