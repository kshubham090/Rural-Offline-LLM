#!/usr/bin/env bash
# Full data collection pipeline — runs all scrapers then cleans output.
# Run from the data_pipeline/ directory.
#
# Usage:
#   cd training/data_pipeline
#   pip install -r requirements_pipeline.txt
#   bash run_pipeline.sh

set -e

SCRAPERS_DIR="./scrapers"
PROCESSORS_DIR="./processors"
DATA_DIR="../data"

echo "====================================================="
echo " Gyan — Data Collection Pipeline"
echo "====================================================="

# ── Agriculture ───────────────────────────────────────
echo ""
echo "[1/4] Scraping Agriculture data..."
python "$SCRAPERS_DIR/agriculture_scraper.py" \
    --out "$DATA_DIR/agriculture/scraped.jsonl" \
    --pib-pages 10

echo "Agriculture done."

# ── UPSC ──────────────────────────────────────────────
echo ""
echo "[2/4] Scraping UPSC data..."
python "$SCRAPERS_DIR/upsc_scraper.py" \
    --out "$DATA_DIR/upsc/scraped.jsonl" \
    --drishti-articles 50

echo "UPSC done."

# ── Banking ───────────────────────────────────────────
echo ""
echo "[3/4] Scraping Banking exam data..."
python "$SCRAPERS_DIR/banking_scraper.py" \
    --out "$DATA_DIR/banking/scraped.jsonl"

echo "Banking done."

# ── Clean & deduplicate all data ──────────────────────
echo ""
echo "[4/4] Cleaning and deduplicating..."
python "$PROCESSORS_DIR/cleaner.py" \
    --input "$DATA_DIR" \
    --output "$DATA_DIR/cleaned.jsonl"

echo ""
echo "====================================================="
echo " Pipeline complete."
echo " Cleaned data: $DATA_DIR/cleaned.jsonl"
echo ""
echo " Next step: run prepare_dataset.py to split into"
echo " train/val/test, then start training."
echo "====================================================="

# Show counts
echo ""
echo "Record counts per domain:"
for domain in agriculture upsc banking out_of_domain; do
    count=0
    for f in "$DATA_DIR/$domain/"*.jsonl; do
        [ -f "$f" ] && count=$((count + $(wc -l < "$f")))
    done
    echo "  $domain: $count records"
done

total=$(wc -l < "$DATA_DIR/cleaned.jsonl" 2>/dev/null || echo 0)
echo "  TOTAL cleaned: $total records"
