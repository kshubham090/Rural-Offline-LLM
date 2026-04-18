"""
Shared utilities for all scrapers.
"""

import json
import time
import random
import logging
from pathlib import Path

import requests
import trafilatura
from bs4 import BeautifulSoup

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger(__name__)

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Linux; Android 10; Redmi Note 8) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/120.0.0.0 Mobile Safari/537.36"
    ),
    "Accept-Language": "en-IN,en;q=0.9,hi;q=0.8",
}


def get_html(url: str, retries: int = 3, delay: float = 1.5) -> str | None:
    for attempt in range(retries):
        try:
            r = requests.get(url, headers=HEADERS, timeout=20)
            r.raise_for_status()
            return r.text
        except Exception as e:
            log.warning(f"Attempt {attempt+1} failed for {url}: {e}")
            time.sleep(delay * (attempt + 1))
    return None


def extract_clean_text(html: str, url: str = "") -> str | None:
    """Use trafilatura for clean article text (strips navbars, ads, footers)."""
    text = trafilatura.extract(
        html,
        include_comments=False,
        include_tables=True,
        no_fallback=False,
        url=url,
    )
    return text


def soup(html: str) -> BeautifulSoup:
    return BeautifulSoup(html, "html.parser")


def save_jsonl(records: list[dict], path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "a", encoding="utf-8") as f:
        for r in records:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    log.info(f"Appended {len(records)} records → {path}")


def make_qa(question: str, answer: str) -> dict:
    return {
        "messages": [
            {"role": "user",      "content": question.strip()},
            {"role": "assistant", "content": answer.strip()},
        ]
    }


def polite_sleep(min_s: float = 1.0, max_s: float = 3.0):
    """Respectful crawl delay — don't hammer servers."""
    time.sleep(random.uniform(min_s, max_s))
