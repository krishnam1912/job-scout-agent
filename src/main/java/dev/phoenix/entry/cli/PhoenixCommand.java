package dev.phoenix.entry.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

/** Root of the command line; the real work is in the subcommands. */
@Component
@Command(name = "phoenix", mixinStandardHelpOptions = true, version = "phoenix 0.1.0",
        description = "Deterministic job-search engine: state, lifecycle, dedupe, discovery, MCP.",
        subcommands = {ImportLegacyCommand.class, ScanCommand.class, QueueCommand.class, JobCommand.class,
                TransitionCommand.class, StatusCommand.class, ExportCommand.class, AsksCommand.class,
                LlmTestCommand.class, McpCommand.class})
public class PhoenixCommand implements Callable<Integer> {

    /** Read before the application starts (it decides where the data lives); declared here so help lists it. */
    @Option(names = "--home", scope = ScopeType.INHERIT, paramLabel = "DIR",
            description = "Data directory (default: $PHOENIX_HOME, else ~/.phoenix).")
    String home;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getErr());
        return 1;
    }
}
