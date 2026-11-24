"""Tests for refer_candidates.py. Run: python3 -m unittest scripts/test_refer_candidates.py"""
import csv
import datetime as dt
import io
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent / "refer_candidates.py"
TODAY = dt.date.today()
HEADER = "Date,Name,Connection (alum / ex-colleague),Company,Role / Job ID,Message sent,Referred?,Follow-up date,Notes\n"


def days_ago(n: int) -> str:
    return str(TODAY - dt.timedelta(days=n))


class ReferCandidatesTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.home = Path(self.tmp.name)
        (self.home / "exports").mkdir()
        (self.home / "data").mkdir()

    def tearDown(self):
        self.tmp.cleanup()

    def write_apps(self, rows):
        lines = ["# Applications", "", "| # | Date | Company | Role | Job ID | Fit % | Status | Notes |",
                 "|---|------|---------|------|--------|-------|--------|-------|"]
        for i, (date, company, job_id, fit, status) in enumerate(rows, 1):
            lines.append(f"| {i} | {date} | {company} | SWE | {job_id} | {fit} | {status} | |")
        (self.home / "exports" / "applications.md").write_text("\n".join(lines) + "\n")

    def write_tracker(self, rows):
        buf = io.StringIO()
        csv.writer(buf, lineterminator="\n").writerows(rows)
        (self.home / "data" / "referrals.csv").write_text(HEADER + buf.getvalue())

    def run_script(self, *args, check=True):
        proc = subprocess.run([sys.executable, str(SCRIPT), "--home", str(self.home), *args],
                              capture_output=True, text=True)
        if check:
            self.assertEqual(proc.returncode, 0, proc.stderr)
            return json.loads(proc.stdout)
        return proc

    def companies(self, report):
        return [c["company"] for c in report["new_referrer_candidates"]]

    def test_dedupes_by_job_id_and_aliases(self):
        self.write_apps([(days_ago(3), "acme", "1", 90, "Applied"),
                         (days_ago(3), "acmeoldname", "1", 90, "Applied"),
                         (days_ago(2), "acmeoldname", "2", 85, "Applied")])
        (self.home / "state").mkdir()
        (self.home / "state" / "refer-state.json").write_text(json.dumps({"aliases": {"acmeold*": "acme"}}))
        report = self.run_script()
        self.assertEqual(report["applications"]["unique_jobs"], 2)
        self.assertEqual(report["applications"]["companies"], 1)
        self.assertEqual(report["new_referrer_candidates"][0]["applications"], 2)

    def test_only_contacted_rows_cover_a_company(self):
        self.write_apps([(days_ago(2), "acme", "1", 90, "Applied"), (days_ago(2), "globex", "2", 90, "Applied")])
        self.write_tracker([[days_ago(2), "Pat", "alum", "Acme", "1", f"Yes (LinkedIn DM, {days_ago(2)} 10:00)",
                             "Pending", days_ago(-3), ""],
                            [days_ago(2), "", "alum", "Globex", "2", "no", "", "", ""]])
        self.assertEqual(self.companies(self.run_script()), ["globex"])

    def test_one_follow_up_then_silent(self):
        self.write_apps([])
        self.write_tracker([[days_ago(10), "Pat", "alum", "Acme", "1", f"Yes (LinkedIn DM, {days_ago(10)} 10:00)",
                             "Pending", days_ago(5), ""],
                            [days_ago(10), "Sam", "alum", "Initech", "2",
                             f"Yes (LinkedIn DM follow-up, {days_ago(6)} 10:00)", "Pending", days_ago(1), ""]])
        report = self.run_script()
        self.assertEqual([p["name"] for p in report["follow_ups_due"]], ["Pat"])
        self.assertEqual([p["name"] for p in report["silent_after_follow_up"]], ["Sam"])

    def test_no_follow_up_within_five_days(self):
        self.write_apps([])
        self.write_tracker([[days_ago(2), "Pat", "alum", "Acme", "1", f"Yes (LinkedIn DM, {days_ago(2)} 10:00)",
                             "Pending", days_ago(0), ""]])
        self.assertEqual(self.run_script()["follow_ups_due"], [])

    def test_pending_connection_and_hold(self):
        self.write_apps([])
        self.write_tracker([[days_ago(9), "Pat", "alum", "Acme", "1", "Connection request with note", "Pending",
                             days_ago(4), ""],
                            [days_ago(9), "Sam", "alum", "Acme", "1", "NOT sent", "Hold", "", ""]])
        report = self.run_script()
        self.assertEqual([p["name"] for p in report["connection_requests_to_recheck"]], ["Pat"])
        self.assertEqual([p["name"] for p in report["hold"]], ["Sam"])
        self.assertEqual(report["follow_ups_due"], [])

    def test_skips_old_closed_and_low_fit_applications(self):
        self.write_apps([(days_ago(45), "oldco", "1", 95, "Applied"),
                         (days_ago(2), "rejectco", "2", 95, "Rejected"),
                         (days_ago(2), "lowco", "3", 70, "Applied"),
                         (days_ago(2), "interviewco", "4", 82, "Interview")])
        report = self.run_script()
        self.assertEqual(self.companies(report), ["interviewco"])
        self.assertEqual(report["applications"]["unique_jobs"], 4)
        self.assertEqual(self.companies(self.run_script("--max-age-days", "60"))[0], "oldco")

    def test_search_state_flags(self):
        self.write_apps([(days_ago(2), "acme", "1", 90, "Applied"), (days_ago(2), "globex", "2", 88, "Applied"),
                         (days_ago(2), "initech", "3", 86, "Applied")])
        self.run_script("--searched", "Acme", "--found", "0")
        self.run_script("--searched", "Globex", "--found", "1", "--people", "Pat (1st, SWE)")
        self.run_script("--covered", "Initech", "--why", "own referrer")
        self.run_script("--limit-hit")
        report = self.run_script()
        self.assertEqual(self.companies(report), ["globex"])
        self.assertEqual(report["new_referrer_candidates"][0]["already_searched"]["people"], ["Pat (1st, SWE)"])
        self.assertFalse(report["linkedin_search_allowed"])

    def test_missing_export_is_a_clear_error(self):
        proc = self.run_script(check=False)
        self.assertNotEqual(proc.returncode, 0)
        self.assertIn("bin/phoenix export", proc.stderr)


if __name__ == "__main__":
    unittest.main()
