package dev.phoenix.legacy;

import dev.phoenix.dedupe.ExactKey;
import dev.phoenix.domain.JobState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Combines rows about the same job (same company and id) from different files into one. For each field the
 * highest-precedence file that has a value wins, so an applications row beats the leads CSV, which beats the queue,
 * which beats the seen list. Within one file the later row wins. Notes of every row are kept.
 */
final class LegacyMerger {

    List<LegacyJob> merge(List<LegacyJob> rows) {
        Map<ExactKey, List<LegacyJob>> byKey = new LinkedHashMap<>();
        for (LegacyJob row : rows) {
            byKey.computeIfAbsent(ExactKey.of(row.company(), row.extId()), k -> new ArrayList<>()).add(row);
        }
        List<LegacyJob> merged = new ArrayList<>();
        for (List<LegacyJob> group : byKey.values()) {
            merged.add(mergeGroup(group));
        }
        return merged;
    }

    private LegacyJob mergeGroup(List<LegacyJob> group) {
        List<LegacyJob> byPrecedence = new ArrayList<>(group);
        byPrecedence.sort(Comparator.comparingInt((LegacyJob j) -> j.origin().precedence()).reversed());
        LegacyJob top = byPrecedence.get(0);
        String note = group.stream().map(LegacyJob::note).filter(n -> n != null && !n.isBlank()).distinct()
                .collect(Collectors.joining(" | "));
        return new LegacyJob(top.origin(), first(byPrecedence, LegacyJob::company), top.extId(),
                byPrecedence.stream().allMatch(LegacyJob::syntheticId),
                first(byPrecedence, LegacyJob::title), first(byPrecedence, LegacyJob::location),
                first(byPrecedence, LegacyJob::url), first(byPrecedence, LegacyJob::score),
                first(byPrecedence, LegacyJob::rationale), first(byPrecedence, LegacyJob::date),
                first(byPrecedence, LegacyJob::state), note);
    }

    private static <T> T first(List<LegacyJob> byPrecedence, Function<LegacyJob, T> field) {
        for (LegacyJob job : byPrecedence) {
            T value = field.apply(job);
            if (value != null && !(value instanceof String s && s.isBlank())) {
                return value;
            }
        }
        return null;
    }

    /** The state the merged job should have; seen-only jobs are placeholders that dedupe but never queue. */
    static JobState stateOf(LegacyJob job) {
        return job.state() == null ? JobState.BELOW : job.state();
    }
}
