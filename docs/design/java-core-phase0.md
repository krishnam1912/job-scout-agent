# Java core, Phase 0: state engine, lifecycle, dedupe, discovery, MCP

Status: spec for the first Java build (2026-11-18). Builds on `docs/design/roadmap.md`, which is the summary below.

## Why
Today the LLM *is* the system: Claude edits CSV and Markdown files and remembers every rule. State drifts between files, an interrupted run can lose leads, and nothing works without Claude. Phase 0 moves everything deterministic into a Java engine. The LLM (Claude Code today) only makes judgment calls and talks to the engine through MCP tools.

## Scope
**In:** SQLite state with migrations · job lifecycle state machine with an event log · 4-layer dedupe · discovery adapters ported from `scripts/scan.py` · legacy importer · exporters (CSV/MD views) · CLI · MCP stdio server for Claude Code · extension points for other LLMs and for notification channels · one working OpenAI-compatible LLM provider (Ollama / LM Studio / OpenAI), disabled by default.

**Out (later phases):** browser form-filling adapters, retry/checkpoint runner, Gmail ingest, Telegram implementation, background daemon/launchd, scoring v2. Leave clean interfaces where these plug in, but **no dead code or empty stub classes**.

## Hard rules
- **No personal data in this repo, ever.** Tests use hand-written synthetic fixtures. The engine reads personal data only at runtime from `PHOENIX_HOME`. The pre-commit privacy check (`scripts/check_private.py`) must pass.
- Don't modify `scripts/*.py` or the existing skills' behaviour until the parity check passes (see "Parity").
- In MCP mode **stdout carries only JSON-RPC**: no Spring banner, no logs. Logs go to stderr and `$PHOENIX_HOME/logs/phoenix.log`.
- Engine-level safety: nothing in Phase 0 submits applications or sends messages. Approvals are records the engine checks. In `claude-code` operator mode, the MCP tool `record_approval` exists because the approval was typed by the user in the Claude chat. In any other operator mode, that tool is not registered (future channels such as Telegram or the CLI create approvals instead).

## Tech
- **Java 21**, **Spring Boot 3.x** (a stable release at least a few months old), **Maven Wrapper** (`./mvnw`, committed), single module.
- **SQLite** via `org.xerial:sqlite-jdbc`, WAL mode, foreign keys on. **Flyway** for migrations if its SQLite support works with the chosen version; otherwise a tiny migration runner (classpath `db/migration/V*.sql` + `schema_version` table). Data access via Spring `JdbcClient`; no JPA.
- **picocli** for the CLI. **Jackson** for JSON. **Jsoup** for HTML-to-text. `java.net.http.HttpClient` for HTTP.
- **MCP:** prefer the official MCP Java SDK (`io.modelcontextprotocol.sdk`), stdio transport. If it can't be resolved or is unusable with this setup, write a minimal JSON-RPC 2.0 stdio server (`initialize`, `notifications/initialized`, `ping`, `tools/list`, `tools/call`) behind an interface, so it can be swapped for the SDK later. Note the choice in the build notes.
- **Tests:** JUnit 5 + AssertJ. HTTP goes through an `HttpFetcher` interface with a fake in tests (no live network in unit tests). SQLite tests use a temp file DB.
- Packaging: `target/phoenix.jar` (Spring Boot fat jar). Launcher script `bin/phoenix` (bash) that runs `java -Xmx384m -jar target/phoenix.jar "$@"`.

## Layout
```
pom.xml, mvnw, .mvn/
bin/phoenix
src/main/java/dev/phoenix/
  PhoenixApplication.java          entry: picks mode from args (cli | mcp)
  config/                          PhoenixHome, PhoenixProperties (phoenix.yml), OperatorMode
  domain/                          records: Job, JobGroup, Score, Application, Event, Ask, Approval, Posting; enum JobState
  lifecycle/                       Transitions (allowed table), LifecycleService, NextActionPolicy
  store/                           Migrations, repositories (one per aggregate), TxRunner
  discovery/                       DiscoveryAdapter + Greenhouse/Lever/Ashby/SmartRecruiters/Amazon/Atlassian,
                                   Filters (port of scan.py filters + prescore), ScanService, SourceHealth
  dedupe/                          ExactKey, MinHash, GroupingService, CooldownGuard
  legacy/                          LegacyImporter (CSV/MD/seen/queue/referrals)
  export/                          LeadsCsvExporter, ApplicationsMdExporter, StatusMdExporter, QueueMdExporter
  llm/                             LlmProvider (interface), LlmRequest/LlmResponse, OpenAiCompatibleProvider,
                                   LlmRouter (task → provider from config), JsonSchemaGuard
  channel/                         NotificationChannel (interface), AskService, ClaudeSessionChannel
  entry/cli/                       picocli commands
  entry/mcp/                       McpServer + tool classes (thin: validate input → call a service → small JSON)
src/main/resources/db/migration/V1__init.sql
src/test/java/... + src/test/resources/fixtures/ (synthetic only)
```
Keep classes small and single-purpose; constructor injection only; records for data; no static mutable state.

