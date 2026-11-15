#!/usr/bin/env python3
"""job-scout-agent scanner (stdlib only, no LLM tokens).

Fetches public ATS job feeds listed in scripts/sources.json, filters them with
your profile/filters.json (made by /setup; falls back to
templates/filters.example.json), drops jobs already in state/seen.txt, gives each
job a 0-100 keyword pre-score and prints the top N as JSON lines on stdout.
Warnings and a run summary go to stderr, so stdout is pure JSON lines.

Usage:
  python3 scripts/scan.py                  # top 10 new jobs
  python3 scripts/scan.py --limit 5 --dry-run   # writes nothing at all
  python3 scripts/scan.py --mark-seen      # also append the printed IDs to state/seen.txt
  python3 scripts/scan.py --company amazon # only sources whose name contains "amazon"
"""
import argparse
import html
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TIMEOUT = 15
USER_AGENT = "job-scout-agent/1.0"
SR_DETAIL_CAP = 25  # max SmartRecruiters detail fetches per company


def warn(msg):
    print(f"WARN: {msg}", file=sys.stderr)


# --------------------------------------------------------------------------- http
def http_json(url, headers=None):
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/json", **(headers or {})})
    with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
        return json.loads(resp.read().decode("utf-8", errors="replace"))


# --------------------------------------------------------------------------- text helpers
TAG_RE = re.compile(r"<[^>]+>")
WS_RE = re.compile(r"\s+")


def strip_html(raw):
    if not raw:
        return ""
    text = html.unescape(raw)          # Greenhouse returns escaped HTML (&lt;p&gt;)
    text = re.sub(r"</(p|div|li|h\d|br|ul|ol)>|<br\s*/?>", " ", text, flags=re.I)
    text = TAG_RE.sub(" ", text)
    text = html.unescape(text)
    return WS_RE.sub(" ", text).strip()


def kw_regex(words):
    """Whole-word, case-insensitive match; allows a plural 's'/'es' suffix."""
    if not words:
        return None
    alts = "|".join(re.escape(w.lower()) for w in sorted(words, key=len, reverse=True))
    return re.compile(rf"(?<![a-z0-9])(?:{alts})(?:s|es)?(?![a-z0-9])", re.I)


YEARS_RE = re.compile(r"(\d{1,2})\s*\+?\s*(?:(?:-|–|to)\s*\d{1,2}\s*\+?\s*)?(?:years?|yrs?)", re.I)


def required_years(text):
    """Highest 'N years' figure that sits next to the word 'experience' (None if none)."""
    found = []
    for m in YEARS_RE.finditer(text):
        window = text[max(0, m.start() - 70): m.end() + 70].lower()
        if "experience" in window or "exp." in window:
            n = int(m.group(1))
            if 0 < n <= 15:
                found.append(n)
    return max(found) if found else None


ROLE_START_RE = re.compile(r"(about the role|the role|what you'?ll do|what you will do|responsibilities|in this role|your role|key responsibilities|job description)", re.I)
QUAL_START_RE = re.compile(r"(basic qualifications|minimum qualifications|qualifications|requirements|what you'?ll need|what we'?re looking for|required skills)", re.I)


def summarize(jd, limit=1500):
    """<= limit chars: start of the role section + the qualifications section."""
    if len(jd) <= limit:
        return jd
    m = ROLE_START_RE.search(jd)
    a_start = m.start() if m and m.start() < len(jd) - 600 else 0
    a = jd[a_start:a_start + 800]
    q = QUAL_START_RE.search(jd, a_start + 800)
    if q:
        b = jd[q.start():q.start() + (limit - len(a) - 5)]
        out = f"{a} … {b}"
    else:
        out = jd[a_start:a_start + limit]
    return out[:limit]


# --------------------------------------------------------------------------- fetchers
# Each fetcher returns a list of dicts: company, job_id, title, location, url, jd, loc_ok
# (loc_ok=True means the source already restricted the country, so skip the location filter).
def fetch_greenhouse(token, cfg):
    data = http_json(f"https://boards-api.greenhouse.io/v1/boards/{token}/jobs?content=true")
    out = []
    for j in data.get("jobs", []):
        out.append({
            "company": token.lower(), "job_id": str(j["id"]), "title": j.get("title", ""),
            "location": (j.get("location") or {}).get("name", ""), "url": j.get("absolute_url", ""),
            "jd": strip_html(j.get("content", "")), "loc_ok": False,
        })
    return out


