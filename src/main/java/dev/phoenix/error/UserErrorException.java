package dev.phoenix.error;

/** A problem the operator can fix (bad input, bad config, illegal request); maps to CLI exit code 1. */
public class UserErrorException extends PhoenixException {

    public UserErrorException(String message) {
        super(message);
    }

    public UserErrorException(String message, Throwable cause) {
        super(message, cause);
    }
}