## PHOENIX_HOME and config
- `PHOENIX_HOME` (env var or `--home`). Default `~/.phoenix`. The DB is at `$PHOENIX_HOME/state/phoenix.db`.
- Optional `$PHOENIX_HOME/phoenix.yml`:
```yaml
operator: claude-code            # claude-code | standalone
sources: scripts/sources.json    # relative to PHOENIX_HOME, or absolute
filters: profile/filters.json    # filters + prescore (same JSON shape as templates/filters.example.json)
seen-file: state/seen.txt        # legacy, import only
legacy:                          # optional, for `phoenix import-legacy`
  leads-csv: data/leads.csv
  applications-md: data/applications.md
  referrals-csv: data/referrals.csv
  queue-dir: queue
dedupe:
  similarity-threshold: 0.9
  company-cooldown-days: 60
llm:                             # only used when operator = standalone (or for explicit `phoenix llm-test`)
  providers:
    local: {type: openai-compatible, base-url: "http://localhost:11434/v1", model: "qwen2.5:3b", api-key-env: ""}
    openai: {type: openai-compatible, base-url: "https://api.openai.com/v1", model: "gpt-4o-mini", api-key-env: "OPENAI_API_KEY"}
  routing: {extract: local, score: openai, answer: openai}
```
Validate on startup: fail fast with a clear message if a referenced file is missing or the YAML is invalid.

## Schema (V1__init.sql)
- `jobs(id INTEGER PK, source TEXT, company TEXT, ext_id TEXT, url TEXT, title TEXT, location TEXT, group_id INTEGER, state TEXT, jd_text TEXT, jd_hash TEXT, prescore INTEGER, first_seen TEXT, last_seen TEXT, closed_at TEXT, closing_date TEXT, next_action_at TEXT, UNIQUE(source, company, ext_id))`
- `job_groups(id PK, company TEXT, norm_title TEXT, minhash BLOB, created_at TEXT)`
- `scores(id PK, job_id FK, scorer TEXT, score REAL, fit_pct INTEGER, rationale TEXT, rubric_version TEXT, scored_at TEXT)`
- `applications(id PK, job_id FK UNIQUE, state TEXT, attempts INTEGER, blocker_type TEXT, blocker_detail TEXT, resume_version TEXT, submitted_at TEXT, confirmation_ref TEXT, note TEXT)`
- `events(id PK, job_id FK, from_state TEXT, to_state TEXT, actor TEXT, note TEXT, at TEXT)`: append-only
- `runs(id PK, kind TEXT, status TEXT, started_at TEXT, finished_at TEXT, checkpoint TEXT)`
- `asks(id PK, job_id FK NULL, kind TEXT, prompt TEXT, options TEXT, priority INTEGER, status TEXT, answer TEXT, channel TEXT, created_at TEXT, due_at TEXT, answered_at TEXT)`
- `approvals(id PK, scope TEXT, granted_via TEXT, user_words TEXT, granted_at TEXT, expires_at TEXT, consumed_at TEXT)`
- `answers(id PK, question_norm TEXT UNIQUE, answer TEXT, source TEXT, updated_at TEXT)`
- `referrals(id PK, name TEXT, connection TEXT, company TEXT, job_ref TEXT, status TEXT, last_contact TEXT, follow_up TEXT, note TEXT)`
- `source_health(source TEXT, board TEXT, last_ok TEXT, last_count INTEGER, consecutive_failures INTEGER, PRIMARY KEY(source, board))`
Timestamps are ISO-8601 UTC strings. Add indexes for `jobs(state, next_action_at)`, `jobs(group_id)` and `events(job_id)`.

