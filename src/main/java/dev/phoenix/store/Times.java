package dev.phoenix.store;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** One timestamp format everywhere (ISO-8601 UTC, whole seconds) so string comparison in SQL matches time order. */
public final class Times {

    private Times() {
    }

    public static String format(Instant t) {
        return t == null ? null : t.truncatedTo(ChronoUnit.SECONDS).toString();
    }

    public static Instant parse(String s) {
        return s == null ? null : Instant.parse(s);
    }
}
