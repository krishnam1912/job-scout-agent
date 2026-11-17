package dev.phoenix.discovery;

/**
 * A posting offered to the operator for scoring. {@code jobId} is the engine's id (null in a dry run, where nothing
 * is stored); {@code extId} is the id on the company's own board.
 */
public record Candidate(Long jobId, String company, String extId, String title, String location, String url,
                        int prescore, String jdSummary) {
}
