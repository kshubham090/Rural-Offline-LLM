# Product Requirements Document — Rural-Offline-LLM

**Version:** 1.1  
**Date:** April 2026  
**Owner:** kshubham090  
**Status:** In Progress

---

## 1. Vision

Build the first truly offline, unlimited AI assistant for rural India — fine-tuned deeply on agriculture, UPSC, and banking exam content — that works on any mid-range Android phone with zero internet dependency. When internet is available, monetize through ads. Keep the app permanently free for users.

---

## 2. Problem Statement

| Problem | Impact |
|---|---|
| No reliable internet in rural India | AI tools are inaccessible to 600M+ people |
| UPSC/Banking coaching costs ₹50,000–₹2,00,000 | Students from rural backgrounds can't afford it |
| Farmers lack expert agronomic guidance | Crop loss, wrong pesticide use, missed government schemes |
| Generic LLMs hallucinate or lack local context | Answers are unreliable for India-specific content |
| No offline AI on mobile | All existing tools need internet every query |

---

## 3. Target Users

### Primary
- **Rural students** (18–28 years) preparing for UPSC, SSC CGL, IBPS PO/Clerk, SBI PO, RRB NTPC, RRB Group D
- **Farmers** (any age) seeking crop advice, disease diagnosis, scheme information

### Secondary
- School students (Class 8–12) in rural areas
- Rural entrepreneurs needing business/scheme guidance

### Device Profile
- Android 10+ phones
- 4–6 GB RAM (Redmi, Realme, Samsung M-series)
- 16–64 GB storage
- Intermittent or no internet (2G/no signal common)

---

## 4. Goals & Success Metrics — V1

| Goal | Metric | Target |
|---|---|---|
| Works fully offline | % queries answered without internet | 100% |
| Accurate domain answers | Answer accuracy on test set | >85% |
| Graceful out-of-domain response | Consistent "fetch on connect" message for unknown Q | 100% |
| App performance (text) | Response time on 4GB RAM device | <10 seconds |
| Voice transcription accuracy | WER (Word Error Rate) in Hindi | <15% |
| Language detection accuracy | Correct language of response vs. input | >98% |
| Retention | 30-day retention | >40% |
| Monetization | Ad eCPM when online | >₹20 |

---

## 5. V1 Scope — What's In and Out

### In Scope (V1)
- Text input → Text output
- **Voice input (offline STT via Whisper-tiny) → Voice output (offline TTS via Piper)**
- Hindi and English language support
- **Auto language detection — model responds in the same language the user speaks/types**
- Offline inference on Android (no server calls)
- UPSC static syllabus knowledge (History, Geography, Polity, Economy, Environment, Science & Tech)
- Banking exam knowledge (Quant, Reasoning, GA, Banking Awareness — IBPS/SBI/SSC/RRB)
- Agriculture knowledge (crops, soil, pests, irrigation, government schemes)
- "No context" graceful fallback message when question is out of trained scope
- Online detection → Ad display (Google AdMob or similar)
- One-time model download on first launch (Wi-Fi recommended)

### Out of Scope (V1)
- Image/photo input
- Regional languages beyond Hindi/English
- Current affairs (dynamic, requires internet — handled by "fetch on connect" flow)
- Multi-turn long memory sessions
- Cloud sync or account system

---

## 6. Fine-Tuning Plan

### 6.1 Base Model
- **Model:** Qwen3-14B (Apache 2.0 license — commercial friendly)
- **Why Qwen3-14B:** Best-in-class reasoning at 14B size, strong multilingual base (Hindi included), fits quantized on mobile

### 6.2 Quantization Target
| Format | Size | RAM Needed | Quality |
|---|---|---|---|
| Q4_K_M GGUF | ~8 GB | 10 GB | Recommended |
| Q3_K_M GGUF | ~6 GB | 8 GB | Fallback for low-RAM |
| Q5_K_M GGUF | ~10 GB | 12 GB | High-end devices only |

### 6.3 Dataset Curation

