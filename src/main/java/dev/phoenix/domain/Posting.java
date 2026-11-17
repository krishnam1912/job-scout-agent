package dev.phoenix.domain;

/**
 * A job as fetched from a source, before it has an identity in the database. {@code locationVerified} means the
 * source already restricted the country, so the location filter is skipped.
 */
public record Posting(String source, String company, String jobId, String title, String location, String url,
                      String jd, boolean locationVerified) {

    public Posting {
        title = title == null ? "" : title;
        location = location == null ? "" : location;
        url = url == null ? "" : url;
        jd = jd == null ? "" : jd;
    }
}
