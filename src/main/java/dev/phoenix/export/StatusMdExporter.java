package dev.phoenix.export;

import dev.phoenix.domain.Ask;
import dev.phoenix.domain.Job;
import dev.phoenix.domain.JobState;
import dev.phoenix.service.StatusReport;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The one-screen STATUS.md: counts by state, open asks and jobs at risk, at most 25 lines. */
public final class StatusMdExporter {

    static final int MAX_LINES = 25;
    private static final int LISTED = 5;

    public String render(StatusReport report) {
        StringBuilder md = new StringBuilder("# Status\n\n");
        md.append("Generated ").append(report.generatedAt()).append(". Last scan: ")
                .append(report.lastScanAt() == null ? "never" : report.lastScanAt() + " (" + report.lastScanStatus() + ")")
                .append(".\n\n## Pipeline\n");
        md.append(report.counts().isEmpty() ? "No jobs yet." : counts(report.counts())).append("\n\n");
        md.append("## Open asks (").append(report.openAsks().size()).append(")\n");
        list(md, report.openAsks(), StatusMdExporter::describe);
        md.append("\n## At risk (").append(report.atRisk().size()).append(")\n");
        list(md, report.atRisk(), job -> describe(job, report.generatedAt()));
        return md.toString();
    }

    private static String counts(Map<JobState, Integer> counts) {
        return counts.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining(" · "));
    }

    private static <T> void list(StringBuilder md, List<T> items, Function<T, String> describe) {
        if (items.isEmpty()) {
            md.append("None.\n");
            return;
        }
        items.stream().limit(LISTED).forEach(i -> md.append("- ").append(describe.apply(i)).append('\n'));
        if (items.size() > LISTED) {
            md.append("- and ").append(items.size() - LISTED).append(" more\n");
        }
    }

    private static String describe(Ask ask) {
        return "#" + ask.id() + " [" + ask.kind() + "] " + MarkdownCells.of(ask.prompt())
                + (ask.jobId() == null ? "" : " (job " + ask.jobId() + ")");
    }

    private static String describe(Job job, Instant now) {
        String why = job.closingDate() != null && !job.closingDate().isAfter(now.plusSeconds(72 * 3600L))
                ? "closes " + job.closingDate()
                : job.state() == JobState.BLOCKED ? "blocked more than a day" : "waiting more than two days";
        return job.state() + " job " + job.id() + " " + MarkdownCells.of(job.company()) + ": "
                + MarkdownCells.of(job.title()) + " (" + why + ")";
    }
}
