package dev.phoenix.store;

import com.zaxxer.hikari.HikariDataSource;
import dev.phoenix.testsupport.MutableClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationsTest {

    @Test
    void createsEverySpecTableAndRecordsTheVersionOnce(@TempDir Path dir) {
        try (HikariDataSource ds = DataSources.sqlite(dir.resolve("phoenix.db"))) {
            Migrations migrations = new Migrations(ds, MutableClock.at("2026-11-18T10:00:00Z"));

            assertThat(migrations.migrate()).isEqualTo(1);
            assertThat(migrations.migrate()).isZero();

            JdbcClient jdbc = JdbcClient.create(ds);
            List<String> tables = jdbc.sql("SELECT name FROM sqlite_master WHERE type = 'table'")
                    .query(String.class).list();
            assertThat(tables).contains("jobs", "job_groups", "scores", "applications", "events", "runs", "asks",
                    "approvals", "answers", "referrals", "source_health", "schema_version");
            assertThat(jdbc.sql("SELECT COUNT(*) FROM schema_version").query(Integer.class).single()).isEqualTo(1);
        }
    }

    @Test
    void usesWalAndForeignKeys(@TempDir Path dir) {
        try (HikariDataSource ds = DataSources.sqlite(dir.resolve("phoenix.db"))) {
            JdbcClient jdbc = JdbcClient.create(ds);

            assertThat(jdbc.sql("PRAGMA journal_mode").query(String.class).single()).isEqualToIgnoringCase("wal");
            assertThat(jdbc.sql("PRAGMA foreign_keys").query(Integer.class).single()).isEqualTo(1);
        }
    }
}
