package dev.phoenix.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * Fetches boards in parallel on virtual threads, at most {@code parallelism} at a time. A board that fails is
 * logged and returned as a failed result: one broken feed never fails the run.
 */
public final class BoardFetcher {

    private static final Logger log = LoggerFactory.getLogger(BoardFetcher.class);

    private final int parallelism;

    public BoardFetcher(int parallelism) {
        this.parallelism = parallelism;
    }

    /** @return one result per task, in task order */
    public List<BoardResult> fetchAll(List<BoardTask> tasks) {
        Semaphore permits = new Semaphore(parallelism);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<BoardResult>> futures = new ArrayList<>();
            for (BoardTask task : tasks) {
                futures.add(pool.submit(() -> fetchOne(task, permits)));
            }
            List<BoardResult> results = new ArrayList<>();
            for (Future<BoardResult> future : futures) {
                results.add(await(future));
            }
            return results;
        }
    }

    private BoardResult fetchOne(BoardTask task, Semaphore permits) throws InterruptedException {
        permits.acquire();
        try {
            long start = System.nanoTime();
            BoardResult result = BoardResult.success(task, task.adapter().fetch(task.board()));
            log.info("{}: {} postings ({} ms)", task.label(), result.postings().size(), (System.nanoTime() - start) / 1_000_000);
            return result;
        } catch (RuntimeException e) {
            log.warn("{} skipped: {}: {}", task.label(), e.getClass().getSimpleName(), e.getMessage());
            return BoardResult.failure(task, e);
        } finally {
            permits.release();
        }
    }

    private static BoardResult await(Future<BoardResult> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Scan interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Board fetch crashed", e.getCause());
        }
    }
}
