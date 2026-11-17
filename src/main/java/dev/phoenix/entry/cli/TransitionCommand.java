package dev.phoenix.entry.cli;

import dev.phoenix.domain.Job;
import dev.phoenix.domain.JobState;
import dev.phoenix.error.UserErrorException;
import dev.phoenix.lifecycle.LifecycleService;
import dev.phoenix.service.JobQueryService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.util.Locale;
import java.util.concurrent.Callable;

@Component
@Command(name = "transition", mixinStandardHelpOptions = true,
        description = "Move a job to another state if the lifecycle allows it (a manual correction, logged as actor 'cli').")
public class TransitionCommand implements Callable<Integer> {

    private static final String ACTOR = "cli";

    private final LifecycleService lifecycle;
    private final JobQueryService jobs;

    @Parameters(index = "0", paramLabel = "ID", description = "The job id.")
    long id;

    @Parameters(index = "1", paramLabel = "STATE", description = "Target state, e.g. SKIPPED.")
    String state;

    @Option(names = "--note", description = "Why, kept in the job's event log.")
    String note;

    @Spec
    CommandSpec spec;

    public TransitionCommand(LifecycleService lifecycle, JobQueryService jobs) {
        this.lifecycle = lifecycle;
        this.jobs = jobs;
    }

    @Override
    public Integer call() {
        JobState target = parse(state);
        JobState before = jobs.get(id).job().state();
        Job moved = lifecycle.transition(id, target, ACTOR, note);
        spec.commandLine().getOut().println("job " + moved.id() + ": " + before + " -> " + moved.state());
        return 0;
    }

    private static JobState parse(String text) {
        try {
            return JobState.valueOf(text.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new UserErrorException("Unknown state '" + text + "'");
        }
    }
}
