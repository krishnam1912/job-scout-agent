package dev.phoenix.error;

/** Root of every error the engine raises on purpose, so entry points can map them to exit codes and tool errors. */
public class PhoenixException extends RuntimeException {

    public PhoenixException(String message) {
        super(message);
    }

    public PhoenixException(String message, Throwable cause) {
        super(message, cause);
    }
}
