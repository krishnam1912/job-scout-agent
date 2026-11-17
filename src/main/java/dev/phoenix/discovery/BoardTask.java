package dev.phoenix.discovery;

/** One board to fetch: an adapter and the company token to give it. */
public record BoardTask(DiscoveryAdapter adapter, String board) {

    public String kind() {
        return adapter.kind();
    }

    /** {@code kind/board}, the name used in logs, health records and the {@code --company} filter. */
    public String label() {
        return adapter.kind() + "/" + board;
    }
}