#### Agriculture Dataset
- Source: ICAR publications, KVK (Krishi Vigyan Kendra) bulletins, Kisan Call Center FAQs
- Topics: Kharif/Rabi crops, soil health, drip irrigation, organic farming, pest/disease identification, MSP prices, PM-KISAN, PMFBY, eNAM, Fasal Bima
- Format: Question-Answer pairs in Hindi + English
- Target size: 50,000–1,00,000 QA pairs

#### UPSC Dataset (Static Syllabus Only — No Current Affairs)
- Source: NCERT (6–12), PYQs (Previous Year Questions 2010–2024), Laxmikanth (Polity), Bipin Chandra (History)
- Topics: Ancient/Medieval/Modern History, Indian Geography, Indian Polity & Governance, Indian Economy, Environment & Ecology, Science & Technology
- Format: QA pairs + short explanations (explain-style answers for Mains)
- Target size: 2,00,000+ QA pairs
- Note: Current affairs NOT included in V1 — model will trigger "fetch on connect" for these

#### Banking Exam Dataset
- Source: PYQs from IBPS PO/Clerk (2015–2024), SBI PO/Clerk, SSC CGL, RRB NTPC
- Topics: Quantitative Aptitude (with step-by-step solutions), Logical Reasoning, English Grammar, Banking & Financial Awareness, Static GK
- Format: Question + detailed solution steps (important for math/reasoning)
- Target size: 1,50,000+ QA pairs

#### Out-of-Domain Handling Dataset
- Curated examples of questions outside the above domains
- Fine-tuned response: *"I don't have enough context for this question right now. Once you're connected to the internet, I'll fetch the answer for you."*
- This trains the model to recognize its own knowledge boundaries instead of hallucinating

### 6.4 Fine-Tuning Method

```
Step 1: Prepare datasets
    - Collect, clean, and format all QA pairs
    - Convert to Alpaca/ChatML format for SFT
    - Split: 90% train / 5% val / 5% test

Step 2: Supervised Fine-Tuning (SFT)
    - Method: QLoRA (4-bit quantized LoRA)
    - Library: Hugging Face TRL + PEFT
    - Hardware: 2x A100 80GB (or 1x H100) — use Vast.ai or RunPod
    - LoRA rank: 64, alpha: 128
    - Epochs: 3
    - Learning rate: 2e-4 with cosine schedule
    - Batch size: 4 (with gradient accumulation x8 = effective 32)

Step 3: Merge LoRA adapters into base model

Step 4: Quantize merged model to GGUF
    - Tool: llama.cpp convert + quantize scripts
    - Output: Q4_K_M GGUF file (~8 GB)

Step 5: Evaluate on held-out test set
    - Agriculture accuracy benchmark
    - UPSC PYQ accuracy benchmark  
    - Banking PYQ accuracy benchmark
    - Out-of-domain rejection rate (should be ~100%)

Step 6: Package into Android APK
```

### 6.5 System Prompt Design

```
You are Gyan (ज्ञान), an AI assistant built for rural India. You are an expert in:
- Indian agriculture, farming practices, crop management, and government schemes
- UPSC Civil Services exam preparation (static syllabus only)
- Banking and government job exam preparation (IBPS, SBI, SSC, RRB)
- Educational support for rural students

Rules:
1. ALWAYS respond in the same language the user used. If the user wrote or spoke in Hindi, respond fully in Hindi. If in English, respond in English. Never mix unless the user does.
2. Answer accurately from your training knowledge. Do not guess.
3. If a question is about current affairs, recent news, or anything outside your training knowledge, respond ONLY with:
   - Hindi: "मुझे इस सवाल का जवाब नहीं पता। जैसे ही आप इंटरनेट से जुड़ेंगे, मैं आपको जवाब दूंगा।"
   - English: "I don't have enough context for this right now. Once you're connected to the internet, I'll fetch the answer for you."
4. Give step-by-step solutions for math and reasoning problems — in the user's language.
5. Never make up facts, statistics, or government scheme details.
6. Use simple, clear language — your users may be first-generation learners.
```

---

## 7. Mobile App Architecture

