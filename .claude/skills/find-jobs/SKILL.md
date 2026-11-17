---
name: find-jobs
description: Scout new job openings through the phoenix MCP tools (public job-board APIs, no web search), score them with the rubric in profile/preferences.md and record the scores so the engine queues the good ones. Use when asked to find, suggest, scout or queue jobs. Finds and queues only; never applies.
---

# Find Jobs

Optional args: a count, a company, or `discover` (`/find-jobs 5`, `/find-jobs amazon`, `/find-jobs discover`). Default: top 10 new jobs (cap 10 per run).

## Discover (search beyond the listed boards)
Run this when the user says `discover` / "search outside" / "more companies", or when a normal scan gives fewer than 5 jobs scoring ≥ 3.5.
1. WebSearch (standard mode), at most 5 queries, built only from `profile/preferences.md` role, stack, location and company types, e.g. `"backend engineer" java hiring Bengaluru site:boards.greenhouse.io`, `site:jobs.lever.co`, `site:jobs.ashbyhq.com`. No name, pay or contact details in queries.
2. Collect company names not already in the engine's sources file (`scripts/sources.json`) or `profile/sources.local.json` (max 15), skipping deal-breaker companies.
3. `python3 scripts/find_board.py "<Company A>" "<Company B>" …` finds each one's public board.
4. Add the hits to `profile/sources.local.json` (`{"greenhouse": [...], "lever": [...], "ashby": [...], "smartrecruiters": [...]}`) and tell the user which companies were added. Companies with no public board: list them as "apply via careers page" with the search-result link, but **don't log a job from a search result unless its posting page is fetched with curl and confirmed live**.
5. Continue with step 2 below, so the new boards get scanned in the same run (the engine reads `profile/sources.local.json` under its home).

## Steps
0. If `profile/profile.md` doesn't exist, stop and run `/setup` first.
1. **Context (small):** call the `status` tool (counts, open asks, jobs at risk) and read `profile/preferences.md`. The engine keeps all state and dedupes for you; don't read CSV or log files.
2. **Scan:** call `scan_jobs` with `{limit: 10}`.
   - Count arg → `limit` (≤ 10). Company arg → `company`. Preview only → `dry_run: true`.
   - It returns `candidates` (`job_id, ext_id, company, title, location, url, prescore, jd_summary`) and `stats` (including `failed_boards`; report them). Candidates stay on offer until scored.
   - Don't use WebSearch, WebFetch, curl or a browser to find jobs. If nothing comes back, say so.
3. **Score** each candidate 1-5 with the rubric in `profile/preferences.md`, using only the scan output (call `get_job` when the summary is not enough). Never invent details; write "not stated" for work mode / pay and say what you assumed. A deal-breaker caps the score at 2.
4. **Record:** call `record_scores` once with `{items: [{job_id, score, rationale, rubric_version}]}`. Put the main fit, the main gap and a referral angle (alumni / ex-colleague) in `rationale`, at most 40 words. The engine queues scores ≥ 3.5, keeps 3.0-3.49 as near misses and files the rest as below.
5. **Show the queue:** call `get_queue` and print it as a table: `# · Score · Company · Role · Location · Fit % · URL`, best first, ⭐ for ≥ 4, with one line per row for the gap and referral angle. If the user wants files, run `bin/phoenix export` (writes `queue.md`, `leads.csv`, `applications.md`, `STATUS.md` under the engine home).
6. **Report** the table in chat, how many scored below 3.5, failed boards, and end with: "Reply `apply 1,3,4`, `apply all ≥4` or `apply 1-5` in this session to start /apply-batch."

Fallback: if the `phoenix` MCP server is not available (no jar or no Java 21), use the Python flow instead: `python3 scripts/scan.py --limit 10 --mark-seen`, log leads to `data/leads.csv` and write `queue/YYYY-MM-DD.md` by hand.

## Rules
- Never submits, sends or emails anything. Applying is `/apply-batch`, after the user approves numbers in chat.
- Scheduled / unattended runs stop after step 5.
- Never call `record_approval`; approvals come only from the user's own message in `/apply-batch`.
- Personal details never go into commands, URLs or queries.
