#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"

#define LOG_TAG "GyanLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static llama_model*   g_model   = nullptr;
static llama_context* g_ctx     = nullptr;
static llama_sampler* g_sampler = nullptr;

static std::string jstring_to_std(JNIEnv* env, jstring js) {
    const char* chars = env->GetStringUTFChars(js, nullptr);
    std::string s(chars);
    env->ReleaseStringUTFChars(js, chars);
    return s;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_gyan_offline_inference_LlamaInference_nativeLoad(
        JNIEnv* env, jobject, jstring modelPath, jint nCtx, jint nThreads) {

    llama_backend_init();

    std::string path = jstring_to_std(env, modelPath);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // CPU-only on Android

    g_model = llama_load_model_from_file(path.c_str(), mparams);
    if (!g_model) {
        LOGE("Failed to load model from %s", path.c_str());
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx     = (uint32_t) nCtx;
    cparams.n_threads = (uint32_t) nThreads;
    cparams.n_threads_batch = (uint32_t) nThreads;
    cparams.flash_attn = true;

    g_ctx = llama_new_context_with_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("Failed to create llama context");
        llama_free_model(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    g_sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(g_sampler, llama_sampler_init_min_p(0.05f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    LOGI("Model loaded successfully");
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_gyan_offline_inference_LlamaInference_nativeInfer(
        JNIEnv* env, jobject, jstring prompt, jint maxTokens) {

    if (!g_model || !g_ctx) {
        return env->NewStringUTF("[ERROR: model not loaded]");
    }

    std::string full_prompt = jstring_to_std(env, prompt);

    std::vector<llama_token> tokens;
    tokens.resize(full_prompt.size() + 4);
    int n = llama_tokenize(g_model,
                           full_prompt.c_str(),
                           (int32_t) full_prompt.size(),
                           tokens.data(),
                           (int32_t) tokens.size(),
                           true, true);
    if (n < 0) {
        return env->NewStringUTF("[ERROR: tokenization failed]");
    }
    tokens.resize(n);

    llama_kv_cache_clear(g_ctx);

    llama_batch batch = llama_batch_get_one(tokens.data(), (int32_t) tokens.size());
    if (llama_decode(g_ctx, batch) != 0) {
        return env->NewStringUTF("[ERROR: decode failed]");
    }

    std::string output;
    output.reserve(512);

    for (int i = 0; i < maxTokens; i++) {
        llama_token token = llama_sampler_sample(g_sampler, g_ctx, -1);

        if (llama_token_is_eog(g_model, token)) break;

        char buf[256];
        int len = llama_token_to_piece(g_model, token, buf, sizeof(buf), 0, true);
        if (len > 0) {
            output.append(buf, len);
        }

        llama_batch next = llama_batch_get_one(&token, 1);
        if (llama_decode(g_ctx, next) != 0) break;
    }

    llama_sampler_reset(g_sampler);

    return env->NewStringUTF(output.c_str());
}

JNIEXPORT void JNICALL
Java_com_gyan_offline_inference_LlamaInference_nativeFree(JNIEnv*, jobject) {
    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    if (g_ctx)     { llama_free(g_ctx);              g_ctx     = nullptr; }
    if (g_model)   { llama_free_model(g_model);      g_model   = nullptr; }
    llama_backend_free();
    LOGI("Model freed");
}

JNIEXPORT jboolean JNICALL
Java_com_gyan_offline_inference_LlamaInference_nativeIsLoaded(JNIEnv*, jobject) {
    return (g_model != nullptr && g_ctx != nullptr) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