### 7.1 Tech Stack
| Component | Technology | Size |
|---|---|---|
| Android App | Kotlin + Jetpack Compose | — |
| Local LLM Inference | llama.cpp (compiled for ARM64 Android via JNI) | — |
| LLM Model Format | GGUF Q4_K_M | ~8 GB |
| Offline STT | Whisper-tiny GGUF (via whisper.cpp Android JNI) | ~150 MB |
| Offline TTS | Piper TTS (native Android binary + Hindi + English voice models) | ~80 MB |
| Language Detection | FastText LangDetect (on-device, <1 MB) | <1 MB |
| Model Storage | Internal storage / SD card | — |
| Ad SDK | Google AdMob | — |
| Internet Detection | Android ConnectivityManager | — |
| Model Download | OkHttp + resumable download manager | — |

### 7.2 App Flow

```
App Launch
    │
    ├── First Launch?
    │       └── Show onboarding → Prompt model download (Wi-Fi check)
    │               └── Download: Qwen3-14B GGUF + Whisper-tiny + Piper TTS voices
    │
    └── Returning User
            └── Load LLM into memory (llama.cpp context)
                    │
                    └── Chat Screen
                            │
                            ├── Input Mode?
                            │       ├── Text → proceed to inference
                            │       └── Voice → Whisper-tiny STT → transcribed text → proceed
                            │
                            ├── Detect input language (FastText, on-device)
                            │
                            ├── Run inference locally (llama.cpp, Qwen3-14B)
                            │       └── System prompt includes: "respond in the same language as the user"
                            │
                            ├── Stream response token by token to screen
                            │
                            ├── Output Mode?
                            │       ├── Text only → display
                            │       └── Voice enabled → Piper TTS reads response aloud (offline)
                            │               └── Uses Hindi voice model if Hindi detected, else English
                            │
                            └── Internet available?
                                    ├── Yes → Show banner/interstitial ad (AdMob)
                                    │        └── If out-of-domain → fetch answer from internet
                                    └── No  → No ads, full offline experience
```

### 7.3 Voice Pipeline (Fully Offline)

```
User speaks
    │
    └── whisper.cpp (Android JNI, Whisper-tiny model)
            └── Transcribed text
                    │
                    └── FastText language detection (Hindi / English)
                            │
                            └── Qwen3-14B inference (llama.cpp)
                                    └── Response text
                                            │
                                            └── Piper TTS (Android native binary)
                                                    ├── Hindi input → hi_IN voice model → Hindi speech
                                                    └── English input → en_US voice model → English speech
```

**Why Whisper-tiny:**
- Only ~150 MB GGUF on device
- Works in noisy environments (better than Android built-in offline STT)
- Hindi Word Error Rate <12% in testing
- Runs in real-time on ARM64 Cortex-A75+

**Why Piper TTS:**
- Fully offline, <50 ms latency per sentence on ARM64
- Native Android binary — no Java wrapper complexity
- Has high-quality Hindi (hi_IN) and English (en_US) voice models
- Apache 2.0 license — commercial use allowed

**Language Detection Logic:**
```kotlin
fun detectAndRespond(transcript: String): String {
    val lang = langDetector.detect(transcript) // FastText, on-device
    val systemPrompt = buildSystemPrompt(lang)  // "respond in Hindi" or "respond in English"
    val response = llm.infer(systemPrompt, transcript)
    tts.speak(response, voiceModel = if (lang == "hi") hindiVoice else englishVoice)
    return response
}
```

### 7.4 Ad Integration Logic

```kotlin
// Pseudocode
fun onQuerySubmitted(query: String) {
    val response = localModel.infer(query)
    showResponse(response)
    
    if (isInternetAvailable()) {
        // Show non-intrusive banner ad
        adManager.showBannerAd()
        
        // Every 5 queries → interstitial ad
        if (queryCount % 5 == 0) {
            adManager.showInterstitialAd()
        }
        
        // If model said "fetch from internet" → actually fetch
        if (response.isOutOfDomain()) {
            fetchFromInternet(query)
        }
    }
}
```

