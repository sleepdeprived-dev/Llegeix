#!/usr/bin/env bash
#
# Build Bergamot, the offline translator behind Firefox's translations, as a
# native arm64 binary for the Mac port.
#
# ML Kit Translate, which does word lookup on Android, has no desktop version,
# and Apple's Translation framework offers neither Catalan nor Romanian. Mozilla's
# Bergamot models cover ca<->en and en<->ro, so the Mac app pivots through
# English the same way ML Kit already does on the phone.
#
# The upstream tree predates CMake 4 and Clang 21, so three small fixes are
# applied on top of the pinned commits. None of them touch translation itself.
#
# Usage:  tools/macos/build-bergamot.sh
# Output: tools/macos/build/bergamot-translator/build/app/bergamot

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$HERE/build"
SRC="$WORK/bergamot-translator"
BERGAMOT_COMMIT=9271618ebbdc5d21ac4dc4df9e72beb7ce644774

mkdir -p "$WORK"

# CMake and Ninja go in a private venv rather than onto the system.
if [[ ! -x "$WORK/venv/bin/cmake" ]]; then
    echo "==> CMake and Ninja (private venv)"
    python3 -m venv "$WORK/venv"
    "$WORK/venv/bin/pip" -q install cmake ninja
fi
export PATH="$WORK/venv/bin:$PATH"
# Bundled pcre2 and others declare cmake_minimum_required below what CMake 4 accepts.
export CMAKE_POLICY_VERSION_MINIMUM=3.5

if [[ ! -d "$SRC" ]]; then
    echo "==> Fetching bergamot-translator"
    git clone -q https://github.com/browsermt/bergamot-translator.git "$SRC"
    git -C "$SRC" checkout -q "$BERGAMOT_COMMIT"
    git -C "$SRC" submodule update -q --init --recursive
fi

MARIAN="$SRC/3rd_party/marian-dev"
echo "==> Applying macOS fixes"
# Clang 21 warns on things the upstream flags turn into errors.
sed -i '' 's/-Wall; -Werror;/-Wall;/' "$MARIAN/CMakeLists.txt"
# Newer Clang rejects an out-of-range enum cast in a constexpr. The sentencepiece
# trainer is never run by the app, only compiled.
sed -i '' 's/constexpr unicode_script::ScriptType kAnyType/const unicode_script::ScriptType kAnyType/' \
    "$MARIAN/src/3rd_party/sentencepiece/src/trainer_interface.cc"
# Old zlib defines fdopen away on Macs, which collides with the modern SDK headers.
perl -0pi -e 's/^(#\s*define fdopen\(fd,mode\) NULL.*)$/#if !defined(__APPLE__)\n$1\n#endif/mg' \
    "$MARIAN/src/3rd_party/zlib/zutil.h"

echo "==> Building"
cmake -S "$SRC" -B "$SRC/build" -G Ninja \
    -DCMAKE_BUILD_TYPE=Release \
    -DUSE_APPLE_ACCELERATE=on \
    -DCOMPILE_CUDA=off \
    -DSSPLIT_USE_INTERNAL_PCRE2=on > "$WORK/configure.log"
ninja -C "$SRC/build"

echo "==> Built $SRC/build/app/bergamot"
