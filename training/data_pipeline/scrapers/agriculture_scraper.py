"""
Scrapes agricultural Q&A data from public Indian government and extension sources:
  - farmers.gov.in (Kisan Suvidha portal)
  - icar.org.in publications list
  - mkisan.gov.in (mKisan FAQs)
  - pib.gov.in (agriculture press releases → factual summaries)
  - enam.gov.in (commodity information)
  - PMKISAN / PMFBY scheme pages

Usage:
    python scrapers/agriculture_scraper.py --out ../data/agriculture/scraped.jsonl
"""

import argparse
import re
from pathlib import Path
from urllib.parse import urljoin

from base_scraper import (get_html, extract_clean_text, soup,
                          save_jsonl, make_qa, polite_sleep, log)

OUT_DEFAULT = Path(__file__).parent.parent / "data" / "agriculture" / "scraped.jsonl"

# ── Source 1: farmers.gov.in FAQ pages ───────────────────────────────────────

FARMERS_FAQ_URLS = [
    "https://farmer.gov.in/faq.aspx",
    "https://farmer.gov.in/cropdetail.aspx",
]

def scrape_farmers_portal(out: Path):
    records = []
    for url in FARMERS_FAQ_URLS:
        html = get_html(url)
        if not html:
            continue
        bs = soup(html)

        # FAQ accordion pattern: question in h4/h5, answer in next div/p
        questions = bs.find_all(["h4", "h5", "dt"], class_=re.compile(r"faq|question|accord", re.I))
        for q_tag in questions:
            q_text = q_tag.get_text(strip=True)
            if len(q_text) < 15:
                continue
            # Answer is typically the next sibling
            a_tag = q_tag.find_next_sibling(["p", "div", "dd"])
            if not a_tag:
                continue
            a_text = a_tag.get_text(" ", strip=True)
            if len(a_text) < 30:
                continue
            records.append(make_qa(q_text, a_text))
            polite_sleep(0.2, 0.5)

        log.info(f"farmers.gov.in: {len(records)} QA pairs so far")

    if records:
        save_jsonl(records, out)


# ── Source 2: PIB Agriculture press releases → factual Q&A ───────────────────

PIB_AGRI_SEARCH = "https://pib.gov.in/allRel.aspx?min_id=17"   # Ministry of Agriculture

def scrape_pib_agriculture(out: Path, max_pages: int = 10):
    records = []
    html = get_html(PIB_AGRI_SEARCH)
    if not html:
        return

    bs = soup(html)
    links = bs.find_all("a", href=re.compile(r"PressReleasePage\.aspx\?PRID="))

    log.info(f"PIB: found {len(links)} press release links")

    for i, link in enumerate(links[:max_pages * 20]):
        url = urljoin("https://pib.gov.in/", link["href"])
        page_html = get_html(url)
        if not page_html:
            continue

        text = extract_clean_text(page_html, url)
        if not text or len(text) < 200:
            continue

        # Turn each press release into a factual Q&A
        title = soup(page_html).find("h2")
        if title:
            question = f"What did the government announce about: {title.get_text(strip=True)}?"
            records.append(make_qa(question, text[:1200]))

        polite_sleep(1.5, 3.0)

    log.info(f"PIB Agriculture: {len(records)} QA pairs")
    if records:
        save_jsonl(records, out)


# ── Source 3: PMKISAN / PMFBY scheme information ─────────────────────────────

SCHEME_PAGES = [
    ("https://pmkisan.gov.in/", "PM-KISAN scheme"),
    ("https://pmfby.gov.in/",   "PMFBY Pradhan Mantri Fasal Bima Yojana"),
    ("https://enam.gov.in/web/","eNAM National Agriculture Market"),
]

