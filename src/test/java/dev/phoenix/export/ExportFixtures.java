package dev.phoenix.export;

import dev.phoenix.domain.Application;
import dev.phoenix.domain.Ask;
import dev.phoenix.domain.AskStatus;
import dev.phoenix.domain.Job;
import dev.phoenix.domain.JobState;
import dev.phoenix.domain.Score;
import dev.phoenix.service.QueueEntry;
import dev.phoenix.service.StatusReport;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** A small hand-written pipeline (synthetic companies) shared by the exporter tests. */
final class ExportFixtures {

    static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

    private ExportFixtures() {
    }

    static Job job(long id, String company, String extId, String title, String location, JobState state, String firstSeen) {
        Instant seen = Instant.parse(firstSeen);
        return new Job(id, "greenhouse", company, extId, "https://jobs.example.com/" + company + "/" + extId, title,
                location, null, state, null, null, 50, seen, seen, null, null, null);
    }

    static Score score(long jobId, double score, String rationale) {
        return new Score(jobId, jobId, "claude-code", score, Score.fitPct(score), rationale, "v1", NOW);
    }

    static ExportSnapshot snapshot() {
        Job submitted = job(1, "acme", "1001", "Backend Engineer II", "Bengaluru", JobState.SUBMITTED, "2026-01-02T08:00:00Z");
        Job queued = job(2, "acme", "1002", "Platform | Engineer", "Pune", JobState.QUEUED, "2026-01-03T08:00:00Z");
        Job below = job(3, "globex", "2001", "Data Engineer", "Pune", JobState.BELOW, "2026-01-03T09:00:00Z");
        Job blocked = job(4, "initech", "3001", "Backend", "Remote", JobState.BLOCKED, "2026-01-04T09:00:00Z");
        Job placeholder = job(5, "hooli", "9999", "(title unknown)", null, JobState.BELOW, "2026-01-04T10:00:00Z");
        Application filed = new Application(1, 1, JobState.SUBMITTED, 1, null, null, null,
                Instant.parse("2026-01-05T00:00:00Z"), "CONF-1", "in office, \"hybrid\"");
        Application stuck = new Application(2, 4, JobState.BLOCKED, 1, "login", "needs a human", null, null, null, "login wall");
        Map<Long, Score> scores = Map.of(1L, score(1, 4.4, "Java, Kafka match"), 2L, score(2, 3.8, "Good fit"),
                3L, score(3, 3.2, "Meh"), 4L, score(4, 4.1, "ok"));
        Ask ask = new Ask(7, 4L, "blocker", "Blocked applying to \"Backend\" at initech: login", null, 5,
                AskStatus.OFFERED, null, "claude-session", NOW, null, null);
        StatusReport status = new StatusReport(NOW,
                Map.of(JobState.SUBMITTED, 1, JobState.QUEUED, 1, JobState.BELOW, 2, JobState.BLOCKED, 1),
                List.of(ask), List.of(queued, blocked), Instant.parse("2026-11-18T09:00:00Z"), "OK");
        return new ExportSnapshot(List.of(submitted, queued, below, blocked, placeholder), scores,
                Map.of(1L, filed, 4L, stuck), List.of(new QueueEntry(queued, scores.get(2L))), status);
    }
}
