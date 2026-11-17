package dev.phoenix.entry.cli;

import dev.phoenix.legacy.ImportReport;
import dev.phoenix.legacy.LegacyImporter;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

@Component
@Command(name = "import-legacy", mixinStandardHelpOptions = true,
        description = "Import the old CSV/Markdown files named in phoenix.yml (read-only on them, safe to repeat). "
                + "Exits 1 when any row could not be imported.")
public class ImportLegacyCommand implements Callable<Integer> {

    private final LegacyImporter importer;

    @Spec
    CommandSpec spec;

    public ImportLegacyCommand(LegacyImporter importer) {
        this.importer = importer;
    }

    @Override
    public Integer call() {
        ImportReport report = importer.run();
        spec.commandLine().getOut().print(report.render());
        return report.clean() ? 0 : 1;
    }
}
