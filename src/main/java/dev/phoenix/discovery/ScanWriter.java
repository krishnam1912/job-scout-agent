package dev.phoenix.discovery;

import dev.phoenix.dedupe.ExactKey;
import dev.phoenix.dedupe.GroupingService;
import dev.phoenix.discovery.PostingEvaluator.Evaluated;
import dev.phoenix.domain.Job;
import dev.phoenix.domain.JobState;
import dev.phoenix.domain.Posting;
import dev.phoenix.lifecycle.LifecycleService;
import dev.phoenix.store.EventRepository;
import dev.phoenix.store.GroupRepository;
import dev.phoenix.store.JobRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stores the outcome of a scan. Must run inside one transaction (the caller's), so a crash half way leaves the
 * database as it was and the next scan simply starts over.
 */
public class ScanWriter {

    private static final String ACTOR = "scanner";

    /** What the write changed beyond plain inserts. */
    record Outcome(int duplicatesHidden, int closed, int failedBoards) {
    }

    private final JobRepository jobs;
    private final EventRepository events;
    private final GroupRepository groups;
    private final GroupingService grouping;
    private final LifecycleService lifecycle;
    private final SourceHealth health;
    private final PostingLiveness liveness;

    public ScanWriter(JobRepository jobs, EventRepository events, GroupRepository groups, GroupingService grouping,
               LifecycleService lifecycle, SourceHealth health, PostingLiveness liveness) {
        this.jobs = jobs;
        this.events = events;
        this.groups = groups;
        this.grouping = grouping;
        this.lifecycle = lifecycle;
        this.health = health;
        this.liveness = liveness;
    }

    Outcome write(Classification classification, List<BoardResult> boards, Instant startedAt) {
        classification.known().forEach(job -> jobs.markSeen(job.id(), startedAt));
        List<Stored> stored = insertAll(classification.fresh(), startedAt);
        int duplicates = settle(stored);
        int closed = 0;
        int failed = 0;
        for (BoardResult board : boards) {
            if (board.ok()) {
                closed += recordSuccess(board, startedAt);
            } else {
                health.recordFailure(board.task().kind(), board.task().board());
                failed++;
            }
        }
        return new Outcome(duplicates, closed, failed);
    }

    private record Stored(long jobId, Evaluated evaluated) {
    }

    private List<Stored> insertAll(List<Evaluated> fresh, Instant now) {
        List<Stored> stored = new ArrayList<>();
        for (Evaluated e : fresh) {
            Posting p = e.posting();
            boolean keepJd = e.filter().passed();
            Job draft = new Job(0, p.source(), ExactKey.normalizeCompany(p.company()), p.jobId(), p.url(), p.title(),
                    p.location(), null, JobState.DISCOVERED, keepJd ? p.jd() : null, JdHash.of(p.jd()),
                    e.prescore(), now, now, null, null, null);
            long id = jobs.insert(draft);
            events.append(id, null, JobState.DISCOVERED, ACTOR, "found on " + p.source() + "/" + p.company(), now);
            stored.add(new Stored(id, e));
        }
        return stored;
    }

    /** Filtered jobs end; passing jobs are grouped and become candidates unless their group is already engaged. */
    private int settle(List<Stored> stored) {
        List<Stored> passing = stored.stream().filter(s -> s.evaluated().filter().passed()).toList();
        stored.stream().filter(s -> !s.evaluated().filter().passed()).forEach(s ->
                lifecycle.transition(s.jobId(), JobState.FILTERED_OUT, ACTOR, "filtered: " + s.evaluated().filter().reason()));
        List<Long> groupIds = grouping.assign(passing.stream().map(ScanWriter::groupingItem).toList(), true);
        Map<Long, Boolean> engagedCache = new HashMap<>();
        int duplicates = 0;
        for (int i = 0; i < passing.size(); i++) {
            Stored s = passing.get(i);
            Long groupId = groupIds.get(i);
            jobs.updateScan(s.jobId(), s.evaluated().prescore(), groupId);
            boolean engaged = groupId != null && engagedCache.computeIfAbsent(groupId, groups::hasEngagedMember);
            if (engaged) {
                lifecycle.transition(s.jobId(), JobState.FILTERED_OUT, ACTOR, "same role as an engaged job in group " + groupId);
                duplicates++;
            } else {
                lifecycle.transition(s.jobId(), JobState.CANDIDATE, ACTOR, "prescore " + s.evaluated().prescore());
            }
        }
        return duplicates;
    }

    private static GroupingService.Item groupingItem(Stored s) {
        Posting p = s.evaluated().posting();
        return new GroupingService.Item(p.company(), p.title(), p.jd());
    }

    private int recordSuccess(BoardResult board, Instant startedAt) {
        BoardTask task = board.task();
        Optional<Instant> previous = health.recordSuccess(task.kind(), task.board(), startedAt, board.postings().size());
        if (previous.isEmpty()) {
            return 0;
        }
        return liveness.closeVanished(task.kind(), ExactKey.normalizeCompany(task.board()), previous.get());
    }
}
