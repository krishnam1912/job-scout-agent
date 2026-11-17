package dev.phoenix.discovery;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The personal side of discovery: what to keep ({@link Filters}), how to rank ({@link Prescore}) and the Amazon
 * search block. Same JSON shape as {@code templates/filters.example.json}; unset lists are empty.
 */
public record FilterConfig(String smartrecruitersCountry, AmazonConfig amazon, Filters filters, Prescore prescore) {

    public static final String DEFAULT_COUNTRY = "in";

    public FilterConfig {
        smartrecruitersCountry = smartrecruitersCountry == null ? DEFAULT_COUNTRY : smartrecruitersCountry;
        filters = filters == null ? new Filters(null, null, null, null, null, 0) : filters;
        prescore = prescore == null ? Prescore.empty() : prescore;
    }

    /** Amazon search settings: one query per entry, paged by {@code resultLimit} up to {@code maxResults}. */
    public record AmazonConfig(String country, List<String> queries, int resultLimit, int maxResults) {
        public static final int DEFAULT_MAX_RESULTS = 150;

        public AmazonConfig {
            queries = queries == null ? List.of() : List.copyOf(queries);
            maxResults = maxResults <= 0 ? DEFAULT_MAX_RESULTS : maxResults;
        }
    }

    /** Keyword filters. An empty include list means "no restriction"; {@code maxMinYears} caps the experience asked. */
    public record Filters(List<String> titleInclude, List<String> titleExclude, List<String> locations,
                          List<String> jdInclude, List<String> jdExclude, int maxMinYears) {
        public Filters {
            titleInclude = titleInclude == null ? List.of() : List.copyOf(titleInclude);
            titleExclude = titleExclude == null ? List.of() : List.copyOf(titleExclude);
            locations = locations == null ? List.of() : List.copyOf(locations);
            jdInclude = jdInclude == null ? List.of() : List.copyOf(jdInclude);
            jdExclude = jdExclude == null ? List.of() : List.copyOf(jdExclude);
        }
    }

    /** The 0-100 keyword pre-score. Map order matters for {@code levelTitles} and {@code locationBonus} (first hit wins). */
    public record Prescore(Map<String, Integer> stack, Map<String, Integer> domain, Map<String, Integer> levelTitles,
                           Map<String, Integer> titlePenalty, Map<String, Integer> locationBonus,
                           List<String> tier1, List<String> tier2, int tier1Bonus, int tier2Bonus,
                           List<Integer> yearsIdeal, int yearsBonus, int yearsPenaltyPerYearOver) {
        public Prescore {
            stack = ordered(stack);
            domain = ordered(domain);
            levelTitles = ordered(levelTitles);
            titlePenalty = ordered(titlePenalty);
            locationBonus = ordered(locationBonus);
            tier1 = tier1 == null ? List.of() : List.copyOf(tier1);
            tier2 = tier2 == null ? List.of() : List.copyOf(tier2);
            yearsIdeal = yearsIdeal == null || yearsIdeal.size() != 2 ? List.of(0, 0) : List.copyOf(yearsIdeal);
        }

        static Prescore empty() {
            return new Prescore(null, null, null, null, null, null, null, 0, 0, null, 0, 0);
        }

        private static Map<String, Integer> ordered(Map<String, Integer> map) {
            return map == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(map));
        }
    }
}
