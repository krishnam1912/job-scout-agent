package dev.phoenix.legacy;

import java.util.ArrayList;
import java.util.List;

/**
 * The outcome of reading one legacy file. Every data row is in exactly one of {@code items}, {@code issues}
 * (could not be used, an error) or {@code skipped} (outside the import by rule), so nothing is dropped silently.
 */
final class ParsedFile<T> {

    private final String name;
    private final List<T> items = new ArrayList<>();
    private final List<RowIssue> issues = new ArrayList<>();
    private final List<RowIssue> skipped = new ArrayList<>();

    ParsedFile(String name) {
        this.name = name;
    }

    String name() {
        return name;
    }

    List<T> items() {
        return items;
    }

    List<RowIssue> issues() {
        return issues;
    }

    /** Rows the file's own format puts outside what is imported (for example tables with no date column). */
    List<RowIssue> skipped() {
        return skipped;
    }

    void skip(int line, String reason) {
        skipped.add(new RowIssue(name, line, reason));
    }

    void accept(T item) {
        items.add(item);
    }

    void reject(int line, String reason) {
        reject(name, line, reason);
    }

    /** Rejects a row of a file inside a directory read as one unit, naming the exact file. */
    void reject(String file, int line, String reason) {
        issues.add(new RowIssue(file, line, reason));
    }

    int rowsRead() {
        return items.size() + issues.size() + skipped.size();
    }
}
