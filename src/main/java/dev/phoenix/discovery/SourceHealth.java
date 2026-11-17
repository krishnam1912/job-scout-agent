package dev.phoenix.discovery;

import dev.phoenix.store.SourceHealthRepository;

import java.time.Instant;
import java.util.Optional;

/** Tracks which boards answer, so a dying feed is visible and liveness only trusts successful scans. */
public class SourceHealth {

    private final SourceHealthRepository repository;

    public SourceHealth(SourceHealthRepository repository) {
        this.repository = repository;
    }

    /** @return the time of the board's previous successful scan, if any */
    public Optional<Instant> recordSuccess(String source, String board, Instant at, int postings) {
        Optional<Instant> previous = repository.find(source, board).map(SourceHealthRepository.Health::lastOk);
        repository.recordSuccess(source, board, at, postings);
        return previous;
    }

    public void recordFailure(String source, String board) {
        repository.recordFailure(source, board);
    }
}
