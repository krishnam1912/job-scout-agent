package dev.phoenix.store;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.time.Instant;
import java.util.Optional;

/** Records scan runs so status can say when the last scan happened and how it ended. */
public class RunRepository {

    /** A run row. */
    public record Run(long id, String kind, String status, Instant startedAt, Instant finishedAt, String checkpoint) {
    }

    private final JdbcClient jdbc;

    public RunRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long start(String kind, Instant at) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO runs (kind, status, started_at) VALUES (:k, 'RUNNING', :at)")
                .param("k", kind).param("at", Times.format(at)).update(keys);
        return keys.getKey().longValue();
    }

    public void finish(long id, String status, Instant at, String checkpoint) {
        jdbc.sql("UPDATE runs SET status = :s, finished_at = :at, checkpoint = :c WHERE id = :id")
                .param("s", status).param("at", Times.format(at)).param("c", checkpoint).param("id", id).update();
    }

    public Optional<Run> latest(String kind) {
        return jdbc.sql("SELECT id, kind, status, started_at, finished_at, checkpoint FROM runs WHERE kind = :k "
                        + "ORDER BY id DESC LIMIT 1")
                .param("k", kind)
                .query((rs, i) -> new Run(rs.getLong("id"), rs.getString("kind"), rs.getString("status"),
                        Times.parse(rs.getString("started_at")), Times.parse(rs.getString("finished_at")),
                        rs.getString("checkpoint")))
                .optional();
    }
}
