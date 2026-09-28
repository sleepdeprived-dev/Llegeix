#!/usr/bin/env bash
#
# Translate lines on stdin between Catalan and Romanian, pivoting through
# English. The check that offline ca<->ro works on the Mac at all.
#
# Usage:  echo "finestra" | tools/macos/translate.sh ca ro
#         echo "fereastră" | tools/macos/translate.sh ro ca

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
BERGAMOT="$HERE/build/bergamot-translator/build/app/bergamot"
MODELS="$HERE/build/models"

case "${1:-}-${2:-}" in
    ca-ro) first=caen; second=enro ;;
    ro-ca) first=roen; second=enca ;;
    *) echo "usage: tools/macos/translate.sh ca ro | ro ca" >&2; exit 1 ;;
esac

"$BERGAMOT" --model-config-paths "$MODELS/$first/config.yml" \
    | "$BERGAMOT" --model-config-paths "$MODELS/$second/config.yml"
