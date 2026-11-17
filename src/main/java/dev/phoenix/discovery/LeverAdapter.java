package dev.phoenix.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import dev.phoenix.domain.Posting;
import dev.phoenix.http.JsonFetcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Lever postings ({@code api.lever.co}). */
public final class LeverAdapter implements DiscoveryAdapter {

    public static final String KIND = "lever";

    private final JsonFetcher http;

    public LeverAdapter(JsonFetcher http) {
        this.http = http;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public List<Posting> fetch(String board) {
        JsonNode data = http.get("https://api.lever.co/v0/postings/" + board + "?mode=json");
        List<Posting> out = new ArrayList<>();
        if (data == null || !data.isArray()) {
            return out;
        }
        for (JsonNode job : data) {
            out.add(new Posting(KIND, board.toLowerCase(Locale.ROOT), Json.text(job, "id"), Json.text(job, "text"),
                    locations(job), Json.text(job, "hostedUrl"), description(job), false));
        }
        return out;
    }

    private static String locations(JsonNode job) {
        JsonNode categories = Json.object(job, "categories");
        List<String> all = Json.list(categories, "allLocations").stream().map(JsonNode::asText).toList();
        if (all.isEmpty()) {
            all = List.of(Json.text(categories, "location"));
        }
        return all.stream().filter(l -> !l.isEmpty()).collect(Collectors.joining("; "));
    }

    private static String description(JsonNode job) {
        String lists = Json.list(job, "lists").stream()
                .map(x -> Json.text(x, "text") + " " + HtmlText.strip(Json.text(x, "content")))
                .collect(Collectors.joining(" "));
        return HtmlText.collapse(String.join(" ", Json.text(job, "descriptionPlain"), lists,
                Json.text(job, "additionalPlain")));
    }
}
