package dev.phoenix.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.phoenix.entry.JsonViews;
import dev.phoenix.entry.mcp.AnswerAskTool;
import dev.phoenix.entry.mcp.GetJobTool;
import dev.phoenix.entry.mcp.GetQueueTool;
import dev.phoenix.entry.mcp.ListAsksTool;
import dev.phoenix.entry.mcp.MarkOutcomeTool;
import dev.phoenix.entry.mcp.McpTool;
import dev.phoenix.entry.mcp.RecordApplicationResultTool;
import dev.phoenix.entry.mcp.RecordApprovalTool;
import dev.phoenix.entry.mcp.RecordScoresTool;
import dev.phoenix.entry.mcp.ScanJobsTool;
import dev.phoenix.entry.mcp.StartApplicationTool;
import dev.phoenix.entry.mcp.StatusTool;
import dev.phoenix.entry.mcp.ToolArgs;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** All MCP tools wired over a test store, callable by name with JSON text. */
public final class McpHarness {

    public final ObjectMapper mapper = new ObjectMapper();
    public final Services services;
    public final ScanHarness scan;
    public final List<McpTool> tools;
    private final Map<String, McpTool> byName;

    public McpHarness(TestStore store, ScanHarness scan) {
        this.services = new Services(store);
        this.scan = scan;
        JsonViews views = new JsonViews(mapper);
        tools = List.of(
                new ScanJobsTool(scan.scan, views, mapper),
                new RecordScoresTool(services.scoring, mapper),
                new GetQueueTool(services.queue, views, mapper),
                new GetJobTool(services.jobQuery, views),
                new RecordApprovalTool(services.approvals, mapper),
                new StartApplicationTool(services.applications, mapper),
                new RecordApplicationResultTool(services.applications, views, mapper),
                new ListAsksTool(services.asks, views, mapper),
                new AnswerAskTool(services.asks, mapper),
                new StatusTool(services.status, views),
                new MarkOutcomeTool(services.applications, mapper));
        byName = tools.stream().collect(Collectors.toMap(McpTool::name, Function.identity()));
    }

    public JsonNode call(String tool, String argumentsJson) {
        try {
            return byName.get(tool).call(new ToolArgs(mapper.readTree(argumentsJson)));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
