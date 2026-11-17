package dev.phoenix.store;

import dev.phoenix.domain.Application;
import dev.phoenix.domain.JobState;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Optional;

public class ApplicationRepository {

    private static final String COLUMNS = "id, job_id, state, attempts, blocker_type, blocker_detail, resume_version, "
            + "submitted_at, confirmation_ref, note";

    private final JdbcClient jdbc;

    public ApplicationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Application> findByJob(long jobId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM applications WHERE job_id = :j").param("j", jobId)
                .query(ApplicationRepository::map).optional();
    }

    public List<Application> findAll() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM applications ORDER BY id").query(ApplicationRepository::map).list();
    }

    /** Inserts or fully replaces the application row of {@code a.jobId()} (one row per job). */
    public void save(Application a) {
        jdbc.sql("INSERT INTO applications (job_id, state, attempts, blocker_type, blocker_detail, resume_version, "
                        + "submitted_at, confirmation_ref, note) VALUES (:j, :s, :att, :bt, :bd, :rv, :sub, :ref, :note) "
                        + "ON CONFLICT(job_id) DO UPDATE SET state = :s, attempts = :att, blocker_type = :bt, "
                        + "blocker_detail = :bd, resume_version = :rv, submitted_at = :sub, confirmation_ref = :ref, note = :note")
                .param("j", a.jobId()).param("s", a.state().name()).param("att", a.attempts())
                .param("bt", a.blockerType()).param("bd", a.blockerDetail()).param("rv", a.resumeVersion())
                .param("sub", Times.format(a.submittedAt())).param("ref", a.confirmationRef()).param("note", a.note())
                .update();
    }

    /** Applied jobs at one company (other than {@code excludeJobId}) with a submission time, for the cooldown guard. */
    public List<Application> findSubmittedForCompany(String company, long excludeJobId) {
        return jdbc.sql("SELECT a.id, a.job_id, a.state, a.attempts, a.blocker_type, a.blocker_detail, a.resume_version, "
                        + "a.submitted_at, a.confirmation_ref, a.note FROM applications a JOIN jobs j ON j.id = a.job_id "
                        + "WHERE j.company = :c AND a.job_id <> :x AND a.submitted_at IS NOT NULL")
                .param("c", company).param("x", excludeJobId)
                .query(ApplicationRepository::map).list();
    }

    private static Application map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Application(rs.getLong("id"), rs.getLong("job_id"), JobState.valueOf(rs.getString("state")),
                rs.getInt("attempts"), rs.getString("blocker_type"), rs.getString("blocker_detail"),
                rs.getString("resume_version"), Times.parse(rs.getString("submitted_at")),
                rs.getString("confirmation_ref"), rs.getString("note"));
    }
}
