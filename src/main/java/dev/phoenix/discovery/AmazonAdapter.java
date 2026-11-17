package dev.phoenix.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import dev.phoenix.domain.Posting;
import dev.phoenix.http.JsonFetcher;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Amazon's own search API; each configured query is paged through and requisitions are de-duplicated by id. */
public final class AmazonAdapter implements DiscoveryAdapter {

    public static final String KIND = "amazon";

    private final JsonFetcher http;
    private final FilterConfig.AmazonConfig config;

    public AmazonAdapter(JsonFetcher http, FilterConfig.AmazonConfig config) {
        this.http = http;
        this.config = config;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public List<Posting> fetch(String board) {
        List<Posting> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode job : search()) {
            String icims = Json.text(job, "id_icims");
            String id = icims.isEmpty() ? Json.text(job, "id") : icims;
            if (!seen.add(id)) {
                continue;
            }
            String path = Json.text(job, "job_path");
            String location = Json.text(job, "normalized_location");
            out.add(new Posting(KIND, KIND, id, Json.text(job, "title"),
                    location.isEmpty() ? Json.text(job, "location") : location,
                    "https://www.amazon.jobs" + (path.isEmpty() ? "/en/jobs/" + id : path), description(job), true));
        }
        return out;
    }

    /** Pages through every query; a query can have more hits than one page holds. */
    private List<JsonNode> search() {
        List<JsonNode> jobs = new ArrayList<>();
        for (String query : config.queries()) {
            int offset = 0;
            while (offset < config.maxResults()) {
                JsonNode data = http.get("https://www.amazon.jobs/en/search.json?base_query="
                        + URLEncoder.encode(query, StandardCharsets.UTF_8)
                        + "&country=" + URLEncoder.encode(config.country(), StandardCharsets.UTF_8)
                        + "&result_limit=" + config.resultLimit() + "&offset=" + offset + "&sort=recent");
                List<JsonNode> page = Json.list(data, "jobs");
                jobs.addAll(page);
                offset += config.resultLimit();
                if (page.isEmpty() || offset >= data.path("hits").asInt(0)) {
                    break;
                }
            }
        }
        return jobs;
    }

    private static String description(JsonNode job) {
        List<String> parts = new ArrayList<>();
        addPart(parts, "", Json.text(job, "description"));
        addPart(parts, "Basic qualifications:", Json.text(job, "basic_qualifications"));
        addPart(parts, "Preferred qualifications:", Json.text(job, "preferred_qualifications"));
        return String.join(" ", parts);
    }

    private static void addPart(List<String> parts, String label, String html) {
        String text = HtmlText.strip(html);
        if (!text.isEmpty() && !text.equalsIgnoreCase("null") && !text.equalsIgnoreCase("none")) {
            parts.add((label + " " + text).strip());
        }
    }
}
