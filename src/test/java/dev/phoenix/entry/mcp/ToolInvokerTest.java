package dev.phoenix.entry.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.phoenix.error.UserErrorException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolInvokerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ToolInvoker invoker = new ToolInvoker(mapper);

    private McpTool tool(java.util.function.Function<ToolArgs, JsonNode> body) {
        return new McpTool() {
            @Override
            public String name() {
                return "demo";
            }

            @Override
            public String description() {
                return "demo";
            }

            @Override
            public JsonNode inputSchema() {
                return mapper.createObjectNode();
            }

            @Override
            public JsonNode call(ToolArgs args) {
                return body.apply(args);
            }
        };
    }

    @Test
    void aResultIsSerialisedCompactly() {
        ToolInvoker.Reply reply = invoker.invoke(tool(a -> mapper.createObjectNode().put("ok", true)), mapper.createObjectNode());

        assertThat(reply.error()).isFalse();
        assertThat(reply.text()).isEqualTo("{\"ok\":true}");
    }

    @Test
    void aUserErrorBecomesItsOwnMessage() {
        ToolInvoker.Reply reply = invoker.invoke(tool(a -> {
            throw new UserErrorException("job 5 is already approved");
        }), null);

        assertThat(reply.error()).isTrue();
        assertThat(reply.text()).isEqualTo("job 5 is already approved");
    }

    @Test
    void anUnexpectedFailureIsReportedWithoutItsInternals() {
        ToolInvoker.Reply reply = invoker.invoke(tool(a -> {
            throw new IllegalStateException("jdbc:sqlite:/Users/someone/private/path exploded");
        }), null);

        assertThat(reply.error()).isTrue();
        assertThat(reply.text()).contains("Internal error in demo").contains("IllegalStateException")
                .doesNotContain("private");
    }

    @Test
    void argumentsAreCheckedAndNamedInErrors() throws Exception {
        ToolArgs args = new ToolArgs(mapper.readTree("{\"n\": \"x\", \"s\": 5, \"b\": \"yes\", \"l\": [1, \"a\"], \"f\": 1.5}"));

        assertThatThrownBy(() -> args.requireLong("n")).hasMessageContaining("'n'");
        assertThatThrownBy(() -> args.optString("s")).hasMessageContaining("'s' must be a string");
        assertThatThrownBy(() -> args.optBoolean("b", false)).hasMessageContaining("'b'");
        assertThatThrownBy(() -> args.optLongList("l")).hasMessageContaining("'l'");
        assertThatThrownBy(() -> args.requireLong("f")).hasMessageContaining("integer");
        assertThatThrownBy(() -> args.requireString("missing")).hasMessageContaining("'missing'");
        assertThat(args.optInt("missing", 7)).isEqualTo(7);
        assertThat(new ToolArgs(null).optString("x")).isEmpty();
    }
}
