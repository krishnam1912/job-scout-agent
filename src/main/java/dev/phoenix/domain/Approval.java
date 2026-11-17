package dev.phoenix.domain;

import java.time.Instant;

/** A recorded human go-ahead. {@code userWords} keeps the user's message verbatim as evidence. */
public record Approval(long id, String scope, String grantedVia, String userWords, Instant grantedAt,
                       Instant expiresAt, Instant consumedAt) {

    public boolean isUsableAt(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }
}
