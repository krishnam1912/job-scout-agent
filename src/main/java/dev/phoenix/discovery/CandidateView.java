package dev.phoenix.discovery;

import java.math.BigInteger;

/** A posting that passed the filters, as the selector sees it: persisted ({@code jobId} set) or only in memory. */
record CandidateView(Long jobId, Long groupId, String company, String extId, String title, String location,
                     String url, int prescore, String jd) {

    /** Numeric requisition ids grow over time, so a bigger one is the newer posting; others count as 0. */
    BigInteger numericId() {
        return extId.chars().allMatch(Character::isDigit) && !extId.isEmpty() ? new BigInteger(extId) : BigInteger.ZERO;
    }
}
