package dev.phoenix.service;

import dev.phoenix.domain.Job;
import dev.phoenix.error.NotFoundException;
import dev.phoenix.store.ApplicationRepository;
import dev.phoenix.store.EventRepository;
import dev.phoenix.store.JobRepository;
import dev.phoenix.store.ScoreRepository;

import java.util.List;

/** Read-only view of one job with its history, for the operator and the CLI. */
public class JobQueryService {

    private final JobRepository jobs;
    private final ScoreRepository scores;
    private final ApplicationRepository applications;
    private final EventRepository events;

    public JobQueryService(JobRepository jobs, ScoreRepository scores, ApplicationRepository applications,
                           EventRepository events) {
        this.jobs = jobs;
        this.scores = scores;
        this.applications = applications;
        this.events = events;
    }

    public JobDetail get(long jobId) {
        Job job = jobs.findById(jobId).orElseThrow(() -> new NotFoundException("Job", jobId));
        List<Job> siblings = job.groupId() == null ? List.of()
                : jobs.findByGroup(job.groupId()).stream().filter(j -> j.id() != jobId).toList();
        return new JobDetail(job, scores.latestFor(jobId).orElse(null), applications.findByJob(jobId).orElse(null),
                events.findByJob(jobId), siblings);
    }
}
