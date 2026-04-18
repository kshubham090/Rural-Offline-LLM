package com.gyan.offline.inference

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WhisperSTT {

    companion object {
        private const val TAG = "WhisperSTT"
        private const val SAMPLE_RATE = 16000
        private const val MAX_RECORD_SECONDS = 30

        init {
            System.loadLibrary("gyan_jni")
        }
    }

    private external fun nativeLoad(modelPath: String): Boolean
    private external fun nativeTranscribe(audioData: FloatArray, langHint: String): String
    private external fun nativeFree()

    private var isRecording = false
    private var audioRecord: AudioRecord? = null

    fun load(modelPath: String): Boolean {
        Log.i(TAG, "Loading Whisper model from $modelPath")
        return nativeLoad(modelPath)
    }

    suspend fun recordAndTranscribe(langHint: String): String = withContext(Dispatchers.IO) {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        val maxSamples = SAMPLE_RATE * MAX_RECORD_SECONDS
        val pcmBuffer = ShortArray(maxSamples)
        var totalRead = 0

        isRecording = true
        audioRecord?.startRecording()

        while (isRecording && totalRead < maxSamples) {
            val read = audioRecord?.read(pcmBuffer, totalRead, bufferSize.coerceAtMost(maxSamples - totalRead)) ?: 0
            if (read > 0) totalRead += read
        }

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        // Convert PCM16 to float [-1, 1] for Whisper
        val floatSamples = FloatArray(totalRead) { pcmBuffer[it] / 32768.0f }

        nativeTranscribe(floatSamples, langHint)
    }

    fun stopRecording() {
        isRecording = false
    }

    suspend fun transcribeAudio(pcmFloats: FloatArray, langHint: String): String =
        withContext(Dispatchers.IO) {
            nativeTranscribe(pcmFloats, langHint)
        }

    fun free() = nativeFree()
}
