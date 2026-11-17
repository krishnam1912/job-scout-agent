package dev.phoenix;

import dev.phoenix.config.PhoenixConfigLoader;
import dev.phoenix.config.PhoenixHome;
import dev.phoenix.config.PhoenixProperties;
import dev.phoenix.entry.cli.PhoenixCli;
import dev.phoenix.error.UserErrorException;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Map;

/**
 * Entry point for both modes: {@code phoenix <command>} runs a CLI command and {@code phoenix mcp} serves MCP over
 * stdio. The data directory and phoenix.yml are resolved and validated before Spring starts, so a bad setup fails
 * fast with one clear line instead of a stack trace. Nothing is written to stdout here.
 */
@SpringBootApplication
public class PhoenixApplication {

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        PrintWriter err = new PrintWriter(System.err, true);
        PhoenixHome home = PhoenixHome.resolve(HomeArgument.find(args), System.getenv(),
                Path.of(System.getProperty("user.home")));
        PhoenixProperties properties;
        try {
            properties = new PhoenixConfigLoader().load(home);
        } catch (UserErrorException e) {
            err.println(e.getMessage());
            return PhoenixCli.EXIT_USER_ERROR;
        }
        SpringApplication app = new SpringApplication(PhoenixApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        app.setLazyInitialization(true);
        app.setDefaultProperties(Map.of("phoenix.log-file", home.logFile().toString(), "spring.main.register-shutdown-hook", "true"));
        app.addInitializers(context -> {
            context.getBeanFactory().registerSingleton("phoenixHome", home);
            context.getBeanFactory().registerSingleton("phoenixProperties", properties);
        });
        try (ConfigurableApplicationContext context = app.run()) {
            return context.getBean(PhoenixCli.class).run(new PrintWriter(System.out, true), err, args);
        }
    }
}
