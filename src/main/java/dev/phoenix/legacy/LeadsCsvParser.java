package dev.phoenix.legacy;

import dev.phoenix.domain.JobState;
import dev.phoenix.error.UserErrorException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Reads the legacy leads CSV (header: Date found, Company, Role, Job ID, Location, ..., Status, Notes). */
final class LeadsCsvParser {

    private static final List<String> REQUIRED = List.of("Company", "Role", "Job ID", "Status");
    private static final List<String> EXTRA_NOTES = List.of("Work mode", "Gaps", "Referral angle", "Notes");

    ParsedFile<LegacyJob> parse(Path file) {
        ParsedFile<LegacyJob> out = new ParsedFile<>(file.getFileName().toString());
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
             CSVParser csv = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                     .setIgnoreSurroundingSpaces(true).setAllowMissingColumnNames(true).get().parse(reader)) {
            requireColumns(csv.getHeaderMap().keySet().stream().toList(), file);
            Iterator<CSVRecord> rows = csv.iterator();
            while (rows.hasNext()) {
                CSVRecord row = rows.next();
                int line = (int) csv.getCurrentLineNumber();
                if (row.size() != csv.getHeaderMap().size()) {
                    out.reject(line, "expected " + csv.getHeaderMap().size() + " columns, found " + row.size());
                    continue;
                }
                parseRow(row, line, out);
            }
        } catch (IOException | IllegalStateException e) {
            throw new UserErrorException("Cannot read " + file.getFileName() + ": " + e.getMessage(), e);
        }
        return out;
    }

    private void requireColumns(List<String> header, Path file) {
        List<String> missing = REQUIRED.stream().filter(c -> !header.contains(c)).toList();
        if (!missing.isEmpty()) {
            throw new UserErrorException(file.getFileName() + " is missing the column(s) " + missing);
        }
    }

    private void parseRow(CSVRecord row, int line, ParsedFile<LegacyJob> out) {
        String company = row.get("Company").strip();
        String extId = row.get("Job ID").strip();
        if (company.isEmpty() || extId.isEmpty()) {
            out.reject(line, "company or job id is empty");
            return;
        }
        Double score = Scores.rubric(cell(row, "Score")).orElse(null);
        Optional<JobState> state = LegacyStatus.map(row.get("Status"), score);
        if (state.isEmpty()) {
            out.reject(line, score == null && !row.get("Status").isBlank()
                    ? "status '" + row.get("Status") + "' needs a numeric score to place the job"
                    : "unrecognised status '" + row.get("Status") + "'");
            return;
        }
        LocalDate date = null;
        String dateText = cell(row, "Date found");
        if (!dateText.isEmpty()) {
            try {
                date = LocalDate.parse(dateText);
            } catch (DateTimeParseException e) {
                out.reject(line, "unreadable date '" + dateText + "'");
                return;
            }
        }
        out.accept(new LegacyJob(LegacyJob.Origin.LEADS_CSV, company, extId, false, row.get("Role").strip(),
                cell(row, "Location"), cell(row, "URL"), score, cell(row, "Why it fits"), date, state.get(), note(row)));
    }

    private static String note(CSVRecord row) {
        List<String> parts = new ArrayList<>();
        parts.add("legacy status: " + row.get("Status").strip());
        for (String column : EXTRA_NOTES) {
            String value = cell(row, column);
            if (!value.isEmpty()) {
                parts.add(column.toLowerCase(Locale.ROOT) + ": " + value);
            }
        }
        return String.join("; ", parts);
    }

    private static String cell(CSVRecord row, String column) {
        Map<String, Integer> header = row.getParser().getHeaderMap();
        return header.containsKey(column) ? row.get(column).strip() : "";
    }
}
