package dev.phoenix.config;

import dev.phoenix.channel.AskService;
import dev.phoenix.channel.ClaudeSessionChannel;
import dev.phoenix.channel.NotificationChannel;
import dev.phoenix.dedupe.CooldownGuard;
import dev.phoenix.dedupe.GroupingService;
import dev.phoenix.dedupe.MinHash;
import dev.phoenix.lifecycle.LifecycleService;
import dev.phoenix.lifecycle.NextActionPolicy;
import dev.phoenix.service.ApplicationService;
import dev.phoenix.service.ApprovalService;
import dev.phoenix.service.JobQueryService;
import dev.phoenix.service.QueueService;
import dev.phoenix.service.ScoringService;
import dev.phoenix.service.StatusService;
import dev.phoenix.store.ApplicationRepository;
import dev.phoenix.store.ApprovalRepository;
import dev.phoenix.store.AskRepository;
import dev.phoenix.store.EventRepository;
import dev.phoenix.store.GroupRepository;
import dev.phoenix.store.JobRepository;
import dev.phoenix.store.RunRepository;
import dev.phoenix.store.ScoreRepository;
import dev.phoenix.store.TxRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

/** The deterministic core: lifecycle, dedupe, channels and the services the operator's tools call. */
@Configuration(proxyBeanMethods = false)
public class EngineConfiguration {

    @Bean
    NextActionPolicy nextActionPolicy() {
        return new NextActionPolicy();
    }

    @Bean
    LifecycleService lifecycleService(JobRepository jobs, EventRepository events, NextActionPolicy policy, TxRunner tx,
                                      Clock clock) {
        return new LifecycleService(jobs, events, policy, tx, clock);
    }

    @Bean
    MinHash minHash() {
        return new MinHash();
    }

    @Bean
    GroupingService groupingService(GroupRepository groups, MinHash minHash, PhoenixProperties properties, Clock clock) {
        return new GroupingService(groups, minHash, properties.dedupe().similarityThreshold(), clock);
    }

    @Bean
    CooldownGuard cooldownGuard(JobRepository jobs, ApplicationRepository applications, PhoenixProperties properties,
                                Clock clock) {
        return new CooldownGuard(jobs, applications, properties.dedupe().similarityThreshold(),
                properties.dedupe().companyCooldownDays(), clock);
    }

    @Bean
    ClaudeSessionChannel claudeSessionChannel() {
        return new ClaudeSessionChannel();
    }

    @Bean
    AskService askService(AskRepository asks, List<NotificationChannel> channels, TxRunner tx, Clock clock) {
        return new AskService(asks, channels, tx, clock);
    }

    @Bean
    ScoringService scoringService(JobRepository jobs, ScoreRepository scores, LifecycleService lifecycle, TxRunner tx,
                                  Clock clock) {
        return new ScoringService(jobs, scores, lifecycle, tx, clock);
    }

    @Bean
    QueueService queueService(JobRepository jobs, ScoreRepository scores) {
        return new QueueService(jobs, scores);
    }

    @Bean
    ApprovalService approvalService(JobRepository jobs, ApprovalRepository approvals, QueueService queue,
                                    CooldownGuard guard, LifecycleService lifecycle, TxRunner tx, Clock clock) {
        return new ApprovalService(jobs, approvals, queue, guard, lifecycle, tx, clock);
    }

    @Bean
    ApplicationService applicationService(JobRepository jobs, ApplicationRepository applications,
                                          ApprovalService approvals, CooldownGuard guard, LifecycleService lifecycle,
                                          AskService asks, TxRunner tx, Clock clock) {
        return new ApplicationService(jobs, applications, approvals, guard, lifecycle, asks, tx, clock);
    }

    @Bean
    JobQueryService jobQueryService(JobRepository jobs, ScoreRepository scores, ApplicationRepository applications,
                                    EventRepository events) {
        return new JobQueryService(jobs, scores, applications, events);
    }

    @Bean
    StatusService statusService(JobRepository jobs, AskRepository asks, RunRepository runs, Clock clock) {
        return new StatusService(jobs, asks, runs, clock);
    }
}