def fetch_lever(token, cfg):
    data = http_json(f"https://api.lever.co/v0/postings/{token}?mode=json")
    out = []
    for j in data:
        cats = j.get("categories") or {}
        locs = cats.get("allLocations") or [cats.get("location", "")]
        lists = " ".join(f"{x.get('text', '')} {strip_html(x.get('content', ''))}" for x in j.get("lists") or [])
        jd = " ".join([j.get("descriptionPlain", ""), lists, j.get("additionalPlain", "")])
        out.append({
            "company": token.lower(), "job_id": j["id"], "title": j.get("text", ""),
            "location": "; ".join(l for l in locs if l), "url": j.get("hostedUrl", ""),
            "jd": WS_RE.sub(" ", jd).strip(), "loc_ok": False,
        })
    return out


def fetch_ashby(token, cfg):
    data = http_json(f"https://api.ashbyhq.com/posting-api/job-board/{token}")
    out = []
    for j in data.get("jobs", []):
        if j.get("isListed") is False:
            continue
        locs = [j.get("location") or ""] + [(s or {}).get("location", "") for s in j.get("secondaryLocations") or []]
        if j.get("isRemote"):
            locs.append("Remote")
        out.append({
            "company": token.lower(), "job_id": str(j["id"]), "title": j.get("title", ""),
            "location": "; ".join(l for l in locs if l), "url": j.get("jobUrl", ""),
            "jd": WS_RE.sub(" ", j.get("descriptionPlain") or strip_html(j.get("descriptionHtml", ""))).strip(),
            "loc_ok": False,
        })
    return out


def fetch_smartrecruiters(token, cfg):
    postings, offset = [], 0
    while True:
        country = urllib.parse.quote(cfg.get("smartrecruiters_country", "in"))
        d = http_json(f"https://api.smartrecruiters.com/v1/companies/{token}/postings?country={country}&limit=100&offset={offset}")
        content = d.get("content", [])
        postings += content
        offset += 100
        if not content or offset >= d.get("totalFound", 0):
            break
    flt = cfg["_title_filter"]
    out, details = [], 0
    for p in postings:
        title = p.get("name", "")
        if not flt(title):  # only fetch the JD for jobs whose title already passes
            continue
        jd = ""
        if details < SR_DETAIL_CAP:
            details += 1
            try:
                det = http_json(f"https://api.smartrecruiters.com/v1/companies/{token}/postings/{p['id']}")
                secs = (det.get("jobAd") or {}).get("sections") or {}
                jd = " ".join(strip_html((s or {}).get("text", "")) for s in secs.values())
            except Exception as e:  # one bad detail call shouldn't drop the posting
                warn(f"smartrecruiters/{token}: detail {p['id']} failed ({e})")
        loc = p.get("location") or {}
        out.append({
            "company": token.lower(), "job_id": str(p["id"]), "title": title,
            "location": loc.get("fullLocation") or loc.get("city", ""),
            "url": f"https://jobs.smartrecruiters.com/{token}/{p['id']}", "jd": jd, "loc_ok": True,
        })
    return out


def fetch_amazon(_token, cfg):
    a = cfg["amazon"]
    out, ids = [], set()
    jobs = []
    for q in a["queries"]:
        offset = 0
        while offset < a.get("max_results", 150):  # paginate: a query can have more hits than result_limit
            qs = urllib.parse.urlencode({"base_query": q, "country": a["country"], "result_limit": a["result_limit"],
                                         "offset": offset, "sort": "recent"})
            data = http_json(f"https://www.amazon.jobs/en/search.json?{qs}")
            page = data.get("jobs", [])
            jobs += page
            offset += a["result_limit"]
            if not page or offset >= data.get("hits", 0):
                break
    for j in jobs:
        jid = str(j.get("id_icims") or j.get("id"))
        if jid in ids:
            continue
        ids.add(jid)
        parts = []
        for label, key in (("", "description"), ("Basic qualifications:", "basic_qualifications"), ("Preferred qualifications:", "preferred_qualifications")):
            txt = strip_html(j.get(key) or "")
            if txt and txt.lower() not in ("null", "none"):
                parts.append(f"{label} {txt}".strip())
        jd = " ".join(parts)
        out.append({
            "company": "amazon", "job_id": jid, "title": j.get("title", ""),
            "location": j.get("normalized_location") or j.get("location", ""),
            "url": "https://www.amazon.jobs" + j.get("job_path", f"/en/jobs/{jid}"), "jd": jd, "loc_ok": True,
        })
    return out


