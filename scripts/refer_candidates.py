#!/usr/bin/env python3
"""Referral bookkeeping for the /refer skill (no LLM, no network).

Reads the engine's exports/applications.md (run `bin/phoenix export` first) and the
referral tracker CSV, and prints, as JSON, what the skill should work on:
  - follow-ups due (one follow-up per person), connection requests to re-check, people on Hold,
    people still silent after a follow-up
  - companies with a live application (Applied / Oa / Interview) >= --min-fit, applied in the
    last --max-age-days, and no outreach yet (one row per company)
  - whether LinkedIn people search is allowed (monthly limit) and today's send budget

Paths (all under --home, default $PHOENIX_HOME or the current folder):
  exports/applications.md, --tracker (default data/referrals.csv), state/refer-state.json

State flags (write state/refer-state.json and exit):
  --limit-hit            record that LinkedIn showed the monthly search limit today
  --searched CO --found N [--people "A (1st, SDE 2)" "B (2nd, alum)"]
                         record a people search for company CO, how many referrers it found and who
  --covered CO [--why TEXT]  mark a company as already covered outside the tracker (e.g. own referrer)
  state "aliases": {"oldname": "canonical", "prefix*": "canonical"} merges misnamed company rows

Usage:
  python3 scripts/refer_candidates.py [--home DIR] [--tracker FILE] [--min-fit 80] [--top 5] [--max-age-days 30]
  python3 scripts/refer_candidates.py --limit-hit
  python3 scripts/refer_candidates.py --searched atlassian --found 2
"""
import argparse
import csv
import datetime as dt
import json
import os
import re
import sys
from pathlib import Path

HOME = Path(os.environ.get("PHOENIX_HOME") or ".")
APPS = TRACKER = STATE = None  # set in main() from --home / --tracker

DAILY_CAP = 10
RESEARCH_AFTER_DAYS = 30  # re-search a company that had no referrers after this long
ALIASES = {}  # loaded from state "aliases"
# Status labels written by `bin/phoenix export` (LegacyStatusLabels).
APPLIED_LABELS = {"applied", "oa", "interview", "rejected", "offer", "ghosted"}
REFERRABLE_LABELS = {"applied", "oa", "interview"}  # a referral can still help


def norm(company: str) -> str:
    key = re.sub(r"[^a-z0-9]", "", company.lower())
    for alias, canon in ALIASES.items():
        if key == alias or (alias.endswith("*") and key.startswith(alias[:-1])):
            return canon
    return key


def load_state() -> dict:
    try:
        state = json.loads(STATE.read_text())
    except (FileNotFoundError, json.JSONDecodeError):
        state = {}
    state.setdefault("search_limit_hit", None)
    for key in ("searched", "covered", "aliases"):
        state.setdefault(key, {})
    return state


def save_state(state: dict) -> None:
    STATE.parent.mkdir(parents=True, exist_ok=True)
    STATE.write_text(json.dumps(state, indent=2, sort_keys=True) + "\n")


def parse_date(text: str):
    m = re.search(r"\d{4}-\d{2}-\d{2}", text or "")
    return dt.date.fromisoformat(m.group(0)) if m else None


def load_applications():
    """One entry per job ID (first row wins), only rows where an application was submitted."""
    if not APPS.exists():
        sys.exit(f"{APPS} not found: run `bin/phoenix export` first (or pass --home).")
    jobs, seen = [], set()
    for line in APPS.read_text().splitlines():
        cells = [c.strip() for c in line.split("|")]
        if len(cells) < 9 or not cells[1].isdigit():
            continue
        _, _, date, company, role, job_id, fit, status = cells[:8]
        if status.lower() not in APPLIED_LABELS or job_id in seen:
            continue
        seen.add(job_id)
        jobs.append({
            "date": date, "company": norm(company), "role": role, "job_id": job_id,
            "fit": int(fit) if fit.isdigit() else 0, "status": status,
        })
    return jobs


def load_tracker():
    if not TRACKER.exists():
        return []
    with TRACKER.open(newline="") as f:
        return list(csv.DictReader(f))


