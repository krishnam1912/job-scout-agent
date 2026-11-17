# Roadmap

Principle: a **thick deterministic Java engine, thin LLM**. The engine owns state, lifecycle, dedupe, scheduling, retries, approvals and notifications. The LLM (Claude Code today; GPT or a local model later) only makes judgment calls through MCP tools or the `LlmProvider` interface.

| Phase | Scope |
|---|---|
| 0 | SQLite state engine, job lifecycle, 4-layer dedupe, discovery ported to Java, legacy import, exports, CLI, MCP server, LLM + channel extension points: [java-core-phase0.md](java-core-phase0.md) |
| 1a | Application adapters per ATS (Amazon, Greenhouse, Workday, SmartRecruiters, Lever…), `BrowserDriver` (Claude in Chrome / Playwright), answer bank |
| 1b | Retry + checkpoint runner, SUBMIT_UNKNOWN verification, JD extraction cache, scoring v2 with calibration |
| 2a | Asks inbox channels: Claude Remote Control (primary), Telegram bot (allowlisted chat, ticket-id callbacks) |
| 2b | Gmail read-only ingest → confirmations, OA invites, rejections |
| 2c | Standalone operator: LLM routing per task (Anthropic, OpenAI, Ollama / LM Studio) |
| 2d | `phoenixd` daemon (launchd), scheduler, worker loop, Playwright with a persistent profile |

Safety invariant across all phases: nothing is submitted or sent without an approval record created by the user through an authenticated channel. The LLM can never create one on its own.
