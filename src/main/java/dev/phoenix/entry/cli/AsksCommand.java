package dev.phoenix.entry.cli;

import dev.phoenix.channel.AskService;
import dev.phoenix.domain.Ask;
import dev.phoenix.domain.AskStatus;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.Callable;

@Component
@Command(name = "asks", mixinStandardHelpOptions = true, description = "List the asks inbox (open and offered by default).")
public class AsksCommand implements Callable<Integer> {

    private final AskService asks;

    @Option(names = "--status", description = "Only asks with this status: ${COMPLETION-CANDIDATES}.")
    AskStatus status;

    @Spec
    CommandSpec spec;

    public AsksCommand(AskService asks) {
        this.asks = asks;
    }

    @Override
    public Integer call() {
        List<Ask> found = asks.list(status == null ? List.of() : List.of(status));
        PrintWriter out = spec.commandLine().getOut();
        if (found.isEmpty()) {
            out.println("No asks.");
        }
        found.forEach(a -> out.printf("#%d [%s, %s] %s%s%n", a.id(), a.kind(), a.status(), a.prompt(),
                a.jobId() == null ? "" : " (job " + a.jobId() + ")"));
        return 0;
    }
}
