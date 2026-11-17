package dev.phoenix.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import dev.phoenix.domain.Posting;
import dev.phoenix.http.HttpFetchException;
import dev.phoenix.http.JsonFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * SmartRecruiters postings, restricted to one country by the API. The listing carries no description, so a detail
 * call is made only for titles that pass the title filter, capped per company.
 */
public final class SmartRecruitersAdapter implements DiscoveryAdapter {

    public static final String KIND = "smartrecruiters";
    static final int DETAIL_CAP = 25;
    private static final int PAGE_SIZE = 100;

    private static final Logger log = LoggerFactory.getLogger(SmartRecruitersAdapter.class);

    private final JsonFetcher http;
    private final String country;
    private final Predicate<String> titleOk;

    public SmartRecruitersAdapter(JsonFetcher http, String country, Predicate<String> titleOk) {
        this.http = http;
        this.country = country;
        this.titleOk = titleOk;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public List<Posting> fetch(String board) {
        List<Posting> out = new ArrayList<>();
        int details = 0;
        for (JsonNode p : listings(board)) {
            String title = Json.text(p, "name");
            if (!titleOk.test(title)) {
                continue;
            }
            String id = Json.text(p, "id");
            String jd = "";
            if (details < DETAIL_CAP) {
                details++;
                jd = detail(board, id);
            }
            JsonNode location = Json.object(p, "location");
            String place = Json.text(location, "fullLocation");
            out.add(new Posting(KIND, board.toLowerCase(Locale.ROOT), id, title,
                    place.isEmpty() ? Json.text(location, "city") : place,
                    "https://jobs.smartrecruiters.com/" + board + "/" + id, jd, true));
        }
        return out;
    }

    private List<JsonNode> listings(String board) {
        List<JsonNode> postings = new ArrayList<>();
        String encodedCountry = URLEncoder.encode(country, StandardCharsets.UTF_8);
        int offset = 0;
        while (true) {
            JsonNode page = http.get("https://api.smartrecruiters.com/v1/companies/" + board
                    + "/postings?country=" + encodedCountry + "&limit=" + PAGE_SIZE + "&offset=" + offset);
            List<JsonNode> content = Json.list(page, "content");
            postings.addAll(content);
            offset += PAGE_SIZE;
            if (content.isEmpty() || offset >= page.path("totalFound").asInt(0)) {
                return postings;
            }
        }
    }

    /** A failed detail call keeps the posting with an empty description instead of dropping it. */
    private String detail(String board, String id) {
        try {
            JsonNode detail = http.get("https://api.smartrecruiters.com/v1/companies/" + board + "/postings/" + id);
            JsonNode sections = Json.object(Json.object(detail, "jobAd"), "sections");
            if (sections == null) {
                return "";
            }
            return StreamSupport.stream(sections.spliterator(), false)
                    .map(s -> HtmlText.strip(Json.text(s, "text")))
                    .collect(Collectors.joining(" "));
        } catch (HttpFetchException e) {
            log.warn("smartrecruiters/{}: detail {} failed ({})", board, id, e.getMessage());
            return "";
        }
    }
}
