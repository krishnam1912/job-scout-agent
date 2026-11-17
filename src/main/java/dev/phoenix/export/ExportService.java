package dev.phoenix.export;

import dev.phoenix.config.PhoenixHome;
import dev.phoenix.error.PhoenixException;
import dev.phoenix.service.QueueService;
import dev.phoenix.service.StatusService;
import dev.phoenix.store.ApplicationRepository;
import dev.phoenix.store.JobRepository;
import dev.phoenix.store.ScoreRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Writes the readable views into {@code $PHOENIX_HOME/exports/}. Nothing outside that folder is touched, and each
 * file is replaced atomically so a reader never sees a half-written view.
 */
public class ExportService {

    private static final int QUEUE_LIMIT = 1000;

    private final PhoenixHome home;
    private final JobRepository jobs;
    private final ScoreRepository scores;
    private final ApplicationRepository applications;
    private final QueueService queue;
    private final StatusService status;
    private final LeadsCsvExporter leads = new LeadsCsvExporter();
    private final ApplicationsMdExporter applicationsMd = new ApplicationsMdExporter();
    private final QueueMdExporter queueMd = new QueueMdExporter();
    private final StatusMdExporter statusMd = new StatusMdExporter();

    public ExportService(PhoenixHome home, JobRepository jobs, ScoreRepository scores,
                         ApplicationRepository applications, QueueService queue, StatusService status) {
        this.home = home;
        this.jobs = jobs;
        this.scores = scores;
        this.applications = applications;
        this.queue = queue;
        this.status = status;
    }

    /** @return the files written */
    public List<Path> export() {
        ExportSnapshot snapshot = new ExportSnapshot(jobs.findAll(), scores.latestForAll(),
                applications.findAll().stream().collect(Collectors.toMap(a -> a.jobId(), Function.identity())),
                queue.queue(null, QUEUE_LIMIT), status.report());
        Map<String, String> files = new LinkedHashMap<>();
        files.put("leads.csv", leads.render(snapshot));
        files.put("applications.md", applicationsMd.render(snapshot));
        files.put("STATUS.md", statusMd.render(snapshot.status()));
        files.put("queue.md", queueMd.render(snapshot));
        try {
            Files.createDirectories(home.exportsDir());
            for (Map.Entry<String, String> file : files.entrySet()) {
                write(home.exportsDir().resolve(file.getKey()), file.getValue());
            }
        } catch (IOException e) {
            throw new PhoenixException("Cannot write the exports to " + home.exportsDir() + ": " + e.getMessage(), e);
        }
        return files.keySet().stream().map(home.exportsDir()::resolve).toList();
    }

    private static void write(Path target, String content) throws IOException {
        Path temp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        Files.writeString(temp, content, StandardCharsets.UTF_8);
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
