#!/usr/bin/env python3
"""Keyword score of a resume or LinkedIn profile vs. the jobs you scanned (stdlib only, no LLM, no network).

Market demand comes from the job descriptions the engine stored for scored jobs
(<home>/state/phoenix.db, last --days days), or from scan snapshots (--snapshots DIR of
*.jsonl.gz with company/title/jd). Each skill in scripts/skills_catalog.json is weighted by
the share of companies that ask for it (one role per company + title, so copy-pasted reqs
don't count twice).

Your own skills come from --me (default <home>/profile/skills.json):
  {"have": [...], "unconfirmed": [...], "dont_claim": [...]}   (names from the catalog)

For each text it prints the weighted coverage and four lists of in-demand skills the text
doesn't mention:
  safe adds    you have them: reword or add them
  ask first    unconfirmed: add only once you confirm real use
  framing      generic words (scalability, security, mentoring): show them through results
  do not add   market gaps you haven't claimed: study list, never the profile

Usage:
  python3 scripts/profile_score.py resume.tex                  # LaTeX commands are stripped
  python3 scripts/profile_score.py old.tex new.tex             # compare two versions
  python3 scripts/profile_score.py profile.txt --days 60 --json
"""
import argparse
import datetime as dt
import gzip
import json
import os
import re
import sqlite3
import sys
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from scan import kw_regex  # noqa: E402  same whole-word matcher as the scanner

MIN_WEIGHT = 0.10  # ignore skills fewer than 10% of companies ask for
MIN_JD_CHARS = 200
BOILERPLATE_RE = re.compile(
    r"equal (employment )?opportunit|without regard to|gender identity|sexual orientation|protected veteran|"
    r"disabilit|reasonable accommodation|work authori[sz]ation|authori[sz]ed to work|background check|"
    r"privacy (notice|policy)|security clearance|affirmative action|pay transparency", re.I)


def clean_jd(text: str) -> str:
    return " ".join(s for s in re.split(r"(?<=[.!?])\s+", text or "") if not BOILERPLATE_RE.search(s))


def jobs_from_db(db: Path, days: int):
    if not db.exists():
        sys.exit(f"{db} not found: run /find-jobs first, or pass --snapshots DIR / --home.")
    since = (dt.datetime.now(dt.timezone.utc) - dt.timedelta(days=days)).strftime("%Y-%m-%dT%H:%M:%SZ")
    con = sqlite3.connect(f"file:{db}?mode=ro", uri=True)
    try:
        rows = con.execute("SELECT company, title, jd_text FROM jobs WHERE jd_text IS NOT NULL "
                           "AND length(jd_text) >= ? AND last_seen >= ?", (MIN_JD_CHARS, since)).fetchall()
    finally:
        con.close()
    return [{"company": c, "title": t, "jd": j} for c, t, j in rows]


def jobs_from_snapshots(folder: Path, days: int, max_years=None):
    files = sorted(folder.glob("*.jsonl.gz"))[-days:]
    if not files:
        sys.exit(f"No *.jsonl.gz snapshots in {folder}.")
    corpus = {}
    for f in files:
        with gzip.open(f, "rt", encoding="utf-8") as fh:
            for line in fh:
                if line.strip():
                    j = json.loads(line)
                    too_senior = max_years is not None and (j.get("years") or 0) > max_years
                    if j.get("jd_ok", True) and len(j.get("jd") or "") >= MIN_JD_CHARS and not too_senior:
                        corpus[f"{j['company']}:{j.get('job_id')}"] = j
    return list(corpus.values())


def compile_skills(skills: dict):
    out = {}
    for name, aliases in skills.items():
        plain = [a for a in aliases if not a.startswith("re:")]
        out[name] = (kw_regex(plain), [re.compile(a[3:]) for a in aliases if a.startswith("re:")])
    return out


def has_skill(text: str, matcher) -> bool:
    ci, regs = matcher
    return bool(ci and ci.search(text)) or any(r.search(text) for r in regs)


def demand(jobs, matchers):
    """[(skill, company-weighted share)] over distinct company + title roles, highest first."""
    roles = {}
    for j in jobs:
        roles.setdefault((j["company"], re.sub(r"[^a-z0-9]+", " ", j["title"].lower()).strip()), j)
    by_co = defaultdict(list)
    for j in roles.values():
        by_co[j["company"]].append(f"{j['title']} {clean_jd(j['jd'])}")
    if not by_co:
        sys.exit("No job descriptions in range: scan more jobs or raise --days.")
    rows = []
    for name, m in matchers.items():
        shares = [sum(has_skill(t, m) for t in texts) / len(texts) for texts in by_co.values()]
        rows.append((name, sum(shares) / len(shares)))
    rows.sort(key=lambda r: r[1], reverse=True)
    return rows, len(roles), len(by_co)


