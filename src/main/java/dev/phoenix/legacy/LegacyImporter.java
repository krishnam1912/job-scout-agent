package dev.phoenix.legacy;

import dev.phoenix.config.PhoenixHome;
import dev.phoenix.config.PhoenixProperties;
import dev.phoenix.domain.JobState;
import dev.phoenix.domain.Referral;
import dev.phoenix.legacy.ImportReport.FileCount;
import dev.phoenix.store.ReferralRepository;
import dev.phoenix.store.TxRunner;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * One-off import of the old CSV and Markdown files into the engine. Read-only on the legacy files, all-or-nothing
 * in the database and idempotent: a second run finds every job and referral already there and adds nothing.
 */
public class LegacyImporter {

    private final PhoenixHome home;
    private final PhoenixProperties properties;
    private final LegacyJobWriter writer;
    private final ReferralRepository referrals;
    private final TxRunner tx;
    private final LegacyMerger merger = new LegacyMerger();

    public LegacyImporter(PhoenixHome home, PhoenixProperties properties, LegacyJobWriter writer,
                          ReferralRepository referrals, TxRunner tx) {
        this.home = home;
        this.properties = properties;
        this.writer = writer;
        this.referrals = referrals;
        this.tx = tx;
    }

    public ImportReport run() {
        List<FileCount> counts = new ArrayList<>();
        List<RowIssue> unparsed = new ArrayList<>();
        List<RowIssue> skipped = new ArrayList<>();
        List<LegacyJob> rows = new ArrayList<>();
        CompanyNames companies = new CompanyNames();

        Collector collector = new Collector(counts, unparsed, skipped, rows, companies);
        collector.collect(path(properties.seenFile()), new SeenListParser()::parse);
        PhoenixProperties.Legacy legacy = properties.legacy();
        collector.collect(path(legacy.queueDir()), new QueueMarkdownParser()::parse);
        collector.collect(path(legacy.leadsCsv()), new LeadsCsvParser()::parse);
        collector.collect(path(legacy.applicationsMd()), new ApplicationsMarkdownParser(companies)::parse);

        ParsedFile<Referral> referralRows = null;
        Path referralsPath = path(legacy.referralsCsv());
        if (referralsPath != null) {
            referralRows = new ReferralsCsvParser().parse(referralsPath);
            counts.add(new FileCount(referralRows.name(), referralRows.rowsRead(), referralRows.items().size(), 0));
            unparsed.addAll(referralRows.issues());
        }
        List<Referral> referralList = referralRows == null ? List.of() : referralRows.items();
        List<LegacyJob> merged = merger.merge(rows);
        return tx.inTx(() -> store(merged, referralList, counts, unparsed, skipped));
    }

    private ImportReport store(List<LegacyJob> merged, List<Referral> referralList, List<FileCount> counts,
                               List<RowIssue> unparsed, List<RowIssue> skipped) {
        Map<JobState, Integer> byState = new EnumMap<>(JobState.class);
        int created = 0;
        int unchanged = 0;
        int synthetic = 0;
        for (LegacyJob job : merged) {
            JobState state = writer.writeIfNew(job);
            if (state == null) {
                unchanged++;
            } else {
                created++;
                byState.merge(state, 1, Integer::sum);
            }
            if (job.syntheticId()) {
                synthetic++;
            }
        }
        if (created + unchanged != merged.size()) {
            throw new IllegalStateException("Import reconciliation failed: " + merged.size() + " jobs merged but "
                    + (created + unchanged) + " accounted for");
        }
        int referralsCreated = 0;
        for (Referral referral : referralList) {
            if (!referrals.exists(referral)) {
                referrals.insert(referral);
                referralsCreated++;
            }
        }
        return new ImportReport(counts, created, unchanged, byState, synthetic, referralsCreated,
                referralList.size() - referralsCreated, unparsed, skipped);
    }

    /** Gathers what each parsed file contributes: its count line, its problems and its jobs. */
    private record Collector(List<FileCount> counts, List<RowIssue> unparsed, List<RowIssue> skipped,
                             List<LegacyJob> rows, CompanyNames companies) {

        void collect(Path file, Function<Path, ParsedFile<LegacyJob>> parser) {
            if (file == null) {
                return;
            }
            ParsedFile<LegacyJob> parsed = parser.apply(file);
            counts.add(new FileCount(parsed.name(), parsed.rowsRead(), parsed.items().size(), parsed.skipped().size()));
            unparsed.addAll(parsed.issues());
            skipped.addAll(parsed.skipped());
            parsed.items().forEach(j -> companies.learn(j.company()));
            rows.addAll(parsed.items());
        }
    }

    private Path path(String configured) {
        return configured == null || configured.isBlank() ? null : home.resolvePath(configured);
    }
}
