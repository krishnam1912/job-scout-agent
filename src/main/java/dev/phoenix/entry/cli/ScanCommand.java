package dev.phoenix.entry.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.phoenix.discovery.Candidate;
import dev.phoenix.discovery.ScanRequest;
import dev.phoenix.discovery.ScanResult;
import dev.phoenix.discovery.ScanService;
import dev.phoenix.entry.JsonViews;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

@Component
@Command(name = "scan", mixinStandardHelpOptions = true,
        description = "Fetch the configured boards and print the best new candidates as JSON lines on stdout "
                + "(a summary goes to stderr).")
public class ScanCommand implements Callable<Integer> {

    private final ScanService scans;
    private final JsonViews views;
    private final ObjectMapper mapper;

    @Option(names = "--limit", defaultValue = "10", description = "Candidates to print (default ${DEFAULT-VALUE}).")
    int limit;

    @Option(names = "--company", description = "Only boards whose 'kind/board' name contains this text.")
    String company;

    @Option(names = "--dry-run", description = "Write nothing: no jobs, no events, no health records.")
    boolean dryRun;

    @Spec
    CommandSpec spec;

    public ScanCommand(ScanService scans, JsonViews views, ObjectMapper mapper) {
        this.scans = scans;
        this.views = views;
        this.mapper = mapper;
    }

    @Override
    public Integer call() throws JsonProcessingException {
        ScanResult result = scans.scan(new ScanRequest(limit, company, dryRun));
        PrintWriter out = spec.commandLine().getOut();
        for (Candidate c : result.candidates()) {
            out.println(mapper.writeValueAsString(views.candidate(c)));
        }
        out.flush();
        spec.commandLine().getErr().println("SUMMARY: " + mapper.writeValueAsString(views.scanStats(result.stats()))
                + " printed=" + result.candidates().size() + (dryRun ? " (dry run, nothing written)" : ""));
        return 0;
    }
}
