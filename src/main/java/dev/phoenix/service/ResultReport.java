package dev.phoenix.service;

/** What the operator tells the engine after an application attempt. */
public record ResultReport(long jobId, ApplicationOutcome outcome, String blockerType, String detail,
                           String confirmationRef) {
}
