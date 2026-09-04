#!/usr/bin/env bash
#
# Cut a release.
#
# The source repository is private and the builds have to be public, because the
# app's own "check for updates" asks GitHub for them with no credentials at all
# and an API token shipped inside a sideloaded APK is a token everybody holding
# the APK has. So there are two repositories, and this is what keeps them one
# command apart: the tag goes on the source, the signed APKs and the notes go to
# sleepdeprived-dev/Llegeix-releases, which carries nothing else.
#
# Usage:  tools/release.sh <notes-file>
#
# The version comes from app/build.gradle.kts. Bump it there first.

set -euo pipefail

NOTES="${1:-}"
if [[ -z "$NOTES" || ! -f "$NOTES" ]]; then
    echo "usage: tools/release.sh <notes-file>" >&2
    exit 1
fi

cd "$(dirname "$0")/.."

RELEASES_REPO="sleepdeprived-dev/Llegeix-releases"
VERSION=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)
CODE=$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts)
ABIS=(arm64-v8a armeabi-v7a x86_64 universal)

if [[ -z "$VERSION" ]]; then
    echo "could not read versionName from app/build.gradle.kts" >&2
    exit 1
fi
echo "==> Llegeix $VERSION (versionCode $CODE)"

if git rev-parse "v$VERSION" >/dev/null 2>&1; then
    echo "v$VERSION is already tagged. Bump the version first." >&2
    exit 1
fi
if [[ -n "$(git status --porcelain)" ]]; then
    echo "the working tree is dirty; commit before releasing" >&2
    exit 1
fi

echo "==> Tests and lint"
./gradlew testDebugUnitTest lintDebug

echo "==> Building signed APKs"
./gradlew assembleRelease

STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT
for abi in "${ABIS[@]}"; do
    # The name the updater matches on: Llegeix-<version>-<abi>.apk, with the
    # architecture as the whole of what follows the version.
    cp "app/build/outputs/apk/release/app-$abi-release.apk" \
       "$STAGE/Llegeix-$VERSION-$abi.apk"
done

echo "==> Tagging the source"
git tag -a "v$VERSION" -m "Llegeix v$VERSION"
git push origin main
git push origin "v$VERSION"

echo "==> Publishing to $RELEASES_REPO"
gh release create "v$VERSION" \
    --repo "$RELEASES_REPO" \
    --title "Llegeix v$VERSION" \
    --notes-file "$NOTES" \
    "$STAGE"/Llegeix-"$VERSION"-*.apk

echo "==> Checking that the app would see it"
SEEN=$(curl -fsS "https://api.github.com/repos/$RELEASES_REPO/releases/latest" \
       | sed -n 's/.*"tag_name": *"\([^"]*\)".*/\1/p')
if [[ "$SEEN" == "v$VERSION" ]]; then
    echo "    latest is $SEEN"
else
    echo "    WARNING: the API still reports $SEEN" >&2
fi
