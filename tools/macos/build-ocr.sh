#!/usr/bin/env bash
#
# Build llegeix-ocr, the Mac's reader of scanned pages, from tools/macos/ocr:
# Apple's Vision behind a small command, universal for Apple silicon and Intel.
# Needs the Xcode command-line tools.
#
# Usage:  tools/macos/build-ocr.sh
# Output: tools/macos/build/llegeix-ocr

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$HERE/build"
mkdir -p "$OUT/ocr"
for arch in arm64 x86_64; do
    swiftc -O -target "$arch-apple-macos13" -o "$OUT/ocr/llegeix-ocr-$arch" "$HERE/ocr/main.swift"
done
lipo -create -output "$OUT/llegeix-ocr" "$OUT/ocr/llegeix-ocr-arm64" "$OUT/ocr/llegeix-ocr-x86_64"
echo "==> $OUT/llegeix-ocr"
