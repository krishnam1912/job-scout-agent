package dev.phoenix.discovery;

/** Everything a scan reads from the user's files, loaded together so a scan sees one consistent snapshot. */
public record DiscoveryConfig(SourcesConfig sources, FilterConfig filters) {

    /** Amazon is scanned only when the sources enable it and the filters say what to search for. */
    public boolean amazonEnabled() {
        return sources.amazon() && filters.amazon() != null;
    }
}
