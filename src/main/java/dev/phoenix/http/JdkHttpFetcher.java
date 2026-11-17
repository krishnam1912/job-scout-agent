package dev.phoenix.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** {@link HttpFetcher} on the JDK's own HTTP client. Never logs headers, which may carry API keys. */
public final class JdkHttpFetcher implements HttpFetcher {

    public static final String USER_AGENT = "job-scout-agent/1.0";

    private final HttpClient client;
    private final Duration timeout;

    public JdkHttpFetcher(Duration timeout) {
        this.timeout = timeout;
        this.client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String get(String url) {
        HttpRequest request = base(url, Map.of("Accept", "application/json")).GET().build();
        return send(request, url);
    }

    @Override
    public String postJson(String url, Map<String, String> headers, String body) {
        HttpRequest.Builder builder = base(url, headers).header("Content-Type", "application/json");
        return send(builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(), url);
    }

    private HttpRequest.Builder base(String url, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", USER_AGENT);
        headers.forEach(builder::header);
        return builder;
    }

    private String send(HttpRequest request, String url) {
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            String body = new String(response.body(), StandardCharsets.UTF_8);
            if (response.statusCode() / 100 != 2) {
                throw new HttpFetchException("HTTP " + response.statusCode() + " from " + url, response.statusCode());
            }
            return body;
        } catch (IOException e) {
            throw new HttpFetchException("Request to " + url + " failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HttpFetchException("Request to " + url + " was interrupted", e);
        }
    }
}
