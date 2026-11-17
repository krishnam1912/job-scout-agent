package dev.phoenix.store;

import dev.phoenix.domain.Score;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ScoreRepository {

    private static final String LATEST_IDS = "SELECT MAX(id) FROM scores GROUP BY job_id";

    private final JdbcClient jdbc;

    public ScoreRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Score s) {
        jdbc.sql("INSERT INTO scores (job_id, scorer, score, fit_pct, rationale, rubric_version, scored_at) "
                        + "VALUES (:job, :scorer, :score, :fit, :why, :rubric, :at)")
                .param("job", s.jobId()).param("scorer", s.scorer()).param("score", s.score())
                .param("fit", s.fitPct()).param("why", s.rationale()).param("rubric", s.rubricVersion())
                .param("at", Times.format(s.scoredAt())).update();
    }

    public Optional<Score> latestFor(long jobId) {
        return jdbc.sql("SELECT * FROM scores WHERE job_id = :j ORDER BY id DESC LIMIT 1").param("j", jobId)
                .query(ScoreRepository::map).optional();
    }

    /** Latest score of every job that has one, keyed by job id. */
    public Map<Long, Score> latestByJob(Collection<Long> jobIds) {
        Map<Long, Score> out = new HashMap<>();
        if (jobIds.isEmpty()) {
            return out;
        }
        jdbc.sql("SELECT * FROM scores WHERE id IN (" + LATEST_IDS + ") AND job_id IN (:ids)")
                .param("ids", jobIds).query(ScoreRepository::map).list()
                .forEach(s -> out.put(s.jobId(), s));
        return out;
    }

    public Map<Long, Score> latestForAll() {
        Map<Long, Score> out = new HashMap<>();
        jdbc.sql("SELECT * FROM scores WHERE id IN (" + LATEST_IDS + ")").query(ScoreRepository::map).list()
                .forEach(s -> out.put(s.jobId(), s));
        return out;
    }

    private static Score map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Score(rs.getLong("id"), rs.getLong("job_id"), rs.getString("scorer"), rs.getDouble("score"),
                rs.getInt("fit_pct"), rs.getString("rationale"), rs.getString("rubric_version"),
                Times.parse(rs.getString("scored_at")));
    }
}