def fetch_atlassian(_token, cfg):
    data = http_json("https://www.atlassian.com/endpoint/careers/listings")
    out = []
    for j in data:
        locs = j.get("locations") or []
        if isinstance(locs, str):
            locs = [locs]
        portal = j.get("portalJobPost")
        url = (portal or {}).get("portalUrl") if isinstance(portal, dict) else None
        jd = " ".join(strip_html(j.get(k, "")) for k in ("overview", "responsibilities", "qualifications"))
        out.append({
            "company": "atlassian", "job_id": str(j["id"]), "title": j.get("title", ""),
            "location": "; ".join(locs), "url": url or j.get("applyUrl", ""), "jd": jd, "loc_ok": False,
        })
    return out


# --------------------------------------------------------------------------- scoring
def prescore(job, years, ps):
    title = job["title"].lower()
    text = f"{job['title']} {job['jd']}".lower()
    score = 0
    for table in ("stack", "domain"):
        for kw, pts in ps[table].items():
            if kw_regex([kw]).search(text):
                score += pts
    for kw, pts in ps["level_titles"].items():
        if kw_regex([kw]).search(title):
            score += pts
            break
    for kw, pts in ps["title_penalty"].items():
        if kw_regex([kw]).search(title):
            score -= pts
    loc = job["location"].lower()
    for kw, pts in ps["location_bonus"].items():
        if kw in loc:
            score += pts
            break
    if job["company"] in ps["tier1"]:
        score += ps["tier1_bonus"]
    elif job["company"] in ps["tier2"]:
        score += ps["tier2_bonus"]
    if years is not None:
        lo, hi = ps["years_ideal"]
        if lo <= years <= hi:
            score += ps["years_bonus"]
        elif years > hi:
            score -= ps["years_penalty_per_year_over"] * (years - hi)
    return max(0, min(100, score))


# --------------------------------------------------------------------------- seen
def load_seen(path):
    keys, ids = set(), set()
    if path.exists():
        for line in path.read_text().splitlines():
            line = line.strip()
            if line and not line.startswith("#"):
                keys.add(line.lower())
                ids.add(line.split(":", 1)[-1].lower())
    return keys, ids


def is_seen(job, keys, ids):
    if f"{job['company']}:{job['job_id']}".lower() in keys:
        return True
    # fall back to the bare ID for long IDs (avoids short-ID collisions across companies)
    return len(job["job_id"]) >= 7 and job["job_id"].lower() in ids


