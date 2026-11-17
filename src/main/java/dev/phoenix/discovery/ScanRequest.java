package dev.phoenix.discovery;

/** What to scan: how many candidates to return, an optional board-name filter, and whether to leave no trace. */
public record ScanRequest(int limit, String company, boolean dryRun) {
}
