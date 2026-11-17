package dev.phoenix.entry.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.phoenix.entry.JsonViews;
import dev.phoenix.service.ApplicationOutcome;
import dev.phoenix.service.ApplicationService;
import dev.phoenix.service.ResultReport;
import org.springframework.stereotype.Component;

@Component
public class RecordApplicationResultTool implements McpTool {

    private static final JsonNode SCHEMA = Schemas.parse("""
            {"type": "object", "required": ["job_id", "outcome"], "properties": {
               "job_id": {"type": "integer"},
               "outcome": {"type": "string", "enum": ["submitted", "blocked", "failed", "submit_unknown", "skipped"]},
               "blocker_type": {"type": "string", "description": "Required when blocked, e.g. captcha, login, missing_answer."},
               "detail": {"type": "string"},
               "confirmation_ref": {"type": "string", "description": "Confirmation number or page text proving submission."}}}""");

    private final ApplicationService applications;
    private final JsonViews views;
    private final ObjectMapper mapper;

    public RecordApplicationResultTool(ApplicationService applications, JsonViews views, ObjectMapper mapper) {
        this.applications = applications;
        this.views = views;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "record_application_result";
    }

    @Override
    public String description() {
        return "Report how an application attempt ended: submitted, blocked (needs blocker_type; opens an ask for the user), "
                + "failed, submit_unknown (you cannot tell whether it went through) or skipped. The job must be IN_PROGRESS, "
                + "except that a queued or approved job can be skipped. Never report submitted without evidence.";
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public JsonNode call(ToolArgs args) {
        ResultReport report = new ResultReport(args.requireLong("job_id"), ApplicationOutcome.parse(args.requireString("outcome")),
                args.optString("blocker_type").orElse(null), args.optString("detail").orElse(null),
                args.optString("confirmation_ref").orElse(null));
        ApplicationService.Reported reported = applications.record(report, ACTOR);
        ObjectNode out = mapper.createObjectNode();
        out.put("job_id", reported.job().id());
        out.put("state", reported.job().state().name());
        if (reported.openedAsk() == null) {
            out.putNull("ask");
        } else {
            out.set("ask", views.ask(reported.openedAsk()));
        }
        return out;
    }
}
