package dev.phoenix.http;

import dev.phoenix.error.PhoenixException;

/** A request failed: a non-2xx status ({@code status} set), a timeout or a malformed response ({@code status} 0). */
public class HttpFetchException extends PhoenixException {

    private final int status;

    public HttpFetchException(String message, int status) {
        super(message);
        this.status = status;
    }

    public HttpFetchException(String message, Throwable cause) {
        super(message, cause);
        this.status = 0;
    }

    public int status() {
        return status;
    }
}
