package dev.phoenix.entry.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.phoenix.service.ApplicationService;
import dev.phoenix.service.StartOutcome;
import org.springframework.stereotype.Component;

@Component
public class StartApplicationTool implements McpTool {

    private static final JsonNode SCHEMA = Schemas.parse("""
            {"type": "object", "required": ["job_id"], "properties": {"job_id": {"type": "integer"},
             "verified_not_submitted": {"type": "boolean", "description": "Only for a job in SUBMIT_UNKNOWN: true once you checked the ATS dashboard or confirmation email and the earlier attempt did NOT go through."}}}""");

    private final ApplicationService applications;
    private final ObjectMapper mapper;

    public StartApplicationTool(ApplicationService applications, ObjectMapper mapper) {
        this.applications = applications;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "start_application";
    }

    @Override
    public String description() {
        return "Begin applying to an approved job. Succeeds (job becomes IN_PROGRESS) only when the job is approved, the "
                + "approval has not expired or been used, and nobody has already applied to the same role. Otherwise "
                + "started is false and reason says why: do not apply in that case. Call it right before you open the "
                + "application form. A blocked or failed job can be started again while its approval is still valid. A job "
                + "whose last submit result was unknown is refused until you verify it did not go through.";
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public JsonNode call(ToolArgs args) {
        StartOutcome outcome = applications.start(args.requireLong("job_id"), ACTOR,
                args.optBoolean("verified_not_submitted", false));
        ObjectNode out = mapper.createObjectNode();
        out.put("started", outcome.started());
        out.put("reason", outcome.reason());
        out.put("job_id", outcome.job().id());
        out.put("state", outcome.job().state().name());
        return out;
    }
}
