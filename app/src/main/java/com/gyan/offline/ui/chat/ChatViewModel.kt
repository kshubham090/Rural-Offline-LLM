package com.gyan.offline.ui.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gyan.offline.GyanApplication
import com.gyan.offline.inference.InferenceResult
import com.gyan.offline.lang.LanguageDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isListening: Boolean = false,
    val isModelReady: Boolean = false,
    val isOnline: Boolean = false,
    val voiceEnabled: Boolean = true,
    val error: String? = null,
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<GyanApplication>()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        observeConnectivity()
        loadModels()
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            app.connectivity.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }
    }

    private fun loadModels() {
        viewModelScope.launch {
            val llmPath = File(app.downloadManager.modelsDir, "qwen3-14b-q4_k_m.gguf").absolutePath
            val whisperPath = File(app.downloadManager.modelsDir, "ggml-whisper-tiny.bin").absolutePath

            val llmLoaded = app.llama.load(llmPath)
            if (!llmLoaded) {
                _uiState.update { it.copy(error = "Failed to load AI model. Please re-download.") }
                return@launch
            }

            app.whisper.load(whisperPath)

            val voiceEnabled = app.prefs.voiceEnabled.first()
            _uiState.update { it.copy(isModelReady = true, voiceEnabled = voiceEnabled) }
            Log.i("ChatVM", "Models ready")
        }
    }

    fun sendTextMessage(text: String) {
        if (text.isBlank() || _uiState.value.isLoading) return

        val lang = LanguageDetector.detect(text)
        val userMsg = ChatMessage(text = text, isUser = true, lang = lang)
        appendMessage(userMsg)
        runInference(text, lang)
    }

    fun startVoiceInput() {
        if (_uiState.value.isListening || _uiState.value.isLoading) return
        _uiState.update { it.copy(isListening = true) }

        viewModelScope.launch {
            val transcript = app.whisper.recordAndTranscribe(
                langHint = LanguageDetector.whisperLangHint("hi")
            )
            _uiState.update { it.copy(isListening = false) }

            if (transcript.isBlank()) return@launch

            val lang = LanguageDetector.detect(transcript)
            appendMessage(ChatMessage(text = transcript, isUser = true, lang = lang))
            runInference(transcript, lang)
        }
    }

    fun stopListening() {
        app.whisper.stopRecording()
        _uiState.update { it.copy(isListening = false) }
    }

    private fun runInference(input: String, lang: String) {
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            val result = app.llama.infer(input, lang)

            val assistantMsg = when (result) {
                is InferenceResult.Success -> {
                    ChatMessage(text = result.text, isUser = false, lang = lang)
                }
                is InferenceResult.OutOfDomain -> {
                    val text = if (lang == "hi")
                        "मुझे इस सवाल का जवाब नहीं पता। जैसे ही आप इंटरनेट से जुड़ेंगे, मैं आपको जवाब दूंगा।"
                    else
                        "I don't have enough context for this right now. Once you're connected to the internet, I'll fetch the answer for you."
                    ChatMessage(text = text, isUser = false, isOutOfDomain = true, lang = lang)
                }
                is InferenceResult.Error -> {
                    ChatMessage(text = "Something went wrong. Please try again.", isUser = false)
                }
            }

            appendMessage(assistantMsg)
            _uiState.update { it.copy(isLoading = false) }

            // Speak the response if voice is enabled
            if (_uiState.value.voiceEnabled && result is InferenceResult.Success) {
                app.tts.speak(assistantMsg.text, lang, app.downloadManager.modelsDir)
            }
        }
    }

    private fun appendMessage(msg: ChatMessage) {
        _uiState.update { it.copy(messages = it.messages + msg) }
    }

    override fun onCleared() {
        super.onCleared()
        app.llama.free()
        app.whisper.free()
    }
}
