package dev.phoenix.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zaxxer.hikari.HikariDataSource;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.time.Clock;

/** Shared infrastructure: clock, JSON, the SQLite data source, transactions, migrations and the repositories. */
@Configuration(proxyBeanMethods = false)
public class StoreConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean(destroyMethod = "close")
    HikariDataSource dataSource(PhoenixHome home) {
        return DataSources.sqlite(home.dbFile());
    }

    @Bean
    DataSourceTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    TxRunner txRunner(DataSourceTransactionManager transactionManager) {
        return new TxRunner(transactionManager);
    }

    @Bean
    JdbcClient jdbcClient(DataSource dataSource) {
        return JdbcClient.create(dataSource);
    }

    @Bean
    Migrations migrations(DataSource dataSource, Clock clock) {
        return new Migrations(dataSource, clock);
    }

    @Bean
    JobRepository jobRepository(JdbcClient jdbc) {
        return new JobRepository(jdbc);
    }

    @Bean
    GroupRepository groupRepository(JdbcClient jdbc) {
        return new GroupRepository(jdbc);
    }

    @Bean
    ScoreRepository scoreRepository(JdbcClient jdbc) {
        return new ScoreRepository(jdbc);
    }

    @Bean
    ApplicationRepository applicationRepository(JdbcClient jdbc) {
        return new ApplicationRepository(jdbc);
    }

    @Bean
    EventRepository eventRepository(JdbcClient jdbc) {
        return new EventRepository(jdbc);
    }

    @Bean
    AskRepository askRepository(JdbcClient jdbc) {
        return new AskRepository(jdbc);
    }

    @Bean
    ApprovalRepository approvalRepository(JdbcClient jdbc) {
        return new ApprovalRepository(jdbc);
    }

    @Bean
    ReferralRepository referralRepository(JdbcClient jdbc) {
        return new ReferralRepository(jdbc);
    }

    @Bean
    RunRepository runRepository(JdbcClient jdbc) {
        return new RunRepository(jdbc);
    }

    @Bean
    SourceHealthRepository sourceHealthRepository(JdbcClient jdbc) {
        return new SourceHealthRepository(jdbc);
    }
}
