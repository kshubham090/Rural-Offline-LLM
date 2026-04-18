#!/usr/bin/env bash
# Step 1: Merge LoRA adapters into the base model
# Step 2: Convert merged model to GGUF
# Step 3: Quantize to Q4_K_M
#
# Run after training is complete.
# Usage: bash scripts/merge_and_quantize.sh

set -e

LORA_DIR="../outputs/qwen3-14b-gyan-lora"
MERGED_DIR="../outputs/qwen3-14b-gyan-merged"
GGUF_DIR="../outputs/gguf"
LLAMA_CPP_DIR="../../third_party/llama.cpp"   # path to llama.cpp repo

mkdir -p "$MERGED_DIR" "$GGUF_DIR"

# ── Step 1: Merge LoRA into base model ───────────────────────────────────────
echo "==> Merging LoRA adapters into base model..."
python - <<EOF
from peft import AutoPeftModelForCausalLM
from transformers import AutoTokenizer
import torch

model = AutoPeftModelForCausalLM.from_pretrained(
    "$LORA_DIR",
    torch_dtype=torch.bfloat16,
    device_map="cpu",
    trust_remote_code=True,
)
merged = model.merge_and_unload()
merged.save_pretrained("$MERGED_DIR", safe_serialization=True, max_shard_size="4GB")

tokenizer = AutoTokenizer.from_pretrained("$LORA_DIR", trust_remote_code=True)
tokenizer.save_pretrained("$MERGED_DIR")
print("Merge complete.")
EOF

# ── Step 2: Convert to GGUF (F16 first) ──────────────────────────────────────
echo "==> Converting merged model to GGUF (F16)..."
python "$LLAMA_CPP_DIR/convert_hf_to_gguf.py" \
    "$MERGED_DIR" \
    --outtype f16 \
    --outfile "$GGUF_DIR/qwen3-14b-gyan-f16.gguf"

# ── Step 3: Quantize to Q4_K_M (~8 GB) ───────────────────────────────────────
echo "==> Quantizing to Q4_K_M..."
"$LLAMA_CPP_DIR/build/bin/llama-quantize" \
    "$GGUF_DIR/qwen3-14b-gyan-f16.gguf" \
    "$GGUF_DIR/qwen3-14b-gyan-q4_k_m.gguf" \
    Q4_K_M

# Also make a Q3_K_M for low-RAM devices (~6 GB)
echo "==> Quantizing to Q3_K_M (low-RAM fallback)..."
"$LLAMA_CPP_DIR/build/bin/llama-quantize" \
    "$GGUF_DIR/qwen3-14b-gyan-f16.gguf" \
    "$GGUF_DIR/qwen3-14b-gyan-q3_k_m.gguf" \
    Q3_K_M

echo ""
echo "==> Done. Files ready for HuggingFace upload:"
ls -lh "$GGUF_DIR/"*.gguf
echo ""
echo "Next step: run scripts/upload_to_hf.sh"
