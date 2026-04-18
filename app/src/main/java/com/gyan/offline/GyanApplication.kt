package com.gyan.offline

import android.app.Application
import com.gyan.offline.ads.AdManager
import com.gyan.offline.data.AppPreferences
import com.gyan.offline.download.ModelDownloadManager
import com.gyan.offline.inference.LlamaInference
import com.gyan.offline.inference.PiperTTS
import com.gyan.offline.inference.WhisperSTT
import com.gyan.offline.network.ConnectivityObserver

class GyanApplication : Application() {

    lateinit var prefs: AppPreferences
    lateinit var connectivity: ConnectivityObserver
    lateinit var downloadManager: ModelDownloadManager
    lateinit var llama: LlamaInference
    lateinit var whisper: WhisperSTT
    lateinit var tts: PiperTTS
    lateinit var adManager: AdManager

    override fun onCreate() {
        super.onCreate()
        prefs            = AppPreferences(this)
        connectivity     = ConnectivityObserver(this)
        downloadManager  = ModelDownloadManager(this)
        llama            = LlamaInference()
        whisper          = WhisperSTT()
        tts              = PiperTTS(this)
        adManager        = AdManager(this)
    }
}
