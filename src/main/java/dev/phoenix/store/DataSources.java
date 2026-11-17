package dev.phoenix.store;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.phoenix.error.PhoenixException;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Builds the SQLite data source: WAL, foreign keys on, writers wait instead of failing, write locks taken up front. */
public final class DataSources {

    private static final int BUSY_TIMEOUT_MS = 5000;
    private static final int POOL_SIZE = 4;

    private DataSources() {
    }

    public static HikariDataSource sqlite(Path dbFile) {
        try {
            Files.createDirectories(dbFile.toAbsolutePath().getParent());
        } catch (IOException e) {
            throw new PhoenixException("Cannot create the state directory for " + dbFile, e);
        }
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        config.setBusyTimeout(BUSY_TIMEOUT_MS);
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);

        SQLiteDataSource raw = new SQLiteDataSource(config);
        raw.setUrl("jdbc:sqlite:" + dbFile.toAbsolutePath());

        HikariConfig pool = new HikariConfig();
        pool.setDataSource((DataSource) raw);
        pool.setMaximumPoolSize(POOL_SIZE);
        pool.setPoolName("phoenix-sqlite");
        return new HikariDataSource(pool);
    }
}
