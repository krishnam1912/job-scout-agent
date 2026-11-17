package dev.phoenix.discovery;

import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Whole-word, case-insensitive keyword matching that also accepts a plural "s" or "es" suffix. An empty keyword
 * list matches nothing; callers decide what an empty list means for them.
 */
public final class KeywordMatcher {

    private final Pattern pattern;

    private KeywordMatcher(Pattern pattern) {
        this.pattern = pattern;
    }

    public static KeywordMatcher of(Collection<String> words) {
        if (words == null || words.isEmpty()) {
            return new KeywordMatcher(null);
        }
        String alternatives = words.stream()
                .map(w -> w.toLowerCase(Locale.ROOT))
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote)
                .reduce((a, b) -> a + "|" + b)
                .orElseThrow();
        return new KeywordMatcher(Pattern.compile(
                "(?<![a-z0-9])(?:" + alternatives + ")(?:s|es)?(?![a-z0-9])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
    }

    public boolean isEmpty() {
        return pattern == null;
    }

    public boolean matches(String text) {
        return pattern != null && text != null && pattern.matcher(text).find();
    }
}
