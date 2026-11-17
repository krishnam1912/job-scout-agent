package dev.phoenix.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.phoenix.dedupe.GroupingService;
import dev.phoenix.discovery.AdapterRegistry;
import dev.phoenix.discovery.BoardFetcher;
import dev.phoenix.discovery.CandidatePool;
import dev.phoenix.discovery.DiscoveryConfigLoader;
import dev.phoenix.discovery.PostingLiveness;
import dev.phoenix.discovery.ScanClassifier;
import dev.phoenix.discovery.ScanService;
import dev.phoenix.discovery.ScanWriter;
import dev.phoenix.discovery.SourceHealth;
import dev.phoenix.export.ExportService;
import dev.phoenix.http.HttpFetcher;
import dev.phoenix.http.JdkHttpFetcher;
import dev.phoenix.http.JsonFetcher;
import dev.phoenix.legacy.LegacyImporter;
import dev.phoenix.legacy.LegacyJobWriter;
import dev.phoenix.lifecycle.LifecycleService;
import dev.phoenix.lifecycle.NextActionPolicy;
import dev.phoenix.service.QueueService;
import dev.phoenix.service.StatusService;
import dev.phoenix.store.ApplicationRepository;
import dev.phoenix.store.EventRepository;
import dev.phoenix.store.GroupRepository;
import dev.phoenix.store.JobRepository;
import dev.phoenix.store.ReferralRepository;
import dev.phoenix.store.RunRepository;
import dev.phoenix.store.ScoreRepository;
import dev.phoenix.store.SourceHealthRepository;
import dev.phoenix.store.TxRunner;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/** Discovery, the one-off legacy import and the exports: everything that moves job data in or out. */
@Configuration(proxyBeanMethods = false)
public class DiscoveryConfiguration {

    static final String BOARD_HTTP = "boardHttp";
    private static final Duration BOARD_TIMEOUT = Duration.ofSeconds(15);
    private static final int PARALLEL_BOARDS = 8;

    @Bean(BOARD_HTTP)
    HttpFetcher boardHttp() {
        return new JdkHttpFetcher(BOARD_TIMEOUT);
    }

    @Bean
    AdapterRegistry adapterRegistry(@Qualifier(BOARD_HTTP) HttpFetcher http, ObjectMapper mapper) {
        return new AdapterRegistry(new JsonFetcher(http, mapper));
    }

    @Bean
    BoardFetcher boardFetcher() {
        return new BoardFetcher(PARALLEL_BOARDS);
    }

    @Bean
    DiscoveryConfigLoader discoveryConfigLoader(PhoenixHome home, PhoenixProperties properties) {
        return new DiscoveryConfigLoader(home, properties);
    }

    @Bean
    SourceHealth sourceHealth(SourceHealthRepository repository) {
        return new SourceHealth(repository);
    }

    @Bean
    PostingLiveness postingLiveness(JobRepository jobs, LifecycleService lifecycle) {
        return new PostingLiveness(jobs, lifecycle);
    }

    @Bean
    ScanClassifier scanClassifier(JobRepository jobs) {
        return new ScanClassifier(jobs);
    }

    @Bean
    ScanWriter scanWriter(JobRepository jobs, EventRepository events, GroupRepository groups, GroupingService grouping,
                          LifecycleService lifecycle, SourceHealth health, PostingLiveness liveness) {
        return new ScanWriter(jobs, events, groups, grouping, lifecycle, health, liveness);
    }

    @Bean
    CandidatePool candidatePool(JobRepository jobs, GroupRepository groups) {
        return new CandidatePool(jobs, groups);
    }

    @Bean
    ScanService scanService(DiscoveryConfigLoader loader, AdapterRegistry registry, BoardFetcher fetcher,
                            ScanClassifier classifier, ScanWriter writer, GroupingService grouping, CandidatePool pool,
                            RunRepository runs, TxRunner tx, Clock clock, ObjectMapper mapper) {
        return new ScanService(loader, registry, fetcher, classifier, writer, grouping, pool, runs, tx, clock, mapper);
    }

    @Bean
    LegacyJobWriter legacyJobWriter(JobRepository jobs, ScoreRepository scores, ApplicationRepository applications,
                                    EventRepository events, NextActionPolicy policy, Clock clock) {
        return new LegacyJobWriter(jobs, scores, applications, events, policy, clock);
    }

    @Bean
    LegacyImporter legacyImporter(PhoenixHome home, PhoenixProperties properties, LegacyJobWriter writer,
                                  ReferralRepository referrals, TxRunner tx) {
        return new LegacyImporter(home, properties, writer, referrals, tx);
    }

    @Bean
    ExportService exportService(PhoenixHome home, JobRepository jobs, ScoreRepository scores,
                                ApplicationRepository applications, QueueService queue, StatusService status) {
        return new ExportService(home, jobs, scores, applications, queue, status);
    }
}
