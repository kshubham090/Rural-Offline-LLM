#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "whisper.h"

#define LOG_TAG "GyanWhisper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static whisper_context* g_whisper = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_gyan_offline_inference_WhisperSTT_nativeLoad(
        JNIEnv* env, jobject, jstring modelPath) {

    const char* path = env->GetStringUTFChars(modelPath, nullptr);

    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;

    g_whisper = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_whisper) {
        LOGE("Failed to load Whisper model");
        return JNI_FALSE;
    }

    LOGI("Whisper model loaded");
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_gyan_offline_inference_WhisperSTT_nativeTranscribe(
        JNIEnv* env, jobject, jfloatArray audioData, jstring langHint) {

    if (!g_whisper) {
        return env->NewStringUTF("");
    }

    jsize len = env->GetArrayLength(audioData);
    jfloat* samples = env->GetFloatArrayElements(audioData, nullptr);

    const char* lang = env->GetStringUTFChars(langHint, nullptr);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress   = false;
    params.print_special    = false;
    params.print_realtime   = false;
    params.print_timestamps = false;
    params.language         = lang;
    params.n_threads        = 4;
    params.single_segment   = false;
    params.no_context       = true;

    int result = whisper_full(g_whisper, params, samples, (int) len);

    env->ReleaseFloatArrayElements(audioData, samples, JNI_ABORT);
    env->ReleaseStringUTFChars(langHint, lang);

    if (result != 0) {
        LOGE("Whisper transcription failed");
        return env->NewStringUTF("");
    }

    std::string transcript;
    int n_segments = whisper_full_n_segments(g_whisper);
    for (int i = 0; i < n_segments; i++) {
        const char* text = whisper_full_get_segment_text(g_whisper, i);
        if (text) transcript += text;
    }

    // trim leading/trailing whitespace
    size_t start = transcript.find_first_not_of(" \t\n");
    if (start == std::string::npos) return env->NewStringUTF("");
    size_t end = transcript.find_last_not_of(" \t\n");
    transcript = transcript.substr(start, end - start + 1);

    return env->NewStringUTF(transcript.c_str());
}

JNIEXPORT void JNICALL
Java_com_gyan_offline_inference_WhisperSTT_nativeFree(JNIEnv*, jobject) {
    if (g_whisper) {
        whisper_free(g_whisper);
        g_whisper = nullptr;
        LOGI("Whisper freed");
    }
}

} // extern "C"
