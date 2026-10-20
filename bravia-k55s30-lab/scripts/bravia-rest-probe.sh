#!/bin/sh
# Finds out whether the TV answers Sony's REST API for picture and sound settings.
# Only "get" methods are sent; nothing is changed on the TV.
#
#   sh scripts/bravia-rest-probe.sh <TV_IP>
#   BRAVIA_PSK=1234 sh scripts/bravia-rest-probe.sh <TV_IP>   if your TV has a Pre-Shared Key setting
#
# Background (reports/compatibility.md): the API is documented by Sony for BRAVIA
# Professional Displays. The K-55S30 Help Guide only mentions "Simple IP control" and has
# no Pre-Shared Key setting, so every call may fail here. A failure is a result, not a bug:
# it means picture settings can only be changed with the remote.
#
# Turn on first: Settings > Network & Internet > IP control (if the menu has it).
# Skipped on purpose: getSystemInformation and getNetworkSettings (serial number, MAC address).
set -e

IP="$1"
[ -n "$IP" ] || { echo "Usage: sh scripts/bravia-rest-probe.sh <TV_IP>" >&2; exit 1; }
command -v curl >/dev/null || { echo "curl is missing." >&2; exit 1; }
cd "$(dirname "$0")/.."
mkdir -p baselines
OUT="baselines/rest-$(date +%Y%m%d-%H%M).txt"

call() { # service, method, version, params
  printf '\n## %s/%s (v%s)\n' "$1" "$2" "$3"
  curl -sS -m 5 -X POST "http://$IP/sony/$1" \
    -H 'Content-Type: application/json; charset=UTF-8' \
    ${BRAVIA_PSK:+-H "X-Auth-PSK: $BRAVIA_PSK"} \
    -d "{\"method\":\"$2\",\"id\":1,\"params\":$4,\"version\":\"$3\"}" 2>&1 || echo "(no answer)"
  echo
}

{
  echo "# K-55S30 REST probe, $(date '+%Y-%m-%d %H:%M'), PSK given: ${BRAVIA_PSK:+yes}${BRAVIA_PSK:-no}"
  call system getInterfaceInformation 1.0 '[]'
  call guide getSupportedApiInfo 1.0 '[{"services":["system","video","videoScreen","audio"]}]'
  call system getPowerSavingMode 1.0 '[]'
  call video getPictureQualitySettings 1.0 '[{"target":""}]'
  call videoScreen getSceneSetting 1.0 '[]'
  call audio getSoundSettings 1.1 '[{"target":""}]'
  call audio getSpeakerSettings 1.0 '[{"target":""}]'
} > "$OUT"

cat "$OUT"
echo
echo "Saved $OUT. \"Forbidden\" or \"Unauthorized\" means a key is needed. Any other error means that method isn't available."
