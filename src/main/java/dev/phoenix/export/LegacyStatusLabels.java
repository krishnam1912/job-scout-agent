package dev.phoenix.export;

import dev.phoenix.domain.JobState;

import java.util.Locale;

/** Status wording of the old files, so exported views read like the ones they replace. */
final class LegacyStatusLabels {

    private LegacyStatusLabels() {
    }

    static String of(JobState state) {
        return switch (state) {
            case QUEUED -> "New";
            case SUBMITTED -> "Applied";
            case SKIPPED -> "Skipped";
            case BLOCKED -> "Blocked";
            default -> {
                String name = state.name().replace('_', ' ').toLowerCase(Locale.ROOT);
                yield Character.toUpperCase(name.charAt(0)) + name.substring(1);
            }
        };
    }
}
