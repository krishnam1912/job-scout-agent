package dev.phoenix.discovery;

import dev.phoenix.domain.Posting;

import java.util.OptionalInt;

/** The keyword filters of scan.py: title, location, description and the experience cap, checked in that order. */
public final class JobFilter {

    private final KeywordMatcher titleInclude;
    private final KeywordMatcher titleExclude;
    private final KeywordMatcher locations;
    private final KeywordMatcher jdInclude;
    private final KeywordMatcher jdExclude;
    private final int maxMinYears;

    public JobFilter(FilterConfig.Filters filters) {
        this.titleInclude = KeywordMatcher.of(filters.titleInclude());
        this.titleExclude = KeywordMatcher.of(filters.titleExclude());
        this.locations = KeywordMatcher.of(filters.locations());
        this.jdInclude = KeywordMatcher.of(filters.jdInclude());
        this.jdExclude = KeywordMatcher.of(filters.jdExclude());
        this.maxMinYears = filters.maxMinYears();
    }

    /** Title test alone; SmartRecruiters uses it to decide which descriptions are worth fetching. */
    public boolean titleOk(String title) {
        return (titleInclude.isEmpty() || titleInclude.matches(title)) && !titleExclude.matches(title);
    }

    public FilterResult evaluate(Posting posting) {
        if (!titleOk(posting.title())) {
            return FilterResult.dropped("title");
        }
        if (!posting.locationVerified() && !locations.isEmpty() && !locations.matches(posting.location())) {
            return FilterResult.dropped("location");
        }
        String jd = posting.jd();
        if (!jd.isEmpty() && ((!jdInclude.isEmpty() && !jdInclude.matches(jd)) || jdExclude.matches(jd))) {
            return FilterResult.dropped("jd");
        }
        OptionalInt years = ExperienceYears.required(jd);
        if (years.isPresent() && years.getAsInt() > maxMinYears) {
            return FilterResult.dropped("years");
        }
        return FilterResult.kept(years);
    }
}
