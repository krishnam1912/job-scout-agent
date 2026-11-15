---
name: setup
description: First-run onboarding for job-scout-agent. Asks the user for their resume link (Overleaf or PDF), LinkedIn, background, job preferences and standard application answers, then writes them to the git-ignored profile/ and data/ folders. Use when profile/profile.md does not exist, or when the user asks to set up, re-run setup, or change their profile.
---

# Setup (first run)

Everything collected here is personal. It goes only into `profile/` and `data/`, which `.gitignore` excludes. Never write it anywhere else in the repo, never put it in a commit, a URL or a search query.

## 0. Check
- If `profile/profile.md` exists, ask whether to **update** specific sections or **start over**. Don't overwrite without a yes.
- Confirm `.gitignore` still lists `profile/`, `data/`, `state/`, `queue/` and `*.pdf`. If not, stop and tell the user.

## 1. Ask, in four short rounds
Ask one round at a time in chat (or with the question tool where choices fit). Accept "skip" for anything optional. Never guess a value; leave it blank and mark it `TODO`.

**Round 1: identity and links**
- Full name, email, phone (with country code), city / current address (only if they want forms auto-filled)
- LinkedIn URL, GitHub / portfolio URL (optional)
- Resume: an Overleaf project link **and/or** a path to the PDF. If a PDF path is given, copy it to `profile/resume.pdf`.

**Round 2: background** (or offer to read it from the resume PDF and confirm each fact with them)
- Current role, company, start date; previous roles with dates
- Main stack and domains; education (degree, college, year)
- Years of full-time experience (excluding internships)
- Anything they **don't** want claimed (skills used lightly etc.)

**Round 3: what they're looking for**
- Target titles / levels (e.g. SDE-2, Backend Engineer, SWE II)
- Preferred stack (strongly preferred / acceptable)
- Locations and work mode (remote / hybrid / office); exceptions
- Compensation floor (stays local, used only for scoring)
- Target companies by tier; companies or company types to avoid
- Deal-breakers; domain bonuses
- Leads per run (default 10, max 10)

**Round 4: standard application answers**
- Notice period; current and expected compensation (or "prefer not to say")
- Work authorisation per country; sponsorship needed?
- Conflicts of interest / relatives at employers / government service
- Self-ID defaults (gender, race, disability, veteran): default **Prefer not to say** unless they choose otherwise
- Optional marketing consents: default unticked
- Whether Claude may read email verification codes during an approved batch (default: **no, ask each batch**)

## 2. Write the files (from `templates/`)
| File | From | Contents |
|---|---|---|
| `profile/profile.md` | `templates/profile.example.md` | Rounds 1–2 |
| `profile/preferences.md` | `templates/preferences.example.md` | Round 3 + the scoring rubric |
| `profile/apply-kit.md` | `templates/apply-kit.example.md` | Round 4 + name/email/phone/links/resume path |
| `profile/filters.json` | `templates/filters.example.json` | Tune `title_include`, `locations`, `jd_include`, `max_min_years`, `tier1`/`tier2`, `location_bonus`, `amazon.country` / `smartrecruiters_country` to Round 3 |
| `profile/private-terms.txt` | — | One per line: full name, email, phone, street address, LinkedIn URL, Overleaf URL. Used by `scripts/check_private.py` to block commits that contain them |
| `data/leads.csv` | `templates/leads.example.csv` | Header row only |
| `data/applications.md` | — | `# Applications` + table header `| Date | Company | Role | Job ID | Fit % | Status | Note |` |
| `data/session-log.md` | — | `# Session log` + a dated "Setup done" entry (no personal values in it) |
| `data/STATUS.md` | — | ≤ 25 lines: pipeline counts (0), next action "/find-jobs" |
| `state/seen.txt` | — | Empty, or `company:jobid` lines for jobs they've already applied to (ask) |

## 3. Finish
- Run `bash scripts/install-hooks.sh` so the privacy check runs before every commit.
- Run `python3 scripts/scan.py --dry-run --limit 5` and show the result count, to prove the filters work.
- Show a short summary of what was saved (no secrets echoed back: say "phone saved", not the number) and suggest `/find-jobs`.

## Rules
- Never ask for or store passwords, OTPs, bank/ID numbers (PAN, SSN, passport). If a form needs those later, the user enters them.
- Don't invent resume facts. Only what the user says or confirms from their resume.
