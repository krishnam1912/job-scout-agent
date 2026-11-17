package dev.phoenix.legacy;

import dev.phoenix.dedupe.ExactKey;
import dev.phoenix.domain.Application;
import dev.phoenix.domain.Job;
import dev.phoenix.domain.JobState;
import dev.phoenix.domain.Score;
import dev.phoenix.lifecycle.NextActionPolicy;
import dev.phoenix.store.ApplicationRepository;
import dev.phoenix.store.EventRepository;
import dev.phoenix.store.JobRepository;
import dev.phoenix.store.ScoreRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Creates one job from a merged legacy record, directly in the state the old files report (the one place a job may
 * enter the lifecycle mid-way), with its first event, score and application. Call it inside the import's transaction.
 */
public class LegacyJobWriter {

    static final String SOURCE = "legacy";
    static final String ACTOR = "legacy-import";
    private static final String UNKNOWN_TITLE = "(title unknown, from the legacy seen list)";

    private final JobRepository jobs;
    private final ScoreRepository scores;
    private final ApplicationRepository applications;
    private final EventRepository events;
    private final NextActionPolicy policy;
    private final Clock clock;

    public LegacyJobWriter(JobRepository jobs, ScoreRepository scores, ApplicationRepository applications,
                    EventRepository events, NextActionPolicy policy, Clock clock) {
        this.jobs = jobs;
        this.scores = scores;
        this.applications = applications;
        this.events = events;
        this.policy = policy;
        this.clock = clock;
    }

    /** @return the state the job was created in, or null when the posting is already known and nothing was written */
    JobState writeIfNew(LegacyJob legacy) {
        String company = ExactKey.normalizeCompany(legacy.company());
        if (!jobs.findByCompanyAndExtId(company, legacy.extId()).isEmpty()) {
            return null;
        }
        Instant now = clock.instant();
        Instant seen = legacy.date() == null ? now : legacy.date().atStartOfDay(ZoneOffset.UTC).toInstant();
        JobState state = LegacyMerger.stateOf(legacy);
        Instant closedAt = state == JobState.CLOSED ? seen : null;
        long id = jobs.insert(new Job(0, SOURCE, company, legacy.extId(), blankToNull(legacy.url()),
                legacy.title() == null || legacy.title().isBlank() ? UNKNOWN_TITLE : legacy.title(),
                blankToNull(legacy.location()), null, state, null, null, null, seen, seen, closedAt, null,
                policy.nextActionAt(state, now, null)));
        events.append(id, null, state, ACTOR, "imported from the legacy files; " + legacy.note(), now);
        if (legacy.score() != null) {
            scores.insert(new Score(0, id, ACTOR, legacy.score(), Score.fitPct(legacy.score()), legacy.rationale(),
                    "legacy", seen));
        }
        if (hasApplication(state)) {
            applications.save(new Application(0, id, state, 1, state == JobState.BLOCKED ? "legacy" : null, null, null,
                    state.isApplied() ? seen : null, null, legacy.note()));
        }
        return state;
    }

    private static boolean hasApplication(JobState state) {
        return state.isEngaged();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
