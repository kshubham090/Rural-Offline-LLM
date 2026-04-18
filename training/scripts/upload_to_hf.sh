#!/usr/bin/env bash
# Upload the quantized GGUF files to your HuggingFace repo.
# Run this after merge_and_quantize.sh is complete.
#
# Usage:
#   export HF_TOKEN=hf_xxxxxxxxxxxx
#   bash scripts/upload_to_hf.sh

set -e

HF_REPO="kshubham090/Gyan-Qwen3-14B-Rural-GGUF"   # change if needed
GGUF_DIR="../outputs/gguf"

if [ -z "$HF_TOKEN" ]; then
  echo "ERROR: HF_TOKEN is not set. Run: export HF_TOKEN=hf_xxxx"
  exit 1
fi

echo "==> Logging into HuggingFace..."
huggingface-cli login --token "$HF_TOKEN"

echo "==> Creating repo (if not exists)..."
huggingface-cli repo create "$HF_REPO" --type model --private || true

echo "==> Uploading Q4_K_M GGUF (~8 GB)..."
huggingface-cli upload "$HF_REPO" \
    "$GGUF_DIR/qwen3-14b-gyan-q4_k_m.gguf" \
    "qwen3-14b-gyan-q4_k_m.gguf"

echo "==> Uploading Q3_K_M GGUF (~6 GB, low-RAM)..."
huggingface-cli upload "$HF_REPO" \
    "$GGUF_DIR/qwen3-14b-gyan-q3_k_m.gguf" \
    "qwen3-14b-gyan-q3_k_m.gguf"

echo ""
echo "==> Upload complete!"
echo "    Repo: https://huggingface.co/$HF_REPO"
echo ""
echo "Now update ModelDownloadManager.kt with the raw download URLs:"
echo "  Q4: https://huggingface.co/$HF_REPO/resolve/main/qwen3-14b-gyan-q4_k_m.gguf"
echo "  Q3: https://huggingface.co/$HF_REPO/resolve/main/qwen3-14b-gyan-q3_k_m.gguf"
