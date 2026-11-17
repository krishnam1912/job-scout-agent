package dev.phoenix.legacy;

import dev.phoenix.domain.JobState;
import dev.phoenix.domain.ScoreBand;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Maps the free-text status of the old files onto lifecycle states. The first matching rule wins. */
final class LegacyStatus {

    private static final Pattern SKIPPED = Pattern.compile("\\bskip");
    private static final Pattern CLOSED = Pattern.compile("\\b(closed|expired|removed)\\b");
    private static final Pattern INTERVIEW = Pattern.compile("\\binterview");
    private static final Pattern REJECTED = Pattern.compile("\\brejected\\b");
    private static final Pattern OFFER = Pattern.compile("\\boffer\\b");
    private static final Pattern GHOSTED = Pattern.compile("\\bghosted\\b");
    private static final Pattern ASSESSMENT = Pattern.compile("hackerrank|assessment|coding test|\\boa\\b|test done");
    private static final Pattern SUBMITTED = Pattern.compile("\\b(applied|submitted)\\b");
    private static final Pattern BLOCKED = Pattern.compile("\\b(blocked|needs|sign-in|retry)\\b");
    private static final Pattern IN_PROGRESS = Pattern.compile("in progress");
    private static final Pattern NOT_APPLIED_YET = Pattern.compile("\\b(new|not started|for referral|queued|pending|to apply)\\b");

    private LegacyStatus() {
    }

    /**
     * @param score the 1-5 score, needed only to place a not-yet-applied job in the queue or below it
     * @return empty when the text is not understood, or when a score was needed and is missing
     */
    static Optional<JobState> map(String raw, Double score) {
        String text = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        if (SKIPPED.matcher(text).find()) {
            return Optional.of(JobState.SKIPPED);
        }
        if (CLOSED.matcher(text).find()) {
            return Optional.of(JobState.CLOSED);
        }
        if (INTERVIEW.matcher(text).find()) {
            return Optional.of(JobState.INTERVIEW);
        }
        if (REJECTED.matcher(text).find()) {
            return Optional.of(JobState.REJECTED);
        }
        if (OFFER.matcher(text).find()) {
            return Optional.of(JobState.OFFER);
        }
        if (GHOSTED.matcher(text).find()) {
            return Optional.of(JobState.GHOSTED);
        }
        if (ASSESSMENT.matcher(text).find()) {
            return Optional.of(JobState.OA);
        }
        if (SUBMITTED.matcher(text).find()) {
            return Optional.of(JobState.SUBMITTED);
        }
        if (BLOCKED.matcher(text).find()) {
            return Optional.of(JobState.BLOCKED);
        }
        if (IN_PROGRESS.matcher(text).find()) {
            return Optional.of(JobState.IN_PROGRESS);
        }
        if (NOT_APPLIED_YET.matcher(text).find()) {
            return Optional.ofNullable(score).map(s -> ScoreBand.of(s) == ScoreBand.QUEUE ? JobState.QUEUED : JobState.BELOW);
        }
        return Optional.empty();
    }
}
