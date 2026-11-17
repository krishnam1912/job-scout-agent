package dev.phoenix.export;

/** Keeps free text from breaking a Markdown table row. */
final class MarkdownCells {

    private MarkdownCells() {
    }

    static String of(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("|", "\\|").replaceAll("\\s*[\\r\\n]+\\s*", " ").strip();
    }
}
