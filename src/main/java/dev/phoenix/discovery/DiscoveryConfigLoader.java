package dev.phoenix.discovery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import dev.phoenix.config.PhoenixHome;
import dev.phoenix.config.PhoenixProperties;
import dev.phoenix.error.ConfigException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads the sources and filters files named in phoenix.yml. Boards found later by discovery live in the optional
 * {@code profile/sources.local.json} under the home; they are merged into the matching lists.
 */
public final class DiscoveryConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryConfigLoader.class);
    private static final String LOCAL_SOURCES = "profile/sources.local.json";

    private final PhoenixHome home;
    private final PhoenixProperties properties;
    private final ObjectMapper json = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public DiscoveryConfigLoader(PhoenixHome home, PhoenixProperties properties) {
        this.home = home;
        this.properties = properties;
    }

    public DiscoveryConfig load() {
        FilterConfig filters = readFilters(requirePath("filters", properties.filters()));
        SourcesConfig sources = readSources(requirePath("sources", properties.sources()));
        if (sources.amazon() && filters.amazon() == null) {
            log.warn("amazon is enabled in the sources but the filters have no 'amazon' block; skipping Amazon");
        }
        return new DiscoveryConfig(sources, filters);
    }

    private Path requirePath(String key, String configured) {
        if (configured == null || configured.isBlank()) {
            throw new ConfigException("phoenix.yml: '" + key + "' is required to scan");
        }
        return home.resolvePath(configured);
    }

    private FilterConfig readFilters(Path file) {
        try {
            return json.readValue(file.toFile(), FilterConfig.class);
        } catch (IOException e) {
            throw new ConfigException("Cannot read filters " + file + ": " + e.getMessage(), e);
        }
    }

    private SourcesConfig readSources(Path file) {
        try {
            JsonNode root = json.readTree(file.toFile());
            Path local = home.root().resolve(LOCAL_SOURCES);
            JsonNode extra = Files.isRegularFile(local) ? json.readTree(local.toFile()) : null;
            return new SourcesConfig(
                    merged(root, extra, "greenhouse"), merged(root, extra, "lever"), merged(root, extra, "ashby"),
                    merged(root, extra, "smartrecruiters"), truthy(root.get("amazon")), truthy(root.get("atlassian")));
        } catch (IOException e) {
            throw new ConfigException("Cannot read sources " + file + ": " + e.getMessage(), e);
        }
    }

    /** Keeps the file order, unless local boards are added: then the union is sorted by name, as scan.py did. */
    private List<String> merged(JsonNode base, JsonNode extra, String kind) {
        List<String> fromBase = strings(base.get(kind));
        List<String> fromLocal = extra == null ? List.of() : strings(extra.get(kind));
        if (fromLocal.isEmpty()) {
            return fromBase;
        }
        Set<String> union = new LinkedHashSet<>(fromBase);
        union.addAll(fromLocal);
        return union.stream().sorted(Comparator.comparing(s -> s.toLowerCase(Locale.ROOT))).toList();
    }

    private List<String> strings(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return json.convertValue(node, new TypeReference<>() {
        });
    }

    private static boolean truthy(JsonNode node) {
        return node != null && !node.isNull() && !(node.isBoolean() && !node.asBoolean())
                && !(node.isContainerNode() && node.isEmpty());
    }
}
