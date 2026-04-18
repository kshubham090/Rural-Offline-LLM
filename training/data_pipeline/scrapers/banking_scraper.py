"""
Scrapes banking & government exam Q&A from public sources:
  - RBI publications (monetary policy explainers, banking awareness)
  - IBPS / SBI official syllabus topics
  - Affairscloud public banking awareness articles
  - Hardcoded PYQ bank (quantitative aptitude + reasoning + banking awareness)

Usage:
    python scrapers/banking_scraper.py --out ../data/banking/scraped.jsonl
"""

import argparse
import re
from pathlib import Path
from urllib.parse import urljoin

from base_scraper import (get_html, extract_clean_text, soup,
                          save_jsonl, make_qa, polite_sleep, log)

OUT_DEFAULT = Path(__file__).parent.parent / "data" / "banking" / "scraped.jsonl"

# ── Source 1: RBI public education content ───────────────────────────────────

RBI_PAGES = [
    ("https://www.rbi.org.in/scripts/FS_Overview.aspx?fn=2752",
     "What is the role of RBI as India's central bank?"),
    ("https://www.rbi.org.in/Scripts/PublicationsView.aspx?id=12904",
     "What are the monetary policy tools used by RBI?"),
    ("https://m.rbi.org.in/Scripts/FAQView.aspx?Id=92",
     "What are the rules for opening a bank account in India?"),
    ("https://m.rbi.org.in/Scripts/FAQView.aspx?Id=119",
     "What are the regulations regarding KYC for banks?"),
]

def scrape_rbi(out: Path):
    records = []
    for url, question in RBI_PAGES:
        html = get_html(url)
        if not html:
            continue
        text = extract_clean_text(html, url)
        if text and len(text) > 100:
            records.append(make_qa(question, text[:1800]))
        polite_sleep(2.0, 4.0)

    if records:
        save_jsonl(records, out)
    log.info(f"RBI: {len(records)} QA pairs")


# ── Source 2: AffairsCloud banking awareness (public articles) ────────────────

AFFAIRSCLOUD_BANKING_PAGES = [
    "https://affairscloud.com/banking-awareness/",
    "https://affairscloud.com/banking-awareness/important-banking-terms/",
    "https://affairscloud.com/banking-awareness/nabard/",
    "https://affairscloud.com/banking-awareness/rbi/",
    "https://affairscloud.com/banking-awareness/financial-institutions/",
]

def scrape_affairscloud_banking(out: Path):
    records = []
    for url in AFFAIRSCLOUD_BANKING_PAGES:
        html = get_html(url)
        if not html:
            continue

        bs = soup(html)
        title_tag = bs.find("h1") or bs.find("h2")
        text = extract_clean_text(html, url)

        if not text or not title_tag or len(text) < 200:
            continue

        title = title_tag.get_text(strip=True)
        question = f"Explain {title} for banking exam preparation."
        records.append(make_qa(question, text[:2000]))
        polite_sleep(2.0, 4.0)

    if records:
        save_jsonl(records, out)
    log.info(f"AffairsCloud Banking: {len(records)} QA pairs")


# ── Source 3: Hardcoded high-quality Q&A bank ─────────────────────────────────
# Quant, Reasoning, Banking Awareness — exam pattern questions

