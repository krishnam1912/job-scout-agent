package dev.phoenix.discovery;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Picks what the operator sees: groups that already have an engaged member are hidden, each remaining group shows
 * only its best posting (highest pre-score, then newest id), and the top {@code limit} by pre-score are returned.
 */
final class CandidateSelector {

    private static final Comparator<CandidateView> BY_PRESCORE_DESC =
            Comparator.comparingInt(CandidateView::prescore).reversed();

    List<CandidateView> select(List<CandidateView> pool, Set<Long> engagedGroups, int limit) {
        List<CandidateView> visible = new ArrayList<>(pool.stream()
                .filter(c -> c.groupId() == null || !engagedGroups.contains(c.groupId()))
                .toList());
        visible.sort(BY_PRESCORE_DESC);
        Map<String, CandidateView> bestPerGroup = new LinkedHashMap<>();
        for (int i = 0; i < visible.size(); i++) {
            CandidateView c = visible.get(i);
            String key = c.groupId() == null ? "single-" + i : "group-" + c.groupId();
            bestPerGroup.merge(key, c, (best, next) -> isBetter(next, best) ? next : best);
        }
        List<CandidateView> distinct = new ArrayList<>(bestPerGroup.values());
        distinct.sort(BY_PRESCORE_DESC);
        return distinct.subList(0, Math.max(0, Math.min(limit, distinct.size())));
    }

    private static boolean isBetter(CandidateView challenger, CandidateView best) {
        if (challenger.prescore() != best.prescore()) {
            return challenger.prescore() > best.prescore();
        }
        BigInteger a = challenger.numericId();
        return a.compareTo(best.numericId()) > 0;
    }
}
