package dev.phoenix.llm;

import dev.phoenix.config.OperatorMode;
import dev.phoenix.config.PhoenixProperties;
import dev.phoenix.error.UserErrorException;
import dev.phoenix.testsupport.FakeHttpFetcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmRouterTest {

    private final Map<String, LlmProvider> providers = LlmProviders.create(
            new PhoenixProperties.Llm(
                    Map.of("local", new PhoenixProperties.Provider("openai-compatible", "http://localhost:11434/v1", "m", "")),
                    Map.of("extract", "local")),
            k -> null, new FakeHttpFetcher(), new ObjectMapper());

    @Test
    void whenClaudeCodeIsTheOperatorTheEngineOffersNoProvider() {
        LlmRouter router = new LlmRouter(OperatorMode.CLAUDE_CODE, providers, Map.of("extract", "local"));

        assertThat(router.forTask(LlmTask.EXTRACT)).isEmpty();
        assertThat(router.configured(LlmTask.EXTRACT)).isPresent();
    }

    @Test
    void inStandaloneModeTasksAreRoutedByTheConfig() {
        LlmRouter router = new LlmRouter(OperatorMode.STANDALONE, providers, Map.of("extract", "local"));

        assertThat(router.forTask(LlmTask.EXTRACT)).get().extracting(LlmProvider::name).isEqualTo("local");
        assertThat(router.forTask(LlmTask.SCORE)).isEmpty();
    }

    @Test
    void taskNamesAreParsedOrRejected() {
        assertThat(LlmTask.parse("Extract")).isEqualTo(LlmTask.EXTRACT);
        assertThat(LlmTask.ANSWER.configKey()).isEqualTo("answer");
        assertThatThrownBy(() -> LlmTask.parse("dance")).isInstanceOf(UserErrorException.class);
    }
}
