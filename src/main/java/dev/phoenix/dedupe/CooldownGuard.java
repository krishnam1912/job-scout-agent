package dev.phoenix.dedupe;

import dev.phoenix.domain.Application;
import dev.phoenix.domain.Job;
import dev.phoenix.store.ApplicationRepository;
import dev.phoenix.store.JobRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Layer 4: stops a second application to the same role. A job is blocked when another member of its group is
 * already approved or later, or when a very similar title at the same company was applied to recently.
 */
public class CooldownGuard {

    private final JobRepository jobs;
    private final ApplicationRepository applications;
    private final double titleThreshold;
    private final int cooldownDays;
    private final Clock clock;

    public CooldownGuard(JobRepository jobs, ApplicationRepository applications, double titleThreshold,
                         int cooldownDays, Clock clock) {
        this.jobs = jobs;
        this.applications = applications;
        this.titleThreshold = titleThreshold;
        this.cooldownDays = cooldownDays;
        this.clock = clock;
    }

    public GuardResult check(Job job) {
        if (job.groupId() != null) {
            for (Job member : jobs.findByGroup(job.groupId())) {
                if (member.id() != job.id() && member.state().isEngaged()) {
                    return new GuardResult(GuardVerdict.BLOCKED_BY_GROUP, member.id(),
                            "same role already " + member.state() + " as job " + member.id());
                }
            }
        }
        Instant cutoff = clock.instant().minus(Duration.ofDays(cooldownDays));
        for (Application a : applications.findSubmittedForCompany(job.company(), job.id())) {
            if (a.submittedAt().isBefore(cutoff)) {
                continue;
            }
            Job other = jobs.findById(a.jobId()).orElse(null);
            if (other != null && TitleText.similarity(other.title(), job.title()) >= titleThreshold) {
                return new GuardResult(GuardVerdict.BLOCKED_BY_COOLDOWN, other.id(),
                        "similar role applied to within " + cooldownDays + " days (job " + other.id() + ")");
            }
        }
        return GuardResult.ok();
    }
}
