package dev.phoenix.legacy;

/** A legacy row the importer could not use, reported with its line so the user can fix or add it by hand. */
public record RowIssue(String file, int line, String reason) {

    @Override
    public String toString() {
        return file + ":" + line + ": " + reason;
    }
}
