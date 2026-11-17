package dev.phoenix.entry.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.phoenix.entry.JsonViews;
import dev.phoenix.service.JobQueryService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

@Component
@Command(name = "job", mixinStandardHelpOptions = true, description = "Show everything known about one job, as JSON.")
public class JobCommand implements Callable<Integer> {

    private final JobQueryService jobs;
    private final JsonViews views;
    private final ObjectMapper mapper;

    @Parameters(index = "0", paramLabel = "ID", description = "The job id.")
    long id;

    @Spec
    CommandSpec spec;

    public JobCommand(JobQueryService jobs, JsonViews views, ObjectMapper mapper) {
        this.jobs = jobs;
        this.views = views;
        this.mapper = mapper;
    }

    @Override
    public Integer call() throws JsonProcessingException {
        spec.commandLine().getOut().println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(views.job(jobs.get(id))));
        return 0;
    }
}
