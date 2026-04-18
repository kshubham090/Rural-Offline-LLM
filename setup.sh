#!/usr/bin/env bash
# Run once after cloning to pull native dependencies and set up the project.
set -e

echo "==> Initialising git submodules (llama.cpp + whisper.cpp)..."
git submodule update --init --depth 1 third_party/llama.cpp
git submodule update --init --depth 1 third_party/whisper.cpp

echo "==> Checking Android NDK..."
if [ -z "$ANDROID_NDK_HOME" ]; then
  echo "WARNING: ANDROID_NDK_HOME is not set."
  echo "Set it to your NDK path, e.g.:"
  echo "  export ANDROID_NDK_HOME=\$HOME/Android/Sdk/ndk/27.0.12077973"
fi

echo "==> Extracting Piper TTS binary for ARM64..."
PIPER_VERSION="2023.11.14-2"
PIPER_URL="https://github.com/rhasspy/piper/releases/download/${PIPER_VERSION}/piper_linux_aarch64.tar.gz"
ASSETS_PIPER="app/src/main/assets/piper"
mkdir -p "$ASSETS_PIPER"

if [ ! -f "$ASSETS_PIPER/piper" ]; then
  echo "Downloading Piper binary..."
  curl -L "$PIPER_URL" | tar -xz -C /tmp
  cp /tmp/piper/piper "$ASSETS_PIPER/piper"
  chmod +x "$ASSETS_PIPER/piper"
  echo "Piper binary placed at $ASSETS_PIPER/piper"
else
  echo "Piper binary already present."
fi

echo ""
echo "==> Setup complete. Open the project in Android Studio and Build > Make Project."
echo "    Model files (~8.5 GB) are downloaded on first app launch."
