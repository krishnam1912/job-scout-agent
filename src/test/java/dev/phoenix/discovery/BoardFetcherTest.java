package dev.phoenix.discovery;

import dev.phoenix.domain.Posting;
import dev.phoenix.http.HttpFetchException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class BoardFetcherTest {

    private static DiscoveryAdapter adapter(String kind, java.util.function.Function<String, List<Posting>> fn) {
        return new DiscoveryAdapter() {
            @Override
            public String kind() {
                return kind;
            }

            @Override
            public List<Posting> fetch(String board) {
                return fn.apply(board);
            }
        };
    }

    private static Posting posting(String id) {
        return new Posting("fake", "acme", id, "Backend", "Pune", "", "", false);
    }

    @Test
    void resultsComeBackInTaskOrderAndOneFailureDoesNotStopTheOthers() {
        DiscoveryAdapter ok = adapter("fake", board -> List.of(posting(board)));
        DiscoveryAdapter broken = adapter("broken", board -> {
            throw new HttpFetchException("HTTP 503 from somewhere", 503);
        });
        List<BoardTask> tasks = List.of(new BoardTask(ok, "a"), new BoardTask(broken, "b"), new BoardTask(ok, "c"));

        List<BoardResult> results = new BoardFetcher(2).fetchAll(tasks);

        assertThat(results).extracting(r -> r.task().board()).containsExactly("a", "b", "c");
        assertThat(results).extracting(BoardResult::ok).containsExactly(true, false, true);
        assertThat(results.get(1).error()).isInstanceOf(HttpFetchException.class);
        assertThat(results.get(2).postings()).extracting(Posting::jobId).containsExactly("c");
    }

    @Test
    void neverRunsMoreBoardsAtOnceThanTheLimit() {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        DiscoveryAdapter slow = adapter("slow", board -> {
            int now = running.incrementAndGet();
            peak.accumulateAndGet(now, Math::max);
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            running.decrementAndGet();
            return List.of();
        });
        List<BoardTask> tasks = java.util.stream.IntStream.range(0, 8).mapToObj(i -> new BoardTask(slow, "b" + i)).toList();

        new BoardFetcher(3).fetchAll(tasks);

        assertThat(peak.get()).isBetween(1, 3);
    }
}
