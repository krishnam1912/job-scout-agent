package dev.phoenix.service;

import java.util.List;

/** Which jobs an approval covered and which were refused, each with the reason the operator can relay. */
public record ApprovalOutcome(List<Long> approved, List<Refusal> refused) {

    /** One job that could not be approved. */
    public record Refusal(long jobId, String reason) {
    }
}
