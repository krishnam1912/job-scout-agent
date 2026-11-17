package dev.phoenix.testsupport;

import dev.phoenix.http.HttpFetchException;
import dev.phoenix.http.HttpFetcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Canned HTTP responses by exact URL (GET) so unit tests never touch the network. */
public final class FakeHttpFetcher implements HttpFetcher {

    private final Map<String, String> responses = new HashMap<>();
    private final Map<String, Integer> failures = new HashMap<>();
    private final List<String> requests = new ArrayList<>();
    private final List<String> postBodies = new ArrayList<>();
    private final List<Map<String, String>> postHeaders = new ArrayList<>();
    private final List<String> postResponses = new ArrayList<>();
    private final List<Integer> postFailures = new ArrayList<>();

    public FakeHttpFetcher on(String url, String body) {
        responses.put(url, body);
        return this;
    }

    public FakeHttpFetcher failOn(String url, int status) {
        failures.put(url, status);
        return this;
    }

    /** Queues the response of the next POST; responses are consumed in order. */
    public FakeHttpFetcher thenPost(String body) {
        postResponses.add(body);
        postFailures.add(0);
        return this;
    }

    public FakeHttpFetcher thenPostFails(int status) {
        postResponses.add("");
        postFailures.add(status);
        return this;
    }

    public List<String> requests() {
        return requests;
    }

    public List<String> postBodies() {
        return postBodies;
    }

    public List<Map<String, String>> postHeaders() {
        return postHeaders;
    }

    @Override
    public String get(String url) {
        requests.add(url);
        if (failures.containsKey(url)) {
            throw new HttpFetchException("HTTP " + failures.get(url) + " from " + url, failures.get(url));
        }
        String body = responses.get(url);
        if (body == null) {
            throw new HttpFetchException("no canned response for " + url, 404);
        }
        return body;
    }

    @Override
    public String postJson(String url, Map<String, String> headers, String body) {
        requests.add(url);
        postBodies.add(body);
        postHeaders.add(headers);
        if (postResponses.isEmpty()) {
            throw new HttpFetchException("no canned POST response for " + url, 404);
        }
        String response = postResponses.remove(0);
        int failure = postFailures.remove(0);
        if (failure != 0) {
            throw new HttpFetchException("HTTP " + failure + " from " + url, failure);
        }
        return response;
    }
}
