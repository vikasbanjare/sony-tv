#!/bin/sh
# Plays one test clip on the TV, started from the Mac. Copies it to the TV first if the TV doesn't
# have it, or has an older copy (different size) after make-patterns.sh made a new one.
#
#   sh scripts/play-on-tv.sh <TV_IP> sdr-black-clipping      (name from tests/patterns, with or without .mp4)
#   sh scripts/play-on-tv.sh <TV_IP> list                    what's on the TV
#   APP=mx sh scripts/play-on-tv.sh <TV_IP> <clip>            force MX Player (or APP=<package name>)
#
# The clip opens on the TV by itself. If the TV asks which app to use, pick one with the remote.
# Press Back on the remote to stop it.
set -e

TARGET="$1"; CLIP="$2"
[ -n "$TARGET" ] && [ -n "$CLIP" ] || { echo "Usage: sh scripts/play-on-tv.sh <TV_IP> <clip name>|list" >&2; exit 1; }
case "$TARGET" in
  *.*.*.*:*) TV=$TARGET ;;
  *)         TV=$TARGET:5555 ;;
esac
command -v adb >/dev/null || { echo "adb is missing. On the Mac: brew install android-platform-tools" >&2; exit 1; }
cd "$(dirname "$0")/.."
DIR=/sdcard/Movies/k55-patterns

adb connect "$TV" >/dev/null || true
[ "$(adb -s "$TV" get-state 2>/dev/null)" = device ] || { echo "Can't reach $TV. Is the TV on and on the same Wi-Fi?" >&2; exit 2; }

if [ "$CLIP" = list ]; then
  adb -s "$TV" shell ls -l "$DIR" 2>/dev/null | tr -d '\r' || echo "Nothing on the TV yet."
  exit 0
fi

case "$CLIP" in *.mp4) ;; *) CLIP=$CLIP.mp4 ;; esac
LOCAL="tests/patterns/$CLIP"
[ -f "$LOCAL" ] || { echo "No $LOCAL. Run: sh scripts/make-patterns.sh" >&2; exit 1; }
here=$(wc -c < "$LOCAL" | tr -d ' ')
there=$(adb -s "$TV" shell stat -c %s "$DIR/$CLIP" 2>/dev/null | tr -d '\r' || true)
if [ "$here" != "$there" ]; then
  echo "Copying $CLIP to the TV…"
  adb -s "$TV" shell mkdir -p "$DIR"
  adb -s "$TV" push "$LOCAL" "$DIR/" >/dev/null
fi

case "${APP:-}" in
  mx) PKG="-p com.mxtech.videoplayer.television" ;;
  "") PKG= ;;
  *)  PKG="-p $APP" ;;
esac

echo "Opening $CLIP on the TV…"
# Home first, then a fresh task: if a player is still open it would otherwise just get the intent
# ("delivered to currently running top-most instance") and keep showing the old, finished clip.
adb -s "$TV" shell input keyevent KEYCODE_HOME
sleep 1
# shellcheck disable=SC2086 # PKG is either empty or two words
out=$(adb -s "$TV" shell am start -a android.intent.action.VIEW -f 0x10008000 $PKG -d "file://$DIR/$CLIP" -t video/mp4 2>&1 | tr -d '\r')
case "$out" in
  *Error*)
    echo "The TV couldn't open it: $out" >&2
    [ -z "$PKG" ] || echo "That app doesn't take files from outside. Run it again without APP=." >&2
    exit 3 ;;
esac
echo "It should be playing now. Press Back on the remote to stop."
