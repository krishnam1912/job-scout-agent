package dev.phoenix.entry.cli;

import dev.phoenix.export.ExportService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

@Component
@Command(name = "export", mixinStandardHelpOptions = true,
        description = "Write leads.csv, applications.md, STATUS.md and queue.md into $PHOENIX_HOME/exports/.")
public class ExportCommand implements Callable<Integer> {

    private final ExportService exports;

    @Spec
    CommandSpec spec;

    public ExportCommand(ExportService exports) {
        this.exports = exports;
    }

    @Override
    public Integer call() {
        exports.export().forEach(f -> spec.commandLine().getOut().println(f));
        return 0;
    }
}
