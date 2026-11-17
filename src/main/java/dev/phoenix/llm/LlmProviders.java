package dev.phoenix.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.phoenix.config.PhoenixProperties;
import dev.phoenix.http.HttpFetcher;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** Builds the providers named in phoenix.yml (config validation already guaranteed their type and fields). */
public final class LlmProviders {

    private LlmProviders() {
    }

    public static Map<String, LlmProvider> create(PhoenixProperties.Llm config, Function<String, String> env,
                                                  HttpFetcher http, ObjectMapper mapper) {
        JsonSchemaGuard guard = new JsonSchemaGuard(mapper);
        Map<String, LlmProvider> providers = new LinkedHashMap<>();
        config.providers().forEach((name, p) -> providers.put(name,
                new OpenAiCompatibleProvider(name, p.baseUrl(), p.model(), p.apiKeyEnv(), env, http, mapper, guard)));
        return providers;
    }
}
