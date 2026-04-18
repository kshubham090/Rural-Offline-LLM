package com.gyan.offline.download

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class ModelSpec(
    val name: String,
    val url: String,       // HuggingFace URL — only used for one-time Wi-Fi download
    val fileName: String,
    val expectedBytes: Long,
)

/**
 * Why HuggingFace URL?
 * The APK itself is <150 MB (Play Store limit). The model is ~8 GB.
 * It is downloaded ONCE on first launch over Wi-Fi, then the app works
 * 100% offline forever. HF is just a reliable free CDN for large files.
 *
 * No internet at all? Users can sideload the GGUF manually:
 *   adb push qwen3-14b-gyan-q4_k_m.gguf /sdcard/Android/data/com.gyan.offline/files/models/
 * The app detects existing files and skips download automatically.
 */

data class DownloadProgress(
    val modelName: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val isDone: Boolean = false,
    val error: String? = null,
) {
    val percent: Int get() = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
}

class ModelDownloadManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelDownload"

        // Replace URLs with actual HuggingFace / CDN links before release
        val MODELS = listOf(
            ModelSpec(
                name = "Qwen3-14B (LLM)",
                url = "https://huggingface.co/YOUR_HF_REPO/resolve/main/qwen3-14b-q4_k_m.gguf",
                fileName = "qwen3-14b-q4_k_m.gguf",
                expectedBytes = 8_500_000_000L
            ),
            ModelSpec(
                name = "Whisper Tiny (Voice)",
                url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
                fileName = "ggml-whisper-tiny.bin",
                expectedBytes = 75_000_000L
            ),
            ModelSpec(
                name = "Piper Hindi Voice",
                url = "https://huggingface.co/rhasspy/piper-voices/resolve/main/hi/IN/hemant/medium/hi_IN-hemant-medium.onnx",
                fileName = "hi_IN-hemant-medium.onnx",
                expectedBytes = 63_000_000L
            ),
            ModelSpec(
                name = "Piper Hindi Config",
                url = "https://huggingface.co/rhasspy/piper-voices/resolve/main/hi/IN/hemant/medium/hi_IN-hemant-medium.onnx.json",
                fileName = "hi_IN-hemant-medium.onnx.json",
                expectedBytes = 5_000L
            ),
            ModelSpec(
                name = "Piper English Voice",
                url = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/IN/ryan/medium/en_IN-ryan-medium.onnx",
                fileName = "en_IN-ryan-medium.onnx",
                expectedBytes = 63_000_000L
            ),
            ModelSpec(
                name = "Piper English Config",
                url = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/IN/ryan/medium/en_IN-ryan-medium.onnx.json",
                fileName = "en_IN-ryan-medium.onnx.json",
                expectedBytes = 5_000L
            ),
        )
    }

    val modelsDir: File = File(context.filesDir, "models").also { it.mkdirs() }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun allModelsPresent(): Boolean = MODELS.all {
        val f = File(modelsDir, it.fileName)
        f.exists() && f.length() >= it.expectedBytes * 0.95 // 5% tolerance
    }

    fun modelFile(fileName: String) = File(modelsDir, fileName)

    fun downloadAll(): Flow<DownloadProgress> = flow {
        for (spec in MODELS) {
            val dest = File(modelsDir, spec.fileName)

            // Skip already-complete files
            if (dest.exists() && dest.length() >= spec.expectedBytes * 0.95) {
                emit(DownloadProgress(spec.name, spec.expectedBytes, spec.expectedBytes, isDone = true))
                continue
            }

            val resumeFrom = if (dest.exists()) dest.length() else 0L
            Log.i(TAG, "Downloading ${spec.name} from byte $resumeFrom")

            val requestBuilder = Request.Builder().url(spec.url)
            if (resumeFrom > 0) requestBuilder.header("Range", "bytes=$resumeFrom-")

            try {
                val response = client.newCall(requestBuilder.build()).execute()
                val body = response.body ?: throw Exception("Empty response body")
                val totalBytes = if (resumeFrom > 0) resumeFrom + body.contentLength()
                                 else body.contentLength()

                body.byteStream().use { input ->
                    dest.outputStream().let { if (resumeFrom > 0) dest.appendBytes(ByteArray(0)); it }
                    val output = if (resumeFrom > 0) dest.outputStream().also { it.skip(resumeFrom) }
                                 else dest.outputStream()

                    output.use {
                        val buf = ByteArray(8192)
                        var downloaded = resumeFrom
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            it.write(buf, 0, read)
                            downloaded += read
                            emit(DownloadProgress(spec.name, downloaded, totalBytes))
                        }
                    }
                }

                emit(DownloadProgress(spec.name, totalBytes, totalBytes, isDone = true))
                Log.i(TAG, "${spec.name} download complete")
            } catch (e: Exception) {
                Log.e(TAG, "Download failed for ${spec.name}: ${e.message}")
                emit(DownloadProgress(spec.name, 0, spec.expectedBytes, error = e.message))
                return@flow
            }
        }
    }.flowOn(Dispatchers.IO)
}
