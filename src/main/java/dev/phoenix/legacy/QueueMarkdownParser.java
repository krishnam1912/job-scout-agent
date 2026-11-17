package dev.phoenix.legacy;

import dev.phoenix.domain.JobState;
import dev.phoenix.domain.ScoreBand;
import dev.phoenix.error.UserErrorException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Reads the daily queue files ({@code YYYY-MM-DD.md}, tables of # / Score / Company / Role / Location / Fit % / URL). */
final class QueueMarkdownParser {

    private static final Pattern DATE_IN_NAME = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");

    ParsedFile<LegacyJob> parse(Path dir) {
        ParsedFile<LegacyJob> out = new ParsedFile<>("queue/");
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".md")).sorted().toList()) {
                parseFile(file, out);
            }
        } catch (IOException e) {
            throw new UserErrorException("Cannot read the queue directory " + dir.getFileName() + ": " + e.getMessage(), e);
        }
        return out;
    }

    private void parseFile(Path file, ParsedFile<LegacyJob> out) throws IOException {
        String name = file.getFileName().toString();
        LocalDate date = dateOf(name);
        for (MarkdownTables.Row row : MarkdownTables.rows(Files.readAllLines(file))) {
            String company = row.cell("company");
            String title = row.cell("role", "title");
            if (company.isEmpty() || title.isEmpty()) {
                out.reject("queue/" + name, row.line(), "row has no company or role");
                continue;
            }
            Double score = Scores.rubric(row.cell("score")).orElse(null);
            if (score == null) {
                out.reject("queue/" + name, row.line(), "row has no usable score");
                continue;
            }
            String url = row.cell("url");
            Optional<String> id = JobIds.fromUrl(url);
            String extId = id.orElseGet(() -> JobIds.synthetic(company, row.raw()));
            JobState state = ScoreBand.of(score) == ScoreBand.QUEUE ? JobState.QUEUED : JobState.BELOW;
            out.accept(new LegacyJob(LegacyJob.Origin.QUEUE, company, extId, id.isEmpty(), title, row.cell("location"),
                    url, score, null, date, state, "queued in " + name));
        }
    }

    private static LocalDate dateOf(String fileName) {
        Matcher m = DATE_IN_NAME.matcher(fileName);
        if (!m.find()) {
            return null;
        }
        try {
            return LocalDate.parse(m.group(1));
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