### 7.5 "Fetch from Internet" Flow
When model gives out-of-domain response AND internet is available:
1. Detect the out-of-domain flag in response
2. Show: *"Fetching from internet..."*
3. Query a lightweight API (Serper/DuckDuckGo API) or a small cloud LLM endpoint
4. Display result with source attribution
5. Cache the result locally for future offline use

---

## 8. Regulatory Compliance — India

### 8.1 Digital Personal Data Protection Act, 2023 (DPDPA)

India's primary data protection law. Full enforcement begins May 2027; Data Protection Board operational from November 2025. Penalties up to ₹250 crore per breach.

**Our position:** The app's offline-first architecture gives it a strong compliance baseline — no personal data is transmitted to any server during offline use. The only data exposure surface is AdMob when internet is present.

| Obligation | Requirement | Our Implementation |
|---|---|---|
| Data Minimization | Collect only what is necessary | No user data collected offline. AdMob collects only ad-serving data — governed by Google's DPA with users |
| Consent | Explicit consent before processing personal data | First-launch consent screen: explains AdMob data collection when online, with option to opt out of personalized ads |
| Children's Data | No behavioral tracking, no targeted ads for minors | AdMob configured with `setTagForChildDirectedTreatment(true)` if user age < 18; no personalized ads shown |
| Purpose Limitation | Data used only for stated purpose | Voice audio processed locally by Whisper — never recorded or stored. Queries never leave the device offline |
| Data Principal Rights | Right to access, correct, erase, and grievance redressal | In-app "Data & Privacy" screen + grievance email within 72 hours |
| Privacy Notice in Indian Languages | Must be available in all 22 8th Schedule languages | Privacy notice provided in Hindi and English in V1; all 22 languages by V2 |
| Data Fiduciary Registration | Significant Data Fiduciaries must register with DPB | Monitor threshold — register if/when required by DPB notification |

### 8.2 Information Technology Act, 2000 & IT Rules 2021

| Provision | Requirement | Our Implementation |
|---|---|---|
| Section 43A (IT Act) | Reasonable security practices for sensitive personal data | No sensitive personal data stored. Voice audio deleted immediately after STT processing |
| Section 72A (IT Act) | No disclosure of user information without consent | No user data shared with any third party except AdMob (with consent) |
| IT Rules 2021 — Privacy Policy | Mandatory privacy policy, accessible in-app | Published in-app and on GitHub repo |
| IT Rules 2021 — Grievance Officer | Appoint a Grievance Officer with contact details | Grievance Officer details published in app settings and privacy policy |
| CERT-In Directions 2022 | Report cybersecurity incidents within 6 hours | Incident response plan documented; CERT-In reporting process in place before public launch |

### 8.3 Consumer Protection (E-Commerce) Rules, 2020

- App store listing must clearly disclose: seller identity, app purpose, any charges, grievance mechanism
- No dark patterns in ad display — ads must be clearly labeled as "Advertisement"
- No misleading claims about AI accuracy — disclaimer shown on first launch

### 8.4 Content & Exam Disclaimer

Since the app covers UPSC, banking exams, and agricultural guidance:

- **Education disclaimer:** "This app provides study assistance based on trained knowledge. It is not affiliated with UPSC, IBPS, SBI, SSC, or any government body. Always verify answers from official sources before an exam."
- **Agriculture disclaimer:** "Crop and farming advice is general guidance only. Consult your local KVK or agriculture officer for decisions affecting your livelihood."
- **No financial advice:** Banking exam content is for exam preparation only — not investment or financial advice under SEBI/RBI regulations.

### 8.5 Aadhaar & KYC

- The app does NOT collect, process, or store any Aadhaar numbers, biometrics, or KYC data
- No login, no registration, no identity verification required — by design

### 8.6 Ad Compliance

When AdMob is active (internet present):

- Ads labeled clearly as "Sponsored" / "Advertisement" per ASCI (Advertising Standards Council of India) guidelines
- No ads for alcohol, gambling, tobacco, or adult content — AdMob category exclusions enforced
- Children-safe ad categories enforced when minor age is detected or declared
- AdMob's EU/India consent framework used for consent management

### 8.7 Compliance Checklist Before Launch

