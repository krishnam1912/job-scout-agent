package dev.phoenix.entry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.phoenix.discovery.Candidate;
import dev.phoenix.discovery.ScanStats;
import dev.phoenix.domain.Application;
import dev.phoenix.domain.Ask;
import dev.phoenix.domain.Event;
import dev.phoenix.domain.Job;
import dev.phoenix.domain.Score;
import dev.phoenix.service.JobDetail;
import dev.phoenix.service.QueueEntry;
import dev.phoenix.service.StatusReport;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Compact JSON shapes shared by the CLI and the MCP tools, so both show the same field names. */
public final class JsonViews {

    private final ObjectMapper mapper;

    public JsonViews(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ObjectNode candidate(Candidate c) {
        ObjectNode n = mapper.createObjectNode();
        n.put("job_id", c.jobId());
        n.put("ext_id", c.extId());
        n.put("company", c.company());
        n.put("title", c.title());
        n.put("location", c.location());
        n.put("url", c.url());
        n.put("prescore", c.prescore());
        n.put("jd_summary", c.jdSummary());
        return n;
    }

    public ObjectNode scanStats(ScanStats s) {
        ObjectNode n = mapper.createObjectNode();
        n.put("fetched", s.fetched());
        n.put("already_known", s.alreadyKnown());
        n.put("new_postings", s.newPostings());
        n.put("new_candidates", s.newCandidates());
        n.put("duplicates_hidden", s.duplicatesHidden());
        n.put("failed_boards", s.failedBoards());
        n.put("closed", s.closed());
        ObjectNode filtered = n.putObject("filtered_out");
        s.filteredByReason().forEach(filtered::put);
        return n;
    }

    public ObjectNode queueEntry(QueueEntry e) {
        ObjectNode n = mapper.createObjectNode();
        n.put("job_id", e.job().id());
        n.put("company", e.job().company());
        n.put("title", e.job().title());
        n.put("location", e.job().location());
        n.put("url", e.job().url());
        n.put("score", e.score().score());
        n.put("fit_pct", e.fitPct());
        n.put("closing_date", text(e.job().closingDate()));
        n.put("first_seen", text(e.job().firstSeen()));
        return n;
    }

    public ObjectNode ask(Ask a) {
        ObjectNode n = mapper.createObjectNode();
        n.put("ask_id", a.id());
        n.put("job_id", a.jobId());
        n.put("kind", a.kind());
        n.put("prompt", a.prompt());
        n.put("options", a.options());
        n.put("priority", a.priority());
        n.put("status", a.status().name());
        n.put("answer", a.answer());
        n.put("created_at", text(a.createdAt()));
        return n;
    }

    public ObjectNode job(JobDetail d) {
        Job j = d.job();
        ObjectNode n = mapper.createObjectNode();
        n.put("job_id", j.id());
        n.put("source", j.source());
        n.put("company", j.company());
        n.put("ext_id", j.extId());
        n.put("title", j.title());
        n.put("location", j.location());
        n.put("url", j.url());
        n.put("state", j.state().name());
        n.put("group_id", j.groupId());
        n.put("prescore", j.prescore());
        n.put("first_seen", text(j.firstSeen()));
        n.put("last_seen", text(j.lastSeen()));
        n.put("closing_date", text(j.closingDate()));
        n.put("next_action_at", text(j.nextActionAt()));
        n.put("jd_text", j.jdText());
        putScore(n, d.score());
        putApplication(n, d.application());
        ArrayNode siblings = n.putArray("group_members");
        d.groupMembers().forEach(m -> siblings.addObject().put("job_id", m.id()).put("state", m.state().name())
                .put("source", m.source()).put("url", m.url()));
        ArrayNode events = n.putArray("events");
        for (Event e : d.events()) {
            events.addObject().put("from", e.from() == null ? null : e.from().name()).put("to", e.to().name())
                    .put("actor", e.actor()).put("note", e.note()).put("at", text(e.at()));
        }
        return n;
    }

    public ObjectNode status(StatusReport r) {
        ObjectNode n = mapper.createObjectNode();
        n.put("generated_at", text(r.generatedAt()));
        n.put("last_scan_at", text(r.lastScanAt()));
        n.put("last_scan_status", r.lastScanStatus());
        ObjectNode counts = n.putObject("counts");
        r.counts().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> counts.put(e.getKey().name(), e.getValue()));
        ArrayNode asks = n.putArray("open_asks");
        r.openAsks().forEach(a -> asks.add(ask(a)));
        ArrayNode risk = n.putArray("at_risk");
        for (Job j : r.atRisk()) {
            risk.addObject().put("job_id", j.id()).put("state", j.state().name()).put("company", j.company())
                    .put("title", j.title()).put("next_action_at", text(j.nextActionAt()))
                    .put("closing_date", text(j.closingDate()));
        }
        return n;
    }

    public ArrayNode array(List<? extends JsonNode> items) {
        ArrayNode out = mapper.createArrayNode();
        items.forEach(out::add);
        return out;
    }

    private void putScore(ObjectNode n, Score s) {
        if (s == null) {
            n.putNull("score");
            return;
        }
        n.putObject("score").put("score", s.score()).put("fit_pct", s.fitPct()).put("rationale", s.rationale())
                .put("rubric_version", s.rubricVersion()).put("scorer", s.scorer());
    }

    private void putApplication(ObjectNode n, Application a) {
        if (a == null) {
            n.putNull("application");
            return;
        }
        n.putObject("application").put("state", a.state().name()).put("attempts", a.attempts())
                .put("blocker_type", a.blockerType()).put("blocker_detail", a.blockerDetail())
                .put("submitted_at", text(a.submittedAt())).put("confirmation_ref", a.confirmationRef())
                .put("note", a.note());
    }

    private static String text(Instant t) {
        return t == null ? null : t.toString();
    }
}
