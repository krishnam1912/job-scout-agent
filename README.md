# job-scout-agent

A job-search agent for [Claude Code](https://claude.com/claude-code). It scans public job boards, scores openings against your preferences, keeps a daily shortlist, and fills in the applications you approve, all from a Claude Code session.

Your personal details never enter the repo: `/setup` asks for them on first run and saves them in git-ignored folders, and a pre-commit check blocks anything that looks personal.

## How it works
```
/find-jobs   scripts/scan.py reads public job-board APIs (no LLM tokens)
             → Claude scores the top 10 with your rubric
             → queue/YYYY-MM-DD.md (numbered shortlist)

/apply-batch you reply "apply 1,3,4" in chat (one approval per batch)
             → Claude fills and submits only those forms
             → passwords, CAPTCHAs, sign-ins, unusual questions are skipped and listed for you
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

## What's tracked vs. private
| Tracked in git | Local only (git-ignored) |
|---|---|
| `scripts/` scanner + job-board list | `profile/` details, resume, preferences, filters, answers |
| `templates/` blanks for `/setup` | `data/` leads, applications, log, status |
| `playbooks/` how each job site's form behaves | `state/` seen job IDs · `queue/` daily shortlists |
| `.claude/skills/` the three commands | `*.pdf`, `*.docx` |

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
