package dev.phoenix.store;

import dev.phoenix.domain.Event;
import dev.phoenix.domain.JobState;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.util.List;

/** Append-only: there is deliberately no update or delete. */
public class EventRepository {

    private final JdbcClient jdbc;

    public EventRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void append(long jobId, JobState from, JobState to, String actor, String note, Instant at) {
        jdbc.sql("INSERT INTO events (job_id, from_state, to_state, actor, note, at) VALUES (:j, :f, :t, :a, :n, :at)")
                .param("j", jobId).param("f", from == null ? null : from.name()).param("t", to.name())
                .param("a", actor).param("n", note).param("at", Times.format(at)).update();
    }

    public List<Event> findByJob(long jobId) {
        return jdbc.sql("SELECT id, job_id, from_state, to_state, actor, note, at FROM events WHERE job_id = :j ORDER BY id")
                .param("j", jobId)
                .query((rs, i) -> new Event(rs.getLong("id"), rs.getLong("job_id"),
                        rs.getString("from_state") == null ? null : JobState.valueOf(rs.getString("from_state")),
                        JobState.valueOf(rs.getString("to_state")), rs.getString("actor"), rs.getString("note"),
                        Times.parse(rs.getString("at"))))
                .list();
    }
}
