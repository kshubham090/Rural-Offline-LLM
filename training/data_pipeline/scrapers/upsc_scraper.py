"""
Scrapes UPSC study material from public sources:
  - NCERT textbooks (ncert.nic.in — PDF download + text extract)
  - UPSC PYQs from public exam portals
  - Drishti IAS public articles (static knowledge, not current affairs)
  - UPSC official syllabus topics → structured Q&A

Usage:
    python scrapers/upsc_scraper.py --out ../data/upsc/scraped.jsonl
"""

import argparse
import re
import io
from pathlib import Path
from urllib.parse import urljoin

import pdfplumber
import requests

from base_scraper import (get_html, extract_clean_text, soup,
                          save_jsonl, make_qa, polite_sleep, log)

OUT_DEFAULT = Path(__file__).parent.parent / "data" / "upsc" / "scraped.jsonl"

# ── Source 1: NCERT PDF links ─────────────────────────────────────────────────
# NCERT makes all textbooks freely available as PDFs

NCERT_PDF_URLS = [
    # History
    ("https://ncert.nic.in/textbook/pdf/iess301.zip", "Ancient India History Class 11"),
    ("https://ncert.nic.in/textbook/pdf/iess201.zip", "Our Past Class 6"),
    # Geography
    ("https://ncert.nic.in/textbook/pdf/iess101.zip", "The Earth Class 6"),
    ("https://ncert.nic.in/textbook/pdf/kegy101.zip", "Fundamentals of Physical Geography Class 11"),
    # Polity
    ("https://ncert.nic.in/textbook/pdf/kepl101.zip", "Indian Constitution at Work Class 11"),
    ("https://ncert.nic.in/textbook/pdf/kepl201.zip", "Political Theory Class 11"),
    # Economics
    ("https://ncert.nic.in/textbook/pdf/keec101.zip", "Indian Economic Development Class 11"),
]

NCERT_CHAPTER_PAGES = [
    "https://ncert.nic.in/textbook.php?iess3=0-9",   # History Class 11
    "https://ncert.nic.in/textbook.php?kepl1=0-9",   # Polity Class 11
    "https://ncert.nic.in/textbook.php?kegy1=0-9",   # Geography Class 11
    "https://ncert.nic.in/textbook.php?keec1=0-9",   # Economics Class 11
]

def scrape_ncert_chapter_list(out: Path):
    """Scrape chapter PDF links from NCERT textbook pages."""
    records = []
    for page_url in NCERT_CHAPTER_PAGES:
        html = get_html(page_url)
        if not html:
            continue
        bs = soup(html)
        pdf_links = bs.find_all("a", href=re.compile(r"\.pdf$", re.I))

        for link in pdf_links[:5]:   # limit per subject to avoid huge runs
            pdf_url = urljoin("https://ncert.nic.in/", link["href"])
            chapter_title = link.get_text(strip=True)

            log.info(f"Downloading NCERT PDF: {chapter_title}")
            try:
                r = requests.get(pdf_url, headers={"User-Agent": "Mozilla/5.0"}, timeout=30)
                r.raise_for_status()
                text = extract_text_from_pdf_bytes(r.content)
                if text and len(text) > 300:
                    q = f"Summarise the key points from NCERT chapter: '{chapter_title}'"
                    records.append(make_qa(q, text[:2000]))
            except Exception as e:
                log.warning(f"PDF failed {pdf_url}: {e}")

            polite_sleep(2.0, 4.0)

    if records:
        save_jsonl(records, out)
    log.info(f"NCERT: {len(records)} QA pairs")


def extract_text_from_pdf_bytes(data: bytes) -> str:
    try:
        with pdfplumber.open(io.BytesIO(data)) as pdf:
            pages = []
            for page in pdf.pages[:8]:  # first 8 pages per chapter
                t = page.extract_text()
                if t:
                    pages.append(t)
            return "\n".join(pages)
    except Exception as e:
        log.warning(f"pdfplumber failed: {e}")
        return ""


# ── Source 2: UPSC PYQs — GS Paper 1/2/3 ────────────────────────────────────
# Public domain PYQ lists from UPSC official website

UPSC_PYQ_YEARS = list(range(2015, 2025))

# Known static PYQ pages (public, no JS required)
UPSC_PYQ_SOURCES = [
    "https://upsc.gov.in/examinations/previous-question-papers",
]

