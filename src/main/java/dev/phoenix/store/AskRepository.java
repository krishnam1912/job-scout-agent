package dev.phoenix.store;

import dev.phoenix.domain.Ask;
import dev.phoenix.domain.AskStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class AskRepository {

    private static final String COLUMNS = "id, job_id, kind, prompt, options, priority, status, answer, channel, "
            + "created_at, due_at, answered_at";

    private final JdbcClient jdbc;

    public AskRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(Ask a) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO asks (job_id, kind, prompt, options, priority, status, answer, channel, created_at, due_at, "
                        + "answered_at) VALUES (:job, :kind, :prompt, :options, :prio, :status, :answer, :channel, :created, "
                        + ":due, :answered)")
                .param("job", a.jobId()).param("kind", a.kind()).param("prompt", a.prompt())
                .param("options", a.options()).param("prio", a.priority()).param("status", a.status().name())
                .param("answer", a.answer()).param("channel", a.channel())
                .param("created", Times.format(a.createdAt())).param("due", Times.format(a.dueAt()))
                .param("answered", Times.format(a.answeredAt())).update(keys);
        return keys.getKey().longValue();
    }

    public Optional<Ask> findById(long id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM asks WHERE id = :id").param("id", id)
                .query(AskRepository::map).optional();
    }

    public List<Ask> findByStatuses(Collection<AskStatus> statuses) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM asks WHERE status IN (:s) ORDER BY priority DESC, id")
                .param("s", statuses.stream().map(Enum::name).toList()).query(AskRepository::map).list();
    }

    public List<Ask> findOpenForJob(long jobId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM asks WHERE job_id = :j AND status <> 'ANSWERED' ORDER BY id")
                .param("j", jobId).query(AskRepository::map).list();
    }

    public void markOffered(long id, String channel) {
        jdbc.sql("UPDATE asks SET status = 'OFFERED', channel = :c WHERE id = :id AND status = 'OPEN'")
                .param("c", channel).param("id", id).update();
    }

    public void markAnswered(long id, String answer, Instant at) {
        jdbc.sql("UPDATE asks SET status = 'ANSWERED', answer = :a, answered_at = :at WHERE id = :id")
                .param("a", answer).param("at", Times.format(at)).param("id", id).update();
    }

    private static Ask map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        long job = rs.getLong("job_id");
        Long jobId = rs.wasNull() ? null : job;
        return new Ask(rs.getLong("id"), jobId, rs.getString("kind"), rs.getString("prompt"), rs.getString("options"),
                rs.getInt("priority"), AskStatus.valueOf(rs.getString("status")), rs.getString("answer"),
                rs.getString("channel"), Times.parse(rs.getString("created_at")), Times.parse(rs.getString("due_at")),
                Times.parse(rs.getString("answered_at")));
    }
}
