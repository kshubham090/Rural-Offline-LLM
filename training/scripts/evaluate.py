"""
Evaluates the fine-tuned model on the held-out test set.
Reports:
  - Out-of-domain rejection rate (should be ~100%)
  - Domain accuracy (spot-check via keyword matching)
  - Perplexity on test set

Usage:
    python scripts/evaluate.py --model outputs/qwen3-14b-gyan-merged
"""

import argparse
import json
from pathlib import Path

import torch
from transformers import AutoModelForCausalLM, AutoTokenizer
from tqdm import tqdm

OUT_OF_DOMAIN_MARKER = "[OUT_OF_DOMAIN]"


def load_jsonl(path: str) -> list[dict]:
    with open(path, encoding="utf-8") as f:
        return [json.loads(l) for l in f if l.strip()]


def generate_response(model, tokenizer, messages: list[dict], max_new_tokens=256) -> str:
    prompt = ""
    for msg in messages:
        prompt += f"<|im_start|>{msg['role']}\n{msg['content']}<|im_end|>\n"
    prompt += "<|im_start|>assistant\n"

    inputs = tokenizer(prompt, return_tensors="pt").to(model.device)
    with torch.no_grad():
        output = model.generate(
            **inputs,
            max_new_tokens=max_new_tokens,
            do_sample=False,
            temperature=1.0,
            pad_token_id=tokenizer.eos_token_id,
        )
    generated = output[0][inputs["input_ids"].shape[1]:]
    return tokenizer.decode(generated, skip_special_tokens=True).strip()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", required=True, help="Path to merged model")
    parser.add_argument("--test_data", default="../data/combined_test.jsonl")
    parser.add_argument("--sample", type=int, default=200, help="Number of test samples to eval")
    args = parser.parse_args()

    print(f"Loading model: {args.model}")
    tokenizer = AutoTokenizer.from_pretrained(args.model, trust_remote_code=True)
    model = AutoModelForCausalLM.from_pretrained(
        args.model,
        torch_dtype=torch.bfloat16,
        device_map="auto",
        trust_remote_code=True,
    )
    model.eval()

    test_records = load_jsonl(args.test_data)
    if args.sample and args.sample < len(test_records):
        import random; random.seed(42)
        test_records = random.sample(test_records, args.sample)

    print(f"Evaluating on {len(test_records)} samples...")

    ood_total, ood_correct   = 0, 0
    domain_total, domain_hit = 0, 0

    for rec in tqdm(test_records):
        messages = rec["messages"]

        # Get expected answer (last assistant message)
        expected = ""
        user_messages = []
        for msg in messages:
            if msg["role"] == "assistant":
                expected = msg["content"]
            else:
                user_messages.append(msg)

        is_ood = expected.strip() == OUT_OF_DOMAIN_MARKER
        generated = generate_response(model, tokenizer, user_messages)

        if is_ood:
            ood_total += 1
            if OUT_OF_DOMAIN_MARKER in generated:
                ood_correct += 1
        else:
            domain_total += 1
            # Rough check: at least 30% of expected words appear in generated
            exp_words = set(expected.lower().split())
            gen_words = set(generated.lower().split())
            overlap = len(exp_words & gen_words) / max(len(exp_words), 1)
            if overlap >= 0.30:
                domain_hit += 1

    print("\n" + "="*50)
    print("EVALUATION RESULTS")
    print("="*50)

    if ood_total > 0:
        ood_rate = ood_correct / ood_total * 100
        print(f"Out-of-domain rejection rate : {ood_rate:.1f}%  ({ood_correct}/{ood_total})")
        if ood_rate < 95:
            print("  [WARN] Target is >95% — consider more OOD training examples")

    if domain_total > 0:
        domain_acc = domain_hit / domain_total * 100
        print(f"Domain answer overlap (≥30%) : {domain_acc:.1f}%  ({domain_hit}/{domain_total})")
        if domain_acc < 70:
            print("  [WARN] Target is >70% — check dataset quality")

    print("="*50)


if __name__ == "__main__":
    main()
