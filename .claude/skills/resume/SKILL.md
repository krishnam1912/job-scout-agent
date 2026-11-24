---
name: resume
description: Review, rewrite, tailor or update the user's resume. Use when the user types /resume (also /resume review, /resume redo, /resume tailor <job id>, /resume apply 1,3) or asks to review, improve, redo or update their resume or CV. Scores it against the jobs they scanned with scripts/profile_score.py, checks it against a fixed checklist, drafts numbered changes from real facts only, and edits the resume (Overleaf or a local file) only for the numbers the user approves.
---

# Resume

## Commands
| You type | What happens | Changes the resume? |
|---|---|---|
| `/resume` or `/resume review` | Score + checklist review, numbered suggestions | No |
| `/resume redo` | Full rewrite draft with old-vs-new score | No (draft only) |
| `/resume tailor <job id>` | Per-job emphasis draft from that job's description | No (draft only) |
| `/resume apply 1,3` / `apply all` | Applies exactly those numbered changes, recompiles, checks the page count | Yes, only those |
| `undo` | Restores the version from before the last apply (Overleaf: History) | Yes |

## Inputs
- `profile/profile.md`: resume link (Overleaf project or `profile/resume.pdf` / `.tex`), background, **Don't claim** list.
- `profile/skills.json`: `{"have": [...], "unconfirmed": [...], "dont_claim": [...]}` using names from `scripts/skills_catalog.json`. If it's missing, build it from the resume's skills section, show it to the user, and save it once they confirm.
- Facts may come only from the resume itself, `profile/profile.md` and what the user says in this chat. Never invent numbers, titles, dates or skills; write `[number?]` where a result needs a figure and ask.

## Steps
1. **Read the current resume (read-only):**
   - Overleaf: open the project in Claude in Chrome (the user's own session), open `main.tex` (or the main file), and copy its text with `get_page_text`. Don't edit anything during a review.
   - Local file: read `profile/resume.tex` / `.md`, or the PDF with the Read tool.
   - Save the text to `profile/resume-current.tex` (or `.txt`); `profile/` is git-ignored.
2. **Score:** `python3 scripts/profile_score.py profile/resume-current.tex` (add `--days 60` when fewer than 10 companies are in range). It prints weighted coverage plus four lists: *safe adds* (skills the user has that the text leaves out), *ask first* (unconfirmed), *show through results* (scalability, security, mentoring: prove them in bullets, don't list them) and *do not add* (market gaps: a study list, never the resume).
3. **Checklist** (one line each: ✅, ⚠️ or ❌):
   - One page for under ~8 years of experience; nothing past the page break.
   - Bullets lead with the result: action verb + what was built + tech + measurable result. Flag bullets with no result or a duty-only line ("responsible for…").
   - Current role first, with dates, titles and locations matching LinkedIn (`/linkedin review` keeps the copy in `profile/linkedin-current.txt`).
   - Skills ordered for the target role in `profile/preferences.md`; nothing from **Don't claim** or `dont_claim`.
   - ATS-safe: standard headings (Experience, Projects, Skills, Education), no text in images or tables, links that work.
   - No pay or personal details beyond name, email, phone, LinkedIn, GitHub; no typos or tense changes.
4. **Report** in chat: score line, checklist, then **numbered suggestions** (each: section · current text → proposed text · why · which fact backs it). Max 10. Stop and wait.
5. **Redo** (`/resume redo`): write the full rewrite to `profile/resume-drafts/YYYY-MM-DD.tex` (or `.md`), score old vs. new (`profile_score.py old new` prints gained/lost skills), and list every change as a numbered suggestion so `apply` can take any subset.
6. **Tailor** (`/resume tailor <job id>`): fetch the job with the `get_job` tool, then draft reordered bullets, a reordered skills line and at most 2 reworded bullets that mirror its wording, using existing facts only. Save to `profile/resume-drafts/<company>-<job id>.tex`. Never change the main resume for one job unless the user applies it.
7. **Apply** (`/resume apply 1,3`): only the numbers the user typed in this chat, with the exact text they last saw.
   - Overleaf: edit `main.tex` in Chrome, recompile, confirm the PDF preview is still one page and has no LaTeX errors. If it spills over, say which lines to trim; don't trim on your own.
   - Local file: keep a copy as `profile/resume-drafts/before-YYYY-MM-DD.tex`, then edit.
   - Downloading the PDF may open a Save-As dialog: leave that to the user.
   - Log what changed in `profile/resume-notes.md` (date, numbers applied, score before → after).

## Rules
- Review, redo and tailor never change the resume. Only `apply` with numbers from the user's own chat message does.
- Never add a skill that isn't in `have`; `ask first` items need the user's yes in chat, then update `profile/skills.json`.
- Keep personal details out of commands, URLs and search queries.
