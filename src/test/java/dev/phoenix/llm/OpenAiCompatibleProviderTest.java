package dev.phoenix.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.phoenix.testsupport.FakeHttpFetcher;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleProviderTest {

    private static final String SCHEMA = """
            {"type": "object", "properties": {"company": {"type": "string"}}, "required": ["company"]}""";

    private final ObjectMapper mapper = new ObjectMapper();
    private final FakeHttpFetcher http = new FakeHttpFetcher();
    private final Map<String, String> environment = Map.of("MODEL_KEY", "secret-token");

    private OpenAiCompatibleProvider provider(String keyEnv) {
        Function<String, String> env = environment::get;
        return new OpenAiCompatibleProvider("local", "http://localhost:11434/v1/", "tiny-model", keyEnv, env, http, mapper,
                new JsonSchemaGuard(mapper));
    }

    private static String reply(String content) {
        return "{\"choices\": [{\"message\": {\"role\": \"assistant\", \"content\": " + quote(content) + "}}]}";
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private JsonNode schema() throws Exception {
        return mapper.readTree(SCHEMA);
    }

    @Test
    void aPlainRequestPostsChatCompletionsAndReturnsTheText() throws Exception {
        http.thenPost(reply("hello"));

        LlmResponse response = provider("").complete(LlmRequest.of("be brief", "say hi", null));

        assertThat(response.text()).isEqualTo("hello");
        assertThat(response.json()).isNull();
        assertThat(http.requests()).containsExactly("http://localhost:11434/v1/chat/completions");
        JsonNode body = mapper.readTree(http.postBodies().get(0));
        assertThat(body.get("model").asText()).isEqualTo("tiny-model");
        assertThat(body.get("messages").get(0).get("role").asText()).isEqualTo("system");
        assertThat(body.get("messages").get(1).get("content").asText()).isEqualTo("say hi");
        assertThat(body.has("response_format")).isFalse();
        assertThat(http.postHeaders().get(0)).isEmpty();
    }

    @Test
    void aSchemaIsSentAsJsonSchemaFormatAndAValidReplyIsParsed() throws Exception {
        http.thenPost(reply("{\"company\": \"Acme\"}"));

        LlmResponse response = provider("").complete(LlmRequest.of("extract", "text", schema()));

        assertThat(response.json().get("company").asText()).isEqualTo("Acme");
        JsonNode format = mapper.readTree(http.postBodies().get(0)).get("response_format");
        assertThat(format.get("type").asText()).isEqualTo("json_schema");
        assertThat(format.get("json_schema").get("schema")).isEqualTo(schema());
    }

    @Test
    void anInvalidReplyIsRetriedWithTheValidationErrorAppended() throws Exception {
        http.thenPost(reply("not json at all")).thenPost(reply("{\"wrong\": 1}")).thenPost(reply("{\"company\": \"Acme\"}"));

        LlmResponse response = provider("").complete(LlmRequest.of("extract", "text", schema()));

        assertThat(response.json().get("company").asText()).isEqualTo("Acme");
        assertThat(http.postBodies()).hasSize(3);
        JsonNode secondMessages = mapper.readTree(http.postBodies().get(1)).get("messages");
        assertThat(secondMessages.get(secondMessages.size() - 1).get("content").asText())
                .contains("rejected").contains("not valid JSON");
        JsonNode thirdMessages = mapper.readTree(http.postBodies().get(2)).get("messages");
        assertThat(thirdMessages.size()).isGreaterThan(secondMessages.size());
        assertThat(thirdMessages.get(thirdMessages.size() - 1).get("content").asText()).contains("company");
    }

    @Test
    void afterTwoRetriesItGivesUpWithATypedFailure() throws Exception {
        http.thenPost(reply("x")).thenPost(reply("y")).thenPost(reply("z")).thenPost(reply("{\"company\": \"never reached\"}"));

        assertThatThrownBy(() -> provider("").complete(LlmRequest.of("extract", "text", schema())))
                .isInstanceOf(LlmException.class).hasMessageContaining("3 attempts");

        assertThat(http.postBodies()).hasSize(3);
    }

    @Test
    void aServerThatRefusesJsonSchemaGetsJsonObjectWithTheSchemaInThePrompt() throws Exception {
        http.thenPostFails(400).thenPost(reply("{\"company\": \"Acme\"}"));

        LlmResponse response = provider("").complete(LlmRequest.of("extract", "text", schema()));

        assertThat(response.json().get("company").asText()).isEqualTo("Acme");
        JsonNode second = mapper.readTree(http.postBodies().get(1));
        assertThat(second.get("response_format").get("type").asText()).isEqualTo("json_object");
        assertThat(second.get("messages").get(0).get("content").asText()).contains("JSON schema").contains("\"company\"");
    }

    @Test
    void theApiKeyComesFromTheNamedEnvironmentVariableAndGoesInTheAuthorizationHeader() {
        http.thenPost(reply("ok"));

        provider("MODEL_KEY").complete(LlmRequest.of("s", "u", null));

        assertThat(http.postHeaders().get(0)).containsEntry("Authorization", "Bearer secret-token");
    }

    @Test
    void aMissingKeyIsReportedWithoutCallingTheServerOrLeakingAnything() {
        assertThatThrownBy(() -> provider("ABSENT_KEY").complete(LlmRequest.of("s", "u", null)))
                .isInstanceOf(LlmException.class).hasMessageContaining("ABSENT_KEY").hasMessageNotContaining("secret");

        assertThat(http.requests()).isEmpty();
    }

    @Test
    void aTransportFailureBecomesATypedFailureNamingTheProvider() {
        http.thenPostFails(500);

        assertThatThrownBy(() -> provider("").complete(LlmRequest.of("s", "u", null)))
                .isInstanceOf(LlmException.class).hasMessageContaining("local").hasMessageContaining("500");
    }

    @Test
    void aReplyWithoutMessageContentIsAFailure() {
        http.thenPost("{\"choices\": []}");

        assertThatThrownBy(() -> provider("").complete(LlmRequest.of("s", "u", null)))
                .isInstanceOf(LlmException.class).hasMessageContaining("no message content");
    }

    @Test
    void jsonSchemaGuardExplainsWhyAReplyWasRejected() throws Exception {
        JsonSchemaGuard guard = new JsonSchemaGuard(mapper);

        assertThat(guard.check(schema(), "{\"company\": \"x\"}").valid()).isTrue();
        assertThat(guard.check(schema(), "{}").problem()).contains("company");
        assertThat(guard.check(schema(), "oops").problem()).contains("not valid JSON");
        assertThat(guard.check(schema(), "").problem()).isNotNull();
    }
}
