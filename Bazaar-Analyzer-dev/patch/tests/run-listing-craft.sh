#!/bin/sh
# SSR test of parts/20-listing-craft.js with the shared helpers of parts/00-shared.js (BA_el, BA_CraftBoundary, ids/names/badge section).
# Usage: sh patch/tests/run-listing-craft.sh   -> prints ALL OK
D=$(cd "$(dirname "$0")/.." && pwd)
OUT="${TMPDIR:-${TEMP:-/tmp}}/ba-listing-craft-run.mjs"
{ cat "$D/tests/listing-craft.head.mjs"
  sed -n '/^function BA_el(/,/^}/p' "$D/parts/00-shared.js"
  sed -n '/^class BA_CraftBoundary/,/^}/p' "$D/parts/00-shared.js"
  sed -n '/---- ids \/ names \/ numbers ----/,/---- hover popover/p' "$D/parts/00-shared.js"
  cat "$D/parts/20-listing-craft.js" "$D/tests/listing-craft.tail.mjs"; } > "$OUT"
"/c/Program Files/nodejs/node.exe" "$OUT"
