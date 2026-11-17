package dev.phoenix.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.phoenix.config.PhoenixHome;
import dev.phoenix.config.PhoenixProperties;
import dev.phoenix.dedupe.GroupingService;
import dev.phoenix.dedupe.MinHash;
import dev.phoenix.discovery.AdapterRegistry;
import dev.phoenix.discovery.BoardFetcher;
import dev.phoenix.discovery.CandidatePool;
import dev.phoenix.discovery.DiscoveryConfigLoader;
import dev.phoenix.discovery.PostingLiveness;
import dev.phoenix.discovery.ScanClassifier;
import dev.phoenix.discovery.ScanService;
import dev.phoenix.discovery.ScanWriter;
import dev.phoenix.discovery.SourceHealth;
import dev.phoenix.http.JsonFetcher;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** A scan service over a temp home with a fake network, sources and the synthetic filters fixture. */
public final class ScanHarness {

    public final FakeHttpFetcher http = new FakeHttpFetcher();
    public final PhoenixHome home;
    public final ScanService scan;

    public ScanHarness(TestStore store, Path dir, String sourcesJson) {
        try {
            Files.writeString(dir.resolve("sources.json"), sourcesJson);
            Files.copy(Path.of("src/test/resources/fixtures/filters.json"), dir.resolve("filters.json"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        home = new PhoenixHome(dir);
        PhoenixProperties props = new PhoenixProperties(null, "sources.json", "filters.json", null, null, null, null);
        ObjectMapper json = new ObjectMapper();
        GroupingService grouping = new GroupingService(store.groups, new MinHash(), 0.9, store.clock);
        ScanWriter writer = new ScanWriter(store.jobs, store.events, store.groups, grouping, store.lifecycle,
                new SourceHealth(store.health), new PostingLiveness(store.jobs, store.lifecycle));
        scan = new ScanService(new DiscoveryConfigLoader(home, props), new AdapterRegistry(new JsonFetcher(http, json)),
                new BoardFetcher(4), new ScanClassifier(store.jobs), writer, grouping,
                new CandidatePool(store.jobs, store.groups), store.runs, store.tx, store.clock, json);
    }
}
