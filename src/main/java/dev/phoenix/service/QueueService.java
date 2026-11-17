package dev.phoenix.service;

import dev.phoenix.domain.Job;
import dev.phoenix.domain.JobState;
import dev.phoenix.domain.Score;
import dev.phoenix.store.JobRepository;
import dev.phoenix.store.ScoreRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The global approval queue: every QUEUED job once per group (best fit, then highest pre-score, then newest),
 * ordered by fit, then by how soon the posting closes, then by how long it has waited.
 */
public class QueueService {

    private static final Comparator<QueueEntry> BEST_IN_GROUP = Comparator
            .comparingInt(QueueEntry::fitPct)
            .thenComparingInt((QueueEntry e) -> e.job().prescore() == null ? 0 : e.job().prescore())
            .thenComparingLong(e -> e.job().id());

    private static final Comparator<QueueEntry> QUEUE_ORDER = Comparator
            .comparingInt(QueueEntry::fitPct).reversed()
            .thenComparing((QueueEntry e) -> e.job().closingDate(), Comparator.nullsLast(Comparator.<Instant>naturalOrder()))
            .thenComparing(e -> e.job().firstSeen())
            .thenComparingLong(e -> e.job().id());

    private final JobRepository jobs;
    private final ScoreRepository scores;
    private final ApprovalExpiry expiry;

    public QueueService(JobRepository jobs, ScoreRepository scores, ApprovalExpiry expiry) {
        this.jobs = jobs;
        this.scores = scores;
        this.expiry = expiry;
    }

    /**
     * Jobs whose approval lapsed unused are put back first, so an approval that was never acted on can't hide a job.
     *
     * @param minFit minimum fit percent, or null for no minimum
     */
    public List<QueueEntry> queue(Integer minFit, int limit) {
        expiry.releaseLapsed();
        List<Job> queued = jobs.findByStates(List.of(JobState.QUEUED));
        Map<Long, Score> latest = scores.latestByJob(queued.stream().map(Job::id).toList());
        Map<String, QueueEntry> bestPerGroup = new LinkedHashMap<>();
        for (Job job : queued) {
            Score score = latest.get(job.id());
            if (score == null || (minFit != null && score.fitPct() < minFit)) {
                continue;
            }
            String key = job.groupId() == null ? "job-" + job.id() : "group-" + job.groupId();
            QueueEntry entry = new QueueEntry(job, score);
            bestPerGroup.merge(key, entry, (a, b) -> BEST_IN_GROUP.compare(b, a) > 0 ? b : a);
        }
        List<QueueEntry> ordered = new ArrayList<>(bestPerGroup.values());
        ordered.sort(QUEUE_ORDER);
        return ordered.subList(0, Math.max(0, Math.min(limit, ordered.size())));
    }
}
