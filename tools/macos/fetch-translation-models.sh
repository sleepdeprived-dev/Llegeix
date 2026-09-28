#!/usr/bin/env bash
#
# Download the four Bergamot models the Mac port pivots through:
# ca->en, en->ro for Catalan to Romanian, and ro->en, en->ca back again.
# About 120 MB in all, from the same Remote Settings feed Firefox reads.
#
# Usage:  tools/macos/fetch-translation-models.sh
# Output: tools/macos/build/models/<pair>/{model,vocab,lex,config.yml}

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
MODELS="$HERE/build/models"
FEED="https://firefox.settings.services.mozilla.com/v1"
# ca<->en has a newer release than en<->ro; take the newest of each.
PAIRS="caen:2.0 enca:2.0 enro:1.0 roen:1.0"

mkdir -p "$MODELS"
BASE=$(curl -fsS "$FEED/" | python3 -c 'import json,sys; print(json.load(sys.stdin)["capabilities"]["attachments"]["base_url"])')
RECORDS=$(curl -fsS "$FEED/buckets/main/collections/translations-models/records")

for entry in $PAIRS; do
    pair=${entry%%:*}; version=${entry##*:}
    from=${pair:0:2}; to=${pair:2:2}
    mkdir -p "$MODELS/$pair"
    echo "==> $from -> $to ($version)"
    python3 -c '
import json, sys
f, t, v = sys.argv[1:]
for r in json.load(sys.stdin)["data"]:
    if (r.get("fromLang"), r.get("toLang"), r.get("version")) == (f, t, v):
        print(r["attachment"]["location"], r["name"])
' "$from" "$to" "$version" <<<"$RECORDS" | while read -r location name; do
        [[ -f "$MODELS/$pair/$name" ]] || curl -fsSL -o "$MODELS/$pair/$name" "$BASE$location"
    done

    dir="$MODELS/$pair"
    cat > "$dir/config.yml" <<YAML
models: [$dir/model.$pair.intgemm.alphas.bin]
vocabs: [$dir/vocab.$pair.spm, $dir/vocab.$pair.spm]
shortlist: [$dir/lex.50.50.$pair.s2t.bin, false]
beam-size: 1
normalize: 1.0
word-penalty: 0
max-length-break: 128
mini-batch-words: 1024
workspace: 128
max-length-factor: 2.0
skip-cost: true
cpu-threads: 0
quiet: true
quiet-translation: true
gemm-precision: int8shiftAlphaAll
alignment: soft
YAML
done
echo "==> Models in $MODELS"
