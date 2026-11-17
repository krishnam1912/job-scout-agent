package dev.phoenix.entry.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import dev.phoenix.config.OperatorMode;

/**
 * One operation the operator can call. Implementations stay thin: validate the arguments, call one service, map the
 * result to small JSON. Anything the user should be able to fix is reported by throwing a
 * {@link dev.phoenix.error.UserErrorException}, which the server turns into an error result the model can read.
 */
public interface McpTool {

    /** Recorded as the actor of every change made through MCP. */
    String ACTOR = "claude-code";

    /** snake_case name the model calls. */
    String name();

    /** What the tool does and when to use it; this is what the model reads, so be exact. */
    String description();

    /** JSON Schema of the arguments object. */
    JsonNode inputSchema();

    JsonNode call(ToolArgs args);

    /** Tools that only make sense for some operators (such as recording approvals) opt out here. */
    default boolean availableTo(OperatorMode mode) {
        return true;
    }
}