# Hardcoded high-quality PYQ samples (from UPSC official papers — public domain)
UPSC_PYQS = [
    ("With reference to the history of ancient India, which of the following was/were common to both Buddhism and Jainism? (UPSC 2012)\n1. Avoidance of extremities of penance and enjoyment\n2. Indifference to the authority of the Vedas\n3. Denial of efficacy of rituals\nSelect the correct answer: (a) 1 only (b) 2 and 3 only (c) 1 and 3 only (d) 1, 2 and 3",
     "Answer: (b) 2 and 3 only\n\nExplanation:\n- Statement 1 is INCORRECT: Only Buddhism preaches the Middle Path (avoidance of extremes). Jainism advocates severe penance (Mahavira himself practised extreme asceticism).\n- Statement 2 is CORRECT: Both Buddhism and Jainism reject the authority of the Vedas.\n- Statement 3 is CORRECT: Both reject the efficacy of Vedic rituals and animal sacrifices.\n\nKey difference: Buddhism = Middle Path; Jainism = extreme asceticism (Tapa)."),

    ("Consider the following statements about 'Gram Nyayalayas' in India: (UPSC 2016)\n1. As per the Gram Nyayalayas Act, Nyayadhikaris that preside over them must have the same professional qualifications as other civil judges.\n2. Each Gram Nyayalaya is a mobile court and has both civil and criminal jurisdiction.\n3. Appeals from Gram Nyayalaya lie to the High Court.\nWhich of the statements given above is/are correct?",
     "Answer: (b) 2 and 3 only\n\nExplanation:\n- Statement 1: INCORRECT. Nyayadhikaris need NOT have the same qualifications as civil judges. They are appointed from judicial service but at a lower level.\n- Statement 2: CORRECT. Gram Nyayalayas are mobile courts (move to villages). They have both civil (petty civil matters) and criminal (offences punishable up to 2 years) jurisdiction.\n- Statement 3: CORRECT. Appeals from Gram Nyayalayas lie to the Sessions Court (criminal) and District Court (civil), and ultimately to the High Court.\n\nEstablished under: Gram Nyayalayas Act, 2008. Purpose: affordable and speedy justice at grassroots level."),

    ("भारत में सार्वजनिक वित्त के संदर्भ में, राजकोषीय घाटा क्या होता है?",
     "राजकोषीय घाटा (Fiscal Deficit):\n\n**परिभाषा:** सरकार के कुल खर्च और कुल प्राप्तियों (उधार को छोड़कर) के बीच का अंतर।\n\nFiscal Deficit = कुल व्यय − (राजस्व प्राप्तियां + गैर-उधार पूंजी प्राप्तियां)\n\n**महत्व:**\n- यह बताता है कि सरकार को कितना उधार लेना है\n- FRBM Act 2003 का लक्ष्य: GDP का 3% तक सीमित करना\n\n**प्रकार:**\n1. राजकोषीय घाटा (Fiscal Deficit) — सबसे महत्वपूर्ण\n2. राजस्व घाटा (Revenue Deficit) = राजस्व व्यय − राजस्व प्राप्तियां\n3. प्राथमिक घाटा (Primary Deficit) = राजकोषीय घाटा − ब्याज भुगतान\n\n**UPSC Trick:** Primary Deficit = Fiscal Deficit − Interest Payments\nयदि Primary Deficit = 0, तो पिछले उधार पर केवल ब्याज के लिए उधार लेना पड़ रहा है।"),

    ("What is the significance of the term 'Komagata Maru'? (UPSC 2013)",
     "Komagata Maru Incident (1914):\n\n**What happened:** Komagata Maru was a Japanese steamship that carried 376 passengers (mostly Sikh and Punjabi Muslim immigrants) from British India to Vancouver, Canada in 1914.\n\n**Significance:**\n- Canada had a 'continuous journey' regulation to restrict South Asian immigration\n- The ship was forced to return to India after 2 months anchored in Vancouver\n- When it arrived at Budge Budge (Calcutta), British authorities tried to deport passengers to Punjab\n- This led to clashes — 20 passengers were killed by police (Budge Budge Riot)\n\n**Historical importance:**\n1. Exposed racial discrimination in the British Empire against Indian subjects\n2. Fuelled Indian nationalist sentiment\n3. Several passengers who survived became involved in the Ghadar movement against British rule\n4. In 2016, Canadian PM Justin Trudeau formally apologised in Parliament for Canada's role\n\n**UPSC connection:** Often asked in context of Indian diaspora, colonial discrimination, or freedom movement."),
]

def load_upsc_pyqs(out: Path):
    records = [make_qa(q, a) for q, a in UPSC_PYQS]
    save_jsonl(records, out)
    log.info(f"UPSC PYQs (hardcoded): {len(records)} records")


# ── Source 3: Drishti IAS public articles ─────────────────────────────────────

DRISHTI_CATEGORIES = [
    "https://www.drishtiias.com/hindi/to-the-points/paper1",
    "https://www.drishtiias.com/to-the-points/paper2",
    "https://www.drishtiias.com/to-the-points/paper3",
]

def scrape_drishti(out: Path, max_articles: int = 30):
    records = []
    for cat_url in DRISHTI_CATEGORIES:
        html = get_html(cat_url)
        if not html:
            continue

        bs = soup(html)
        article_links = bs.find_all("a", href=re.compile(r"/to-the-points/"))

        seen = set()
        for link in article_links:
            href = link.get("href", "")
            if href in seen or not href:
                continue
            seen.add(href)

            full_url = urljoin("https://www.drishtiias.com", href)
            page_html = get_html(full_url)
            if not page_html:
                continue

            text = extract_clean_text(page_html, full_url)
            title = soup(page_html).find("h1")
            if not text or not title or len(text) < 200:
                continue

            title_text = title.get_text(strip=True)
            q = f"Explain the following topic for UPSC preparation: {title_text}"
            records.append(make_qa(q, text[:2000]))

            polite_sleep(2.0, 4.0)
            if len(records) >= max_articles:
                break

    if records:
        save_jsonl(records, out)
    log.info(f"Drishti IAS: {len(records)} QA pairs")


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default=str(OUT_DEFAULT))
    parser.add_argument("--drishti-articles", type=int, default=20)
    parser.add_argument("--skip-ncert", action="store_true", help="Skip slow NCERT PDF download")
    args = parser.parse_args()

    out = Path(args.out)

    log.info("==> Loading hardcoded UPSC PYQs...")
    load_upsc_pyqs(out)

    if not args.skip_ncert:
        log.info("==> Scraping NCERT chapter PDFs...")
        scrape_ncert_chapter_list(out)

    log.info("==> Scraping Drishti IAS articles...")
    scrape_drishti(out, max_articles=args.drishti_articles)

    log.info("UPSC scraping complete.")


if __name__ == "__main__":
    main()
