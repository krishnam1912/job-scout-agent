package dev.phoenix.entry.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import dev.phoenix.config.OperatorMode;
import dev.phoenix.domain.JobState;
import dev.phoenix.error.UserErrorException;
import dev.phoenix.testsupport.McpHarness;
import dev.phoenix.testsupport.ScanHarness;
import dev.phoenix.testsupport.TestStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** JSON in, JSON out for every tool, through the real services over a real SQLite file. */
class McpToolsContractTest {

    private static final String BOARD_URL = "https://boards-api.greenhouse.io/v1/boards/acme/jobs?content=true";
    private static final String JD = "Java backend services with Kafka for payment systems. "
            + "You will own design reviews, on-call rotations, capacity planning and incident follow-ups for the platform.";

    private TestStore store;
    private McpHarness mcp;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        store = TestStore.create(dir);
        ScanHarness scan = new ScanHarness(store, dir, "{\"greenhouse\": [\"acme\"]}");
        scan.http.on(BOARD_URL, "{\"jobs\": ["
                + "{\"id\": 1001, \"title\": \"SDE II Backend\", \"location\": {\"name\": \"Bengaluru\"}, \"absolute_url\": \"https://boards.example.com/1001\", \"content\": \"" + JD + "\"},"
                + "{\"id\": 1002, \"title\": \"Backend Engineer\", \"location\": {\"name\": \"Pune\"}, \"absolute_url\": \"https://boards.example.com/1002\", \"content\": \"" + JD + " Platform team.\"}]}");
        mcp = new McpHarness(store, scan);
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    private long firstCandidateId(JsonNode scanResult) {
        return scanResult.get("candidates").get(0).get("job_id").asLong();
    }

    private long scanScoreAndQueue(double score) {
        JsonNode scanned = mcp.call("scan_jobs", "{\"limit\": 1}");
        long id = firstCandidateId(scanned);
        mcp.call("record_scores", "{\"items\": [{\"job_id\": " + id + ", \"score\": " + score + ", \"rationale\": \"fit\", \"rubric_version\": \"v1\"}]}");
        return id;
    }

    @Test
    void scanJobsReturnsCandidatesWithTheDocumentedFields() {
        JsonNode out = mcp.call("scan_jobs", "{\"limit\": 5}");

        assertThat(out.get("candidates")).hasSize(2);
        JsonNode first = out.get("candidates").get(0);
        assertThat(first.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "job_id", "ext_id", "company", "title", "location", "url", "prescore", "jd_summary");
        assertThat(first.get("company").asText()).isEqualTo("acme");
        assertThat(out.get("stats").get("fetched").asInt()).isEqualTo(2);
        assertThat(out.get("stats").get("filtered_out").isObject()).isTrue();
    }

    @Test
    void scanJobsDryRunStoresNothingAndTheLimitIsCapped() {
        JsonNode out = mcp.call("scan_jobs", "{\"dry_run\": true}");

        assertThat(out.get("candidates").get(0).get("job_id").isNull()).isTrue();
        assertThat(store.jobs.findAll()).isEmpty();
        assertThatThrownBy(() -> mcp.call("scan_jobs", "{\"limit\": 11}"))
                .isInstanceOf(UserErrorException.class).hasMessageContaining("between 1 and 10");
    }

    @Test
    void recordScoresReturnsTheNewStateAndFit() {
        long id = firstCandidateId(mcp.call("scan_jobs", "{}"));

        JsonNode out = mcp.call("record_scores", "{\"items\": [{\"job_id\": " + id + ", \"score\": 4.2, \"rationale\": \"good\", \"rubric_version\": \"v1\"}]}");

        assertThat(out.get("scored")).singleElement().satisfies(s -> {
            assertThat(s.get("job_id").asLong()).isEqualTo(id);
            assertThat(s.get("state").asText()).isEqualTo("QUEUED");
            assertThat(s.get("fit_pct").asInt()).isEqualTo(84);
        });
    }

    @Test
    void recordScoresNamesTheMissingArgument() {
        assertThatThrownBy(() -> mcp.call("record_scores", "{\"items\": [{\"job_id\": 1, \"score\": 4}]}"))
                .isInstanceOf(UserErrorException.class).hasMessageContaining("rubric_version");
        assertThatThrownBy(() -> mcp.call("record_scores", "{}")).hasMessageContaining("items");
    }

    @Test
    void getQueueListsQueuedJobsBestFirst() {
        long id = scanScoreAndQueue(4.4);

        JsonNode out = mcp.call("get_queue", "{\"min_fit\": 80, \"limit\": 5}");

        assertThat(out.get("count").asInt()).isEqualTo(1);
        JsonNode entry = out.get("queue").get(0);
        assertThat(entry.get("job_id").asLong()).isEqualTo(id);
        assertThat(entry.get("fit_pct").asInt()).isEqualTo(88);
        assertThat(entry.get("score").asDouble()).isEqualTo(4.4);
        assertThat(mcp.call("get_queue", "{\"min_fit\": 95}").get("count").asInt()).isZero();
        assertThatThrownBy(() -> mcp.call("get_queue", "{\"limit\": 0}")).isInstanceOf(UserErrorException.class);
    }

    @Test
    void getJobReturnsTheFullRecord() {
        long id = scanScoreAndQueue(4.4);

        JsonNode job = mcp.call("get_job", "{\"job_id\": " + id + "}");

        assertThat(job.get("state").asText()).isEqualTo("QUEUED");
        assertThat(job.get("jd_text").asText()).contains("Java backend");
        assertThat(job.get("score").get("fit_pct").asInt()).isEqualTo(88);
        assertThat(job.get("application").isNull()).isTrue();
        assertThat(job.get("events")).hasSizeGreaterThanOrEqualTo(3);
        assertThat(job.get("group_members").isArray()).isTrue();
        assertThatThrownBy(() -> mcp.call("get_job", "{\"job_id\": 999}")).hasMessageContaining("not found");
    }

