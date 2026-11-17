package dev.phoenix.legacy;

import dev.phoenix.domain.JobState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyParsingTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NONE", value = {
            "Applied 2026-11-17|NONE|SUBMITTED", "Applied ✅|NONE|SUBMITTED", "Submitted via Greenhouse embed|NONE|SUBMITTED",
            "Skipped (Amazon 10-app limit)|NONE|SKIPPED", "Skipped (posting closed)|NONE|SKIPPED",
            "Blocked: captcha|NONE|BLOCKED", "Needs sign-in|NONE|BLOCKED", "Needs retry (site down)|NONE|BLOCKED",
            "New|4.0|QUEUED", "New|3.4|BELOW", "For referral|3.9|QUEUED", "Not started|2.0|BELOW",
            "Hackerrank test done|NONE|OA", "Closed|NONE|CLOSED", "In progress|NONE|IN_PROGRESS",
            "Rejected by email|NONE|REJECTED", "Interview scheduled|NONE|INTERVIEW", "New|NONE|NONE", "Teleported|NONE|NONE", "|NONE|NONE"})
    void statusTextMapsToLifecycleStates(String text, Double score, String expected) {
        Optional<JobState> state = LegacyStatus.map(text, score);

        if (expected == null) {
            assertThat(state).isEmpty();
        } else {
            assertThat(state).contains(JobState.valueOf(expected));
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NONE", value = {
            "10594189|10594189", "R-289567|R-289567", "R0138416|R0138416", "JR347341|JR347341",
            "200678409-1052|200678409-1052", "2026-28864|2026-28864", "4004 / 4005|4004",
            "186b763a-2a61-4563-8813-ff6b40c9c8a1|186b763a-2a61-4563-8813-ff6b40c9c8a1",
            "HackerRank PayPal-SrJava-CL6|NONE", "see email|NONE", "|NONE"})
    void idsAreFoundInFreeText(String text, String expected) {
        assertThat(JobIds.fromText(text)).isEqualTo(Optional.ofNullable(expected));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NONE", value = {
            "https://www.example.com/jobs/10594189/software-engineer|10594189",
            "https://www.example.com/careers/opportunity/7765038?gh_jid=7765038|7765038",
            "https://careers.example.com/en/job/123456/title|123456",
            "https://jobs.example.com/acme/186b763a-2a61-4563-8813-ff6b40c9c8a1|186b763a-2a61-4563-8813-ff6b40c9c8a1",
            "https://www.example.com/careers/data-engineer|NONE"})
    void idsAreFoundInUrls(String url, String expected) {
        assertThat(JobIds.fromUrl(url)).isEqualTo(Optional.ofNullable(expected));
    }

    @Test
    void madeUpIdsAreStableAndDifferPerRow() {
        assertThat(JobIds.synthetic("acme", "row one")).isEqualTo(JobIds.synthetic("acme", "row one"))
                .isNotEqualTo(JobIds.synthetic("acme", "row two")).startsWith("legacy-");
    }

    @Test
    void scoresAreReadFromLooseNotation() {
        assertThat(Scores.rubric("⭐ 4.4")).contains(4.4);
        assertThat(Scores.rubric("4/5")).contains(4.0);
        assertThat(Scores.rubric("9.5")).isEmpty();
        assertThat(Scores.rubric("n/a")).isEmpty();
        assertThat(Scores.fromPercent("~84% (estimate)")).contains(4.2);
        assertThat(Scores.fromPercent("no number")).isEmpty();
    }

    @Test
    void aHeaderAppliesToRowsThatContinueTheTableAfterATextLine() {
        List<String> lines = List.of("| Date | Company |", "|---|---|", "| 2026-01-01 | Acme |", "", "Total: 1",
                "| 2026-01-02 | Globex |");

        List<MarkdownTables.Row> rows = MarkdownTables.rows(lines);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).cell("company")).isEqualTo("Globex");
        assertThat(rows.get(1).line()).isEqualTo(6);
    }

    @Test
    void companiesAreRecognisedFromNamesKnownElsewhere() {
        CompanyNames names = new CompanyNames();
        names.learn("Wayne Enterprises");

        assertThat(names.split("Wayne Enterprises Backend Dev (Pune)")).containsExactly("Wayne Enterprises", "Backend Dev (Pune)");
        assertThat(names.split("Hooli Senior SWE")).containsExactly("Hooli", "Senior SWE");
    }
}
