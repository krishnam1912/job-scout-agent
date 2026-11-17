package dev.phoenix.discovery;

import dev.phoenix.domain.Posting;

/** Runs the filters and, for postings that pass, the pre-score. One instance per scan, built from that scan's filters. */
public final class PostingEvaluator {

    /** A posting with its verdict; {@code prescore} is null when the posting was filtered out. */
    public record Evaluated(Posting posting, FilterResult filter, Integer prescore) {
    }

    private final JobFilter filter;
    private final Prescorer prescorer;

    public PostingEvaluator(FilterConfig config) {
        this.filter = new JobFilter(config.filters());
        this.prescorer = new Prescorer(config.prescore());
    }

    public boolean titleOk(String title) {
        return filter.titleOk(title);
    }

    public Evaluated evaluate(Posting posting) {
        FilterResult result = filter.evaluate(posting);
        Integer prescore = result.passed() ? prescorer.score(posting, result.requiredYears()) : null;
        return new Evaluated(posting, result, prescore);
    }
}
