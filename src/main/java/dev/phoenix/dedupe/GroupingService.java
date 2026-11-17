package dev.phoenix.dedupe;

import dev.phoenix.domain.JobGroup;
import dev.phoenix.store.GroupRepository;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Layers 2 and 3: postings of the same company with the same normalised title and a similar JD share a group,
 * whether they came from different sources (cross-source) or are several requisitions of one role (multi-req).
 * The group keeps the first member's signature; the queue later shows one job per group.
 */
public class GroupingService {

    /** What grouping needs to know about one posting. */
    public record Item(String company, String title, String jdText) {
    }

    private record View(long id, String normTitle, int[] signature) {
    }

    private final GroupRepository groups;
    private final MinHash minHash;
    private final double threshold;
    private final Clock clock;

    public GroupingService(GroupRepository groups, MinHash minHash, double threshold, Clock clock) {
        this.groups = groups;
        this.minHash = minHash;
        this.threshold = threshold;
        this.clock = clock;
    }

    /**
     * Assigns a group to each item, in order, so later items can join groups opened by earlier ones.
     *
     * @param persist when false nothing is written and new groups get synthetic negative ids (dry runs)
     * @return one entry per item; null when the title is blank and nothing can be compared
     */
    public List<Long> assign(List<Item> items, boolean persist) {
        Map<String, List<View>> byCompany = new HashMap<>();
        List<Long> result = new ArrayList<>(items.size());
        long syntheticId = -1;
        for (Item item : items) {
            String company = ExactKey.normalizeCompany(item.company());
            String normTitle = TitleText.normalize(item.title());
            if (normTitle.isEmpty()) {
                result.add(null);
                continue;
            }
            int[] signature = minHash.signature(item.jdText());
            List<View> known = byCompany.computeIfAbsent(company, this::load);
            View match = bestMatch(known, normTitle, signature);
            if (match == null) {
                long id = persist ? groups.insert(company, normTitle, signature, clock.instant()) : syntheticId--;
                match = new View(id, normTitle, signature);
                known.add(match);
            }
            result.add(match.id());
        }
        return result;
    }

    private View bestMatch(List<View> known, String normTitle, int[] signature) {
        View best = null;
        double bestSimilarity = -1;
        for (View v : known) {
            if (!v.normTitle().equals(normTitle)) {
                continue;
            }
            double similarity = minHash.similarity(v.signature(), signature);
            if (similarity >= threshold && similarity > bestSimilarity) {
                best = v;
                bestSimilarity = similarity;
            }
        }
        return best;
    }

    private List<View> load(String company) {
        List<View> views = new ArrayList<>();
        for (JobGroup g : groups.findByCompany(company)) {
            views.add(new View(g.id(), g.normTitle(), g.signature()));
        }
        return views;
    }
}
