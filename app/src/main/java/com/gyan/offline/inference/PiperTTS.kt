package com.gyan.offline.inference

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Wraps the Piper TTS binary (ARM64 native executable) bundled in the APK.
 * Piper reads text from stdin and writes raw PCM to stdout.
 * Voice models (.onnx + .json) are downloaded with the other models on first launch.
 */
class PiperTTS(private val context: Context) {

    companion object {
        private const val TAG = "PiperTTS"
        private const val SAMPLE_RATE = 22050
        private const val PIPER_BINARY = "piper"
    }

    private val piperBinary: File by lazy { extractPiperBinary() }

    private fun extractPiperBinary(): File {
        val dest = File(context.filesDir, PIPER_BINARY)
        if (!dest.exists()) {
            context.assets.open("piper/piper").use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest.setExecutable(true)
        }
        return dest
    }

    suspend fun speak(text: String, lang: String, modelsDir: File) =
        withContext(Dispatchers.IO) {
            val modelName = if (lang == "hi") "hi_IN-hemant-medium" else "en_IN-ryan-medium"
            val modelOnnx = File(modelsDir, "$modelName.onnx")
            val modelJson = File(modelsDir, "$modelName.onnx.json")

            if (!modelOnnx.exists() || !modelJson.exists()) {
                Log.w(TAG, "TTS model not found for lang=$lang, skipping speech")
                return@withContext
            }

            val process = ProcessBuilder(
                piperBinary.absolutePath,
                "--model", modelOnnx.absolutePath,
                "--output-raw"
            )
                .redirectErrorStream(true)
                .start()

            process.outputStream.bufferedWriter().use { it.write(text) }

            val pcmBytes = process.inputStream.readBytes()
            process.waitFor()

            if (pcmBytes.isEmpty()) {
                Log.w(TAG, "Piper produced no audio")
                return@withContext
            }

            playPcm(pcmBytes)
        }

    private fun playPcm(pcmBytes: ByteArray) {
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcmBytes.size)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(pcmBytes, 0, pcmBytes.size)
        audioTrack.play()

        // Block until playback completes
        while (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
            Thread.sleep(100)
        }

        audioTrack.release()
    }
}
