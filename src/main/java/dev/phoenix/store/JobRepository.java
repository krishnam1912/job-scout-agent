package dev.phoenix.store;

import dev.phoenix.domain.Job;
import dev.phoenix.domain.JobState;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Persistence for {@link Job}. Contains no business rules; lifecycle and dedupe decisions live in services. */
public class JobRepository {

    private static final String COLUMNS = "id, source, company, ext_id, url, title, location, group_id, state, jd_text, "
            + "jd_hash, prescore, first_seen, last_seen, closed_at, closing_date, next_action_at";

    private final JdbcClient jdbc;

    public JobRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Job> findById(long id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM jobs WHERE id = :id").param("id", id)
                .query(JobRepository::map).optional();
    }

    public Optional<Job> findByKey(String source, String company, String extId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM jobs WHERE source = :s AND company = :c AND ext_id = :e")
                .param("s", source).param("c", company).param("e", extId)
                .query(JobRepository::map).optional();
    }

    /** Same company and external id from any source (a legacy row and a scanned row are the same posting). */
    public List<Job> findByCompanyAndExtId(String company, String extId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM jobs WHERE company = :c AND ext_id = :e ORDER BY id")
                .param("c", company).param("e", extId).query(JobRepository::map).list();
    }

    public List<Job> findByExtId(String extId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM jobs WHERE ext_id = :e ORDER BY id")
                .param("e", extId).query(JobRepository::map).list();
    }

    public List<Job> findByCompany(String company) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM jobs WHERE company = :c ORDER BY id")
                .param("c", company).query(JobRepository::map).list();
    }

    public List<Job> findByGroup(long groupId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM jobs WHERE group_id = :g ORDER BY id")
                .param("g", groupId).query(JobRepository::map).list();
    }

    public List<Job> findByStates(Collection<JobState> states) {
        if (states.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT " + COLUMNS + " FROM jobs WHERE state IN (:states) ORDER BY id")
                .param("states", states.stream().map(Enum::name).toList())
                .query(JobRepository::map).list();
    }

    public List<Job> findAll() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM jobs ORDER BY id").query(JobRepository::map).list();
    }

    /** Jobs of one board that a scan may still close (state filter is applied by the caller's list of states). */
    public List<Job> findByBoardAndStates(String source, String company, Collection<JobState> states) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM jobs WHERE source = :s AND company = :c AND state IN (:states) ORDER BY id")
                .param("s", source).param("c", company)
                .param("states", states.stream().map(Enum::name).toList())
                .query(JobRepository::map).list();
    }

    /** Open jobs that need attention: QUEUED/BLOCKED past their next action, or closing before the horizon. */
    public List<Job> findAtRisk(Instant now, Instant closingHorizon) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM jobs WHERE "
                        + "(state IN ('QUEUED','BLOCKED') AND next_action_at IS NOT NULL AND next_action_at <= :now) "
                        + "OR (closing_date IS NOT NULL AND closing_date <= :horizon "
                        + "AND state IN ('DISCOVERED','CANDIDATE','SCORED','NEAR_MISS','BELOW','QUEUED','APPROVED')) "
                        + "ORDER BY id")
                .param("now", Times.format(now)).param("horizon", Times.format(closingHorizon))
                .query(JobRepository::map).list();
    }

    public Map<JobState, Integer> countByState() {
        Map<JobState, Integer> counts = new EnumMap<>(JobState.class);
        jdbc.sql("SELECT state, COUNT(*) AS n FROM jobs GROUP BY state")
                .query((rs, i) -> {
                    counts.put(JobState.valueOf(rs.getString("state")), rs.getInt("n"));
                    return null;
                }).list();
        return counts;
    }

    public long insert(Job j) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO jobs (source, company, ext_id, url, title, location, group_id, state, jd_text, jd_hash, "
                        + "prescore, first_seen, last_seen, closed_at, closing_date, next_action_at) VALUES "
                        + "(:source, :company, :ext, :url, :title, :location, :group, :state, :jd, :hash, :prescore, "
                        + ":first, :last, :closed, :closing, :next)")
                .param("source", j.source()).param("company", j.company()).param("ext", j.extId())
                .param("url", j.url()).param("title", j.title()).param("location", j.location())
                .param("group", j.groupId()).param("state", j.state().name())
                .param("jd", j.jdText()).param("hash", j.jdHash()).param("prescore", j.prescore())
                .param("first", Times.format(j.firstSeen())).param("last", Times.format(j.lastSeen()))
                .param("closed", Times.format(j.closedAt())).param("closing", Times.format(j.closingDate()))
                .param("next", Times.format(j.nextActionAt()))
                .update(keys);
        return keys.getKey().longValue();
    }

    public void markSeen(long id, Instant at) {
        jdbc.sql("UPDATE jobs SET last_seen = :at WHERE id = :id").param("at", Times.format(at)).param("id", id).update();
    }

    /** Refreshes the posting data of a job (a re-fetch can carry a newer title, URL or JD). */
    public void updatePosting(long id, String url, String title, String location, String jdText, String jdHash) {
        jdbc.sql("UPDATE jobs SET url = :url, title = :title, location = :loc, jd_text = :jd, jd_hash = :hash WHERE id = :id")
                .param("url", url).param("title", title).param("loc", location)
                .param("jd", jdText).param("hash", jdHash).param("id", id).update();
    }

    public void updateScan(long id, Integer prescore, Long groupId) {
        jdbc.sql("UPDATE jobs SET prescore = :p, group_id = :g WHERE id = :id")
                .param("p", prescore).param("g", groupId).param("id", id).update();
    }

    public void updateState(long id, JobState state, Instant nextActionAt, Instant closedAt) {
        jdbc.sql("UPDATE jobs SET state = :s, next_action_at = :n, closed_at = :c WHERE id = :id")
                .param("s", state.name()).param("n", Times.format(nextActionAt)).param("c", Times.format(closedAt))
                .param("id", id).update();
    }

    public void updateClosingDate(long id, Instant closingDate, Instant nextActionAt) {
        jdbc.sql("UPDATE jobs SET closing_date = :d, next_action_at = :n WHERE id = :id")
                .param("d", Times.format(closingDate)).param("n", Times.format(nextActionAt)).param("id", id).update();
    }

    private static Job map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        long group = rs.getLong("group_id");
        Long groupId = rs.wasNull() ? null : group;
        int prescore = rs.getInt("prescore");
        Integer prescoreBoxed = rs.wasNull() ? null : prescore;
        return new Job(rs.getLong("id"), rs.getString("source"), rs.getString("company"), rs.getString("ext_id"),
                rs.getString("url"), rs.getString("title"), rs.getString("location"), groupId,
                JobState.valueOf(rs.getString("state")), rs.getString("jd_text"), rs.getString("jd_hash"),
                prescoreBoxed, Times.parse(rs.getString("first_seen")), Times.parse(rs.getString("last_seen")),
                Times.parse(rs.getString("closed_at")), Times.parse(rs.getString("closing_date")),
                Times.parse(rs.getString("next_action_at")));
    }
}
