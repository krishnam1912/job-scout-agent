package dev.phoenix.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.phoenix.domain.Posting;
import dev.phoenix.http.JsonFetcher;
import dev.phoenix.testsupport.FakeHttpFetcher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptersTest {

    private final FakeHttpFetcher http = new FakeHttpFetcher();
    private final JsonFetcher json = new JsonFetcher(http, new ObjectMapper());

    @Test
    void greenhouseMapsPostingsAndUnescapesTheDescription() {
        http.on("https://boards-api.greenhouse.io/v1/boards/AcmeCo/jobs?content=true", """
                {"jobs": [{"id": 101, "title": "Backend Engineer", "location": {"name": "Pune, India"},
                  "absolute_url": "https://boards.example.com/acme/101",
                  "content": "&lt;p&gt;Java &amp;amp; Kafka&lt;/p&gt;"},
                 {"id": 102, "title": "Other"}]}""");

        List<Posting> postings = new GreenhouseAdapter(json).fetch("AcmeCo");

        assertThat(postings).hasSize(2);
        assertThat(postings.get(0)).isEqualTo(new Posting("greenhouse", "acmeco", "101", "Backend Engineer",
                "Pune, India", "https://boards.example.com/acme/101", "Java & Kafka", false));
        assertThat(postings.get(1).location()).isEmpty();
        assertThat(postings.get(1).jd()).isEmpty();
    }

    @Test
    void leverJoinsLocationsAndDescriptionParts() {
        http.on("https://api.lever.co/v0/postings/acme?mode=json", """
                [{"id": "abc-1", "text": "Platform Engineer", "hostedUrl": "https://jobs.example.com/acme/abc-1",
                  "categories": {"allLocations": ["Pune", "Remote"], "location": "Pune"},
                  "descriptionPlain": "Build  things.", "additionalPlain": "Perks.",
                  "lists": [{"text": "You have", "content": "<li>Java</li>"}]},
                 {"id": "abc-2", "text": "Other", "categories": {"location": "Bengaluru"}}]""");

        List<Posting> postings = new LeverAdapter(json).fetch("acme");

        assertThat(postings.get(0).location()).isEqualTo("Pune; Remote");
        assertThat(postings.get(0).jd()).isEqualTo("Build things. You have Java Perks.");
        assertThat(postings.get(1).location()).isEqualTo("Bengaluru");
    }

    @Test
    void ashbySkipsUnlistedPostingsAndAddsRemote() {
        http.on("https://api.ashbyhq.com/posting-api/job-board/acme", """
                {"jobs": [
                  {"id": "j1", "title": "Backend", "location": "Pune", "secondaryLocations": [{"location": "Delhi"}],
                   "isRemote": true, "jobUrl": "https://jobs.example.com/j1", "descriptionPlain": "Java"},
                  {"id": "j2", "title": "Hidden", "isListed": false},
                  {"id": "j3", "title": "Html only", "location": "Pune", "descriptionHtml": "<p>Kotlin</p>"}]}""");

        List<Posting> postings = new AshbyAdapter(json).fetch("acme");

        assertThat(postings).extracting(Posting::jobId).containsExactly("j1", "j3");
        assertThat(postings.get(0).location()).isEqualTo("Pune; Delhi; Remote");
        assertThat(postings.get(1).jd()).isEqualTo("Kotlin");
    }

    @Test
    void smartRecruitersPagesFetchesDetailsOnlyForGoodTitlesAndMarksTheLocationVerified() {
        http.on("https://api.smartrecruiters.com/v1/companies/Acme/postings?country=in&limit=100&offset=0", """
                {"totalFound": 150, "content": [
                  {"id": "1", "name": "Backend Engineer", "location": {"fullLocation": "Pune, India"}},
                  {"id": "2", "name": "Sales Manager", "location": {"city": "Pune"}}]}""");
        http.on("https://api.smartrecruiters.com/v1/companies/Acme/postings?country=in&limit=100&offset=100", """
                {"totalFound": 150, "content": [{"id": "3", "name": "Backend Engineer II", "location": {"city": "Chennai"}}]}""");
        http.on("https://api.smartrecruiters.com/v1/companies/Acme/postings/1",
                "{\"jobAd\": {\"sections\": {\"a\": {\"text\": \"<p>Java</p>\"}, \"b\": {\"text\": \"Kafka\"}}}}");
        http.failOn("https://api.smartrecruiters.com/v1/companies/Acme/postings/3", 500);

        List<Posting> postings = new SmartRecruitersAdapter(json, "in", t -> t.contains("Backend")).fetch("Acme");

        assertThat(postings).extracting(Posting::jobId).containsExactly("1", "3");
        assertThat(postings.get(0).jd()).isEqualTo("Java Kafka");
        assertThat(postings.get(0).location()).isEqualTo("Pune, India");
        assertThat(postings.get(0).locationVerified()).isTrue();
        assertThat(postings.get(0).url()).isEqualTo("https://jobs.smartrecruiters.com/Acme/1");
        assertThat(postings.get(1).jd()).as("a failed detail call keeps the posting").isEmpty();
        assertThat(postings.get(1).location()).isEqualTo("Chennai");
        assertThat(http.requests()).doesNotContain("https://api.smartrecruiters.com/v1/companies/Acme/postings/2");
    }

    @Test
    void smartRecruitersStopsFetchingDetailsAtTheCap() {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < SmartRecruitersAdapter.DETAIL_CAP + 3; i++) {
            content.append(i > 0 ? "," : "").append("{\"id\": \"").append(i).append("\", \"name\": \"Backend\"}");
            http.on("https://api.smartrecruiters.com/v1/companies/Acme/postings/" + i, "{}");
        }
        http.on("https://api.smartrecruiters.com/v1/companies/Acme/postings?country=in&limit=100&offset=0",
                "{\"totalFound\": 28, \"content\": [" + content + "]}");

        List<Posting> postings = new SmartRecruitersAdapter(json, "in", t -> true).fetch("Acme");

        assertThat(postings).hasSize(SmartRecruitersAdapter.DETAIL_CAP + 3);
        long detailCalls = http.requests().stream().filter(u -> u.matches(".*/postings/\\d+$")).count();
        assertThat(detailCalls).isEqualTo(SmartRecruitersAdapter.DETAIL_CAP);
    }

    @Test
    void amazonPagesEveryQueryDeduplicatesAndBuildsTheDescription() {
        FilterConfig.AmazonConfig config = new FilterConfig.AmazonConfig("IND", List.of("sde backend", "sde platform"), 2, 4);
        String base = "https://www.amazon.jobs/en/search.json?base_query=";
        http.on(base + "sde+backend&country=IND&result_limit=2&offset=0&sort=recent", """
                {"hits": 3, "jobs": [
                  {"id_icims": "9001", "title": "SDE II", "normalized_location": "Bengaluru, KA, IND",
                   "job_path": "/en/jobs/9001/sde-ii", "description": "<b>Build</b>",
                   "basic_qualifications": "Java", "preferred_qualifications": "null"},
                  {"id": "9002", "title": "SDE I", "location": "Pune", "description": "None"}]}""");
        http.on(base + "sde+backend&country=IND&result_limit=2&offset=2&sort=recent", """
                {"hits": 3, "jobs": [{"id_icims": "9003", "title": "SDE III", "location": "Hyderabad"}]}""");
        http.on(base + "sde+platform&country=IND&result_limit=2&offset=0&sort=recent", """
                {"hits": 1, "jobs": [{"id_icims": "9001", "title": "SDE II duplicate"}]}""");

        List<Posting> postings = new AmazonAdapter(json, config).fetch("amazon");

        assertThat(postings).extracting(Posting::jobId).containsExactly("9001", "9002", "9003");
        assertThat(postings.get(0).jd()).isEqualTo("Build Basic qualifications: Java");
        assertThat(postings.get(0).url()).isEqualTo("https://www.amazon.jobs/en/jobs/9001/sde-ii");
        assertThat(postings.get(0).location()).isEqualTo("Bengaluru, KA, IND");
        assertThat(postings.get(1).url()).isEqualTo("https://www.amazon.jobs/en/jobs/9002");
        assertThat(postings.get(1).jd()).isEmpty();
        assertThat(postings).allMatch(p -> p.company().equals("amazon") && p.locationVerified());
    }

    @Test
    void atlassianAcceptsLocationsAsListOrString() {
        http.on("https://www.atlassian.com/endpoint/careers/listings", """
                [{"id": 11, "title": "Backend Engineer", "locations": ["Bengaluru", "Remote"],
                  "portalJobPost": {"portalUrl": "https://careers.example.com/11"},
                  "overview": "<p>Intro</p>", "responsibilities": "Do", "qualifications": ""},
                 {"id": 12, "title": "Other", "locations": "Sydney", "applyUrl": "https://apply.example.com/12"}]""");

        List<Posting> postings = new AtlassianAdapter(json).fetch("atlassian");

        assertThat(postings.get(0).location()).isEqualTo("Bengaluru; Remote");
        assertThat(postings.get(0).url()).isEqualTo("https://careers.example.com/11");
        assertThat(postings.get(0).jd()).isEqualTo("Intro Do ");
        assertThat(postings.get(1).location()).isEqualTo("Sydney");
        assertThat(postings.get(1).url()).isEqualTo("https://apply.example.com/12");
        assertThat(postings.get(0).company()).isEqualTo("atlassian");
    }
}
