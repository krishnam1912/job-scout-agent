package dev.phoenix.entry.cli;

import dev.phoenix.service.QueueEntry;
import dev.phoenix.service.QueueService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.Callable;

@Component
@Command(name = "queue", mixinStandardHelpOptions = true, description = "Show the approval queue, best fit first.")
public class QueueCommand implements Callable<Integer> {

    private final QueueService queue;

    @Option(names = "--min-fit", description = "Only jobs with at least this fit percent.")
    Integer minFit;

    @Option(names = "--limit", defaultValue = "10", description = "Rows to show (default ${DEFAULT-VALUE}).")
    int limit;

    @Spec
    CommandSpec spec;

    public QueueCommand(QueueService queue) {
        this.queue = queue;
    }

    @Override
    public Integer call() {
        List<QueueEntry> entries = queue.queue(minFit, limit);
        PrintWriter out = spec.commandLine().getOut();
        if (entries.isEmpty()) {
            out.println("Nothing is queued.");
        }
        int n = 0;
        for (QueueEntry e : entries) {
            out.printf("%2d. %3d%%  job %-5d %s: %s (%s) %s%n", ++n, e.fitPct(), e.job().id(), e.job().company(),
                    e.job().title(), e.job().location() == null ? "-" : e.job().location(), e.job().url());
        }
        return 0;
    }
}
