package dev.phoenix.store;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.util.Optional;

public class SourceHealthRepository {

    /** Health of one board. */
    public record Health(String source, String board, Instant lastOk, Integer lastCount, int consecutiveFailures) {
    }

    private final JdbcClient jdbc;

    public SourceHealthRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Health> find(String source, String board) {
        return jdbc.sql("SELECT source, board, last_ok, last_count, consecutive_failures FROM source_health "
                        + "WHERE source = :s AND board = :b")
                .param("s", source).param("b", board)
                .query((rs, i) -> {
                    int count = rs.getInt("last_count");
                    Integer lastCount = rs.wasNull() ? null : count;
                    return new Health(rs.getString("source"), rs.getString("board"),
                            Times.parse(rs.getString("last_ok")), lastCount, rs.getInt("consecutive_failures"));
                }).optional();
    }

    public void recordSuccess(String source, String board, Instant at, int count) {
        jdbc.sql("INSERT INTO source_health (source, board, last_ok, last_count, consecutive_failures) "
                        + "VALUES (:s, :b, :at, :n, 0) ON CONFLICT(source, board) DO UPDATE SET "
                        + "last_ok = :at, last_count = :n, consecutive_failures = 0")
                .param("s", source).param("b", board).param("at", Times.format(at)).param("n", count).update();
    }

    public void recordFailure(String source, String board) {
        jdbc.sql("INSERT INTO source_health (source, board, consecutive_failures) VALUES (:s, :b, 1) "
                        + "ON CONFLICT(source, board) DO UPDATE SET consecutive_failures = consecutive_failures + 1")
                .param("s", source).param("b", board).update();
    }
}
