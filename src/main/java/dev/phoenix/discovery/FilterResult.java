package dev.phoenix.discovery;

import java.util.OptionalInt;

/** Why a posting was kept or dropped. {@code reason} names the first filter that failed ("title", "location", ...). */
public record FilterResult(boolean passed, String reason, OptionalInt requiredYears) {

    public static FilterResult kept(OptionalInt years) {
        return new FilterResult(true, null, years);
    }

    public static FilterResult dropped(String reason) {
        return new FilterResult(false, reason, OptionalInt.empty());
    }
}
