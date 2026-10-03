#!/usr/bin/env bash
#
# Download the JDK that packages the Mac app: Eclipse Temurin 25 for Apple
# silicon (GPL-2.0 with the Classpath Exception). Android Studio's own runtime
# builds and tests the project but has no jpackage, which turns the app into a
# .app bundle and a .dmg. Pinned to one release and checked against its SHA-256.
#
# Usage:  tools/macos/fetch-jdk.sh
# Output: tools/macos/build/jdk (a JDK home)

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$HERE/build/jdk"
URL="https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/OpenJDK25U-jdk_aarch64_mac_hotspot_25.0.4.1_1.tar.gz"
SHA256="61979887f7506a24a57439ff99adb8b3a7fc89977d9cfe3b8984f58a981b7b9d"

if [[ -x "$OUT/bin/jpackage" ]]; then
    echo "==> JDK already in $OUT"
    exit 0
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
echo "==> Temurin 25.0.4.1"
curl -fsSL -o "$TMP/jdk.tar.gz" "$URL"
echo "$SHA256  $TMP/jdk.tar.gz" | shasum -a 256 -c -
tar -xzf "$TMP/jdk.tar.gz" -C "$TMP"
rm -rf "$OUT"
mv "$TMP"/jdk-*/Contents/Home "$OUT"
echo "==> JDK in $OUT"
