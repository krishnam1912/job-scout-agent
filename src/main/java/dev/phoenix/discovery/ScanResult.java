package dev.phoenix.discovery;

import java.util.List;

/** The candidates a scan found plus its counters. */
public record ScanResult(List<Candidate> candidates, ScanStats stats) {
}
