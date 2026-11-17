package dev.phoenix.legacy;

import dev.phoenix.domain.JobState;
import dev.phoenix.error.UserErrorException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the applications log, a Markdown file with several pipe tables of different shapes. Only rows that carry a
 * date and a company are jobs; job ids are free text, so the first id-looking token is used and the whole row is
 * kept in the note.
 */
final class ApplicationsMarkdownParser {

    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private final CompanyNames companies;

    ApplicationsMarkdownParser(CompanyNames companies) {
        this.companies = companies;
    }

    ParsedFile<LegacyJob> parse(Path file) {
        ParsedFile<LegacyJob> out = new ParsedFile<>(file.getFileName().toString());
        try {
            for (MarkdownTables.Row row : MarkdownTables.rows(Files.readAllLines(file))) {
                parseRow(row, out);
            }
        } catch (IOException e) {
            throw new UserErrorException("Cannot read " + file.getFileName() + ": " + e.getMessage(), e);
        }
        return out;
    }

    private void parseRow(MarkdownTables.Row row, ParsedFile<LegacyJob> out) {
        if (row.headers().isEmpty()) {
            out.reject(row.line(), "table row without a header");
            return;
        }
        if (!row.hasColumn("date")) {
            out.skip(row.line(), "table has no date column");
            return;
        }
        Matcher date = DATE.matcher(row.cell("date"));
        if (!date.find()) {
            out.reject(row.line(), "row has no date");
            return;
        }
        LocalDate day;
        try {
            day = LocalDate.parse(date.group());
        } catch (DateTimeParseException e) {
            out.reject(row.line(), "unreadable date '" + date.group() + "'");
            return;
        }
        String[] named = companyAndTitle(row);
        String company = named[0];
        String title = named[1];
        if (company.isEmpty()) {
            out.reject(row.line(), "row has no company");
            return;
        }
        Double score = Scores.fromPercent(row.cell("match", "fit")).orElse(null);
        Optional<JobState> state = LegacyStatus.map(row.cell("status"), score);
        if (state.isEmpty()) {
            out.reject(row.line(), "unrecognised status '" + row.cell("status") + "'");
            return;
        }
        Optional<String> id = JobIds.fromText(row.cell("job id", "req id", "id"));
        String extId = id.orElseGet(() -> JobIds.synthetic(company, row.raw()));
        out.accept(new LegacyJob(LegacyJob.Origin.APPLICATIONS, company, extId, id.isEmpty(), title, "", "", score,
                null, day, state.get(), "applications row: " + row.raw()));
    }

    /** Tables without a company column put the company first in the role cell. */
    private String[] companyAndTitle(MarkdownTables.Row row) {
        String company = row.cell("company");
        String title = row.cell("role", "title");
        if (company.isEmpty() && !title.isEmpty()) {
            return companies.split(title);
        }
        return new String[] {company, title};
    }
}
