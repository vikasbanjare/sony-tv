#!/bin/sh
# Reads facts from the TV over ADB and saves them to baselines/. Changes nothing on the TV.
#
#   sh scripts/adb-diagnostics.sh <TV_IP>:<PORT>          read and save
#   sh scripts/adb-diagnostics.sh <TV_IP>:<PORT> --names  also list Sony property NAMES (no values)
#
# Use the port the TV shows (Settings > System > Developer options > Wireless debugging,
# or the port you set up yourself). This script never guesses 5555. Pair first, once:
#   adb pair <TV_IP>:<PAIRING_PORT>      (enter the code the TV shows)
#
# Only the commands in this file run, all of them reads (getprop, dumpsys, settings get,
# wm size, pm list). It skips serial numbers, MAC addresses, Android ID, accounts and logs.
set -e

TV="$1"; NAMES=
[ "$2" = --names ] && NAMES=1
case "$TV" in
  *:*) ;;
  *) echo "Usage: sh scripts/adb-diagnostics.sh <TV_IP>:<PORT> [--names]" >&2
     echo "Give the port the TV shows. This script never assumes 5555." >&2; exit 1 ;;
esac
command -v adb >/dev/null || { echo "adb is missing. On the Mac: brew install android-platform-tools" >&2; exit 1; }
cd "$(dirname "$0")/.."

adb connect "$TV" >/dev/null || true
[ "$(adb -s "$TV" get-state 2>/dev/null)" = device ] || {
  echo "Can't reach $TV. Check Wireless debugging is on, the port is right and you ran adb pair." >&2; exit 2; }

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
  echo "Note: none of these is the Sony firmware version (e.g. 6120900201). Read it on the TV: HOME > Help."

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
