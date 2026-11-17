package dev.phoenix.export;

import dev.phoenix.domain.Application;
import dev.phoenix.domain.Job;
import dev.phoenix.domain.Score;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.StringWriter;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Locale;

/**
 * Every scored job as a row of the legacy leads CSV (same header). Columns the engine does not keep (work mode,
 * gaps, referral angle) are left empty; the rationale and the application note fill "Why it fits" and "Notes".
 */
public final class LeadsCsvExporter {

    static final String[] HEADER = {"Date found", "Company", "Role", "Job ID", "Location", "Work mode", "Score",
            "Why it fits", "Gaps", "URL", "Referral angle", "Status", "Notes"};

    public String render(ExportSnapshot snapshot) {
        StringWriter out = new StringWriter();
        try (CSVPrinter csv = new CSVPrinter(out, CSVFormat.DEFAULT.builder().setHeader(HEADER).setRecordSeparator("\n").get())) {
            for (Job job : snapshot.jobs().stream().sorted(Comparator.comparing(Job::firstSeen).thenComparingLong(Job::id)).toList()) {
                Score score = snapshot.scores().get(job.id());
                if (score == null) {
                    continue;
                }
                Application application = snapshot.applications().get(job.id());
                csv.printRecord(job.firstSeen().atZone(ZoneOffset.UTC).toLocalDate(), job.company(), job.title(),
                        job.extId(), orEmpty(job.location()), "", String.format(Locale.ROOT, "%.1f", score.score()),
                        orEmpty(score.rationale()), "", orEmpty(job.url()), "", LegacyStatusLabels.of(job.state()),
                        application == null ? "" : orEmpty(application.note()));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Writing CSV to memory cannot fail", e);
        }
        return out.toString();
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
