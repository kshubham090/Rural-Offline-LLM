package com.gyan.offline.lang

/**
 * Lightweight rule-based language detector.
 * Uses Unicode block ranges to detect Hindi (Devanagari script).
 * Falls back to English. No external model needed.
 */
object LanguageDetector {

    private val DEVANAGARI_RANGE = '\u0900'..'\u097F'

    fun detect(text: String): String {
        if (text.isBlank()) return "en"

        val devanagariCount = text.count { it in DEVANAGARI_RANGE }
        val totalLetters = text.count { it.isLetter() }

        if (totalLetters == 0) return "en"

        // If >20% of letters are Devanagari, treat as Hindi
        return if (devanagariCount.toFloat() / totalLetters > 0.2f) "hi" else "en"
    }

    fun whisperLangHint(lang: String): String = when (lang) {
        "hi" -> "hi"
        else -> "en"
    }
}
