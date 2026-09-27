#!/bin/bash
# usage: shoot-on.sh <log> <timeoutSec> <pattern> <png> [<pattern> <png>...]  - screenshots the dev window when each log pattern appears
log=$1; to=$2; shift 2
start=$(date +%s)
while [ $# -ge 2 ]; do
  pat=$1; png=$2; shift 2
  until grep -q -- "$pat" "$log" 2>/dev/null; do
    sleep 0.3
    if [ $(( $(date +%s) - start )) -gt $to ]; then echo "timeout waiting for $pat"; exit 1; fi
  done
  sleep ${SHOT_DELAY:-0.3}
  powershell -NoProfile -ExecutionPolicy Bypass -File "C:/Users/Lenovo/Bazaar-Analyzer-dev/tools/shot.ps1" -Out "$png" && echo "shot $png"
done
