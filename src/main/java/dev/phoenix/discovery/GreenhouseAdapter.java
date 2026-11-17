package dev.phoenix.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import dev.phoenix.domain.Posting;
import dev.phoenix.http.JsonFetcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Greenhouse job boards ({@code boards-api.greenhouse.io}); descriptions arrive as escaped HTML. */
public final class GreenhouseAdapter implements DiscoveryAdapter {

    public static final String KIND = "greenhouse";

    private final JsonFetcher http;

    public GreenhouseAdapter(JsonFetcher http) {
        this.http = http;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public List<Posting> fetch(String board) {
        JsonNode data = http.get("https://boards-api.greenhouse.io/v1/boards/" + board + "/jobs?content=true");
        List<Posting> out = new ArrayList<>();
        for (JsonNode job : Json.list(data, "jobs")) {
            out.add(new Posting(KIND, board.toLowerCase(Locale.ROOT), Json.text(job, "id"), Json.text(job, "title"),
                    Json.text(Json.object(job, "location"), "name"), Json.text(job, "absolute_url"),
                    HtmlText.strip(Json.text(job, "content")), false));
        }
        return out;
    }
}
