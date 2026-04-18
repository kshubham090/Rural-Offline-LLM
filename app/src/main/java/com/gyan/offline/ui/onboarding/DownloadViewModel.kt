package com.gyan.offline.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gyan.offline.GyanApplication
import com.gyan.offline.download.DownloadProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DownloadUiState(
    val isDownloading: Boolean = false,
    val allDone: Boolean = false,
    val progresses: List<DownloadProgress> = emptyList(),
    val error: String? = null,
    val sideloadDetected: Boolean = false,   // user copied model manually via USB/Bluetooth
)

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<GyanApplication>()

    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    init {
        if (app.downloadManager.allModelsPresent()) {
            _uiState.update { it.copy(allDone = true, sideloadDetected = true) }
        }
    }

    // Called from UI when user taps "I already have the model file"
    fun checkSideload() {
        if (app.downloadManager.allModelsPresent()) {
            viewModelScope.launch { app.prefs.setModelsDownloaded() }
            _uiState.update { it.copy(allDone = true, sideloadDetected = true) }
        }
    }

    fun startDownload() {
        _uiState.update { it.copy(isDownloading = true, error = null) }

        viewModelScope.launch {
            app.downloadManager.downloadAll().collect { progress ->
                _uiState.update { state ->
                    val updated = state.progresses.toMutableList()
                    val idx = updated.indexOfFirst { it.modelName == progress.modelName }
                    if (idx >= 0) updated[idx] = progress else updated.add(progress)

                    val allDone = updated.size == app.downloadManager.MODELS.size &&
                                  updated.all { it.isDone }

                    if (allDone) {
                        viewModelScope.launch { app.prefs.setModelsDownloaded() }
                    }

                    state.copy(
                        progresses = updated,
                        allDone = allDone,
                        error = progress.error,
                        isDownloading = progress.error == null
                    )
                }
            }
        }
    }
}
