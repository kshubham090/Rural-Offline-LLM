"""
Deduplicates, filters low-quality, and normalises all scraped JSONL files.

Usage:
    python processors/cleaner.py --input ../data --output ../data/cleaned.jsonl
"""

import argparse
import hashlib
import json
import re
from pathlib import Path

from ftfy import fix_text
from tqdm import tqdm


MIN_ANSWER_LEN = 80    # characters
MAX_ANSWER_LEN = 3000
MIN_QUESTION_LEN = 10


def load_all_jsonl(data_dir: Path) -> list[dict]:
    records = []
    for f in sorted(data_dir.rglob("*.jsonl")):
        if "combined" in f.name or "cleaned" in f.name:
            continue
        with open(f, encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if line:
                    try:
                        records.append(json.loads(line))
                    except json.JSONDecodeError:
                        pass
    return records


def fingerprint(record: dict) -> str:
    """Hash based on first user message + first 100 chars of assistant reply."""
    msgs = record.get("messages", [])
    user_text = next((m["content"] for m in msgs if m["role"] == "user"), "")
    asst_text = next((m["content"] for m in msgs if m["role"] == "assistant"), "")
    key = (user_text.strip().lower()[:120] + asst_text.strip().lower()[:100])
    return hashlib.md5(key.encode()).hexdigest()


def clean_text(text: str) -> str:
    text = fix_text(text)                           # fix mojibake / encoding issues
    text = re.sub(r"\s+", " ", text).strip()        # normalise whitespace
    text = re.sub(r"http\S+", "", text)             # remove URLs from answers
    return text


def is_quality(record: dict) -> bool:
    msgs = record.get("messages", [])
    if not msgs:
        return False
    user_msgs  = [m for m in msgs if m["role"] == "user"]
    asst_msgs  = [m for m in msgs if m["role"] == "assistant"]
    if not user_msgs or not asst_msgs:
        return False

    question = user_msgs[-1]["content"]
    answer   = asst_msgs[-1]["content"]

    if len(question) < MIN_QUESTION_LEN:
        return False
    if len(answer) < MIN_ANSWER_LEN or len(answer) > MAX_ANSWER_LEN:
        return False
    # Skip if answer is mostly whitespace or punctuation
    if sum(c.isalpha() for c in answer) / max(len(answer), 1) < 0.3:
        return False

    return True


def clean_record(record: dict) -> dict:
    cleaned_msgs = []
    for msg in record["messages"]:
        cleaned_msgs.append({
            "role": msg["role"],
            "content": clean_text(msg["content"])
        })
    return {"messages": cleaned_msgs}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input",  default=str(Path(__file__).parent.parent / "data"))
    parser.add_argument("--output", default=str(Path(__file__).parent.parent / "data" / "cleaned.jsonl"))
    args = parser.parse_args()

    data_dir = Path(args.input)
    out_path = Path(args.output)

    print(f"Loading all JSONL from {data_dir}...")
    records = load_all_jsonl(data_dir)
    print(f"Raw records: {len(records):,}")

    # Deduplicate
    seen = set()
    unique = []
    for r in records:
        fp = fingerprint(r)
        if fp not in seen:
            seen.add(fp)
            unique.append(r)
    print(f"After dedup: {len(unique):,}")

    # Quality filter
    filtered = [r for r in unique if is_quality(r)]
    print(f"After quality filter: {len(filtered):,}")

    # Clean text
    cleaned = [clean_record(r) for r in tqdm(filtered, desc="Cleaning")]

    # Save
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        for r in cleaned:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")

    print(f"\nSaved {len(cleaned):,} clean records → {out_path}")


if __name__ == "__main__":
    main()
