# job-scout-agent

A job-search agent for [Claude Code](https://claude.com/claude-code). It scans public job boards, scores openings against your preferences, keeps a daily shortlist, and fills in the applications you approve, all from a Claude Code session.

Your personal details never enter the repo: `/setup` asks for them on first run and saves them in git-ignored folders, and a pre-commit check blocks anything that looks personal.

## How it works
```
/find-jobs   scripts/scan.py reads public job-board APIs (no LLM tokens)
             → Claude scores the top 10 with your rubric
             → queue/YYYY-MM-DD.md (numbered shortlist)

/apply-batch you reply "apply 1,3,4" or type `/apply-batch ≥80` (one approval per batch)
             → Claude fills and submits only those forms
             → passwords, CAPTCHAs, sign-ins, unusual questions are skipped and listed for you

/refer       scripts/refer_candidates.py picks follow-ups due + companies with no referrer yet
             → Claude checks LinkedIn replies, finds alumni / ex-colleagues, drafts ≤ 5 messages
             → sends only the ones you approve ("send 1,3")
```

## Quick start
1. Install [Claude Code](https://claude.com/claude-code) and Python 3 (no packages needed).
2. Clone and open the folder in Claude Code:
   ```bash
   git clone https://github.com/<you>/job-scout-agent.git && cd job-scout-agent && claude
   ```
3. Run `/setup`. It asks for:
   - your resume (Overleaf link and/or PDF), LinkedIn, GitHub
   - your background, target roles, stack, locations, comp floor, target companies, deal-breakers
   - standard application answers (notice period, work authorisation, self-ID defaults)
4. Run `/find-jobs`, open `queue/<today>.md`, then reply `apply 1,2` to start `/apply-batch`.

## Java engine
The deterministic parts (state in SQLite, the job lifecycle, four layers of de-duplication, discovery from the public job boards, approvals) run in a Java 21 + Spring Boot engine. Claude Code only makes the judgment calls, through MCP tools. Other models (GPT, Ollama, LM Studio) and chat channels (Telegram) are extension points, not requirements.

```bash
./mvnw -q package                      # builds target/phoenix.jar (needs Java 21)
bin/phoenix status                     # counts, open asks, jobs at risk
bin/phoenix scan --dry-run --limit 5   # preview candidates; writes nothing
bin/phoenix import-legacy              # one-off import of the old CSV/Markdown files
bin/phoenix export                     # leads.csv, applications.md, STATUS.md, queue.md
```

- **Data and config:** everything personal lives in `$PHOENIX_HOME` (default `~/.phoenix`, or pass `--home DIR`). Start from `templates/phoenix.example.yml`; pointing `PHOENIX_HOME` at this folder reuses `profile/`, `state/` and `data/`.
- **Claude Code:** `.mcp.json` registers the `phoenix` MCP server (`bin/phoenix mcp`). `/find-jobs` and `/apply-batch` call its tools: `scan_jobs`, `record_scores`, `get_queue`, `get_job`, `record_approval`, `start_application`, `record_application_result`, `list_asks`, `answer_ask`, `status`, `mark_outcome`.
- **Safety:** nothing is submitted or sent without an approval record the user created in chat; `start_application` also refuses roles already applied to. Design: [docs/design/java-core-phase0.md](docs/design/java-core-phase0.md), channels: [docs/design/channels.md](docs/design/channels.md).
- **Python fallback:** `scripts/scan.py` still works on its own (below).

## Commands
Type these in a Claude Code session opened in this folder (or from the Claude phone app over Remote Control).

| Command | What it does | Sends or submits? |
|---|---|---|
| `/setup` | First-run onboarding: resume, LinkedIn, preferences, standard answers into `profile/` | No |
| `/find-jobs` · `/find-jobs 5` · `/find-jobs <company>` · `/find-jobs discover` | Scans the boards, scores with your rubric, queues the good ones | No |
| `apply 1,3,4` · `apply all ≥4` · `/apply-batch ≥80` | Fills and submits only the queued jobs you name | Yes, only those |
| `/refer` · `/refer followups` · `/refer <company>` | Follow-ups due + alumni / ex-colleague referrers at companies you applied to; up to 5 drafts | No |
| `send 1,3` · `send all` · `edit 2: <change>` · `skip 4` · `hold <name>` | Acts on the drafts `/refer` just showed | `send` only |

Plain scripts (no LLM, no tokens):
```bash
python3 scripts/scan.py --dry-run --limit 5          # preview a scan
python3 scripts/refer_candidates.py                  # who to follow up / which companies need a referrer
bin/phoenix export                                   # refresh queue.md, applications.md, STATUS.md
python3 scripts/check_private.py --all               # privacy check
python3 -m unittest scripts/test_refer_candidates.py # referral script tests
```

## Run it from your phone
Start runs, answer questions and approve applications from the Claude mobile app or any browser. Use Chrome Remote Desktop for sign-ins and CAPTCHAs. Step-by-step: [docs/remote-control.md](docs/remote-control.md).

## What's tracked vs. private
| Tracked in git | Local only (git-ignored) |
|---|---|
| `scripts/` Python scanner + job-board list · `src/` Java engine | `profile/` details, resume, preferences, filters, answers |
| `templates/` blanks for `/setup` | `data/` leads, applications, log, status |
| `playbooks/` how each job site's form behaves | `state/` seen job IDs · `queue/` daily shortlists |
| `.claude/skills/` the four commands | `*.pdf`, `*.docx` |

`scripts/check_private.py` runs before every commit (`/setup` installs it with `scripts/install-hooks.sh`). It blocks commits that include private folders, emails, phone numbers, LinkedIn/Overleaf URLs or anything in `profile/private-terms.txt`. Run it anytime with `python3 scripts/check_private.py --all`.

## Job boards scanned
Greenhouse, Lever and SmartRecruiters company boards (edit `scripts/sources.json`), plus Amazon and Atlassian. Workday, Oracle HCM, iCIMS and Eightfold need a signed-in browser, so they're apply-only.

```bash
python3 scripts/scan.py --dry-run --limit 5      # preview, writes nothing
python3 scripts/scan.py --company stripe         # one board
```

## Safety rules the agent follows
- Applies only to jobs you approve **in chat, in that session**. Scheduled runs only find and queue.
- Never enters passwords, creates accounts, solves CAPTCHAs or enters ID/bank numbers.
- Never sends messages or emails; it drafts them for you.
- Never invents resume content.

## License
MIT