## Lifecycle
`JobState`: DISCOVERED, FILTERED_OUT, CANDIDATE, SCORED, NEAR_MISS, BELOW, QUEUED, APPROVED, IN_PROGRESS, SUBMITTED, BLOCKED, FAILED, SUBMIT_UNKNOWN, SKIPPED, OA, INTERVIEW, REJECTED, OFFER, GHOSTED, CLOSED, EXPIRED.
Allowed transitions (one table in code, one test per row and for every illegal pair):
```
DISCOVERED → FILTERED_OUT | CANDIDATE
CANDIDATE → SCORED
SCORED → NEAR_MISS | BELOW | QUEUED
NEAR_MISS | BELOW → SCORED (re-score on rubric change)
QUEUED → APPROVED | SKIPPED
APPROVED → IN_PROGRESS | SKIPPED
IN_PROGRESS → SUBMITTED | BLOCKED | FAILED | SUBMIT_UNKNOWN | SKIPPED
BLOCKED | FAILED → IN_PROGRESS | SKIPPED
SUBMIT_UNKNOWN → SUBMITTED | IN_PROGRESS
SUBMITTED → OA | INTERVIEW | REJECTED | GHOSTED
OA → INTERVIEW | REJECTED     INTERVIEW → OFFER | REJECTED
any non-terminal → CLOSED | EXPIRED
```
Terminal: FILTERED_OUT, SKIPPED, REJECTED, OFFER, GHOSTED, CLOSED, EXPIRED. `LifecycleService.transition(jobId, to, actor, note)` updates the state, writes an event and recomputes `next_action_at` (from `NextActionPolicy`: QUEUED +2d, BLOCKED +1d, SUBMITTED +21d → GHOSTED candidate, a closing date within 72h → urgent), all in one transaction. An illegal transition throws a typed exception.

Score bands (fit % = score / 5 × 100): ≥ 3.5 → QUEUED, 3.0–3.49 → NEAR_MISS, below → BELOW.

## Dedupe (4 layers, `dedupe/`)
1. **Exact:** `UNIQUE(source, company, ext_id)`. A re-seen posting only updates `last_seen`.
2. **Cross-source:** same company (normalised) + normalised title + MinHash Jaccard ≥ threshold on 5-word JD shingles (64–128 hash functions, seeded, deterministic) → same `group_id`.
3. **Multi-req:** identical normalised title + JD similarity ≥ threshold at the same company → same group. The queue shows one job per group (best prescore, then newest), and the others stay attached so they never resurface as new.
4. **Already-applied guard:** `CooldownGuard.check(job)` returns BLOCKED_BY_GROUP (a group member already at APPROVED or later) or BLOCKED_BY_COOLDOWN (a job at the same company with title similarity ≥ threshold applied within `company-cooldown-days`). Enforce it in `approve`/`start_application`.

## Discovery (port of `scripts/scan.py`)
- `scripts/scan.py` in this repo is the reference. Port fetchers (Greenhouse, Lever, Ashby, SmartRecruiters with detail cap, Amazon with pagination + dedupe, Atlassian), `strip_html`, `kw_regex` semantics (whole word, case-insensitive, optional `s`/`es` suffix), `required_years`, `summarize`, filters and `prescore` exactly. Fetch boards in parallel with virtual threads; 15s timeout; one failing board = a warning + `source_health` update, never a failed run.
- `ScanService.scan(limit, companyFilter, dryRun)`: fetch → upsert jobs (DISCOVERED) → filters (FILTERED_OUT with reason, or CANDIDATE) → dedupe/grouping → return the top N CANDIDATEs by prescore (one per group), with `jd_summary` ≤ 1500 chars. Dry-run writes nothing.
- Liveness: after a successful board fetch, any open job from that board not seen for 2 consecutive successful scans → CLOSED (actor `scanner`, note "posting gone").

## Legacy importer (`phoenix import-legacy`)
- Reads the legacy files named in `phoenix.yml`:
  - leads CSV (header `Date found,Company,Role,Job ID,Location,Work mode,Score,Why it fits,Gaps,URL,Referral angle,Status,Notes`);
  - applications Markdown (pipe tables: take rows that contain a date and a company; job IDs are free text, so extract them with a regex and keep the raw row in the note);
  - seen.txt (`company:id` per line);
  - queue Markdown tables;
  - referrals CSV.
- Map statuses: `Applied*` / Submitted → SUBMITTED; `New` → QUEUED if score ≥ 3.5; `Skipped` → SKIPPED; `Blocked*` → BLOCKED; a seen-only ID → SCORED/BELOW placeholder (title unknown) so it dedupes. An applications row wins over a CSV status.
- **Idempotent:** a re-run adds nothing new. Print a reconciliation summary (rows read per file, jobs created/updated, by state, unparsed rows with line numbers) and exit non-zero if any row was silently dropped.
- **Never writes to legacy files.**

