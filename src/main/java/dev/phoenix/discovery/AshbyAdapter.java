package dev.phoenix.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import dev.phoenix.domain.Posting;
import dev.phoenix.http.JsonFetcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Ashby job boards ({@code api.ashbyhq.com}); unlisted postings are skipped. */
public final class AshbyAdapter implements DiscoveryAdapter {

    public static final String KIND = "ashby";

    private final JsonFetcher http;

    public AshbyAdapter(JsonFetcher http) {
        this.http = http;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public List<Posting> fetch(String board) {
        JsonNode data = http.get("https://api.ashbyhq.com/posting-api/job-board/" + board);
        List<Posting> out = new ArrayList<>();
        for (JsonNode job : Json.list(data, "jobs")) {
            JsonNode listed = job.get("isListed");
            if (listed != null && listed.isBoolean() && !listed.asBoolean()) {
                continue;
            }
            out.add(new Posting(KIND, board.toLowerCase(Locale.ROOT), Json.text(job, "id"), Json.text(job, "title"),
                    locations(job), Json.text(job, "jobUrl"), description(job), false));
        }
        return out;
    }

    private static String locations(JsonNode job) {
        List<String> locations = new ArrayList<>();
        locations.add(Json.text(job, "location"));
        Json.list(job, "secondaryLocations").forEach(s -> locations.add(Json.text(s, "location")));
        if (Json.truthy(job, "isRemote")) {
            locations.add("Remote");
        }
        return locations.stream().filter(l -> !l.isEmpty()).collect(Collectors.joining("; "));
    }

    private static String description(JsonNode job) {
        String plain = Json.text(job, "descriptionPlain");
        return HtmlText.collapse(plain.isEmpty() ? HtmlText.strip(Json.text(job, "descriptionHtml")) : plain);
    }
}
