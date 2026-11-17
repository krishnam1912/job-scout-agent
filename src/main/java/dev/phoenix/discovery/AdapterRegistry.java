package dev.phoenix.discovery;

import dev.phoenix.http.JsonFetcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** Turns the configured sources into the list of boards a scan fetches, in the order scan.py used. */
public final class AdapterRegistry {

    private final JsonFetcher http;

    public AdapterRegistry(JsonFetcher http) {
        this.http = http;
    }

    /**
     * @param titleOk the title filter, which SmartRecruiters needs to limit its detail calls
     * @param company when not blank, keeps only boards whose {@code kind/board} label contains it
     */
    public List<BoardTask> plan(DiscoveryConfig config, Predicate<String> titleOk, String company) {
        SourcesConfig sources = config.sources();
        List<BoardTask> tasks = new ArrayList<>();
        add(tasks, new GreenhouseAdapter(http), sources.greenhouse());
        add(tasks, new LeverAdapter(http), sources.lever());
        add(tasks, new AshbyAdapter(http), sources.ashby());
        add(tasks, new SmartRecruitersAdapter(http, config.filters().smartrecruitersCountry(), titleOk),
                sources.smartrecruiters());
        if (config.amazonEnabled()) {
            tasks.add(new BoardTask(new AmazonAdapter(http, config.filters().amazon()), AmazonAdapter.KIND));
        }
        if (sources.atlassian()) {
            tasks.add(new BoardTask(new AtlassianAdapter(http), AtlassianAdapter.KIND));
        }
        if (company == null || company.isBlank()) {
            return tasks;
        }
        String needle = company.toLowerCase(Locale.ROOT);
        return tasks.stream().filter(t -> t.label().toLowerCase(Locale.ROOT).contains(needle)).toList();
    }

    private static void add(List<BoardTask> tasks, DiscoveryAdapter adapter, List<String> boards) {
        boards.forEach(board -> tasks.add(new BoardTask(adapter, board)));
    }
}
