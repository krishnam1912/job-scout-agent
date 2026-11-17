package dev.phoenix.service;

import dev.phoenix.domain.JobState;
import dev.phoenix.error.UserErrorException;

import java.util.Arrays;
import java.util.Locale;

/** How an application attempt ended, as the operator reports it. */
public enum ApplicationOutcome {
    SUBMITTED(JobState.SUBMITTED), BLOCKED(JobState.BLOCKED), FAILED(JobState.FAILED),
    SUBMIT_UNKNOWN(JobState.SUBMIT_UNKNOWN), SKIPPED(JobState.SKIPPED);

    private final JobState state;

    ApplicationOutcome(JobState state) {
        this.state = state;
    }

    public JobState state() {
        return state;
    }

    public static ApplicationOutcome parse(String text) {
        String wanted = text == null ? "" : text.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(o -> o.name().equals(wanted)).findFirst()
                .orElseThrow(() -> new UserErrorException("Unknown outcome '" + text
                        + "' (expected submitted, blocked, failed, submit_unknown or skipped)"));
    }
}
