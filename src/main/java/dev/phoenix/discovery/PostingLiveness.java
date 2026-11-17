package dev.phoenix.discovery;

import dev.phoenix.domain.Job;
import dev.phoenix.domain.JobState;
import dev.phoenix.lifecycle.LifecycleService;
import dev.phoenix.store.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Closes postings that disappeared. A job is gone when it was missing from two successful scans of its board in a
 * row, i.e. it was last seen before the previous successful scan. Jobs already being applied to are never touched.
 */
public class PostingLiveness {

    private static final Logger log = LoggerFactory.getLogger(PostingLiveness.class);
    private static final List<JobState> CLOSABLE =
            Arrays.stream(JobState.values()).filter(JobState::isPreApplication).toList();

    private final JobRepository jobs;
    private final LifecycleService lifecycle;

    public PostingLiveness(JobRepository jobs, LifecycleService lifecycle) {
        this.jobs = jobs;
        this.lifecycle = lifecycle;
    }

    /** @return how many jobs were closed */
    public int closeVanished(String source, String company, Instant previousSuccessfulScan) {
        int closed = 0;
        for (Job job : jobs.findByBoardAndStates(source, company, CLOSABLE)) {
            if (job.lastSeen().isBefore(previousSuccessfulScan)) {
                lifecycle.transition(job.id(), JobState.CLOSED, "scanner", "posting gone");
                log.info("closed job {} ({}/{}): posting gone", job.id(), source, company);
                closed++;
            }
        }
        return closed;
    }
}
