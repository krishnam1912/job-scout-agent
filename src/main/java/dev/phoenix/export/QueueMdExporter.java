package dev.phoenix.export;

import dev.phoenix.service.QueueEntry;

import java.util.Locale;

/** The approval queue as a numbered table, the same columns the old daily queue files used. */
public final class QueueMdExporter {

    private static final double STAR_FROM = 4.0;

    public String render(ExportSnapshot snapshot) {
        StringBuilder md = new StringBuilder("# Queue\n\n");
        if (snapshot.queue().isEmpty()) {
            return md.append("Nothing is queued.\n").toString();
        }
        md.append("| # | Score | Company | Role | Location | Fit % | URL |\n")
                .append("|---|-------|---------|------|----------|-------|-----|\n");
        int n = 0;
        for (QueueEntry entry : snapshot.queue()) {
            double score = entry.score().score();
            md.append("| ").append(++n)
                    .append(" | ").append(score >= STAR_FROM ? "⭐ " : "").append(String.format(Locale.ROOT, "%.1f", score))
                    .append(" | ").append(MarkdownCells.of(entry.job().company()))
                    .append(" | ").append(MarkdownCells.of(entry.job().title()))
                    .append(" | ").append(MarkdownCells.of(entry.job().location()))
                    .append(" | ").append(entry.fitPct())
                    .append(" | ").append(MarkdownCells.of(entry.job().url()))
                    .append(" |\n");
        }
        return md.toString();
    }
}
