#!/bin/bash
# usage: devrun.sh "<-D flags>" <donePattern> [timeoutSec]  - runs the dev client with the flags, waits for the log pattern, then kills it.
cd /c/Users/Lenovo/Bazaar-Mod
export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot"
export JAVA_TOOL_OPTIONS="$1 -Dbazaaranalyzer.dev.noBrowser=1"
LOG=/c/Users/Lenovo/Bazaar-Analyzer-dev/tools/run.log
./gradlew runClient > $LOG 2>&1 &
start=$(date +%s); to=${3:-400}
until grep -q -- "$2" $LOG; do
  sleep 2
  if [ $(( $(date +%s) - start )) -gt $to ]; then echo "TIMEOUT"; break; fi
done
sleep 3
powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe' OR Name='javaw.exe'\" | Where-Object { \$_.CommandLine -like '*Bazaar-Mod*' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force -Confirm:\$false }"
wait 2>/dev/null
grep "(bazaaranalyzer)\|Exception\|ERROR" $LOG | grep -v "SignedJWT\|Realms" | head -60
