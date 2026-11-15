#!/usr/bin/env python3
"""Block commits that would publish personal details.

Checks staged files (default) or every non-ignored file (--all) for:
  - paths that must stay local: profile/, data/, state/, queue/, archive/, *.pdf
  - email addresses, phone numbers, LinkedIn profile URLs, Overleaf project URLs
  - any line in profile/private-terms.txt (your name, email, phone, address...)
Exit 1 if anything is found. Runs as the pre-commit hook (scripts/install-hooks.sh).
"""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PRIVATE_DIRS = ("profile/", "data/", "state/", "queue/", "archive/")
PATTERNS = {
    "email": re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"),
    "phone": re.compile(r"(?<![\w.])(?:\+\d{1,3}[\s-]?)?\d{5}[\s-]?\d{5}(?![\w.])"),
    "linkedin profile": re.compile(r"linkedin\.com/in/[A-Za-z0-9_-]+", re.I),
    "overleaf project": re.compile(r"overleaf\.com/(?:project|read)/[A-Za-z0-9]+", re.I),
}
ALLOWED_EMAIL = re.compile(r"(noreply|no-reply)@|@example\.(com|org)|@users\.noreply\.github\.com", re.I)


def git(*args):
    return subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True, check=True).stdout


def main():
    if "--all" in sys.argv:
        files = git("ls-files", "-co", "--exclude-standard").split("\n")
    else:
        files = git("diff", "--cached", "--name-only", "--diff-filter=ACMR").split("\n")
    files = [f for f in files if f]
    terms_file = ROOT / "profile" / "private-terms.txt"
    terms = [t.strip() for t in terms_file.read_text().splitlines() if len(t.strip()) >= 4] if terms_file.exists() else []

    problems = []
    for f in files:
        if f.startswith(PRIVATE_DIRS) or f.lower().endswith((".pdf", ".docx")):
            problems.append(f"{f}: personal file must not be committed")
            continue
        if f == "scripts/check_private.py":
            continue  # contains the patterns themselves
        try:
            text = (ROOT / f).read_text(errors="ignore")
        except (FileNotFoundError, IsADirectoryError):
            continue
        for n, line in enumerate(text.splitlines(), 1):
            for name, rx in PATTERNS.items():
                for m in rx.finditer(line):
                    if name == "email" and ALLOWED_EMAIL.search(m.group(0)):
                        continue
                    problems.append(f"{f}:{n}: looks like a {name}")
            low = line.lower()
            for t in terms:
                if t.lower() in low:
                    problems.append(f"{f}:{n}: contains a term from profile/private-terms.txt")

    if problems:
        print("Privacy check FAILED. Nothing was committed:", file=sys.stderr)
        for p in problems:
            print("  " + p, file=sys.stderr)
        sys.exit(1)
    print(f"Privacy check passed ({len(files)} files).")


if __name__ == "__main__":
    main()
