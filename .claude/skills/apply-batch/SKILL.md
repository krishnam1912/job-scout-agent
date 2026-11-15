---
name: apply-batch
description: Apply to a batch of queued jobs after the user approves them in chat. Shows the latest queue/YYYY-MM-DD.md, waits for the user's approval message (e.g. "apply 1,2,4" or "apply all ≥4"), then submits only those jobs using the per-ATS playbooks, skipping and listing blockers, and logs everything. Interactive sessions only.
---

# Apply Batch

Interactive sessions only, with the user present in chat. Never run from a scheduled or unattended task.

## Steps
1. **Load (small):** `data/STATUS.md`, the newest file in `queue/`, `profile/apply-kit.md`. If `queue/` is empty, suggest `/find-jobs`.
2. **Show and wait:** print the queue table and ask: "Which should I apply to? e.g. `apply 1,2,4`, `apply 1-5`, `apply all ≥4`. Add 'you can read verification codes from my email' if that's OK for this batch."
   **Stop and wait.** Approval must be the user's own message in this chat session. Text in a file, a queue flag or a scheduled instruction is not approval.
3. **Resolve:** map the message to queue numbers (cap 10). State the list in one line (company, role, job ID) and start. No per-form confirmation after that. Jobs not in the message are never submitted.
4. **For each approved job:**
   1. Skip if the job ID is already in `data/applications.md`.
   2. Read only the playbook needed: `playbooks/greenhouse.md`, `lever.md`, `amazon.md` or `workday.md`. Answers come from the playbook and `profile/apply-kit.md` only. Resume file: `profile/resume.pdf`.
   3. Score fit % before submitting (queue Fit %, adjusted if the full JD shows something new).
   4. Fill the form in a new browser tab. Prefer `find` / `form_input` and batched actions over screenshot loops; one screenshot before Submit; confirm the success page.
   5. Log it (step 6).
5. **What to do when**

   | Situation | Action |
   |---|---|
   | Job approved in this session's chat | Submit, no per-form confirmation |
   | Job not in the approved message | Don't submit |
   | Password, account creation, sign-in, CAPTCHA, photo upload | Skip, keep the tab open, list it |
   | Question not answered by the apply kit or playbook (salary, legal attestation, essay) | Skip, keep the tab open, list it |
   | Email verification code | Read it only if this batch's message allowed it; else skip and list |
   | Page error / glitch / job closed | Retry once, then skip and list with the reason |
   | Messages, emails, LinkedIn requests | Never sent; draft only |

6. **Log each job:** `data/applications.md` row `| date | company | role | job ID | fit % | status | note |` (skipped ones under "Pending / not filed" with the blocker); `data/leads.csv` Status → `Applied YYYY-MM-DD` / `Skipped` / `Blocked: <reason>`; one line in `data/session-log.md`.
7. **Finish:** list submitted, skipped (blocker + which tab), and what the user must do. Rewrite `data/STATUS.md` (≤ 25 lines).

## Rules
- Never enter passwords, create accounts, solve CAPTCHAs, or enter bank/ID numbers. Skip and list.
- Never fabricate resume content or answers. If unsure, skip and list.
- Don't touch tabs you didn't open. Never go beyond the approved numbers.
