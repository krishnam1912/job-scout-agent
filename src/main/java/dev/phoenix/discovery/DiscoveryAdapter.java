package dev.phoenix.discovery;

import dev.phoenix.domain.Posting;

import java.util.List;

/**
 * One kind of public job feed. An adapter turns a board token into postings and nothing more: filtering,
 * persistence and scoring happen elsewhere, so a new ATS is one new class.
 */
public interface DiscoveryAdapter {

    /** Short feed name; also stored as {@code jobs.source}. */
    String kind();

    /**
     * @param board the company's token on this ATS (ignored by company-wide feeds such as Amazon)
     * @throws dev.phoenix.http.HttpFetchException when the feed cannot be read
     */
    List<Posting> fetch(String board);
}
