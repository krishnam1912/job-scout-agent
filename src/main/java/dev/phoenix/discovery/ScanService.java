package dev.phoenix.discovery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.phoenix.dedupe.GroupingService;
import dev.phoenix.discovery.PostingEvaluator.Evaluated;
import dev.phoenix.domain.Posting;
import dev.phoenix.error.PhoenixException;
import dev.phoenix.store.RunRepository;
import dev.phoenix.store.TxRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Discovery end to end: fetch the boards, split known from new postings, filter and score the new ones, group
 * them, store everything in one transaction and return the best candidates. A dry run does the same thinking and
 * writes nothing, so it can be compared with scan.py or used to preview.
 */
public class ScanService {

    private static final Logger log = LoggerFactory.getLogger(ScanService.class);
    private static final String RUN_KIND = "scan";

    private final DiscoveryConfigLoader configLoader;
    private final AdapterRegistry registry;
    private final BoardFetcher fetcher;
    private final ScanClassifier classifier;
    private final ScanWriter writer;
    private final GroupingService grouping;
    private final CandidatePool pool;
    private final RunRepository runs;
    private final TxRunner tx;
    private final Clock clock;
    private final ObjectMapper json;
    private final CandidateSelector selector = new CandidateSelector();

    public ScanService(DiscoveryConfigLoader configLoader, AdapterRegistry registry, BoardFetcher fetcher,
                       ScanClassifier classifier, ScanWriter writer, GroupingService grouping, CandidatePool pool,
                       RunRepository runs, TxRunner tx, Clock clock, ObjectMapper json) {
        this.configLoader = configLoader;
        this.registry = registry;
        this.fetcher = fetcher;
        this.classifier = classifier;
        this.writer = writer;
        this.grouping = grouping;
        this.pool = pool;
        this.runs = runs;
        this.tx = tx;
        this.clock = clock;
        this.json = json;
    }

    public ScanResult scan(ScanRequest request) {
        DiscoveryConfig config = configLoader.load();
        PostingEvaluator evaluator = new PostingEvaluator(config.filters());
        List<BoardTask> tasks = registry.plan(config, evaluator::titleOk, request.company());
        if (tasks.isEmpty()) {
            log.warn("no source to scan{}", request.company() == null ? "" : " matches --company " + request.company());
        }
        Instant startedAt = clock.instant();
        Long runId = request.dryRun() ? null : runs.start(RUN_KIND, startedAt);
        try {
            List<BoardResult> boards = fetcher.fetchAll(tasks);
            Classification classification = classifier.classify(boards, evaluator);
            ScanResult result = request.dryRun()
                    ? preview(classification, boards, request.limit())
                    : store(classification, boards, startedAt, request.limit());
            if (runId != null) {
                runs.finish(runId, "OK", clock.instant(), checkpoint(result.stats()));
            }
            return result;
        } catch (RuntimeException e) {
            if (runId != null) {
                runs.finish(runId, "FAILED", clock.instant(), e.getMessage());
            }
            throw e;
        }
    }

    private ScanResult store(Classification c, List<BoardResult> boards, Instant startedAt, int limit) {
        ScanWriter.Outcome outcome = tx.inTx(() -> writer.write(c, boards, startedAt));
        List<CandidateView> chosen = selector.select(pool.existing(), pool.hiddenGroups(), limit);
        return result(c, chosen, outcome.duplicatesHidden(), outcome.closed(), outcome.failedBoards());
    }

    private ScanResult preview(Classification c, List<BoardResult> boards, int limit) {
        List<Evaluated> passing = c.passing();
        List<Long> groupIds = grouping.assign(passing.stream().map(ScanService::groupingItem).toList(), false);
        List<CandidateView> candidates = pool.existing();
        for (int i = 0; i < passing.size(); i++) {
            Evaluated e = passing.get(i);
            Posting p = e.posting();
            candidates.add(new CandidateView(null, groupIds.get(i), p.company(), p.jobId(), p.title(), p.location(),
                    p.url(), e.prescore(), p.jd()));
        }
        int failed = (int) boards.stream().filter(b -> !b.ok()).count();
        return result(c, selector.select(candidates, pool.hiddenGroups(), limit), 0, 0, failed);
    }

    private static GroupingService.Item groupingItem(Evaluated e) {
        Posting p = e.posting();
        return new GroupingService.Item(p.company(), p.title(), p.jd());
    }

    private ScanResult result(Classification c, List<CandidateView> chosen, int duplicates, int closed, int failed) {
        Map<String, Integer> byReason = new TreeMap<>();
        c.fresh().stream().filter(e -> !e.filter().passed())
                .forEach(e -> byReason.merge(e.filter().reason(), 1, Integer::sum));
        ScanStats stats = new ScanStats(c.fetched(), c.known().size(), c.fresh().size(), c.passing().size(),
                duplicates, byReason, failed, closed);
        List<Candidate> candidates = chosen.stream()
                .map(v -> new Candidate(v.jobId(), v.company(), v.extId(), v.title(), v.location(), v.url(),
                        v.prescore(), JdSummary.of(v.jd())))
                .toList();
        return new ScanResult(candidates, stats);
    }

    private String checkpoint(ScanStats stats) {
        try {
            return json.writeValueAsString(stats);
        } catch (JsonProcessingException e) {
            throw new PhoenixException("Cannot serialise the scan summary", e);
        }
    }
}
