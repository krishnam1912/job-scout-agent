package dev.phoenix.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import dev.phoenix.domain.Posting;
import dev.phoenix.http.JsonFetcher;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/** Atlassian's careers listing, one document with every opening. */
public final class AtlassianAdapter implements DiscoveryAdapter {

    public static final String KIND = "atlassian";

    private final JsonFetcher http;

    public AtlassianAdapter(JsonFetcher http) {
        this.http = http;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public List<Posting> fetch(String board) {
        JsonNode data = http.get("https://www.atlassian.com/endpoint/careers/listings");
        List<Posting> out = new ArrayList<>();
        if (data == null || !data.isArray()) {
            return out;
        }
        for (JsonNode job : data) {
            String portalUrl = Json.text(Json.object(job, "portalJobPost"), "portalUrl");
            out.add(new Posting(KIND, KIND, Json.text(job, "id"), Json.text(job, "title"), locations(job),
                    portalUrl.isEmpty() ? Json.text(job, "applyUrl") : portalUrl, description(job), false));
        }
        return out;
    }

    private static String locations(JsonNode job) {
        JsonNode locations = job.get("locations");
        if (locations == null || locations.isNull()) {
            return "";
        }
        if (locations.isTextual()) {
            return locations.asText();
        }
        return StreamSupport.stream(locations.spliterator(), false).map(JsonNode::asText).collect(Collectors.joining("; "));
    }

    private static String description(JsonNode job) {
        return String.join(" ", HtmlText.strip(Json.text(job, "overview")),
                HtmlText.strip(Json.text(job, "responsibilities")), HtmlText.strip(Json.text(job, "qualifications")));
    }
}
