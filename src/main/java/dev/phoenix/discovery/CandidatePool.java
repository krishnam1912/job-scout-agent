package dev.phoenix.discovery;

import dev.phoenix.domain.Job;
import dev.phoenix.domain.JobState;
import dev.phoenix.store.GroupRepository;
import dev.phoenix.store.JobRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** The stored candidates that are still waiting for a score, plus which groups must stay hidden. */
public class CandidatePool {

    private final JobRepository jobs;
    private final GroupRepository groups;

    public CandidatePool(JobRepository jobs, GroupRepository groups) {
        this.jobs = jobs;
        this.groups = groups;
    }

    List<CandidateView> existing() {
        List<CandidateView> pool = new ArrayList<>();
        for (Job job : jobs.findByStates(List.of(JobState.CANDIDATE))) {
            pool.add(new CandidateView(job.id(), job.groupId(), job.company(), job.extId(), job.title(),
                    orEmpty(job.location()), orEmpty(job.url()), job.prescore() == null ? 0 : job.prescore(),
                    orEmpty(job.jdText())));
        }
        return pool;
    }

    Set<Long> hiddenGroups() {
        return groups.engagedGroupIds();
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
