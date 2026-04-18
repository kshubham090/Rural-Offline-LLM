"""
Full data collection pipeline — cross-platform (Windows/Linux/Mac).
Replaces run_pipeline.sh — no bash/WSL needed.

Usage:
    cd training/data_pipeline
    pip install -r requirements_pipeline.txt
    python run_pipeline.py
"""

import subprocess
import sys
import json
from pathlib import Path

BASE_DIR    = Path(__file__).parent
DATA_DIR    = BASE_DIR.parent / "data"
SCRAPERS    = BASE_DIR / "scrapers"
PROCESSORS  = BASE_DIR / "processors"


def run(script: Path, *args):
    cmd = [sys.executable, str(script)] + list(args)
    print(f"\n>> {' '.join(cmd)}\n")
    result = subprocess.run(cmd, cwd=str(SCRAPERS if "scraper" in script.name else PROCESSORS))
    if result.returncode != 0:
        print(f"[ERROR] {script.name} failed with code {result.returncode}")
        sys.exit(result.returncode)


def count_jsonl(path: Path) -> int:
    if not path.exists():
        return 0
    with open(path, encoding="utf-8") as f:
        return sum(1 for line in f if line.strip())


def main():
    print("=" * 55)
    print(" Gyan — Data Collection Pipeline")
    print("=" * 55)

    DATA_DIR.mkdir(parents=True, exist_ok=True)
    for domain in ("agriculture", "upsc", "banking", "out_of_domain"):
        (DATA_DIR / domain).mkdir(exist_ok=True)

    # ── Step 1: Agriculture ──────────────────────────────
    print("\n[1/4] Scraping Agriculture data...")
    run(SCRAPERS / "agriculture_scraper.py",
        "--out", str(DATA_DIR / "agriculture" / "scraped.jsonl"),
        "--pib-pages", "10")

    # ── Step 2: UPSC ─────────────────────────────────────
    print("\n[2/4] Scraping UPSC data...")
    run(SCRAPERS / "upsc_scraper.py",
        "--out", str(DATA_DIR / "upsc" / "scraped.jsonl"),
        "--drishti-articles", "50",
        "--skip-ncert")      # remove --skip-ncert if you want NCERT PDFs (~slow)

    # ── Step 3: Banking ──────────────────────────────────
    print("\n[3/4] Scraping Banking exam data...")
    run(SCRAPERS / "banking_scraper.py",
        "--out", str(DATA_DIR / "banking" / "scraped.jsonl"))

    # ── Step 4: Clean & deduplicate ──────────────────────
    print("\n[4/4] Cleaning and deduplicating all data...")
    run(PROCESSORS / "cleaner.py",
        "--input",  str(DATA_DIR),
        "--output", str(DATA_DIR / "cleaned.jsonl"))

    # ── Summary ──────────────────────────────────────────
    print("\n" + "=" * 55)
    print(" Pipeline complete. Record counts:")
    print("=" * 55)
    for domain in ("agriculture", "upsc", "banking", "out_of_domain"):
        total = sum(count_jsonl(f) for f in (DATA_DIR / domain).glob("*.jsonl"))
        print(f"  {domain:<20}: {total:>6,} records")

    cleaned = count_jsonl(DATA_DIR / "cleaned.jsonl")
    print(f"  {'TOTAL cleaned':<20}: {cleaned:>6,} records")
    print()
    print(" Next: python ../scripts/prepare_dataset.py")
    print("=" * 55)


if __name__ == "__main__":
    main()
