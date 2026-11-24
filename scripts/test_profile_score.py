"""Tests for profile_score.py. Run: python3 -m unittest scripts/test_profile_score.py"""
import datetime as dt
import gzip
import json
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent / "profile_score.py"
CATALOG = {"skills": {"Java": ["java"], "Kafka": ["kafka"], "Docker": ["docker"], "Python": ["python"],
                      "Security": ["security"], "Go": ["golang", "re:\\bGo\\b"]},
           "framing": ["Security"]}
NOW = dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
JD = "We build backend services. " * 10  # padding so every JD passes the minimum length


class ProfileScoreTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.home = Path(self.tmp.name)
        (self.home / "state").mkdir()
        (self.home / "profile").mkdir()
        (self.home / "catalog.json").write_text(json.dumps(CATALOG))
        (self.home / "profile" / "skills.json").write_text(json.dumps(
            {"have": ["Java", "Kafka"], "unconfirmed": ["Docker"], "dont_claim": []}))

    def tearDown(self):
        self.tmp.cleanup()

    def make_db(self, rows):
        con = sqlite3.connect(self.home / "state" / "phoenix.db")
        con.execute("CREATE TABLE jobs (company TEXT, title TEXT, jd_text TEXT, last_seen TEXT)")
        con.executemany("INSERT INTO jobs VALUES (?, ?, ?, ?)", rows)
        con.commit()
        con.close()

    def run_script(self, *files, extra=(), check=True):
        proc = subprocess.run([sys.executable, str(SCRIPT), *map(str, files), "--home", str(self.home),
                               "--catalog", str(self.home / "catalog.json"), "--json", *extra],
                              capture_output=True, text=True)
        if check:
            self.assertEqual(proc.returncode, 0, proc.stderr)
            return json.loads(proc.stdout)
        return proc

    def write(self, name, text):
        path = self.home / name
        path.write_text(text)
        return path

    def test_categories_and_company_weighting(self):
        # acme posts the same Java+Kafka+Docker role twice (counted once); globex asks for Java+Python+security.
        self.make_db([("acme", "SWE II", JD + "Java Kafka Docker", NOW),
                      ("acme", "SWE II", JD + "Java Kafka Docker", NOW),
                      ("globex", "Backend", JD + "Java Python security", NOW)])
        resume = self.write("resume.txt", "Java engineer.")
        report = self.run_script(resume)
        self.assertEqual(report["market"]["roles"], 2)
        r = report["results"][str(resume)]
        self.assertEqual(r["found"], ["Java"])
        self.assertEqual(r["safe_adds"], ["Kafka (50%)"])
        self.assertEqual(r["ask_first"], ["Docker (50%)"])
        self.assertEqual(r["framing"], ["Security (50%)"])
        self.assertEqual(r["do_not_add"], ["Python (50%)"])

    def test_latex_is_stripped_and_regex_aliases_are_case_sensitive(self):
        self.make_db([("acme", "SWE", JD + "Go and Kafka", NOW)])
        tex = self.write("resume.tex", "\\textbf{Kafka} pipelines % golang in a comment\n\\item let's go home")
        r = self.run_script(tex)["results"][str(tex)]
        self.assertEqual(r["found"], ["Kafka"])

    def test_compare_two_versions(self):
        self.make_db([("acme", "SWE", JD + "Java Kafka", NOW)])
        old, new = self.write("old.txt", "Java"), self.write("new.txt", "Java and Kafka")
        market = self.run_script(old, new)["market"]
        self.assertEqual((market["gained"], market["lost"]), (["Kafka"], []))

    def test_old_jobs_are_out_of_range(self):
        self.make_db([("acme", "SWE", JD + "Java", "2020-01-01T00:00:00Z")])
        proc = self.run_script(self.write("r.txt", "Java"), check=False)
        self.assertNotEqual(proc.returncode, 0)
        self.assertIn("No job descriptions in range", proc.stderr)

    def test_snapshots_source_and_max_years(self):
        snaps = self.home / "snaps"
        snaps.mkdir()
        with gzip.open(snaps / "2026-01-01.jsonl.gz", "wt") as fh:
            fh.write(json.dumps({"company": "acme", "job_id": 1, "title": "SWE", "jd": JD + "Kafka", "years": 3}) + "\n")
            fh.write(json.dumps({"company": "globex", "job_id": 2, "title": "Staff", "jd": JD + "Python", "years": 12}) + "\n")
        resume = self.write("r.txt", "nothing")
        report = self.run_script(resume, extra=("--snapshots", str(snaps), "--max-years", "6"))
        self.assertEqual(report["market"]["companies"], 1)
        self.assertEqual(report["results"][str(resume)]["safe_adds"], ["Kafka (100%)"])

    def test_reads_older_me_layout(self):
        self.make_db([("acme", "SWE", JD + "Kafka", NOW)])
        legacy = self.write("legacy-skills.json", json.dumps({"me": {"on_resume": [], "on_linkedin": ["Kafka"]}}))
        resume = self.write("r.txt", "nothing")
        r = self.run_script(resume, extra=("--me", str(legacy)))["results"][str(resume)]
        self.assertEqual(r["safe_adds"], ["Kafka (100%)"])


if __name__ == "__main__":
    unittest.main()
