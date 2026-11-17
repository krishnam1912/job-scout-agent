package dev.phoenix.legacy;

import dev.phoenix.domain.JobState;

import java.time.LocalDate;

/**
 * One job as a legacy file describes it. {@code state} is null when the file says nothing about progress (a seen-list
 * entry); {@code score} is the 1-5 rubric score when known. {@code syntheticId} marks an id made up from the row
 * because the row held none.
 */
record LegacyJob(Origin origin, String company, String extId, boolean syntheticId, String title, String location,
                 String url, Double score, String rationale, LocalDate date, JobState state, String note) {

    /** Which file a row came from; a higher precedence wins when files disagree (applications beat the leads CSV). */
    enum Origin {
        SEEN_LIST(0), QUEUE(1), LEADS_CSV(2), APPLICATIONS(3);

        private final int precedence;

        Origin(int precedence) {
            this.precedence = precedence;
        }

        int precedence() {
            return precedence;
        }
    }
}
