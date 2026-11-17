package dev.phoenix.discovery;

import dev.phoenix.domain.Posting;

import java.util.List;

/** The outcome of fetching one board: postings on success, or the error that stopped it. */
public record BoardResult(BoardTask task, List<Posting> postings, RuntimeException error) {

    public static BoardResult success(BoardTask task, List<Posting> postings) {
        return new BoardResult(task, List.copyOf(postings), null);
    }

    public static BoardResult failure(BoardTask task, RuntimeException error) {
        return new BoardResult(task, List.of(), error);
    }

    public boolean ok() {
        return error == null;
    }
}
