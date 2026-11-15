#!/usr/bin/env python3
"""Find which public job board a company uses (read-only GET requests).

Usage: python3 scripts/find_board.py stripe "Acme Corp" rippling
Tries Greenhouse, Lever, Ashby and SmartRecruiters with a few slug spellings and
prints one JSON line per hit: {"company", "ats", "token", "jobs"}.
/find-jobs discover adds hits to profile/sources.local.json.
"""
import json
import re
import sys
import urllib.request

UA = {"User-Agent": "job-scout-agent/1.0", "Accept": "application/json"}
CHECKS = {
    "greenhouse": ("https://boards-api.greenhouse.io/v1/boards/{t}/jobs", lambda d: len(d.get("jobs", []))),
    "lever": ("https://api.lever.co/v0/postings/{t}?mode=json", lambda d: len(d) if isinstance(d, list) else 0),
    "ashby": ("https://api.ashbyhq.com/posting-api/job-board/{t}", lambda d: len(d.get("jobs", []))),
    "smartrecruiters": ("https://api.smartrecruiters.com/v1/companies/{t}/postings?limit=1", lambda d: d.get("totalFound", 0)),
}


def slugs(name):
    base = re.sub(r"[^a-z0-9 ]", "", name.lower()).strip()
    words = base.split()
    out = ["".join(words), "-".join(words)]
    for suffix in ("inc", "corp", "llc", "technologies", "labs"):
        if words and words[-1] == suffix and len(words) > 1:
            out.append("".join(words[:-1]))
    if name.strip() and " " not in name.strip():
        out.append(name.strip())  # exact token as typed (SmartRecruiters is case-sensitive)
    return list(dict.fromkeys(s for s in out if s))


def get(url):
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=10) as r:
            return json.loads(r.read().decode("utf-8", "replace"))
    except Exception:
        return None


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    for name in sys.argv[1:]:
        found = False
        for ats, (url, count) in CHECKS.items():
            for t in slugs(name):
                d = get(url.format(t=t))
                n = count(d) if d is not None else 0
                if n >= 3:  # 1-2 postings is usually a dead or test board
                    print(json.dumps({"company": name, "ats": ats, "token": t, "jobs": n}))
                    found = True
                    break
        if not found:
            print(json.dumps({"company": name, "ats": None, "token": None, "jobs": 0}))


if __name__ == "__main__":
    main()
