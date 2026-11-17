package dev.phoenix.entry.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * The MCP server on the official Java SDK, speaking JSON-RPC over stdio. stdout carries only protocol messages
 * (the SDK writes there; our logging goes to stderr and the log file). It runs until the client closes stdin.
 */
public final class SdkMcpServer {

    private static final Logger log = LoggerFactory.getLogger(SdkMcpServer.class);
    private static final String SERVER_NAME = "phoenix";

    private final McpToolCatalog catalog;
    private final ToolInvoker invoker;
    private final ObjectMapper mapper;
    private final String version;

    public SdkMcpServer(McpToolCatalog catalog, ToolInvoker invoker, ObjectMapper mapper, String version) {
        this.catalog = catalog;
        this.invoker = invoker;
        this.mapper = mapper;
        this.version = version;
    }

    /** Serves until {@code in} reaches end of file, then shuts the transport down. */
    public void serve(InputStream in, OutputStream out) throws InterruptedException {
        CountDownLatch clientGone = new CountDownLatch(1);
        McpJsonMapper jsonMapper = new JacksonMcpJsonMapper(mapper);
        StdioServerTransportProvider transport =
                new StdioServerTransportProvider(jsonMapper, new EndOfInputSignal(in, clientGone), out);
        McpSyncServer server = McpServer.sync(transport)
                .serverInfo(SERVER_NAME, version)
                .instructions("Phoenix job-search engine. The engine owns all state; you make the judgment calls and "
                        + "record them with these tools. Never apply to a job that has not been approved by the user.")
                .jsonMapper(jsonMapper)
                .tools(specifications(jsonMapper))
                .build();
        log.info("MCP server ready with {} tools", catalog.tools().size());
        try {
            clientGone.await();
        } finally {
            server.closeGracefully();
            log.info("MCP client disconnected; server stopped");
        }
    }

    private List<McpServerFeatures.SyncToolSpecification> specifications(McpJsonMapper jsonMapper) {
        return catalog.tools().stream().map(tool -> McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder().name(tool.name()).description(tool.description())
                        .inputSchema(jsonMapper, tool.inputSchema().toString()).build())
                .callHandler((exchange, request) -> {
                    ToolInvoker.Reply reply = invoker.invoke(tool, mapper.valueToTree(request.arguments()));
                    return McpSchema.CallToolResult.builder().addTextContent(reply.text()).isError(reply.error()).build();
                }).build()).toList();
    }

    /** Wraps stdin so the server notices when the client hangs up. */
    private static final class EndOfInputSignal extends InputStream {
        private final InputStream delegate;
        private final CountDownLatch ended;

        EndOfInputSignal(InputStream delegate, CountDownLatch ended) {
            this.delegate = delegate;
            this.ended = ended;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b < 0) {
                ended.countDown();
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int off, int len) throws IOException {
            int n = delegate.read(buffer, off, len);
            if (n < 0) {
                ended.countDown();
            }
            return n;
        }

        @Override
        public int available() throws IOException {
            return delegate.available();
        }
    }
}
