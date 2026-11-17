package dev.phoenix.export;

import dev.phoenix.domain.Ask;
import dev.phoenix.domain.AskStatus;
import dev.phoenix.domain.Job;
import dev.phoenix.domain.JobState;
import dev.phoenix.service.StatusReport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExportersTest {

    private static String golden(String name) throws IOException {
        return Files.readString(Path.of("src/test/resources/golden/" + name));
    }

    @Test
    void leadsCsvMatchesTheGoldenFile() throws IOException {
        assertThat(new LeadsCsvExporter().render(ExportFixtures.snapshot())).isEqualTo(golden("leads.csv"));
    }

    @Test
    void leadsCsvKeepsTheLegacyHeader() {
        String header = new LeadsCsvExporter().render(ExportFixtures.snapshot()).lines().findFirst().orElseThrow();

        assertThat(header).isEqualTo("Date found,Company,Role,Job ID,Location,Work mode,Score,Why it fits,Gaps,URL,Referral angle,Status,Notes");
    }

    @Test
    void applicationsMarkdownMatchesTheGoldenFile() throws IOException {
        assertThat(new ApplicationsMdExporter().render(ExportFixtures.snapshot())).isEqualTo(golden("applications.md"));
    }

    @Test
    void queueMarkdownMatchesTheGoldenFile() throws IOException {
        assertThat(new QueueMdExporter().render(ExportFixtures.snapshot())).isEqualTo(golden("queue.md"));
    }

    @Test
    void statusMarkdownMatchesTheGoldenFile() throws IOException {
        assertThat(new StatusMdExporter().render(ExportFixtures.snapshot().status())).isEqualTo(golden("STATUS.md"));
    }

    @Test
    void anEmptyQueueSaysSo() {
        ExportSnapshot empty = new ExportSnapshot(List.of(), Map.of(), Map.of(), List.of(),
                new StatusReport(ExportFixtures.NOW, Map.of(), List.of(), List.of(), null, null));

        assertThat(new QueueMdExporter().render(empty)).contains("Nothing is queued.");
        String status = new StatusMdExporter().render(empty.status());
        assertThat(status).contains("Last scan: never").contains("No jobs yet.");
    }

    @Test
    void statusStaysWithinTwentyFiveLinesHoweverMuchThereIs() {
        List<Ask> asks = new ArrayList<>();
        List<Job> atRisk = new ArrayList<>();
        for (int i = 1; i <= 40; i++) {
            asks.add(new Ask(i, null, "question", "Question " + i, null, 0, AskStatus.OPEN, null, null, ExportFixtures.NOW, null, null));
            atRisk.add(ExportFixtures.job(i, "acme", "id" + i, "Role " + i, "Pune", JobState.QUEUED, "2026-01-01T00:00:00Z"));
        }
        StatusReport report = new StatusReport(ExportFixtures.NOW, Map.of(JobState.QUEUED, 40), asks, atRisk, null, null);

        String status = new StatusMdExporter().render(report);

        assertThat(status.lines().count()).isLessThanOrEqualTo(StatusMdExporter.MAX_LINES);
        assertThat(status).contains("and 35 more");
    }

    @Test
    void cellsCannotBreakATableRow() {
        assertThat(MarkdownCells.of("a | b\nc")).isEqualTo("a \\| b c");
        assertThat(MarkdownCells.of(null)).isEmpty();
    }
}
