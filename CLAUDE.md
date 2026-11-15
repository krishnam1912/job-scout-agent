# job-scout-agent

A Claude Code job-search agent: it finds openings on public job boards, scores them against your preferences, queues them, and applies to the ones you approve in chat.

## Start of every session
1. If `profile/profile.md` doesn't exist, run `/setup` before anything else.
2. Read `data/STATUS.md` and the last ~10 entries of `data/session-log.md`.
3. Read `profile/preferences.md` only when scoring, `profile/profile.md` only when background is needed. Dedupe with `state/seen.txt`.

## End of every session
- Append a dated entry to `data/session-log.md` and refresh `data/STATUS.md` (≤ 25 lines).
- If preferences changed, update `profile/preferences.md` and `profile/filters.json` (confirm with the user).

## Layout
| Path | In git? | Purpose |
|---|---|---|
| `scripts/scan.py`, `scripts/sources.json` | yes | No-LLM job-board scanner and the boards it reads |
| `templates/` | yes | Blank templates `/setup` copies from |
| `playbooks/*.md` | yes | Per-ATS form notes (site mechanics only, no personal answers) |
| `.claude/skills/` | yes | `/setup`, `/find-jobs`, `/apply-batch` |
| `profile/` | **no** | Your details, resume, preferences, filters, apply answers |
| `data/`, `state/`, `queue/` | **no** | Leads, applications, log, status, seen IDs, daily queues |

## Rules
- **Privacy:** personal details live only in `profile/` and `data/` (git-ignored). Never write them into tracked files, commits, URLs or search queries. Generic learnings about a job site go in `playbooks/`, without the user's answers.
- **Applications:** submit only in an interactive session, only for jobs the user approved in chat during that session (one message can approve a batch). Scheduled or unattended runs only find, score and queue.
- Never enter passwords, create accounts, solve CAPTCHAs, or enter bank/ID numbers; skip and list them for the user.
- Score fit % before submitting and log it in `data/applications.md`.
- Messages and emails are drafted only; the user sends them.
- Never fabricate resume content; only reword what's in `profile/profile.md` or the resume.

## Token budget
- Find jobs with `scripts/scan.py`; WebSearch only when the user asks for a company the scanner doesn't cover. Browser only for approved applications and actions that need the user's session.
- Caps: 10 leads per scan, 10 applications per batch, 5 referral drafts per batch.
