package com.gyan.offline.inference

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LlamaInference {

    companion object {
        private const val TAG = "LlamaInference"
        private const val N_CTX = 2048
        private const val MAX_TOKENS = 512

        init {
            System.loadLibrary("gyan_jni")
        }

        val SYSTEM_PROMPT = """
You are Gyan (ज्ञान), an AI assistant built for rural India. You are an expert in:
- Indian agriculture, farming practices, crop management, and government schemes
- UPSC Civil Services exam preparation (static syllabus only — History, Geography, Polity, Economy, Environment, Science & Tech)
- Banking and government job exam preparation (IBPS, SBI, SSC CGL, RRB NTPC, RRB Group D)
- Educational support for rural students

Rules:
1. ALWAYS respond in the same language the user used. Hindi in → Hindi out. English in → English out.
2. Answer accurately from your training knowledge. Do not guess or fabricate facts.
3. If a question is about current affairs, recent news, live prices, or anything outside your training, respond ONLY with the exact phrase: [OUT_OF_DOMAIN]
4. Give step-by-step solutions for math and reasoning problems.
5. Never make up government scheme amounts, dates, or eligibility criteria.
6. Use simple, clear language — your users may be first-generation learners.
        """.trimIndent()

        private const val OUT_OF_DOMAIN_MARKER = "[OUT_OF_DOMAIN]"
    }

    private val nThreads = Runtime.getRuntime().availableProcessors().coerceAtMost(4)

    // JNI declarations
    private external fun nativeLoad(modelPath: String, nCtx: Int, nThreads: Int): Boolean
    private external fun nativeInfer(prompt: String, maxTokens: Int): String
    private external fun nativeFree()
    private external fun nativeIsLoaded(): Boolean

    fun isLoaded(): Boolean = nativeIsLoaded()

    suspend fun load(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Loading model from $modelPath")
        nativeLoad(modelPath, N_CTX, nThreads)
    }

    suspend fun infer(userMessage: String, detectedLang: String): InferenceResult =
        withContext(Dispatchers.Default) {
            val prompt = buildChatMLPrompt(userMessage)
            val raw = nativeInfer(prompt, MAX_TOKENS)

            when {
                raw.contains(OUT_OF_DOMAIN_MARKER) ->
                    InferenceResult.OutOfDomain(lang = detectedLang)
                raw.startsWith("[ERROR") ->
                    InferenceResult.Error(raw)
                else ->
                    InferenceResult.Success(raw.trim())
            }
        }

    fun free() {
        nativeFree()
    }

    private fun buildChatMLPrompt(userMessage: String): String {
        return "<|im_start|>system\n$SYSTEM_PROMPT<|im_end|>\n" +
               "<|im_start|>user\n$userMessage<|im_end|>\n" +
               "<|im_start|>assistant\n"
    }
}

sealed class InferenceResult {
    data class Success(val text: String) : InferenceResult()
    data class OutOfDomain(val lang: String) : InferenceResult()
    data class Error(val message: String) : InferenceResult()
}
