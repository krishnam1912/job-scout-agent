package dev.phoenix.entry.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.phoenix.error.UserErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs one tool and turns its outcome into the text the model reads. A user error becomes a short, actionable
 * message; anything unexpected is logged in full and reported without internals, so nothing leaks into the chat
 * and nothing is swallowed.
 */
public final class ToolInvoker {

    /** What came back: the text for the model and whether it is an error. */
    public record Reply(String text, boolean error) {
    }

    private static final Logger log = LoggerFactory.getLogger(ToolInvoker.class);

    private final ObjectMapper mapper;

    public ToolInvoker(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Reply invoke(McpTool tool, JsonNode arguments) {
        try {
            return new Reply(mapper.writeValueAsString(tool.call(new ToolArgs(arguments))), false);
        } catch (UserErrorException e) {
            return new Reply(e.getMessage(), true);
        } catch (JsonProcessingException | RuntimeException e) {
            log.error("Tool {} failed", tool.name(), e);
            return new Reply("Internal error in " + tool.name() + ": " + e.getClass().getSimpleName()
                    + ". Details are in the phoenix log.", true);
        }
    }
}
