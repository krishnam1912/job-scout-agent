package dev.phoenix.legacy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Reads pipe tables out of Markdown. A table's header applies to its rows and, like the old files assumed, to
 * later header-less rows that continue the table after a text line. Everything that is not a table row is ignored.
 */
final class MarkdownTables {

    /** One data row: its 1-based line, the lower-cased headers in force and the trimmed cells. */
    record Row(int line, List<String> headers, List<String> cells, String raw) {

        /** The cell under the first header that contains one of the names; empty when none. */
        String cell(String... names) {
            int index = indexOf(names);
            return index < 0 || index >= cells.size() ? "" : cells.get(index);
        }

        boolean hasColumn(String... names) {
            return indexOf(names) >= 0;
        }

        private int indexOf(String... names) {
            for (String name : names) {
                for (int i = 0; i < headers.size(); i++) {
                    if (headers.get(i).contains(name)) {
                        return i;
                    }
                }
            }
            return -1;
        }
    }

    private static final Pattern SEPARATOR = Pattern.compile("^\\|?[\\s:|-]+\\|?$");

    private MarkdownTables() {
    }

    static List<Row> rows(List<String> lines) {
        List<Row> rows = new ArrayList<>();
        List<String> headers = List.of();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (!line.startsWith("|")) {
                continue;
            }
            if (SEPARATOR.matcher(line).matches()) {
                continue;
            }
            boolean startsTable = i + 1 < lines.size() && SEPARATOR.matcher(lines.get(i + 1).strip()).matches()
                    && lines.get(i + 1).contains("-");
            if (startsTable) {
                headers = split(line).stream().map(h -> h.toLowerCase(Locale.ROOT)).toList();
                continue;
            }
            rows.add(new Row(i + 1, headers, split(line), line));
        }
        return rows;
    }

    private static List<String> split(String line) {
        String inner = line.strip();
        if (inner.startsWith("|")) {
            inner = inner.substring(1);
        }
        if (inner.endsWith("|")) {
            inner = inner.substring(0, inner.length() - 1);
        }
        List<String> cells = new ArrayList<>();
        for (String cell : inner.split("\\|", -1)) {
            cells.add(cell.strip());
        }
        return cells;
    }
}