    @Test
    void theWholeApplicationFlowWorksThroughTheTools() {
        long id = scanScoreAndQueue(4.4);

        JsonNode approval = mcp.call("record_approval", "{\"job_ids\": [" + id + "], \"user_words\": \"apply 1\"}");
        assertThat(approval.get("approved")).extracting(JsonNode::asLong).containsExactly(id);
        assertThat(approval.get("refused")).isEmpty();

        JsonNode started = mcp.call("start_application", "{\"job_id\": " + id + "}");
        assertThat(started.get("started").asBoolean()).isTrue();
        assertThat(started.get("state").asText()).isEqualTo("IN_PROGRESS");

        JsonNode blocked = mcp.call("record_application_result",
                "{\"job_id\": " + id + ", \"outcome\": \"blocked\", \"blocker_type\": \"captcha\", \"detail\": \"puzzle\"}");
        assertThat(blocked.get("state").asText()).isEqualTo("BLOCKED");
        assertThat(blocked.get("ask").get("kind").asText()).isEqualTo("blocker");

        JsonNode asks = mcp.call("list_asks", "{}");
        assertThat(asks.get("asks")).hasSize(1);
        long askId = asks.get("asks").get(0).get("ask_id").asLong();
        JsonNode answered = mcp.call("answer_ask", "{\"ask_id\": " + askId + ", \"answer\": \"solved it\"}");
        assertThat(answered.get("status").asText()).isEqualTo("ANSWERED");
        assertThat(mcp.call("list_asks", "{}").get("asks")).isEmpty();

        assertThat(mcp.call("start_application", "{\"job_id\": " + id + "}").get("started").asBoolean()).isTrue();
        JsonNode submitted = mcp.call("record_application_result",
                "{\"job_id\": " + id + ", \"outcome\": \"submitted\", \"confirmation_ref\": \"C-1\"}");
        assertThat(submitted.get("state").asText()).isEqualTo("SUBMITTED");
        assertThat(submitted.get("ask").isNull()).isTrue();

        JsonNode interview = mcp.call("mark_outcome", "{\"job_id\": " + id + ", \"outcome\": \"interview\"}");
        assertThat(interview.get("state").asText()).isEqualTo("INTERVIEW");
        assertThat(store.jobs.findById(id).orElseThrow().state()).isEqualTo(JobState.INTERVIEW);
    }

    @Test
    void recordApprovalReportsRefusalsAndSupportsRules() {
        long id = scanScoreAndQueue(4.4);

        JsonNode refused = mcp.call("record_approval", "{\"job_ids\": [999], \"user_words\": \"apply 999\"}");
        assertThat(refused.get("refused").get(0).get("reason").asText()).contains("not found");

        JsonNode byRule = mcp.call("record_approval", "{\"rule\": \"fit>=80\", \"user_words\": \"apply all >=80\"}");
        assertThat(byRule.get("approved")).extracting(JsonNode::asLong).containsExactly(id);
        assertThatThrownBy(() -> mcp.call("record_approval", "{\"job_ids\": [1]}")).hasMessageContaining("user_words");
        assertThatThrownBy(() -> mcp.call("record_approval", "{\"rule\": \"all of them\", \"user_words\": \"x\"}"))
                .isInstanceOf(UserErrorException.class);
    }

    @Test
    void startApplicationExplainsWhyItCannotStart() {
        long id = scanScoreAndQueue(4.4);

        JsonNode out = mcp.call("start_application", "{\"job_id\": " + id + "}");

        assertThat(out.get("started").asBoolean()).isFalse();
        assertThat(out.get("reason").asText()).contains("approved first");
        assertThat(out.get("state").asText()).isEqualTo("QUEUED");
    }

    @Test
    void statusSummarisesThePipeline() {
        scanScoreAndQueue(4.4);

        JsonNode out = mcp.call("status", "{}");

        assertThat(out.get("counts").get("QUEUED").asInt()).isEqualTo(1);
        assertThat(out.get("counts").get("CANDIDATE").asInt()).isEqualTo(1);
        assertThat(out.get("last_scan_status").asText()).isEqualTo("OK");
        assertThat(out.get("open_asks").isArray()).isTrue();
        assertThat(out.get("at_risk").isArray()).isTrue();
    }

    @Test
    void listAsksRejectsAnUnknownStatus() {
        assertThatThrownBy(() -> mcp.call("list_asks", "{\"status\": \"gone\"}")).isInstanceOf(UserErrorException.class);
    }

    @Test
    void everyToolHasADescriptionAndAnObjectSchema() {
        assertThat(mcp.tools).hasSize(11);
        for (McpTool tool : mcp.tools) {
            assertThat(tool.description()).as(tool.name()).isNotBlank();
            assertThat(tool.inputSchema().get("type").asText()).as(tool.name()).isEqualTo("object");
            assertThat(tool.name()).matches("[a-z_]+");
        }
    }

    @Test
    void approvalsCanOnlyBeRecordedWhenClaudeCodeIsTheOperator() {
        List<String> claude = names(new McpToolCatalog(OperatorMode.CLAUDE_CODE, mcp.tools));
        List<String> standalone = names(new McpToolCatalog(OperatorMode.STANDALONE, mcp.tools));

        assertThat(claude).contains("record_approval").hasSize(11);
        assertThat(standalone).doesNotContain("record_approval").hasSize(10);
    }

    private static List<String> names(McpToolCatalog catalog) {
        return StreamSupport.stream(catalog.tools().spliterator(), false).map(McpTool::name).toList();
    }
}
