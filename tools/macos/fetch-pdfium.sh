#!/usr/bin/env bash
#
# Download PDFium for the Mac: the same engine the phone renders with, as the
# prebuilt library from bblanchon/pdfium-binaries (BSD-3 / Apache-2.0, the
# licences of PDFium itself). Universal, so it runs on Apple silicon and Intel.
# Pinned to one release and checked against its SHA-256.
#
# Usage:  tools/macos/fetch-pdfium.sh
# Output: tools/macos/build/pdfium/lib/libpdfium.dylib (and its headers and licences)

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$HERE/build/pdfium"
RELEASE="chromium/8076"
ARCHIVE="pdfium-mac-univ.tgz"
SHA256="3bdb93e229298dfdf083dc8ccc7d1a8cf87790b6917e5073335504fe2ff0bdc1"

if [[ -f "$OUT/lib/libpdfium.dylib" ]]; then
    echo "==> PDFium already in $OUT"
    exit 0
fi

mkdir -p "$OUT"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
echo "==> PDFium $RELEASE"
curl -fsSL -o "$TMP/$ARCHIVE" "https://github.com/bblanchon/pdfium-binaries/releases/download/$RELEASE/$ARCHIVE"
echo "$SHA256  $TMP/$ARCHIVE" | shasum -a 256 -c -
tar -xzf "$TMP/$ARCHIVE" -C "$OUT"
echo "==> PDFium in $OUT"
