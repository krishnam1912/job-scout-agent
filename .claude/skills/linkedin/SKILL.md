---
name: linkedin
description: Review, rewrite or update the user's LinkedIn profile. Use when the user types /linkedin (also /linkedin review, /linkedin redo, /linkedin apply 1,3) or asks to review, improve, redo or update their LinkedIn profile, headline, About, skills or Open to Work. Reads the live profile in the user's own Chrome, scores it against the jobs they scanned with scripts/profile_score.py, compares it with the resume, drafts numbered changes from real facts only, and edits the profile only for the numbers the user approves, without posting anything.
---

# LinkedIn

## Commands
| You type | What happens | Changes the profile? |
|---|---|---|
| `/linkedin` or `/linkedin review` | Reads the live profile, scores it, numbered suggestions | No |
| `/linkedin redo` | Full rewrite drafts: headline, About, experience, skills, Open to Work | No (drafts only) |
| `/linkedin apply 1,3` / `apply all` | Applies exactly those numbered changes in Chrome | Yes, only those |

## Inputs
- `profile/profile.md` (background, **Don't claim**), `profile/preferences.md` (target role, locations, work mode), `profile/skills.json` (see the `/resume` skill; build and confirm it if missing).
- The resume text in `profile/resume-current.tex` (run `/resume review` first if it's missing or older than the last resume change).
- Facts only from these files and the user's chat messages. No invented numbers, titles or skills.

## Steps
1. **Read the live profile (read-only)** in Claude in Chrome, the user's own signed-in session: open `https://www.linkedin.com/feed/`, then **Me → View profile**, and take the text with `get_page_text` (expand "see more" on About and Experience; open the Skills page if the list is cut). Save it to `profile/linkedin-current.txt` (git-ignored). Don't click Edit during a review.
2. **Score:** `python3 scripts/profile_score.py profile/linkedin-current.txt profile/resume-current.tex` compares profile vs. resume and prints what each is missing.
3. **Review table** (item · live now · suggestion):
   - **Headline:** title + top 3-4 stack keywords + niche; under 220 characters; matches the target role.
   - **About:** 3-5 short lines with 1-2 real results; no "open to work" line if the current employer can see the profile.
   - **Experience:** each role has 2-4 result-first bullets (reuse the resume's); titles, dates and locations match the resume; promotions shown as separate positions under one company.
   - **Skills:** in-demand skills the user has (`safe adds`) added; top skills pinned to the target stack; anything in **Don't claim** / `dont_claim` or unrelated removed.
   - **Open to Work:** recruiters-only by default, titles and locations from `profile/preferences.md`, work modes the user really wants.
   - **Featured, URL, photo/banner:** custom URL, one or two projects that are fine for the current employer to see.
4. **Report** in chat: score line, the table, then **numbered suggestions** with the exact new text (max 10). Stop and wait.
5. **Redo** (`/linkedin redo`): full drafts for every section, saved to `profile/linkedin-drafts/YYYY-MM-DD.md`, each as a numbered suggestion so `apply` can take any subset.
6. **Apply** (`/linkedin apply 1,3`): only the numbers the user typed in this chat, with the exact text they last saw.
   - Open the section's edit (pencil) dialog, paste the approved text, and **turn off "Notify network" / "Share with network"** before saving. Skip any share-a-post prompt.
   - Confirm the saved text on the profile page, then log it in `profile/linkedin.md` (date, number, old → new).
   - Stop and list the item instead if LinkedIn asks for a password, a verification code or a CAPTCHA, or shows a limit warning.

## Rules
- Never post, comment, react, message, send connection requests or change privacy/account settings from this skill. Messages are `/refer`.
- Never buy or start Premium or click upgrade prompts.
- Never add a skill that isn't in `have`; `ask first` items need the user's yes in chat.
- Open to Work stays recruiters-only unless the user says otherwise in chat. Remember the current employer can see the headline, About and Featured.
- Keep personal details out of commands, URLs and search queries.
