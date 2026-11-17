package dev.phoenix.service;

import dev.phoenix.domain.JobState;
import dev.phoenix.error.UserErrorException;

import java.util.Arrays;
import java.util.Locale;

/** What the employer did after a submitted application. */
public enum PostSubmitOutcome {
    OA(JobState.OA), INTERVIEW(JobState.INTERVIEW), REJECTED(JobState.REJECTED), OFFER(JobState.OFFER);

    private final JobState state;

    PostSubmitOutcome(JobState state) {
        this.state = state;
    }

    public JobState state() {
        return state;
    }

    public static PostSubmitOutcome parse(String text) {
        String wanted = text == null ? "" : text.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(o -> o.name().equals(wanted)).findFirst()
                .orElseThrow(() -> new UserErrorException("Unknown outcome '" + text
                        + "' (expected oa, interview, rejected or offer)"));
    }
}