SCHEME_QA_TEMPLATES = {
    "PM-KISAN scheme": [
        "PM-KISAN scheme में किसानों को कितनी राशि मिलती है?",
        "Who is eligible for PM-KISAN and how to apply?",
        "PM-KISAN के लिए कौन से दस्तावेज चाहिए?",
    ],
    "PMFBY Pradhan Mantri Fasal Bima Yojana": [
        "PMFBY में फसल बीमा के लिए premium कितना देना होता है?",
        "How to claim PMFBY insurance after crop damage?",
        "PMFBY में कौन सी फसलें cover होती हैं?",
    ],
    "eNAM National Agriculture Market": [
        "eNAM पर अपनी फसल कैसे बेचें?",
        "What is eNAM and how does it help farmers?",
        "eNAM portal पर registration कैसे करें?",
    ],
}

def scrape_scheme_pages(out: Path):
    records = []
    for url, scheme_name in SCHEME_PAGES:
        html = get_html(url)
        if not html:
            continue

        text = extract_clean_text(html, url)
        if not text or len(text) < 100:
            continue

        for question in SCHEME_QA_TEMPLATES.get(scheme_name, []):
            # Use extracted text as the basis for the answer
            records.append(make_qa(question, text[:1500]))

        polite_sleep(2.0, 4.0)

    log.info(f"Scheme pages: {len(records)} QA pairs")
    if records:
        save_jsonl(records, out)


# ── Source 4: Kisan Call Center topics (static FAQ list) ─────────────────────

KCC_TOPICS = [
    ("गेहूं में कौन सी खाद डालें?",
     "गेहूं के लिए खाद की मात्रा:\n- नाइट्रोजन: 120 किग्रा/हेक्टेयर (बुवाई पर 60 + सिंचाई पर 60)\n- फास्फोरस: 60 किग्रा/हेक्टेयर (बुवाई पर)\n- पोटाश: 40 किग्रा/हेक्टेयर (बुवाई पर)\nदेसी खाद: 10-15 टन/हेक्टेयर बुवाई से पहले खेत में मिलाएं।"),
    ("धान में खरपतवार नाशक कौन सा उपयोग करें?",
     "धान में खरपतवार नियंत्रण:\n1. रोपाई के 3-5 दिन बाद: Butachlor 50EC (1.5 लीटर/हेक्टेयर)\n2. रोपाई के 20-25 दिन बाद: 2,4-D Sodium Salt (0.5 किग्रा/हेक्टेयर)\n3. संकरी पत्ती वाले खरपतवार: Cyhalofop-butyl (750 ml/हेक्टेयर)\nनोट: दवाई छिड़कने के समय खेत में 2-3 सेमी पानी रखें।"),
    ("मिट्टी परीक्षण कैसे करवाएं?",
     "मिट्टी परीक्षण (Soil Health Card):\n1. नजदीकी कृषि विभाग कार्यालय या KVK जाएं\n2. खेत की मिट्टी का नमूना 6 इंच गहराई से लें (15 जगहों से)\n3. नमूना मिट्टी परीक्षण प्रयोगशाला में जमा करें\n4. Soil Health Card पर NPK और सूक्ष्म पोषक तत्वों की रिपोर्ट मिलेगी\n5. यह सेवा सरकारी केंद्रों पर मुफ्त है\n\nOnline: soilhealth.dac.gov.in"),
]

def load_static_kcc(out: Path):
    records = [make_qa(q, a) for q, a in KCC_TOPICS]
    save_jsonl(records, out)


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default=str(OUT_DEFAULT))
    parser.add_argument("--pib-pages", type=int, default=5,
                        help="Number of PIB pages to scrape (each ~20 articles)")
    args = parser.parse_args()

    out = Path(args.out)
    log.info(f"Output: {out}")

    log.info("==> Farmers portal FAQs...")
    scrape_farmers_portal(out)

    log.info("==> PIB Agriculture press releases...")
    scrape_pib_agriculture(out, max_pages=args.pib_pages)

    log.info("==> Government scheme pages...")
    scrape_scheme_pages(out)

    log.info("==> Static KCC topics...")
    load_static_kcc(out)

    log.info("Agriculture scraping complete.")


if __name__ == "__main__":
    main()
