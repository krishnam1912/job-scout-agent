package dev.phoenix.discovery;

import dev.phoenix.domain.Posting;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;

/** The 0-100 keyword pre-score of scan.py, used to pick which postings are worth the operator's judgment. */
public final class Prescorer {

    private final FilterConfig.Prescore config;
    private final Map<String, KeywordMatcher> matchers = new LinkedHashMap<>();

    public Prescorer(FilterConfig.Prescore config) {
        this.config = config;
        List.of(config.stack(), config.domain(), config.levelTitles(), config.titlePenalty())
                .forEach(table -> table.keySet().forEach(kw -> matchers.put(kw, KeywordMatcher.of(List.of(kw)))));
    }

    public int score(Posting posting, OptionalInt years) {
        String title = posting.title().toLowerCase(Locale.ROOT);
        String text = (posting.title() + " " + posting.jd()).toLowerCase(Locale.ROOT);
        int score = 0;
        score += sumMatches(config.stack(), text);
        score += sumMatches(config.domain(), text);
        score += firstMatch(config.levelTitles(), title);
        score -= sumMatches(config.titlePenalty(), title);
        score += firstLocationBonus(posting.location().toLowerCase(Locale.ROOT));
        score += companyBonus(posting.company());
        score += yearsAdjustment(years);
        return Math.max(0, Math.min(100, score));
    }

    private int sumMatches(Map<String, Integer> table, String text) {
        int sum = 0;
        for (Map.Entry<String, Integer> e : table.entrySet()) {
            if (matchers.get(e.getKey()).matches(text)) {
                sum += e.getValue();
            }
        }
        return sum;
    }

    private int firstMatch(Map<String, Integer> table, String text) {
        for (Map.Entry<String, Integer> e : table.entrySet()) {
            if (matchers.get(e.getKey()).matches(text)) {
                return e.getValue();
            }
        }
        return 0;
    }

    private int firstLocationBonus(String location) {
        for (Map.Entry<String, Integer> e : config.locationBonus().entrySet()) {
            if (location.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return 0;
    }

    private int companyBonus(String company) {
        if (config.tier1().contains(company)) {
            return config.tier1Bonus();
        }
        return config.tier2().contains(company) ? config.tier2Bonus() : 0;
    }

    private int yearsAdjustment(OptionalInt years) {
        if (years.isEmpty()) {
            return 0;
        }
        int low = config.yearsIdeal().get(0);
        int high = config.yearsIdeal().get(1);
        int n = years.getAsInt();
        if (n >= low && n <= high) {
            return config.yearsBonus();
        }
        return n > high ? -config.yearsPenaltyPerYearOver() * (n - high) : 0;
    }
}
