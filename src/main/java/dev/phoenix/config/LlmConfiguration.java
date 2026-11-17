package dev.phoenix.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.phoenix.http.HttpFetcher;
import dev.phoenix.http.JdkHttpFetcher;
import dev.phoenix.llm.LlmRouter;
import dev.phoenix.llm.LlmProviders;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/** Language-model providers named in phoenix.yml and the router that picks one per task. */
@Configuration(proxyBeanMethods = false)
public class LlmConfiguration {

    private static final Duration MODEL_TIMEOUT = Duration.ofSeconds(120);

    @Bean
    LlmRouter llmRouter(PhoenixProperties properties, ObjectMapper mapper) {
        HttpFetcher modelHttp = new JdkHttpFetcher(MODEL_TIMEOUT);
        return new LlmRouter(properties.operator(),
                LlmProviders.create(properties.llm(), System::getenv, modelHttp, mapper), properties.llm().routing());
    }
}