# --------------------------------------------------------------------------- main
def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--limit", type=int, default=10, help="max jobs to print (default 10)")
    ap.add_argument("--dry-run", action="store_true", help="write nothing (overrides --mark-seen)")
    ap.add_argument("--mark-seen", action="store_true", help="append printed job IDs to state/seen.txt")
    ap.add_argument("--company", help="only scan sources whose name contains this text")
    ap.add_argument("--config", default=str(ROOT / "scripts" / "sources.json"), help="job boards to scan")
    ap.add_argument("--filters", default=str(ROOT / "profile" / "filters.json"), help="your filters (from /setup)")
    ap.add_argument("--seen", default=str(ROOT / "state" / "seen.txt"))
    args = ap.parse_args()

    filters_path = Path(args.filters)
    if not filters_path.exists():
        filters_path = ROOT / "templates" / "filters.example.json"
        warn(f"{args.filters} not found; using {filters_path.name}. Run /setup to create your own filters.")
    sources = json.loads(Path(args.config).read_text())
    local = ROOT / "profile" / "sources.local.json"  # boards found by /find-jobs discover (git-ignored)
    if local.exists():
        for kind, tokens in json.loads(local.read_text()).items():
            if isinstance(tokens, list):
                sources[kind] = sorted(set(sources.get(kind, [])) | set(tokens), key=str.lower)
    personal = json.loads(filters_path.read_text())
    cfg = {**sources, **personal}  # filters.json supplies filters, prescore, amazon queries/country
    if not sources.get("amazon"):
        cfg.pop("amazon", None)
    elif not isinstance(cfg.get("amazon"), dict):
        cfg.pop("amazon", None)
        warn("amazon enabled in sources.json but no 'amazon' block in filters; skipping Amazon")
    f = cfg["filters"]
    title_inc, title_exc = kw_regex(f["title_include"]), kw_regex(f["title_exclude"])
    loc_inc = kw_regex(f["locations"])
    jd_inc, jd_exc = kw_regex(f["jd_include"]), kw_regex(f["jd_exclude"])

    def title_ok(t):
        return bool(title_inc.search(t)) and not title_exc.search(t)

    cfg["_title_filter"] = title_ok

    tasks = [("greenhouse", t, fetch_greenhouse) for t in cfg.get("greenhouse", [])]
    tasks += [("lever", t, fetch_lever) for t in cfg.get("lever", [])]
    tasks += [("ashby", t, fetch_ashby) for t in cfg.get("ashby", [])]
    tasks += [("smartrecruiters", t, fetch_smartrecruiters) for t in cfg.get("smartrecruiters", [])]
    if cfg.get("amazon"):
        tasks.append(("amazon", "amazon", fetch_amazon))
    if cfg.get("atlassian"):
        tasks.append(("atlassian", "atlassian", fetch_atlassian))
    if args.company:
        tasks = [t for t in tasks if args.company.lower() in f"{t[0]}/{t[1]}".lower()]
        if not tasks:
            warn(f"no source matches --company {args.company!r}")

    def run(task):
        kind, token, fn = task
        try:
            t0 = time.time()
            res = fn(token, cfg)
            print(f"  {kind}/{token}: {len(res)} postings ({time.time() - t0:.1f}s)", file=sys.stderr)
            return kind, token, res, None
        except Exception as e:  # one failing source must not stop the run
            return kind, token, [], e

    all_jobs, failed = [], 0
    with ThreadPoolExecutor(max_workers=8) as pool:
        for kind, token, jobs, err in pool.map(run, tasks):
            if err is not None:
                failed += 1
                warn(f"{kind}/{token} skipped: {type(err).__name__}: {err}")
            else:
                all_jobs += jobs

    keys, ids = load_seen(Path(args.seen))
    stats = {"fetched": len(all_jobs), "title": 0, "location": 0, "jd": 0, "years": 0, "seen": 0}
    results, dedupe = [], set()
    for j in all_jobs:
        if not title_ok(j["title"]):
            continue
        stats["title"] += 1
        if not j["loc_ok"] and not loc_inc.search(j["location"]):
            continue
        stats["location"] += 1
        if j["jd"] and (not jd_inc.search(j["jd"]) or jd_exc.search(j["jd"])):
            continue
        stats["jd"] += 1
        years = required_years(j["jd"])
        if years is not None and years > f["max_min_years"]:
            continue
        stats["years"] += 1
        if is_seen(j, keys, ids):
            continue
        key = f"{j['company']}:{j['job_id']}"
        if key in dedupe:
            continue
        dedupe.add(key)
        stats["seen"] += 1  # = new (unseen) matches
        j["prescore"] = prescore(j, years, cfg["prescore"])
        results.append(j)

    results.sort(key=lambda j: j["prescore"], reverse=True)
    # Collapse multiple reqs of the same role (e.g. Amazon posts 3 identical "Prime and Marketing Tech" reqs):
    # keep the best-scoring / newest-ID one per company + normalised title, so the queue shows distinct roles.
    by_title = {}
    for j in results:
        k = (j["company"], re.sub(r"[^a-z0-9]+", " ", j["title"].lower()).strip())
        best = by_title.get(k)
        newer = lambda x: int(x["job_id"]) if x["job_id"].isdigit() else 0  # numeric IDs: bigger = newer
        if best is None or (j["prescore"], newer(j)) > (best["prescore"], newer(best)):
            by_title[k] = j
    results = sorted(by_title.values(), key=lambda j: j["prescore"], reverse=True)
    top = results[: max(0, args.limit)]
    for j in top:
        print(json.dumps({
            "company": j["company"], "job_id": j["job_id"], "title": j["title"], "location": j["location"],
            "url": j["url"], "prescore": j["prescore"], "jd_summary": summarize(j["jd"]),
        }, ensure_ascii=False))
    sys.stdout.flush()

    print(f"SUMMARY: fetched={stats['fetched']} title_ok={stats['title']} location_ok={stats['location']} "
          f"jd_ok={stats['jd']} years_ok={stats['years']} new={stats['seen']} printed={len(top)} failed_sources={failed}",
          file=sys.stderr)

    if args.dry_run:
        if args.mark_seen:
            print("dry-run: --mark-seen ignored, nothing written", file=sys.stderr)
    elif args.mark_seen and top:
        seen_path = Path(args.seen)
        seen_path.parent.mkdir(parents=True, exist_ok=True)
        existing = seen_path.read_text() if seen_path.exists() else ""
        with seen_path.open("a") as fh:
            if existing and not existing.endswith("\n"):
                fh.write("\n")
            for j in top:
                fh.write(f"{j['company']}:{j['job_id']}\n")
        print(f"marked {len(top)} IDs as seen in {seen_path}", file=sys.stderr)


if __name__ == "__main__":
    main()