## Exporters (`phoenix export`)
Write to `$PHOENIX_HOME/exports/`: `leads.csv` (same header as legacy), `applications.md`, `STATUS.md` (≤ 25 lines: counts by state, open asks, at-risk jobs = QUEUED > 2d, BLOCKED > 1d, closing ≤ 72h), `queue.md` (numbered: # · Score · Company · Role · Location · Fit % · URL). Nothing outside `exports/` is overwritten in Phase 0.

## CLI (`bin/phoenix …`)
`import-legacy` · `scan [--limit N] [--company X] [--dry-run]` · `queue [--min-fit 80] [--limit 10]` · `job <id>` · `transition <id> <STATE> --note` · `status` · `export` · `asks` · `llm-test --task extract --text "..."` (exercises the configured provider) · `mcp` (starts the MCP server). Exit codes: 0 ok, 1 user error, 2 runtime error.

## MCP tools (Claude Code), with compact JSON outputs
| Tool | Does |
|---|---|
| `scan_jobs {limit≤10, company?, dry_run?}` | runs ScanService, returns candidates `{job_id, company, title, location, url, prescore, jd_summary}` |
| `record_scores {items:[{job_id, score, rationale, rubric_version}]}` | stores scores, moves to QUEUED / NEAR_MISS / BELOW |
| `get_queue {min_fit?, limit?}` | the global queue, one per group, ordered by fit, closing urgency, age |
| `get_job {job_id}` | full record incl. JD text, group, events |
| `record_approval {job_ids? , rule?, user_words}` | (claude-code mode only) stores the approval exactly as the user typed it |
| `start_application {job_id}` | checks approval + CooldownGuard → IN_PROGRESS, or returns the reason it can't |
| `record_application_result {job_id, outcome: submitted/blocked/failed/submit_unknown/skipped, blocker_type?, detail?, confirmation_ref?}` | transitions, and creates an ask for blocked jobs |
| `list_asks {status?}` / `answer_ask {ask_id, answer}` | the asks inbox |
| `status {}` | the STATUS.md content as JSON |
| `mark_outcome {job_id, outcome: oa/interview/rejected/offer}` | post-submit states |
Tool classes stay thin (validate input → call a service → map the result). Put the tool descriptions where Claude sees them and keep them precise. Add `.mcp.json` at the repo root: `{"mcpServers":{"phoenix":{"command":"bin/phoenix","args":["mcp"]}}}`.

## LLM extension point (`llm/`)
- `LlmProvider { String name(); LlmResponse complete(LlmRequest req); }`. `LlmRequest` = system prompt, user text, optional JSON schema (as a Jackson `JsonNode`), temperature, max tokens.
- `OpenAiCompatibleProvider`: POST `{base-url}/chat/completions`; with a schema, use `response_format` json_schema (fall back to `json_object` + the schema in the prompt). Validate the response against the schema (`JsonSchemaGuard`) and retry at most 2× with the validation error appended. API key from the env var named in config (never logged). Works with Ollama (`http://localhost:11434/v1`), LM Studio and OpenAI.
- `LlmRouter.forTask(Task)` → provider per `routing`. In `claude-code` mode, `forTask` returns `Optional.empty()`: judgment is done by the operator (Claude) through MCP tools.
- Test with a fake `HttpFetcher`; `phoenix llm-test` for manual checks against a real local model.

## Channel extension point (`channel/`)
- `NotificationChannel { String id(); boolean deliver(Ask ask); }`. `AskService` stores asks and fans out to enabled channels.
- `ClaudeSessionChannel`: delivery = "pull": asks are fetched by Claude via `list_asks`, and Claude relays them through Remote Control / push notifications. `deliver` just marks the ask as offered.
- Telegram comes later as another `NotificationChannel` + an approval source. Document it in `docs/design/channels.md` (how it plugs in, the chat_id allowlist, the ticket-id callback rule). Don't write a stub class.

## Parity (Python → Java)
On the same day, run `python3 scripts/scan.py --dry-run --limit 200 --filters <filters>` and `bin/phoenix scan --dry-run --limit 200` with the same sources and filters. Compare the sets of `company:job_id` (and the prescore per job). Write the result to `docs/build-notes/phase0.md`. Target: identical sets; any differences explained and fixed or justified. Only after parity: update `.claude/skills/find-jobs` and `apply-batch` to use the MCP tools (keep a one-line fallback note pointing to the Python script) and update `CLAUDE.md` / `README.md` (Java engine section, build and run instructions).

## Quality bar
- `./mvnw verify` green. Tests cover: every allowed and illegal transition, NextActionPolicy, MinHash determinism and threshold behaviour, grouping, CooldownGuard, filters + prescore against fixtures copied from scan.py behaviour, the importer (synthetic legacy files incl. a malformed row), exporters (golden files), the MCP tool contract (JSON in/out for each tool), the OpenAiCompatibleProvider (schema retry), and config validation.
- SOLID, small classes, no God services, no swallowed exceptions (log + rethrow or return a typed result), transactions around multi-row writes, idempotent commands, no secrets or personal data in code, logs or fixtures.
- Public interfaces get a short Javadoc explaining *why*. No commented-out code, no TODOs without an issue-style note in the build notes.
- `docs/build-notes/phase0.md`: what was built, the MCP SDK vs custom choice, the parity result, test counts, known gaps. No personal data.
