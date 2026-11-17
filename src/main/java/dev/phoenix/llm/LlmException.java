package dev.phoenix.llm;

import dev.phoenix.error.PhoenixException;

/** A model call failed: unreachable, rejected, or no reply that satisfied the requested schema. */
public class LlmException extends PhoenixException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
