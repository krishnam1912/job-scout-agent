package dev.phoenix.testsupport;

import com.zaxxer.hikari.HikariDataSource;
import dev.phoenix.domain.Job;
import dev.phoenix.domain.JobState;
import dev.phoenix.lifecycle.LifecycleService;
import dev.phoenix.lifecycle.NextActionPolicy;
import dev.phoenix.store.ApplicationRepository;
import dev.phoenix.store.ApprovalRepository;
import dev.phoenix.store.AskRepository;
import dev.phoenix.store.DataSources;
import dev.phoenix.store.EventRepository;
import dev.phoenix.store.GroupRepository;
import dev.phoenix.store.JobRepository;
import dev.phoenix.store.Migrations;
import dev.phoenix.store.ReferralRepository;
import dev.phoenix.store.RunRepository;
import dev.phoenix.store.ScoreRepository;
import dev.phoenix.store.SourceHealthRepository;
import dev.phoenix.store.TxRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.nio.file.Path;
import java.time.Instant;

/** A real SQLite database in a temp directory with every repository wired, for store and service tests. */
public final class TestStore implements AutoCloseable {

    public static final String START = "2026-11-18T10:00:00Z";

    public final HikariDataSource dataSource;
    public final MutableClock clock = MutableClock.at(START);
    public final JdbcClient jdbc;
    public final TxRunner tx;
    public final JobRepository jobs;
    public final GroupRepository groups;
    public final ScoreRepository scores;
    public final ApplicationRepository applications;
    public final EventRepository events;
    public final AskRepository asks;
    public final ApprovalRepository approvals;
    public final ReferralRepository referrals;
    public final RunRepository runs;
    public final SourceHealthRepository health;
    public final NextActionPolicy policy = new NextActionPolicy();
    public final LifecycleService lifecycle;

    private TestStore(Path dir) {
        dataSource = DataSources.sqlite(dir.resolve("state").resolve("phoenix.db"));
        new Migrations(dataSource, clock).migrate();
        jdbc = JdbcClient.create(dataSource);
        tx = new TxRunner(new DataSourceTransactionManager(dataSource));
        jobs = new JobRepository(jdbc);
        groups = new GroupRepository(jdbc);
        scores = new ScoreRepository(jdbc);
        applications = new ApplicationRepository(jdbc);
        events = new EventRepository(jdbc);
        asks = new AskRepository(jdbc);
        approvals = new ApprovalRepository(jdbc);
        referrals = new ReferralRepository(jdbc);
        runs = new RunRepository(jdbc);
        health = new SourceHealthRepository(jdbc);
        lifecycle = new LifecycleService(jobs, events, policy, tx, clock);
    }

    public static TestStore create(Path dir) {
        return new TestStore(dir);
    }

    /** Inserts a job straight into {@code state} (no event), the way fixtures need it. */
    public Job insertJob(String company, String extId, String title, JobState state) {
        return insertJob("greenhouse", company, extId, title, state, null);
    }

    public Job insertJob(String source, String company, String extId, String title, JobState state) {
        return insertJob(source, company, extId, title, state, null);
    }

    public Job insertJob(String source, String company, String extId, String title, JobState state, Long groupId) {
        Instant now = clock.instant();
        Job draft = new Job(0, source, company, extId, "https://example.com/" + extId, title, "Bengaluru", groupId,
                state, "jd for " + title, null, 50, now, now, null, null, null);
        long id = jobs.insert(draft);
        return jobs.findById(id).orElseThrow();
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
