package dev.phoenix.entry.cli;

import dev.phoenix.error.UserErrorException;
import dev.phoenix.store.Migrations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.io.PrintWriter;

/**
 * Runs the picocli command tree with Spring-built commands and maps outcomes to exit codes: 0 ok, 1 a problem the
 * user can fix (bad input, bad config, an illegal request), 2 an unexpected failure.
 */
@Component
public class PhoenixCli {

    public static final int EXIT_OK = 0;
    public static final int EXIT_USER_ERROR = 1;
    public static final int EXIT_RUNTIME_ERROR = 2;

    private static final Logger log = LoggerFactory.getLogger(PhoenixCli.class);

    private final BeanFactory beans;
    private final Migrations migrations;

    public PhoenixCli(BeanFactory beans, Migrations migrations) {
        this.beans = beans;
        this.migrations = migrations;
    }

    public int run(PrintWriter out, PrintWriter err, String... args) {
        CommandLine cli = new CommandLine(PhoenixCommand.class, this::create);
        cli.setOut(out);
        cli.setErr(err);
        cli.getCommandSpec().exitCodeOnInvalidInput(EXIT_USER_ERROR);
        cli.getCommandSpec().exitCodeOnExecutionException(EXIT_RUNTIME_ERROR);
        cli.setExecutionExceptionHandler((e, commandLine, parseResult) -> handle(e, err));
        migrations.migrate();
        int exit = cli.execute(args);
        out.flush();
        err.flush();
        return exit;
    }

    private int handle(Exception e, PrintWriter err) {
        if (e instanceof UserErrorException) {
            err.println(e.getMessage());
            return EXIT_USER_ERROR;
        }
        log.error("Command failed", e);
        err.println("Unexpected failure: " + e.getClass().getSimpleName() + ": " + e.getMessage()
                + " (details are in the phoenix log)");
        return EXIT_RUNTIME_ERROR;
    }

    private <K> K create(Class<K> type) throws Exception {
        try {
            return beans.getBean(type);
        } catch (NoSuchBeanDefinitionException e) {
            return CommandLine.defaultFactory().create(type);
        }
    }
}
