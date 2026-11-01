#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/homebrew/share/android-commandlinetools}}"
if [[ -f local.properties ]]; then
  LOCAL_SDK=$(sed -n 's/^sdk.dir=//p' local.properties)
  SDK="${LOCAL_SDK:-$SDK}"
fi
NDK="$SDK/ndk/28.2.13676358"
CMAKE="$SDK/cmake/3.22.1/bin/cmake"
"$CMAKE" -S native -B native/build -G Ninja -DCMAKE_MAKE_PROGRAM="$SDK/cmake/3.22.1/bin/ninja" -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 -DANDROID_STL=c++_static -DCMAKE_BUILD_TYPE=Release
"$CMAKE" --build native/build -j 4
