#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TRIME_DIR="${TRIME_DIR:-$ROOT/.rime-build/trime}"
OUT="$ROOT/app/src/main/jniLibs"
TRIME_REF="${TRIME_REF:-develop}"
DRUM_CPP="$ROOT/app/src/main/cpp/drumstick_rime.cpp"
[ -f "$DRUM_CPP" ] || { echo "ERROR: missing JNI bridge: $DRUM_CPP"; exit 1; }

mkdir -p "$ROOT/.rime-build"
if [ ! -d "$TRIME_DIR/.git" ]; then
  git clone --depth 1 --recurse-submodules --shallow-submodules --branch "$TRIME_REF" https://github.com/osfans/trime.git "$TRIME_DIR"
else
  git -C "$TRIME_DIR" fetch --depth 1 origin "$TRIME_REF"
  git -C "$TRIME_DIR" checkout -q FETCH_HEAD
  git -C "$TRIME_DIR" submodule update --init --recursive --depth 1
fi

rm -rf "$OUT" "$ROOT/.rime-build/native-out"
mkdir -p "$OUT/armeabi-v7a" "$OUT/arm64-v8a" "$ROOT/.rime-build/native-out"

TRIME_CMAKE_FILE="$TRIME_DIR/app/src/main/jni/CMakeLists.txt"

# Rime is BSD-3-Clause. We use Trime only as an Android dependency build harness.
# Disable Trime's GPL JNI target and optional plugins; the final shared library is
# our own JNI bridge plus the BSD librime static target.
python3 - "$TRIME_DIR/app/src/main/jni/cmake/Rime.cmake" <<'PY'
from pathlib import Path
import re, sys
p=Path(sys.argv[1]); s=p.read_text()
s=re.sub(r"set\(RIME_PLUGINS\s+[^)]*\)", "set(RIME_PLUGINS)", s, count=1)
s=re.sub(r"(?ms)^\s*target_compile_options\(\s*rime-lua-objs\s+PRIVATE\s+\"-ffile-prefix-map=\$\{CMAKE_CURRENT_SOURCE_DIR\}=\.\"\s*\)\s*", "if(TARGET rime-lua-objs)\n  target_compile_options(rime-lua-objs PRIVATE \"-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=.\")\nendif()\n", s, count=1)
s=re.sub(r"(?ms)^\s*target_compile_options\(\s*rime-octagram-objs\s+PRIVATE\s+\"-ffile-prefix-map=\$\{CMAKE_CURRENT_SOURCE_DIR\}=\.\"\s*\)\s*", "if(TARGET rime-octagram-objs)\n  target_compile_options(rime-octagram-objs PRIVATE \"-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=.\")\nendif()\n", s, count=1)
p.write_text(s)
PY

python3 - "$TRIME_CMAKE_FILE" <<'PY'
from pathlib import Path
import sys
p=Path(sys.argv[1]); s=p.read_text()
s=s.replace('cmake_minimum_required(VERSION 3.18.0)', 'cmake_minimum_required(VERSION 3.18.0)\nset(CMAKE_POSITION_INDEPENDENT_CODE ON)')
s=s.replace('add_subdirectory(librime_jni)', '# Drumstick builds its own JNI bridge below; do not build Trime GPL JNI.')
if 'add_library(drumstick_rime SHARED' not in s:
    s += """

# Drumstick JNI + statically linked librime.
find_library(DRUMSTICK_LOG log)
if(NOT DEFINED DRUMSTICK_CPP)
  message(FATAL_ERROR "DRUMSTICK_CPP is not set")
endif()
if(NOT EXISTS "${DRUMSTICK_CPP}")
  message(FATAL_ERROR "Drumstick JNI source not found: ${DRUMSTICK_CPP}")
endif()
add_library(drumstick_rime SHARED "${DRUMSTICK_CPP}")
target_compile_features(drumstick_rime PRIVATE cxx_std_17)
target_include_directories(drumstick_rime PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/librime/src")
target_link_libraries(drumstick_rime PRIVATE "-Wl,--whole-archive" rime-static "-Wl,--no-whole-archive" ${DRUMSTICK_LOG})
set_target_properties(drumstick_rime PROPERTIES
  OUTPUT_NAME "drumstick_rime"
  LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/drumstick-out")
"""
p.write_text(s)
PY

CMAKE_BIN="${CMAKE_BIN:-$ANDROID_SDK_ROOT/cmake/3.22.1/bin/cmake}"
[ -x "$CMAKE_BIN" ] || CMAKE_BIN="$(command -v cmake)"

echo "Using CMake: $CMAKE_BIN"

echo "Trime build harness: $TRIME_DIR"

build_one() {
  local abi="$1" api="$2"
  local b="$ROOT/.rime-build/cmake-$abi"
  rm -rf "$b"
  RIME_PLUGINS="" "$CMAKE_BIN" -S "$TRIME_DIR/app/src/main/jni" -B "$b" \
    -DDRUMSTICK_CPP:FILEPATH="$DRUM_CPP" \
    -G Ninja \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$abi" \
    -DANDROID_PLATFORM="android-$api" \
    -DANDROID_STL=c++_static \
    -DBUILD_SHARED_LIBS=OFF \
    -DBUILD_TESTING=OFF \
    -DBUILD_TEST=OFF \
    -DBUILD_SAMPLE=OFF \
    -DENABLE_LOGGING=ON \
    -DENABLE_TIMESTAMP=OFF
  "$CMAKE_BIN" --build "$b" --target drumstick_rime --parallel
  local so
  so="$(find "$b/drumstick-out" -type f -name 'libdrumstick_rime.so' -print -quit)"
  test -n "$so"
  cp "$so" "$OUT/$abi/libdrumstick_rime.so"
  echo "== $abi: linked symbols =="
  "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf" -d "$OUT/$abi/libdrumstick_rime.so" | grep -E 'NEEDED|SONAME' || true
  echo "Checking rime_get_api..."
  "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf" -Ws "$OUT/$abi/libdrumstick_rime.so" | grep -q "rime_get_api"     && echo "rime_get_api found"     || echo "rime_get_api is statically linked"

  ! "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf" -d "$OUT/$abi/libdrumstick_rime.so" | grep -q 'librime.so'
}

build_one armeabi-v7a 19
build_one arm64-v8a 21

find "$OUT" -type f -name 'libdrumstick_rime.so' -print
