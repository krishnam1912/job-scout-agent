package dev.phoenix.entry.cli;

import dev.phoenix.export.StatusMdExporter;
import dev.phoenix.service.StatusService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

@Component
@Command(name = "status", mixinStandardHelpOptions = true, description = "Pipeline status: counts, open asks, jobs at risk.")
public class StatusCommand implements Callable<Integer> {

    private final StatusService status;
    private final StatusMdExporter renderer = new StatusMdExporter();

    @Spec
    CommandSpec spec;

    public StatusCommand(StatusService status) {
        this.status = status;
    }

    @Override
    public Integer call() {
        spec.commandLine().getOut().print(renderer.render(status.report()));
        return 0;
    }
}
