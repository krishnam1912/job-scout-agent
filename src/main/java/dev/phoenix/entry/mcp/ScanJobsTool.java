package dev.phoenix.entry.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.phoenix.discovery.ScanRequest;
import dev.phoenix.discovery.ScanResult;
import dev.phoenix.discovery.ScanService;
import dev.phoenix.entry.JsonViews;
import dev.phoenix.error.UserErrorException;
import org.springframework.stereotype.Component;

@Component
public class ScanJobsTool implements McpTool {

    static final int MAX_LIMIT = 10;

    private static final JsonNode SCHEMA = Schemas.parse("""
            {"type": "object", "properties": {
               "limit": {"type": "integer", "minimum": 1, "maximum": 10, "description": "How many candidates to return (default 10)."},
               "company": {"type": "string", "description": "Only scan boards whose 'kind/board' name contains this text, e.g. 'amazon'."},
               "dry_run": {"type": "boolean", "description": "Preview only: store nothing."}}}""");

    private final ScanService scans;
    private final JsonViews views;
    private final ObjectMapper mapper;

    public ScanJobsTool(ScanService scans, JsonViews views, ObjectMapper mapper) {
        this.scans = scans;
        this.views = views;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "scan_jobs";
    }

    @Override
    public String description() {
        return "Fetch the configured job boards, filter and pre-score new postings, and return up to 10 candidates "
                + "(job_id, ext_id, company, title, location, url, prescore, jd_summary) that still need a 1-5 score. "
                + "Candidates stay on offer until scored with record_scores. Nothing is applied to. "
                + "Use dry_run to preview without storing anything.";
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public JsonNode call(ToolArgs args) {
        int limit = args.optInt("limit", MAX_LIMIT);
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new UserErrorException("'limit' must be between 1 and " + MAX_LIMIT);
        }
        ScanResult result = scans.scan(new ScanRequest(limit, args.optString("company").orElse(null), args.optBoolean("dry_run", false)));
        ObjectNode out = mapper.createObjectNode();
        out.set("candidates", views.array(result.candidates().stream().map(views::candidate).toList()));
        out.set("stats", views.scanStats(result.stats()));
        return out;
    }
}