def load_me(path: Path) -> dict:
    """profile/skills.json ({have, unconfirmed, dont_claim}); also reads the older {"me": {...}} layout."""
    if not path.exists():
        return {"have": [], "unconfirmed": [], "dont_claim": []}
    data = json.loads(path.read_text())
    if "me" in data:
        me = data["me"]
        return {"have": me.get("on_resume", []) + me.get("on_linkedin", []),
                "unconfirmed": me.get("unconfirmed", []), "dont_claim": me.get("used_not_claimed", [])}
    return {k: data.get(k, []) for k in ("have", "unconfirmed", "dont_claim")}


def plain_text(path: Path) -> str:
    text = path.read_text(errors="ignore")
    if path.suffix == ".tex":
        text = re.sub(r"(?<!\\)%.*", " ", text)        # comments
        text = re.sub(r"\\[a-zA-Z@]+\*?", " ", text)    # \commands
        text = re.sub(r"[{}\[\]]", " ", text)
    return re.sub(r"\s+", " ", text)


def score(text: str, rows, matchers, me: dict, framing: set) -> dict:
    wanted = [(n, w) for n, w in rows if w >= MIN_WEIGHT]
    total = sum(w for _, w in wanted) or 1
    found = {n for n, _ in wanted if has_skill(text, matchers[n])}
    have, unconfirmed = set(me["have"]), set(me["unconfirmed"])

    def missing(pred):
        return [f"{n} ({round(100 * w)}%)" for n, w in wanted if n not in found and pred(n)]

    return {
        "words": len(text.split()),
        "matched": f"{len(found)}/{len(wanted)}",
        "weighted_coverage": round(100 * sum(w for n, w in wanted if n in found) / total, 1),
        "safe_adds": missing(lambda n: n in have),
        "ask_first": missing(lambda n: n in unconfirmed and n not in have),
        "framing": missing(lambda n: n in framing and n not in have),
        "do_not_add": missing(lambda n: n not in have and n not in unconfirmed and n not in framing),
        "found": sorted(found),
    }


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("files", nargs="+", type=Path, help="resume .tex/.txt/.md or saved LinkedIn profile text")
    ap.add_argument("--home", type=Path, default=Path(os.environ.get("PHOENIX_HOME") or "."))
    ap.add_argument("--days", type=int, default=30, help="jobs seen in the last N days (snapshots: last N files)")
    ap.add_argument("--snapshots", type=Path, help="read scan snapshots from this folder instead of the engine DB")
    ap.add_argument("--max-years", type=int, help="snapshots only: skip roles asking for more years than this")
    ap.add_argument("--me", type=Path, help="your skills file (default <home>/profile/skills.json)")
    ap.add_argument("--catalog", type=Path, default=HERE / "skills_catalog.json")
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args()

    catalog = json.loads(args.catalog.read_text())
    matchers = compile_skills(catalog["skills"])
    me = load_me(args.me or args.home / "profile" / "skills.json")
    jobs = jobs_from_snapshots(args.snapshots, args.days, args.max_years) if args.snapshots else \
        jobs_from_db(args.home / "state" / "phoenix.db", args.days)
    rows, n_roles, n_cos = demand(jobs, matchers)
    framing = set(catalog.get("framing", []))
    results = {str(f): score(plain_text(f), rows, matchers, me, framing) for f in args.files}
    market = {"roles": n_roles, "companies": n_cos, "source": str(args.snapshots or "engine DB"), "days": args.days}
    if len(args.files) == 2:
        a, b = (set(results[str(f)]["found"]) for f in args.files)
        market["gained"], market["lost"] = sorted(b - a), sorted(a - b)
    if n_cos < 10:
        market["warning"] = f"only {n_cos} companies in range; treat percentages as rough"

    if args.json:
        print(json.dumps({"market": market, "results": results}, indent=2))
        return
    print(f"Market: {n_roles} roles at {n_cos} companies ({market['source']}, {args.days} days)"
          + (f"  ⚠ {market['warning']}" if "warning" in market else ""))
    if not me["have"]:
        print("  (no skills file: every missing skill shows as 'do not add'; create profile/skills.json)")
    for f, r in results.items():
        print(f"\n{f}: {r['weighted_coverage']}% weighted, {r['matched']} skills, {r['words']} words")
        for key, label in (("safe_adds", "Safe to add (you have these)"), ("ask_first", "Ask first (unconfirmed)"),
                           ("framing", "Show through results"), ("do_not_add", "Market gaps, do NOT add")):
            if r[key]:
                print(f"  {label}: {', '.join(r[key][:10])}")
    if "gained" in market:
        print(f"\nGained: {', '.join(market['gained']) or 'nothing'}   Lost: {', '.join(market['lost']) or 'nothing'}")


if __name__ == "__main__":
    main()
