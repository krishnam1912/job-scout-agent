package dev.phoenix.discovery;

import java.util.Map;

/** Counters of one scan, for the run summary and the run record. */
public record ScanStats(int fetched, int alreadyKnown, int newPostings, int newCandidates, int duplicatesHidden,
                        Map<String, Integer> filteredByReason, int failedBoards, int closed) {

    public ScanStats {
        filteredByReason = Map.copyOf(filteredByReason);
    }
}