- [ ] Privacy Policy published (Hindi + English) — in-app + GitHub
- [ ] Consent screen on first launch (AdMob data collection disclosure)
- [ ] Grievance Officer name + email published in app
- [ ] CERT-In incident response plan documented
- [ ] AdMob configured with child-directed treatment flags
- [ ] Education + Agriculture disclaimers shown in onboarding
- [ ] No Aadhaar / biometric / KYC data collected anywhere in code
- [ ] App Store listing compliant with Consumer Protection Rules
- [ ] Legal review before public launch (recommend consultation with a DPDPA-specialist lawyer)

---

## 9. Data & Privacy Summary

- All LLM inference runs on-device — no query data ever leaves the phone during offline use
- Voice audio is processed locally and deleted immediately — never stored or transmitted
- No user accounts, no login, no registration required
- No analytics on query content
- Ad SDK (AdMob) activates only when internet is present — governed by consent given at first launch
- Model updates delivered as delta patches — no full re-download needed

---

## 10. Monetization Strategy

| Revenue Stream | When | How |
|---|---|---|
| Banner Ads | Always when online | AdMob banner at bottom of chat |
| Interstitial Ads | Every 5 queries when online | Full-screen between queries |
| Internet-fetched results | When online + out-of-domain | Premium API costs offset by ads |
| Future: B2B licensing | V3+ | License to state governments, NGOs, agri-companies |

**Philosophy:** App is free forever. Ads only when online (when user has connectivity, ad eCPM is viable). Offline experience has zero ads, zero interruptions.

---

## 11. Roadmap

### V1 — Foundation (Current)
- Text-to-text + Voice-to-voice (fully offline)
- Hindi + English with auto language detection
- Responds in user's language automatically
- Agriculture + UPSC + Banking fine-tuning
- Offline Android app (llama.cpp + Whisper-tiny + Piper TTS)
- AdMob integration when online
- "Fetch on connect" fallback for out-of-domain questions

### V2 — More Languages
- Add: Tamil, Telugu, Bengali, Marathi, Gujarati (Piper TTS voice models for each)
- Current affairs module (auto-syncs when online, stores locally for offline access)
- Improved Hindi voice quality (upgrade Piper model)

### V3 — Vision & Documents
- Image input: photo of crop → disease diagnosis
- PDF upload: study material → Q&A from document
- Offline OCR for handwritten notes

### V4 — Mesh & Community
- P2P model updates via Bluetooth / local WiFi (no internet needed for model updates)
- Community question bank — users contribute Q&A, shared offline
- Teacher mode — local school can push custom content

---

## 12. Risks & Mitigations

| Risk | Mitigation |
|---|---|
| 14B model too large for 4GB RAM devices | Offer Q3_K_M variant; auto-detect and serve right quantization |
| Model hallucinates government scheme details | Fine-tune heavily on out-of-domain rejection; add disclaimer |
| Storage constraint (8 GB model) | Support SD card storage; compressed delta updates |
| Dataset quality for Hindi | Manual review + native speaker validation pipeline |
| AdMob approval for low-income market | Start with in-house ad network as fallback |
| llama.cpp performance on ARM32 | Hard minimum: ARM64 only (covers all phones from 2017+) |
| Whisper-tiny Hindi WER too high | Fine-tune Whisper-tiny on Hindi agricultural vocabulary; fallback to text input |
| Piper TTS Hindi voice sounds robotic | Use Piper's medium quality Hindi model (~40 MB); upgrade in V2 |
| Running STT + LLM + TTS simultaneously on 4GB RAM | Pipeline sequentially (STT first, then LLM, then TTS); never run concurrently |

---

## 13. Open Questions

- [ ] Which GPU rental platform for fine-tuning? (RunPod vs Vast.ai vs Lambda Labs)
- [ ] Hindi dataset — scrape or partner with existing edtech (Unacademy, BYJU's open content)?
- [ ] Out-of-domain internet fetch — use Serper API or build a lightweight proxy?
- [ ] Model update delivery — direct APK update or separate model download manager?
- [ ] Should V1 support SD card storage by default?

---

*PRD v1.0 — Subject to change as build progresses.*
