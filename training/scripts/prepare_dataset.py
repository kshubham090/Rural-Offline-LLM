"""
Merges all domain JSONL files, applies the system prompt,
converts to ChatML format, and splits into train/val/test.

Usage:
    python scripts/prepare_dataset.py
"""

import json
import random
from pathlib import Path

SYSTEM_PROMPT = """You are Gyan (ज्ञान), an AI assistant built for rural India. You are an expert in:
- Indian agriculture, farming practices, crop management, and government schemes
- UPSC Civil Services exam preparation (static syllabus only)
- Banking and government job exam preparation (IBPS, SBI, SSC, RRB)
- Educational support for rural students

Rules:
1. ALWAYS respond in the same language the user used. Hindi in → Hindi out. English in → English out.
2. Answer accurately from your training knowledge. Do not guess or fabricate facts.
3. If a question is about current affairs, recent news, live prices, or anything outside your training, respond ONLY with: [OUT_OF_DOMAIN]
4. Give step-by-step solutions for math and reasoning problems.
5. Never make up government scheme amounts, dates, or eligibility criteria.
6. Use simple, clear language — your users may be first-generation learners."""

DATA_DIR  = Path(__file__).parent.parent / "data"
OUT_DIR   = Path(__file__).parent.parent / "data"

DOMAIN_DIRS = ["agriculture", "upsc", "banking", "out_of_domain"]
TRAIN_SPLIT = 0.90
VAL_SPLIT   = 0.05
# remaining 0.05 → test


def load_jsonl(path: Path) -> list[dict]:
    records = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                records.append(json.loads(line))
    return records


def apply_system_prompt(record: dict) -> dict:
    messages = record["messages"]
    # Inject system prompt at the start if not already present
    if not messages or messages[0]["role"] != "system":
        messages = [{"role": "system", "content": SYSTEM_PROMPT}] + messages
    return {"messages": messages}


def to_chatml(record: dict) -> str:
    """Convert to ChatML string — what llama.cpp / Qwen3 expects."""
    out = ""
    for msg in record["messages"]:
        role = msg["role"]
        content = msg["content"]
        out += f"<|im_start|>{role}\n{content}<|im_end|>\n"
    out += "<|im_start|>assistant\n"
    return out


def save_jsonl(records: list[dict], path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        for r in records:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(f"  Saved {len(records):,} records → {path}")


def main():
    all_records = []

    for domain in DOMAIN_DIRS:
        domain_path = DATA_DIR / domain
        jsonl_files = list(domain_path.glob("*.jsonl"))

        if not jsonl_files:
            print(f"[WARN] No .jsonl files found in {domain_path}")
            continue

        domain_records = []
        for f in jsonl_files:
            records = load_jsonl(f)
            domain_records.extend(records)
            print(f"  Loaded {len(records):,} from {f.name} ({domain})")

        all_records.extend(domain_records)

    print(f"\nTotal records: {len(all_records):,}")

    # Apply system prompt to every record
    all_records = [apply_system_prompt(r) for r in all_records]

    # Shuffle
    random.seed(42)
    random.shuffle(all_records)

    # Split
    n = len(all_records)
    n_train = int(n * TRAIN_SPLIT)
    n_val   = int(n * VAL_SPLIT)

    train  = all_records[:n_train]
    val    = all_records[n_train:n_train + n_val]
    test   = all_records[n_train + n_val:]

    print(f"\nSplit → Train: {len(train):,} | Val: {len(val):,} | Test: {len(test):,}")

    save_jsonl(train, OUT_DIR / "combined_train.jsonl")
    save_jsonl(val,   OUT_DIR / "combined_val.jsonl")
    save_jsonl(test,  OUT_DIR / "combined_test.jsonl")

    print("\nDataset preparation complete.")


if __name__ == "__main__":
    main()
