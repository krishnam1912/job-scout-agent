package dev.phoenix.http;

import java.util.Map;

/**
 * The only way the engine reaches the network. An interface so tests run against canned responses and a
 * different transport (proxy, retry wrapper) can be swapped in without touching adapters or providers.
 */
public interface HttpFetcher {

    /** @return the response body of a GET request */
    String get(String url);

    /** @return the response body of a POST request with a JSON body */
    String postJson(String url, Map<String, String> headers, String body);
}
