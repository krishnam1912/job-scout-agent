package dev.phoenix.legacy;

import java.util.ArrayList;
import java.util.List;

/**
 * The outcome of reading one legacy file. Every data row is either in {@code items} or in {@code issues}, so
 * {@code rowsRead == items.size() + issues.size()} always holds and nothing can be dropped silently.
 */
final class ParsedFile<T> {

    private final String name;
    private final List<T> items = new ArrayList<>();
    private final List<RowIssue> issues = new ArrayList<>();

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
        return items.size() + issues.size();
    }
}
