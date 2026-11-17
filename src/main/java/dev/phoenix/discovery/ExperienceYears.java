package dev.phoenix.discovery;

import java.util.Locale;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds the experience requirement ("5+ years of experience") in a job description. */
public final class ExperienceYears {

    private static final Pattern YEARS = Pattern.compile(
            "(\\d{1,2})\\s*\\+?\\s*(?:(?:-|–|to)\\s*\\d{1,2}\\s*\\+?\\s*)?(?:years?|yrs?)", Pattern.CASE_INSENSITIVE);
    private static final int WINDOW = 70;
    private static final int MAX_PLAUSIBLE = 15;

    private ExperienceYears() {
    }

    /** The highest "N years" figure that sits next to the word "experience"; empty when there is none. */
    public static OptionalInt required(String text) {
        int highest = 0;
        Matcher m = YEARS.matcher(text);
        while (m.find()) {
            String window = text.substring(Math.max(0, m.start() - WINDOW), Math.min(text.length(), m.end() + WINDOW))
                    .toLowerCase(Locale.ROOT);
            if (window.contains("experience") || window.contains("exp.")) {
                int n = Integer.parseInt(m.group(1));
                if (n > 0 && n <= MAX_PLAUSIBLE) {
                    highest = Math.max(highest, n);
                }
            }
        }
        return highest == 0 ? OptionalInt.empty() : OptionalInt.of(highest);
    }
}
