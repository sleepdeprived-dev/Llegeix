#!/usr/bin/env bash
#
# Build the Mac app's disk image: fetch or build whatever it carries that is
# not there yet, then let Gradle make the .app and the .dmg.
#
#   - a JDK with jpackage (fetch-jdk.sh)
#   - PDFium (fetch-pdfium.sh)
#   - the reader of scanned pages (build-ocr.sh)
#   - the translator (build-bergamot.sh; the slow one, ten minutes or so)
#
# The image is named as the app's updater looks for it in a release:
# Llegeix-<version>-macos.dmg. It is for Apple silicon, and signed ad hoc —
# without an Apple developer account there is nothing else to sign it with —
# so the first time it is opened macOS asks the reader to confirm it in
# System Settings > Privacy & Security.
#
# Usage:  tools/macos/package.sh [<folder to copy the image into>]
# Output: build/macos/Llegeix-<version>-macos.dmg, and a copy in the folder

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
BUILD="$HERE/build"

if [[ "$(uname -s)" != Darwin || "$(uname -m)" != arm64 ]]; then
    echo "The Mac app is built on a Mac with Apple silicon." >&2
    exit 1
fi

"$HERE/fetch-jdk.sh"
"$HERE/fetch-pdfium.sh"
[[ -x "$BUILD/llegeix-ocr" ]] || "$HERE/build-ocr.sh"
[[ -x "$BUILD/bergamot-translator/build/app/bergamot" ]] || "$HERE/build-bergamot.sh"

VERSION=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' "$ROOT/app/build.gradle.kts")
echo "==> Llegeix $VERSION for the Mac"
(cd "$ROOT" && ./gradlew :desktop:packageDmg)

OUT="$ROOT/build/macos"
mkdir -p "$OUT"
IMAGE="$OUT/Llegeix-$VERSION-macos.dmg"
cp "$ROOT/desktop/build/compose/binaries/main/dmg/Llegeix-$VERSION.dmg" "$IMAGE"
echo "==> $IMAGE"
if [[ -n "${1:-}" ]]; then
    cp "$IMAGE" "$1/"
fi
