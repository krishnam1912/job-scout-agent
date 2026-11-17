package dev.phoenix.error;

/** phoenix.yml or a file it references is missing or invalid; raised at startup so the run fails fast. */
public class ConfigException extends UserErrorException {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
