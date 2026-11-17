package dev.phoenix.llm;

import dev.phoenix.config.OperatorMode;

import java.util.Map;
import java.util.Optional;

/**
 * Chooses the provider for a task from the {@code llm.routing} config. When the operator is Claude Code the
 * judgment happens in the chat through MCP tools, so {@link #forTask} deliberately offers no provider; only an
 * explicit {@link #configured} lookup (the {@code llm-test} command) ignores the operator.
 */
public final class LlmRouter {

    private final OperatorMode operator;
    private final Map<String, LlmProvider> providers;
    private final Map<String, String> routing;

    public LlmRouter(OperatorMode operator, Map<String, LlmProvider> providers, Map<String, String> routing) {
        this.operator = operator;
        this.providers = Map.copyOf(providers);
        this.routing = Map.copyOf(routing);
    }

    /** @return the provider the engine itself should call, empty when the operator does the judging */
    public Optional<LlmProvider> forTask(LlmTask task) {
        return operator == OperatorMode.CLAUDE_CODE ? Optional.empty() : configured(task);
    }

    /** @return the provider routed to the task in the config, whatever the operator mode */
    public Optional<LlmProvider> configured(LlmTask task) {
        return Optional.ofNullable(routing.get(task.configKey())).map(providers::get);
    }
}
