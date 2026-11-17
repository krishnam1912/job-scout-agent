package dev.phoenix.entry.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.phoenix.config.OperatorMode;
import dev.phoenix.testsupport.McpHarness;
import dev.phoenix.testsupport.ScanHarness;
import dev.phoenix.testsupport.TestStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** The real SDK transport over in-memory pipes: the protocol handshake, tool listing, a call and shutdown on EOF. */
class SdkMcpServerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void speaksJsonRpcOverStdioAndStopsWhenTheClientHangsUp(@TempDir Path dir) throws Exception {
        try (TestStore store = TestStore.create(dir)) {
            McpHarness harness = new McpHarness(store, new ScanHarness(store, dir, "{}"));
            SdkMcpServer server = new SdkMcpServer(new McpToolCatalog(OperatorMode.CLAUDE_CODE, harness.tools),
                    new ToolInvoker(mapper), mapper, "test");
            PipedOutputStream clientToServer = new PipedOutputStream();
            PipedInputStream serverIn = new PipedInputStream(clientToServer);
            PipedOutputStream serverToClient = new PipedOutputStream();
            PipedInputStream clientIn = new PipedInputStream(serverToClient);
            ExecutorService pool = Executors.newSingleThreadExecutor();
            Future<?> running = pool.submit(() -> {
                server.serve(serverIn, serverToClient);
                return null;
            });
            PrintWriter toServer = new PrintWriter(clientToServer, true, StandardCharsets.UTF_8);
            BufferedReader fromServer = new BufferedReader(new InputStreamReader(clientIn, StandardCharsets.UTF_8));

            toServer.println("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2024-11-05\","
                    + "\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}");
            JsonNode init = read(fromServer);
            assertThat(init.at("/result/serverInfo/name").asText()).isEqualTo("phoenix");

            toServer.println("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
            toServer.println("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");
            JsonNode list = read(fromServer);
            assertThat(list.at("/result/tools")).hasSize(11);
            assertThat(list.at("/result/tools/0/inputSchema/type").asText()).isEqualTo("object");

            toServer.println("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"status\",\"arguments\":{}}}");
            JsonNode status = read(fromServer);
            assertThat(status.at("/result/isError").asBoolean()).isFalse();
            assertThat(mapper.readTree(status.at("/result/content/0/text").asText()).has("counts")).isTrue();

            toServer.println("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"get_job\",\"arguments\":{\"job_id\":9}}}");
            JsonNode missing = read(fromServer);
            assertThat(missing.at("/result/isError").asBoolean()).isTrue();
            assertThat(missing.at("/result/content/0/text").asText()).isEqualTo("Job 9 not found");

            clientToServer.close();
            running.get(10, TimeUnit.SECONDS);
            pool.shutdown();
        }
    }

    private JsonNode read(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        assertThat(line).as("a protocol line from the server").isNotNull();
        return mapper.readTree(line);
    }
}
