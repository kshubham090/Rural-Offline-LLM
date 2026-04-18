# llama.cpp / whisper.cpp JNI — keep all native method names
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep inference classes referenced by JNI
-keep class com.gyan.offline.inference.LlamaInference { *; }
-keep class com.gyan.offline.inference.WhisperSTT { *; }

# AdMob
-keep class com.google.android.gms.ads.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# DataStore
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}
