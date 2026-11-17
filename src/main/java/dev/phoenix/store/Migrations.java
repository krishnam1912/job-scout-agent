package dev.phoenix.store;

import dev.phoenix.error.PhoenixException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies {@code classpath:db/migration/V<n>__<name>.sql} files in order and records them in
 * {@code schema_version}. A small runner instead of Flyway: the schema is SQLite-only, startup time matters for
 * a CLI, and every migration runs in one transaction. Statements are split on a semicolon at the end of a line.
 */
public final class Migrations {

    private static final Logger log = LoggerFactory.getLogger(Migrations.class);
    private static final Pattern FILE_NAME = Pattern.compile("V(\\d+)__(.+)\\.sql");

    private final DataSource dataSource;
    private final Clock clock;

    public Migrations(DataSource dataSource, Clock clock) {
        this.dataSource = dataSource;
        this.clock = clock;
    }

    /** @return the number of migrations applied by this call */
    public int migrate() {
        try (Connection c = dataSource.getConnection()) {
            ensureVersionTable(c);
            Set<Integer> applied = appliedVersions(c);
            int count = 0;
            for (Migration m : discover()) {
                if (!applied.contains(m.version())) {
                    apply(c, m);
                    count++;
                }
            }
            return count;
        } catch (SQLException | IOException e) {
            throw new PhoenixException("Database migration failed: " + e.getMessage(), e);
        }
    }

    private void ensureVersionTable(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS schema_version ("
                    + "version INTEGER PRIMARY KEY, name TEXT NOT NULL, applied_at TEXT NOT NULL)");
        }
    }

    private Set<Integer> appliedVersions(Connection c) throws SQLException {
        Set<Integer> versions = new HashSet<>();
        try (Statement s = c.createStatement(); var rs = s.executeQuery("SELECT version FROM schema_version")) {
            while (rs.next()) {
                versions.add(rs.getInt(1));
            }
        }
        return versions;
    }

    private List<Migration> discover() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/V*.sql");
        return Arrays.stream(resources)
                .map(Migrations::toMigration)
                .sorted(Comparator.comparingInt(Migration::version))
                .toList();
    }

    private static Migration toMigration(Resource r) {
        String fileName = r.getFilename();
        Matcher m = fileName == null ? null : FILE_NAME.matcher(fileName);
        if (m == null || !m.matches()) {
            throw new PhoenixException("Bad migration file name: " + fileName);
        }
        try {
            String sql = new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new Migration(Integer.parseInt(m.group(1)), m.group(2), sql);
        } catch (IOException e) {
            throw new PhoenixException("Cannot read migration " + fileName, e);
        }
    }

    private void apply(Connection c, Migration m) throws SQLException {
        log.info("Applying migration V{} {}", m.version(), m.name());
        boolean previousAutoCommit = c.getAutoCommit();
        c.setAutoCommit(false);
        try (Statement s = c.createStatement()) {
            for (String statement : m.statements()) {
                s.executeUpdate(statement);
            }
            try (var ps = c.prepareStatement("INSERT INTO schema_version (version, name, applied_at) VALUES (?, ?, ?)")) {
                ps.setInt(1, m.version());
                ps.setString(2, m.name());
                ps.setString(3, Times.format(clock.instant()));
                ps.executeUpdate();
            }
            c.commit();
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(previousAutoCommit);
        }
    }

    private record Migration(int version, String name, String sql) {

        List<String> statements() {
            String withoutComments = sql.lines()
                    .filter(line -> !line.strip().startsWith("--"))
                    .reduce("", (a, b) -> a + b + "\n");
            return Arrays.stream(withoutComments.split(";\\s*\\n"))
                    .map(String::strip)
                    .map(s -> s.endsWith(";") ? s.substring(0, s.length() - 1) : s)
                    .filter(s -> !s.isBlank())
                    .toList();
        }
    }
}
