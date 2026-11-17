package dev.phoenix.config;

import java.nio.file.Path;
import java.util.Map;

/**
 * The directory that holds all personal runtime data. The engine never reads personal data from anywhere else,
 * which keeps the repository free of it.
 */
public record PhoenixHome(Path root) {

    public static final String ENV_VAR = "PHOENIX_HOME";

    /** Resolution order: explicit {@code --home}, then {@code PHOENIX_HOME}, then {@code ~/.phoenix}. */
    public static PhoenixHome resolve(String explicit, Map<String, String> env, Path userHome) {
        if (explicit != null && !explicit.isBlank()) {
            return new PhoenixHome(Path.of(explicit).toAbsolutePath().normalize());
        }
        String fromEnv = env.get(ENV_VAR);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return new PhoenixHome(Path.of(fromEnv).toAbsolutePath().normalize());
        }
        return new PhoenixHome(userHome.resolve(".phoenix").toAbsolutePath().normalize());
    }

    public Path configFile() {
        return root.resolve("phoenix.yml");
    }

    public Path dbFile() {
        return root.resolve("state").resolve("phoenix.db");
    }

    public Path exportsDir() {
        return root.resolve("exports");
    }

    public Path logFile() {
        return root.resolve("logs").resolve("phoenix.log");
    }

    /** Relative paths in phoenix.yml are relative to the home; absolute ones are kept. */
    public Path resolvePath(String configured) {
        Path p = Path.of(configured);
        return p.isAbsolute() ? p.normalize() : root.resolve(p).normalize();
    }
}
