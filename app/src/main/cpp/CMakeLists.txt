cmake_minimum_required(VERSION 3.22.1)
project("gyan_native")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ── llama.cpp submodule ──────────────────────────────────────────────────────
# Clone with: git submodule add https://github.com/ggerganov/llama.cpp third_party/llama.cpp
set(LLAMA_DIR "${CMAKE_SOURCE_DIR}/../../../third_party/llama.cpp")

set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN ON CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_DIR} llama_build)

# ── whisper.cpp submodule ────────────────────────────────────────────────────
# Clone with: git submodule add https://github.com/ggerganov/whisper.cpp third_party/whisper.cpp
set(WHISPER_DIR "${CMAKE_SOURCE_DIR}/../../../third_party/whisper.cpp")

set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)

add_subdirectory(${WHISPER_DIR} whisper_build)

# ── JNI shared library ───────────────────────────────────────────────────────
add_library(gyan_jni SHARED
    llama_jni.cpp
    whisper_jni.cpp
)

target_include_directories(gyan_jni PRIVATE
    ${LLAMA_DIR}/include
    ${LLAMA_DIR}/ggml/include
    ${WHISPER_DIR}
)

target_link_libraries(gyan_jni
    llama
    whisper
    android
    log
)

target_compile_options(gyan_jni PRIVATE
    -O3
    -ffast-math
    -DNDEBUG
)