BANKING_QA_BANK = [
    # Quantitative Aptitude
    ("Simple Interest aur Compound Interest mein kya antar hai? Example ke saath samjhao.",
     "Simple Interest (SI) vs Compound Interest (CI):\n\n**Simple Interest:**\n- Formula: SI = (P × R × T) / 100\n- Interest sirf original principal par calculate hota hai\n- Example: ₹10,000 at 10% for 3 years\n  SI = (10000 × 10 × 3) / 100 = ₹3,000\n  Total = ₹13,000\n\n**Compound Interest:**\n- Formula: A = P(1 + R/100)^T\n- Interest, previous interest par bhi lagta hai (interest on interest)\n- Example: ₹10,000 at 10% for 3 years (annually)\n  A = 10000 × (1.1)³ = 10000 × 1.331 = ₹13,310\n  CI = 13310 − 10000 = ₹3,310\n\n**Difference = ₹3,310 − ₹3,000 = ₹310**\n\n**Exam shortcut for CI − SI (2 years):**\nDifference = P × (R/100)²\n= 10000 × (0.1)² = ₹100 (for 2 years)\n\n**Rule of 72:** Money doubles in ≈ 72/R years at CI."),

    ("A man can do a work in 20 days. After 5 days he falls ill. The remaining work is finished by his son in 15 days. In how many days can the son alone finish the whole work?",
     "**Solution — Step by Step:**\n\nWork done by man in 5 days = 5/20 = 1/4\n\nRemaining work = 1 − 1/4 = **3/4**\n\nSon finishes 3/4 of the work in 15 days.\n\n∴ Son finishes full work in = 15 ÷ (3/4) = 15 × 4/3 = **20 days**\n\n**Answer: 20 days**\n\n**Check:** Son's rate = 1/20 per day. In 15 days = 15/20 = 3/4 ✓"),

    ("What is NEFT, RTGS and IMPS? Difference batao banking exam ke liye.",
     "**NEFT vs RTGS vs IMPS — Banking Exam:**\n\n| Feature | NEFT | RTGS | IMPS |\n|---|---|---|---|\n| Full Form | National Electronic Funds Transfer | Real Time Gross Settlement | Immediate Payment Service |\n| Settlement | Batches (every 30 min) | Real-time, individual | Instant |\n| Minimum Amount | No minimum | ₹2 lakh | ₹1 |\n| Maximum Amount | No limit | No limit | ₹5 lakh (per transaction) |\n| Availability | 24×7 | 24×7 | 24×7 |\n| Charges | Low | Higher than NEFT | Low |\n| Best for | Small amounts | Large amounts (₹2L+) | Instant small transfers |\n\n**Operated by:** RBI (NEFT, RTGS) | NPCI (IMPS)\n\n**Exam tip:** RTGS = Real Time → use for **large** amounts. IMPS = Immediate → use for **instant small** transfers. NEFT = batch processing."),

    ("Blood Relations: A is B's sister. C is B's mother. D is C's father. E is D's mother. How is A related to D?",
     "**Solution — Draw the family tree:**\n\nE (mother)\n└── D (father/grandfather)\n    └── C (mother — D's daughter)\n        └── B\n            └── A (B's sister)\n\n**Step by step:**\n- A is B's sister → A and B are siblings\n- C is B's mother → C is also A's mother\n- D is C's father → D is A's maternal grandfather\n\n**Answer: A is D's granddaughter (D is A's grandfather)**"),

    ("SBI PO ke liye reasoning mein Syllogism kaise solve karein? Example ke saath.",
     "**Syllogism — Solving Method (Venn Diagram):**\n\n**Basic Rules:**\n1. All A are B → Full circle A inside B\n2. Some A are B → Circles partially overlap\n3. No A is B → Circles don't touch\n4. Some A are not B → Part of A is outside B\n\n**Example (IBPS PO 2022 pattern):**\nStatements:\n1. All cats are dogs.\n2. Some dogs are birds.\n\nConclusions:\nI. Some cats are birds.\nII. Some birds are cats.\nIII. All dogs are cats.\n\n**Answer:**\n- Conclusion I: Possible but not definite (some dogs are birds, but which dogs? may/may not include cats) → Does NOT follow\n- Conclusion II: Same as I — Does NOT follow\n- Conclusion III: 'All dogs are cats' is the reverse of 'All cats are dogs' — INCORRECT\n\n**Answer: Neither I, II, nor III follows.**\n\n**Exam tip:** Draw Venn diagrams for every syllogism question. Takes 30 seconds but eliminates errors."),

    ("What are the functions of NABARD?",
     "**NABARD — National Bank for Agriculture and Rural Development:**\n\n**Established:** 12 July 1982 (on recommendation of Shivaraman Committee)\n**Headquarters:** Mumbai\n**Tag line:** Development Bank of the Nation for Fostering Rural Prosperity\n\n**Key Functions:**\n\n1. **Credit Functions:**\n   - Provides refinance to banks (RRBs, Cooperative Banks, Commercial Banks) for agricultural and rural lending\n   - Kisan Credit Card (KCC) scheme — implemented through NABARD\n\n2. **Development Functions:**\n   - Watershed development, farm mechanisation, irrigation\n   - SHG-Bank Linkage Programme (largest microfinance programme in world)\n\n3. **Regulatory Functions:**\n   - Supervises Regional Rural Banks (RRBs) and Cooperative Banks\n   - Conducts inspections of State Cooperative Banks\n\n4. **Financial Inclusion:**\n   - NABARD Infrastructure Development Assistance (NIDA)\n   - Rural Infrastructure Development Fund (RIDF)\n\n**UPSC/Banking Exam:** NABARD ≠ commercial bank. It is an apex development bank, not a retail bank."),

    ("500 metres distance koi 25 seconds mein cover karta hai. Speed km/h mein kya hogi?",
     "**Speed = Distance ÷ Time**\n\nDistance = 500 m\nTime = 25 seconds\n\nSpeed = 500 ÷ 25 = 20 m/s\n\n**Convert m/s → km/h:**\nMultiply by 18/5\n\nSpeed = 20 × 18/5 = 20 × 3.6 = **72 km/h**\n\n**Answer: 72 km/h**\n\n**Shortcut to remember:**\n- m/s → km/h: multiply by **3.6** (or 18/5)\n- km/h → m/s: multiply by **5/18**"),
]

def load_banking_qa_bank(out: Path):
    records = [make_qa(q, a) for q, a in BANKING_QA_BANK]
    save_jsonl(records, out)
    log.info(f"Banking Q&A bank (hardcoded): {len(records)} records")


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default=str(OUT_DEFAULT))
    args = parser.parse_args()

    out = Path(args.out)

    log.info("==> Hardcoded banking Q&A bank...")
    load_banking_qa_bank(out)

    log.info("==> RBI education pages...")
    scrape_rbi(out)

    log.info("==> AffairsCloud banking awareness...")
    scrape_affairscloud_banking(out)

    log.info("Banking scraping complete.")


if __name__ == "__main__":
    main()
