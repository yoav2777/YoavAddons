#!/usr/bin/env bash
# Replays real Coflnet sales through the mod's price prediction (core.Appraiser) and prints the error.
# Data: push a commit with [data] in its message (.github/workflows/data.yml) and fetch the `data-dump` branch:
#   git fetch origin data-dump && mkdir -p /tmp/bt-data && git archive origin/data-dump | tar -x -C /tmp/bt-data
# Needs a JDK and gson (GSON=path/to/gson.jar). Usage: run.sh /tmp/bt-data
# Thin market (1-2 sales / history / anchor): java ...Probe <data> <recipes> 5 >> .github/data-tags.txt, push [data],
# fetch the new dump, then java ...Fall <data> <recipes> (compile both like JavaBacktest below).
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
DATA=${1:?data dir}
GSON=${GSON:?set GSON to a gson jar}
OUT=$(mktemp -d)
javac -nowarn -d "$OUT" -cp "$GSON" $(find "$ROOT/Bazaar-Mod/src/main/java/com/yoav3577/bazaaranalyzer/core" -name '*.java') "$(dirname "$0")/JavaBacktest.java"
java -cp "$OUT:$GSON" com.yoav3577.bazaaranalyzer.core.JavaBacktest "$DATA" "$ROOT/Bazaar-Analyzer-v12/app/data/recipes.json"
