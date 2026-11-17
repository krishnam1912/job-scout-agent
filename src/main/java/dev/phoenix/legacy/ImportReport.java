package dev.phoenix.legacy;

import dev.phoenix.domain.JobState;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * What an import did, per file and in total. {@code unparsed} lists every row that could not be used, so a
 * non-empty list is the signal that something needs the user's attention.
 */
public record ImportReport(List<FileCount> files, int jobsCreated, int jobsUnchanged, Map<JobState, Integer> createdByState,
                           int syntheticIds, int referralsCreated, int referralsUnchanged, List<RowIssue> unparsed) {

    /** Rows of one file: how many were read and how many became jobs (or referrals). */
    public record FileCount(String file, int rowsRead, int rowsUsed) {
    }

    public ImportReport {
        files = List.copyOf(files);
        createdByState = Map.copyOf(createdByState);
        unparsed = List.copyOf(unparsed);
    }

    public boolean clean() {
        return unparsed.isEmpty();
    }

    public String render() {
        StringBuilder out = new StringBuilder("Legacy import\n");
        files.forEach(f -> out.append(String.format("  %-28s rows read %4d, used %4d%n", f.file(), f.rowsRead(), f.rowsUsed())));
        out.append(String.format("Jobs: %d created, %d already imported%n", jobsCreated, jobsUnchanged));
        if (!createdByState.isEmpty()) {
            out.append("Created by state: ").append(createdByState.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining(", "))).append('\n');
        }
        out.append(String.format("Rows with a made-up id (none found in the row): %d%n", syntheticIds));
        out.append(String.format("Referrals: %d created, %d already imported%n", referralsCreated, referralsUnchanged));
        if (unparsed.isEmpty()) {
            out.append("Unparsed rows: none\n");
        } else {
            out.append("Unparsed rows (not imported, fix or add by hand):\n");
            unparsed.forEach(i -> out.append("  ").append(i).append('\n'));
        }
        return out.toString();
    }
}
