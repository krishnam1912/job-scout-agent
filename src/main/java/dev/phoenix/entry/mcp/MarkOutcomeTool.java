package dev.phoenix.entry.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.phoenix.domain.Job;
import dev.phoenix.service.ApplicationService;
import dev.phoenix.service.PostSubmitOutcome;
import org.springframework.stereotype.Component;

@Component
public class MarkOutcomeTool implements McpTool {

    private static final JsonNode SCHEMA = Schemas.parse("""
            {"type": "object", "required": ["job_id", "outcome"], "properties": {
               "job_id": {"type": "integer"},
               "outcome": {"type": "string", "enum": ["oa", "interview", "rejected", "offer"]},
               "note": {"type": "string"}}}""");

    private final ApplicationService applications;
    private final ObjectMapper mapper;

    public MarkOutcomeTool(ApplicationService applications, ObjectMapper mapper) {
        this.applications = applications;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "mark_outcome";
    }

    @Override
    public String description() {
        return "Record what the employer did after a submitted application: oa (online assessment), interview, rejected "
                + "or offer. Submitted jobs may go to oa, interview or rejected; oa to interview or rejected; interview to "
                + "offer or rejected.";
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public JsonNode call(ToolArgs args) {
        Job job = applications.markOutcome(args.requireLong("job_id"), PostSubmitOutcome.parse(args.requireString("outcome")),
                ACTOR, args.optString("note").orElse(null));
        ObjectNode out = mapper.createObjectNode();
        out.put("job_id", job.id());
        out.put("state", job.state().name());
        return out;
    }
}
