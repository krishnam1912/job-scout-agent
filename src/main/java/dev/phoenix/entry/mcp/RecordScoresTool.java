package dev.phoenix.entry.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.phoenix.service.ScoreInput;
import dev.phoenix.service.ScoringService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RecordScoresTool implements McpTool {

    private static final JsonNode SCHEMA = Schemas.parse("""
            {"type": "object", "required": ["items"], "properties": {
               "items": {"type": "array", "minItems": 1, "items": {"type": "object",
                 "required": ["job_id", "score", "rubric_version"], "properties": {
                   "job_id": {"type": "integer"},
                   "score": {"type": "number", "minimum": 1, "maximum": 5, "description": "Rubric score from 1 to 5."},
                   "rationale": {"type": "string", "description": "Why it fits, and the main gaps."},
                   "rubric_version": {"type": "string", "description": "Version label of the rubric used."}}}}}}""");

    private final ScoringService scoring;
    private final ObjectMapper mapper;

    public RecordScoresTool(ScoringService scoring, ObjectMapper mapper) {
        this.scoring = scoring;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "record_scores";
    }

    @Override
    public String description() {
        return "Store your 1-5 rubric scores for candidate jobs. 3.5 and above goes to QUEUED, 3.0 to 3.49 to NEAR_MISS, "
                + "below 3.0 to BELOW. Only CANDIDATE, NEAR_MISS or BELOW jobs can be scored. The whole batch is rejected "
                + "if any item is invalid, so nothing is half-stored.";
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public JsonNode call(ToolArgs args) {
        List<ScoreInput> items = args.requireObjects("items").stream()
                .map(i -> new ScoreInput(i.requireLong("job_id"), i.requireDouble("score"),
                        i.optString("rationale").orElse(null), i.requireString("rubric_version")))
                .toList();
        ArrayNode scored = mapper.createArrayNode();
        scoring.record(items, ACTOR).forEach(s -> scored.addObject().put("job_id", s.jobId())
                .put("state", s.state().name()).put("fit_pct", s.fitPct()));
        ObjectNode out = mapper.createObjectNode();
        out.set("scored", scored);
        return out;
    }
}
