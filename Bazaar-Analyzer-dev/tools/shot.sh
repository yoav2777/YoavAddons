#!/bin/sh
# usage: shot.sh <port> <hash-route> <out.png> [width height]
# headless Edge screenshot of the locally served site (fresh throwaway profile)
EDGE="/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
PROF="$(cygpath -w "${TMP:-/tmp}")\ba-shot-profile-$1"
"$EDGE" --headless=new --disable-gpu --hide-scrollbars --user-data-dir="$PROF" \
  --window-size="${4:-1440},${5:-900}" --virtual-time-budget=12000 \
  --screenshot="$(cygpath -w "$3")" "http://127.0.0.1:$1/#$2" 2>/dev/null
