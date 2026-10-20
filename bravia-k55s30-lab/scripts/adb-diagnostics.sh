#!/bin/sh
# Reads facts from the TV over ADB and saves them to baselines/. Changes nothing on the TV.
#
#   sh scripts/adb-diagnostics.sh <TV_IP>                 read and save, over Wi-Fi like install.sh
#   sh scripts/adb-diagnostics.sh <TV_IP> --names         also list Sony property NAMES (no values)
#   sh scripts/adb-diagnostics.sh <TV_IP>:<PORT>          a different port (e.g. Wireless debugging)
#
# Same connection as the rest of this repo: USB debugging on, TV and Mac on the same Wi-Fi,
# port 5555 unless you give one. The first time, the TV asks "Allow debugging?": tick
# "Always allow", press Allow, and run the command again.
#
# Only the commands in this file run, all of them reads (getprop, dumpsys, settings get,
# wm size, pm list). It skips serial numbers, MAC addresses, Android ID, accounts and logs.
set -e

TV="$1"; NAMES=
[ "$2" = --names ] && NAMES=1
case "$TV" in
  *.*.*.*:*) ;;
  *.*.*.*)   TV=$TV:5555 ;;
  *) echo "Usage: sh scripts/adb-diagnostics.sh <TV_IP>[:PORT] [--names]" >&2; exit 1 ;;
esac
command -v adb >/dev/null || { echo "adb is missing. On the Mac: brew install android-platform-tools" >&2; exit 1; }
cd "$(dirname "$0")/.."

adb connect "$TV" >/dev/null || true
[ "$(adb -s "$TV" get-state 2>/dev/null)" = device ] || {
  echo "Can't reach $TV. Check USB debugging is on, the IP is right, and press Allow on the TV, then run this again." >&2; exit 2; }

mkdir -p baselines
OUT="baselines/adb-$(date +%Y%m%d-%H%M).txt"
sh_tv() { adb -s "$TV" shell "$@" </dev/null | tr -d '\r'; }
# Strips identifiers that dumpsys can print next to display and audio devices.
scrub() { sed -E 's/([Uu]nique[Ii]d|[Aa]ddress|[Ss]erial|[Mm][Aa][Cc]|android_id)[=:] ?[^,} ]*/\1=<removed>/g'; }
section() { printf '\n## %s\n' "$1"; }

{
  echo "# K-55S30 ADB read-only diagnostics, $(date '+%Y-%m-%d %H:%M')"
  section "Build"
  for p in ro.product.brand ro.product.manufacturer ro.product.model ro.product.name ro.product.device \
           ro.board.platform ro.build.id ro.build.display.id ro.build.version.incremental \
           ro.build.version.release ro.build.version.sdk ro.build.version.security_patch ro.build.fingerprint; do
    echo "$p = $(sh_tv getprop "$p")"
  done
  echo "Note: on BRAVIA BF1 TVs ro.build.version.incremental looks like the Sony firmware version without"
  echo "its last digit (612090020 for 6120900201). Sony's documented place to read it is HOME > Help."

  section "Screen"
  sh_tv wm size
  sh_tv wm density
  section "Display modes and HDR (dumpsys display, filtered)"
  sh_tv dumpsys display | grep -iE 'supportedModes|mActiveModeId|activeMode|HdrCapabilities|supportedHdrTypes|hdrConversion|refreshRate=|renderFrameRate|mUserPreferredMode' | scrub | sort -u | head -60

  section "Audio output (filtered)"
  for s in encoded_surround_output encoded_surround_output_enabled_formats; do
    echo "global $s = $(sh_tv settings get global "$s")"
  done
  sh_tv dumpsys audio | grep -iE 'surround|encoded|devices:|hdmi|(^|[^a-z])e?arc' | scrub | sort -u | head -40

  section "HDMI-CEC / ARC (filtered)"
  sh_tv dumpsys hdmi_control | grep -iE '(^|[^a-z])e?arc|cec_enabled|mHdmiControlEnabled|system_audio|power_status' | scrub | sort -u | head -30

  section "Animation settings"
  for s in window_animation_scale transition_animation_scale animator_duration_scale; do
    echo "global $s = $(sh_tv settings get global "$s")"
  done
  section "Thermal status (0 = none)"
  sh_tv dumpsys thermalservice | grep -iE 'Thermal Status|mStatus' | head -5

  section "Sony and picture-related packages (names only)"
  sh_tv pm list packages | sed 's/^package://' | grep -iE 'sony|bravia|picture|calib|dolby|audio' | sort

  if [ -n "$NAMES" ]; then
    section "Sony property names (values not read)"
    sh_tv getprop | sed -n 's/^\[\([^]]*\)\].*/\1/p' | grep -iE 'sony|bravia|version|panel|hdr|dolby' | grep -viE 'serial|mac|imei|wifi|bt\.|bluetooth' | sort
  fi
} > "$OUT"

echo "Saved $OUT. Read it before sharing: check there is nothing you consider private."
