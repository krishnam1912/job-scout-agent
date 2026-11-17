package dev.phoenix.testsupport;

import dev.phoenix.channel.AskService;
import dev.phoenix.channel.ClaudeSessionChannel;
import dev.phoenix.dedupe.CooldownGuard;
import dev.phoenix.service.ApplicationService;
import dev.phoenix.service.ApprovalExpiry;
import dev.phoenix.service.ApprovalService;
import dev.phoenix.service.JobQueryService;
import dev.phoenix.service.QueueService;
import dev.phoenix.service.ScoringService;
import dev.phoenix.service.StatusService;

import java.util.List;

/** The engine services wired over a {@link TestStore}, the way the application wires them. */
public final class Services {

    public final CooldownGuard guard;
    public final ScoringService scoring;
    public final QueueService queue;
    public final AskService asks;
    public final ApprovalService approvals;
    public final ApplicationService applications;
    public final StatusService status;
    public final JobQueryService jobQuery;

    public Services(TestStore s) {
        guard = new CooldownGuard(s.jobs, s.applications, 0.9, 60, s.clock);
        scoring = new ScoringService(s.jobs, s.scores, s.lifecycle, s.tx, s.clock);
        queue = new QueueService(s.jobs, s.scores, new ApprovalExpiry(s.jobs, s.approvals, s.lifecycle, s.tx, s.clock));
        asks = new AskService(s.asks, List.of(new ClaudeSessionChannel()), s.tx, s.clock);
        approvals = new ApprovalService(s.jobs, s.approvals, queue, guard, s.lifecycle, s.tx, s.clock);
        applications = new ApplicationService(s.jobs, s.applications, approvals, guard, s.lifecycle, asks, s.tx, s.clock);
        status = new StatusService(s.jobs, s.asks, s.runs, s.clock);
        jobQuery = new JobQueryService(s.jobs, s.scores, s.applications, s.events);
    }
}
