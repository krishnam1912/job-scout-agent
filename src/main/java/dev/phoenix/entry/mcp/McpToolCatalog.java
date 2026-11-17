package dev.phoenix.entry.mcp;

import dev.phoenix.config.OperatorMode;

import java.util.List;

/** The tools exposed for one operator mode: tools that do not apply to the mode are not registered at all. */
public final class McpToolCatalog {

    private final List<McpTool> tools;

    public McpToolCatalog(OperatorMode mode, List<McpTool> all) {
        this.tools = all.stream().filter(t -> t.availableTo(mode)).toList();
    }

    public List<McpTool> tools() {
        return tools;
    }
}
