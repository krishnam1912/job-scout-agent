package dev.phoenix.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Fetches a URL and parses the body, so adapters deal with {@link JsonNode} and one error type. */
public final class JsonFetcher {

    private final HttpFetcher http;
    private final ObjectMapper mapper;

    public JsonFetcher(HttpFetcher http, ObjectMapper mapper) {
        this.http = http;
        this.mapper = mapper;
    }

    public JsonNode get(String url) {
        String body = http.get(url);
        try {
            return mapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new HttpFetchException("Invalid JSON from " + url + ": " + e.getOriginalMessage(), e);
        }
    }
}
