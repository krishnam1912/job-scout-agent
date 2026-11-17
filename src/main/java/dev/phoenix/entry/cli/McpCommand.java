package dev.phoenix.entry.cli;

import dev.phoenix.entry.mcp.SdkMcpServer;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/** Starts the MCP server on stdin/stdout; stdout carries only protocol messages while it runs. */
@Component
@Command(name = "mcp", mixinStandardHelpOptions = true,
        description = "Run the MCP server over stdio (started by Claude Code from .mcp.json).")
public class McpCommand implements Callable<Integer> {

    private final SdkMcpServer server;

    public McpCommand(SdkMcpServer server) {
        this.server = server;
    }

    @Override
    public Integer call() throws InterruptedException {
        server.serve(System.in, System.out);
        return 0;
    }
}
