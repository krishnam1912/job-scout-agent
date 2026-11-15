---
name: find-jobs
description: Scout new job openings by running scripts/scan.py (public job-board APIs, no web search), score them with the rubric in profile/preferences.md, log leads ≥ 3.5 to data/leads.csv and write the day's approval queue to queue/YYYY-MM-DD.md. Use when asked to find, suggest, scout or queue jobs. Finds and queues only; never applies.
---

# Find Jobs

Optional args: a count, a company, or `discover` (`/find-jobs 5`, `/find-jobs amazon`, `/find-jobs discover`). Default: top 10 new jobs (cap 10 per run).

## Discover (search beyond the listed boards)
Run this when the user says `discover` / "search outside" / "more companies", or when a normal scan gives fewer than 5 jobs scoring ≥ 3.5.
1. WebSearch (standard mode), at most 5 queries, built only from `profile/preferences.md` role, stack, location and company types, e.g. `"backend engineer" java hiring Bengaluru site:boards.greenhouse.io`, `site:jobs.lever.co`, `site:jobs.ashbyhq.com`. No name, pay or contact details in queries.
2. Collect company names not already in `scripts/sources.json` or `profile/sources.local.json` (max 15), skipping deal-breaker companies.
3. `python3 scripts/find_board.py "<Company A>" "<Company B>" …` finds each one's public board.
4. Add the hits to `profile/sources.local.json` (`{"greenhouse": [...], "lever": [...], "ashby": [...], "smartrecruiters": [...]}`) and tell the user which companies were added. Companies with no public board: list them as "apply via careers page" with the search-result link, but **don't log a job from a search result unless its posting page is fetched with curl and confirmed live**.
5. Continue with step 2 below, so the new boards get scanned in the same run.

## Steps
0. If `profile/profile.md` doesn't exist, stop and run `/setup` first.
1. **Context (small):** read `data/STATUS.md` and `profile/preferences.md`. Don't read the whole leads CSV or session log; dedupe is done by `state/seen.txt`.
2. **Scan:** `python3 scripts/scan.py --limit 10 --mark-seen`
   - Count arg → `--limit N` (≤ 10). Company arg → `--company <name>`. Preview only → `--dry-run`.
   - Stdout: JSON lines (`company, job_id, title, location, url, prescore, jd_summary`). Stderr: warnings + `SUMMARY`. Report failed sources.
   - Don't use WebSearch, WebFetch, curl or a browser to find jobs. If the scan returns nothing, say so.
3. **Score** each job 1–5 with the rubric in `profile/preferences.md`, using only the scan output. Never invent details; write "not stated" for work mode / pay and say what you assumed. A deal-breaker caps the score at 2.
4. **Log** every job ≥ 3.5 to `data/leads.csv` (Status `New`). Columns: `Date found,Company,Role,Job ID,Location,Work mode,Score,Why it fits,Gaps,URL,Referral angle,Status,Notes`. Display names for companies; quote fields with commas.
5. **Queue:** write `queue/YYYY-MM-DD.md` (append `## Run 2` with continued numbering if it exists):
   - Table: `# · Score · Company · Role · Location · Fit % · URL` (Fit % = score ÷ 5 × 100), best first, ⭐ for ≥ 4.
   - One line per number: main gap and a referral angle (≤ 20 words each).
   - Footer: jobs printed, how many scored < 3.5, failed sources.
6. **Report** the table in chat and end with: "Reply `apply 1,3,4`, `apply all ≥4` or `apply 1-5` in this session to start /apply-batch."
7. **Memory:** one short dated entry in `data/session-log.md`; update the counts line in `data/STATUS.md`.

## Rules
- Never submits, sends or emails anything. Applying is `/apply-batch`, after the user approves numbers in chat.
- Scheduled / unattended runs stop after step 5.
- Personal details never go into commands, URLs or queries.
