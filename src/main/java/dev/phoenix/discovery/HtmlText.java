package dev.phoenix.discovery;

import org.jsoup.parser.Parser;

import java.util.regex.Pattern;

/** HTML to plain text exactly as scan.py did it, so descriptions match word for word. */
public final class HtmlText {

    private static final Pattern BLOCK_END = Pattern.compile(
            "</(p|div|li|h\\d|br|ul|ol)>|<br\\s*/?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private HtmlText() {
    }

    /** Unescapes (Greenhouse sends escaped HTML), drops tags and collapses whitespace; null gives "". */
    public static String strip(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String text = Parser.unescapeEntities(raw, false);
        text = BLOCK_END.matcher(text).replaceAll(" ");
        text = TAG.matcher(text).replaceAll(" ");
        text = Parser.unescapeEntities(text, false);
        return collapse(text);
    }

    /** Collapses every whitespace run to one space and trims. */
    public static String collapse(String text) {
        return WHITESPACE.matcher(text).replaceAll(" ").strip();
    }
}
