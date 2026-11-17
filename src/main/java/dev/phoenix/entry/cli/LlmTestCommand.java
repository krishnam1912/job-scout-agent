package dev.phoenix.entry.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.phoenix.error.UserErrorException;
import dev.phoenix.llm.LlmProvider;
import dev.phoenix.llm.LlmRequest;
import dev.phoenix.llm.LlmResponse;
import dev.phoenix.llm.LlmRouter;
import dev.phoenix.llm.LlmTask;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Component
@Command(name = "llm-test", mixinStandardHelpOptions = true,
        description = "Send one request to the model routed to a task in phoenix.yml and print the reply (a manual check "
                + "of a local or hosted provider; ignores the operator mode).")
public class LlmTestCommand implements Callable<Integer> {

    private static final String SYSTEM_PROMPT = "Summarise the user's text in one sentence. Reply as JSON.";

    private final LlmRouter router;
    private final JsonNode schema;

    @Option(names = "--task", defaultValue = "extract", description = "Task whose routed provider to use: extract, score or answer.")
    String task;

    @Option(names = "--text", required = true, description = "The text to send.")
    String text;

    @Spec
    CommandSpec spec;

    public LlmTestCommand(LlmRouter router, ObjectMapper mapper) {
        this.router = router;
        this.schema = mapper.valueToTree(Map.of("type", "object", "required", List.of("summary"),
                "properties", Map.of("summary", Map.of("type", "string"))));
    }

    @Override
    public Integer call() {
        LlmTask llmTask = LlmTask.parse(task);
        LlmProvider provider = router.configured(llmTask).orElseThrow(() -> new UserErrorException(
                "No provider is routed to '" + llmTask.configKey() + "'; add llm.providers and llm.routing to phoenix.yml"));
        LlmResponse response = provider.complete(LlmRequest.of(SYSTEM_PROMPT, text, schema));
        spec.commandLine().getOut().println(response.text());
        return 0;
    }
}
