package dev.phoenix.discovery;

import dev.phoenix.dedupe.ExactKey;
import dev.phoenix.discovery.PostingEvaluator.Evaluated;
import dev.phoenix.domain.Job;
import dev.phoenix.domain.Posting;
import dev.phoenix.store.JobRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Layer 1 of dedupe on the way in: a posting is known when the same company and id exist from any source (a
 * legacy row and a scanned row are the same posting), or when a long id (7+ characters) exists at all, which is
 * how scan.py's seen-list behaved. Known postings are not re-evaluated; new ones get their filter verdict.
 */
public class ScanClassifier {

    private static final int BARE_ID_MIN_LENGTH = 7;

    private final JobRepository jobs;

    public ScanClassifier(JobRepository jobs) {
        this.jobs = jobs;
    }

    Classification classify(List<BoardResult> results, PostingEvaluator evaluator) {
        Set<ExactKey> inThisScan = new HashSet<>();
        List<Job> known = new ArrayList<>();
        List<Evaluated> fresh = new ArrayList<>();
        int fetched = 0;
        for (BoardResult result : results) {
            for (Posting posting : result.postings()) {
                fetched++;
                if (!inThisScan.add(ExactKey.of(posting.company(), posting.jobId()))) {
                    continue;
                }
                Optional<Job> existing = find(posting);
                if (existing.isPresent()) {
                    known.add(existing.get());
                } else {
                    fresh.add(evaluator.evaluate(posting));
                }
            }
        }
        return new Classification(fetched, known, fresh);
    }

    private Optional<Job> find(Posting posting) {
        String company = ExactKey.normalizeCompany(posting.company());
        Optional<Job> sameCompany = jobs.findByCompanyAndExtId(company, posting.jobId()).stream().findFirst();
        if (sameCompany.isPresent() || posting.jobId().length() < BARE_ID_MIN_LENGTH) {
            return sameCompany;
        }
        return jobs.findByExtId(posting.jobId()).stream().findFirst();
    }
}
