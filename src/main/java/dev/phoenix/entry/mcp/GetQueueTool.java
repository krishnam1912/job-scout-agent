package dev.phoenix.entry.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.phoenix.entry.JsonViews;
import dev.phoenix.error.UserErrorException;
import dev.phoenix.service.QueueEntry;
import dev.phoenix.service.QueueService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GetQueueTool implements McpTool {

    static final int DEFAULT_LIMIT = 10;
    static final int MAX_LIMIT = 50;

    private static final JsonNode SCHEMA = Schemas.parse("""
            {"type": "object", "properties": {
               "min_fit": {"type": "integer", "minimum": 0, "maximum": 100, "description": "Only jobs with at least this fit percent."},
               "limit": {"type": "integer", "minimum": 1, "maximum": 50, "description": "Default 10."}}}""");

    private final QueueService queue;
    private final JsonViews views;
    private final ObjectMapper mapper;

    public GetQueueTool(QueueService queue, JsonViews views, ObjectMapper mapper) {
        this.queue = queue;
        this.views = views;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "get_queue";
    }

    @Override
    public String description() {
        return "The approval queue: QUEUED jobs, one per group of duplicate postings, ordered by fit, then closing date, "
                + "then how long they have waited. Show it to the user and wait for their approval; do not approve on their behalf.";
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public JsonNode call(ToolArgs args) {
        int limit = args.optInt("limit", DEFAULT_LIMIT);
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new UserErrorException("'limit' must be between 1 and " + MAX_LIMIT);
        }
        Integer minFit = args.optLong("min_fit").map(Long::intValue).orElse(null);
        List<QueueEntry> entries = queue.queue(minFit, limit);
        ObjectNode out = mapper.createObjectNode();
        out.put("count", entries.size());
        out.set("queue", views.array(entries.stream().map(views::queueEntry).toList()));
        return out;
    }
}
