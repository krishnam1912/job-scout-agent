package dev.phoenix.legacy;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads scores out of the old files' loose notation ("4.4", "4.4/5", "88%", "star 4.1"). */
final class Scores {

    private static final Pattern DECIMAL = Pattern.compile("(\\d+(?:\\.\\d+)?)");
    private static final Pattern PERCENT = Pattern.compile("(\\d{1,3})\\s*%");
    private static final double PERCENT_PER_POINT = 20.0;

    private Scores() {
    }

    /** A 1-5 score: the first number in the text, when it is in range. */
    static Optional<Double> rubric(String text) {
        Matcher m = DECIMAL.matcher(text == null ? "" : text);
        if (!m.find()) {
            return Optional.empty();
        }
        double value = Double.parseDouble(m.group(1));
        return value >= 1.0 && value <= 5.0 ? Optional.of(value) : Optional.empty();
    }

    /** A fit percentage converted to the 1-5 scale. */
    static Optional<Double> fromPercent(String text) {
        Matcher m = PERCENT.matcher(text == null ? "" : text);
        if (!m.find()) {
            return Optional.empty();
        }
        double score = Math.round(Integer.parseInt(m.group(1)) / PERCENT_PER_POINT * 10.0) / 10.0;
        return score >= 1.0 && score <= 5.0 ? Optional.of(score) : Optional.empty();
    }
}
