#!/bin/sh
# Phase 1: checks the Mac has what the lab needs. Installs nothing; prints what's missing.
#
#   sh scripts/env-check.sh
echo "Machine: $(uname -s) $(uname -m)"
[ "$(uname -s)" = Darwin ] && echo "macOS $(sw_vers -productVersion 2>/dev/null)"
[ "$(uname -m)" = arm64 ] || echo "  note  not Apple Silicon; everything still works, the mpv notes assume an M-series Mac"
missing=
for tool in git adb ffmpeg mpv curl; do
  if command -v "$tool" >/dev/null 2>&1; then
    case $tool in ffmpeg) flag=-version ;; adb) flag=version ;; *) flag=--version ;; esac
    printf '  ok    %-7s %s\n' "$tool" "$("$tool" $flag 2>/dev/null | head -1)"
  else
    printf '  MISSING %s\n' "$tool"; missing="$missing $tool"
  fi
done
command -v brew >/dev/null 2>&1 && echo "  ok    brew" || echo "  MISSING brew (https://brew.sh)"
if [ -n "$missing" ]; then
  echo
  echo "To install (asks nothing of the TV):"
  echo "  brew install$(echo "$missing" | sed 's/ adb/ android-platform-tools/')"
fi
