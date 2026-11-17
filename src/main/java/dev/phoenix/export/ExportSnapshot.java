package dev.phoenix.export;

import dev.phoenix.domain.Application;
import dev.phoenix.domain.Job;
import dev.phoenix.domain.Score;
import dev.phoenix.service.QueueEntry;
import dev.phoenix.service.StatusReport;

import java.util.List;
import java.util.Map;

/** All the data the views are rendered from, read once so every file describes the same moment. */
public record ExportSnapshot(List<Job> jobs, Map<Long, Score> scores, Map<Long, Application> applications,
                             List<QueueEntry> queue, StatusReport status) {

    public ExportSnapshot {
        jobs = List.copyOf(jobs);
        scores = Map.copyOf(scores);
        applications = Map.copyOf(applications);
        queue = List.copyOf(queue);
    }
}