def report(min_fit: int, top: int, max_age_days: int) -> dict:
    today = dt.date.today()
    state = load_state()
    jobs = load_applications()
    tracker = load_tracker()

    follow_ups, recheck, hold, silent, covered = [], [], [], [], set(state["covered"])
    sent_today = 0
    for row in tracker:
        sent = row.get("Message sent", "").strip()
        referred = row.get("Referred?", "").strip().lower()
        reached_out = sent.lower().startswith("yes") or "connection request" in sent.lower()
        # A row only covers its company once someone was actually contacted (or is on Hold).
        if reached_out or referred in ("pending", "hold", "yes", "referred"):
            covered.add(norm(row.get("Company", "")))
        last_contact = parse_date(sent) or parse_date(row.get("Date", ""))
        if parse_date(sent) == today:
            sent_today += 1
        connection = next((v for k, v in row.items() if k and k.startswith("Connection")), None)
        person = {"name": row.get("Name"), "company": row.get("Company"),
                  "job": row.get("Role / Job ID"), "connection": connection,
                  "last_contact": str(last_contact) if last_contact else None,
                  "follow_up_date": row.get("Follow-up date") or None}
        if referred == "hold":
            hold.append(person)
            continue
        if referred != "pending":
            continue
        if "connection request" in sent.lower():
            recheck.append(person)  # can only message after they accept
            continue
        due = parse_date(row.get("Follow-up date", ""))
        rested = last_contact is None or (today - last_contact).days >= 5
        if not (sent.lower().startswith("yes") and due and due <= today and rested):
            continue
        if "follow-up" in sent.lower():
            silent.append(person)  # one follow-up only; don't chase further
        else:
            follow_ups.append(person)

    by_company = {}
    oldest = today - dt.timedelta(days=max_age_days)
    for job in jobs:
        if job["fit"] < min_fit or job["company"] in covered or job["status"].lower() not in REFERRABLE_LABELS:
            continue
        applied_on = parse_date(job["date"])
        if applied_on and applied_on < oldest:
            continue
        searched = state["searched"].get(job["company"])
        if searched and searched["found"] == 0 and \
                (today - dt.date.fromisoformat(searched["date"])).days < RESEARCH_AFTER_DAYS:
            continue
        entry = by_company.setdefault(job["company"], {"company": job["company"], "applications": 0, "best": job,
                                                       "already_searched": searched})
        entry["applications"] += 1
        best = entry["best"]
        if (job["fit"], job["date"]) > (best["fit"], best["date"]):
            entry["best"] = job
    # Highest fit first, then most recent, then companies with more applications.
    candidates = sorted(by_company.values(),
                        key=lambda e: (e["best"]["fit"], e["best"]["date"], e["applications"]), reverse=True)

    limit_hit = parse_date(state.get("search_limit_hit") or "")
    search_allowed = not (limit_hit and (limit_hit.year, limit_hit.month) == (today.year, today.month))

    applied_companies = {}
    for job in jobs:
        applied_companies[job["company"]] = applied_companies.get(job["company"], 0) + 1

    return {
        "today": str(today),
        "applications": {"unique_jobs": len(jobs), "companies": len(applied_companies),
                         "by_company": dict(sorted(applied_companies.items(), key=lambda kv: -kv[1]))},
        "follow_ups_due": follow_ups,
        "connection_requests_to_recheck": recheck,
        "hold": hold,
        "silent_after_follow_up": silent,
        "new_referrer_candidates": candidates[:top],
        "candidates_left_after_top": max(0, len(candidates) - top),
        "linkedin_search_allowed": search_allowed,
        "linkedin_search_limit_hit": state.get("search_limit_hit"),
        "sends_left_today": max(0, DAILY_CAP - sent_today),
    }


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--home", type=Path, default=HOME)
    ap.add_argument("--tracker", type=Path, help="referral tracker CSV (default <home>/data/referrals.csv)")
    ap.add_argument("--min-fit", type=int, default=80)
    ap.add_argument("--top", type=int, default=5)
    ap.add_argument("--max-age-days", type=int, default=30,
                    help="only suggest referrers for applications this recent (default 30)")
    ap.add_argument("--limit-hit", action="store_true")
    ap.add_argument("--searched")
    ap.add_argument("--found", type=int)
    ap.add_argument("--people", nargs="*", default=[])
    ap.add_argument("--covered")
    ap.add_argument("--why", default="")
    args = ap.parse_args()

    global APPS, TRACKER, STATE
    APPS = args.home / "exports" / "applications.md"
    TRACKER = args.tracker or args.home / "data" / "referrals.csv"
    STATE = args.home / "state" / "refer-state.json"
    ALIASES.update({norm(k.rstrip("*")) + ("*" if k.endswith("*") else ""): norm(v)
                    for k, v in load_state().get("aliases", {}).items()})

    if args.limit_hit or args.searched or args.covered:
        state = load_state()
        today = str(dt.date.today())
        if args.limit_hit:
            state["search_limit_hit"] = today
        if args.searched:
            if args.found is None:
                ap.error("--searched needs --found N")
            state["searched"][norm(args.searched)] = {"date": today, "found": args.found, "people": args.people}
        if args.covered:
            state["covered"][norm(args.covered)] = {"date": today, "why": args.why}
        save_state(state)
        print(json.dumps(state, indent=2))
        return

    print(json.dumps(report(args.min_fit, args.top, args.max_age_days), indent=2))


if __name__ == "__main__":
    main()
