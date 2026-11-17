package dev.phoenix.entry.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import dev.phoenix.entry.JsonViews;
import dev.phoenix.service.JobQueryService;
import org.springframework.stereotype.Component;

@Component
public class GetJobTool implements McpTool {

    private static final JsonNode SCHEMA = Schemas.parse("""
            {"type": "object", "required": ["job_id"], "properties": {"job_id": {"type": "integer"}}}""");

    private final JobQueryService jobs;
    private final JsonViews views;

    public GetJobTool(JobQueryService jobs, JsonViews views) {
        this.jobs = jobs;
        this.views = views;
    }

    @Override
    public String name() {
        return "get_job";
    }

    @Override
    public String description() {
        return "Everything known about one job: fields, full job description text, latest score, application, "
                + "other postings of the same role (group_members) and the event history.";
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public JsonNode call(ToolArgs args) {
        return views.job(jobs.get(args.requireLong("job_id")));
    }
}
