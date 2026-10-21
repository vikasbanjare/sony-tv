#!/bin/sh
# Just the cat. Puts her on your TV's own home screen and changes nothing else.
#
#   sh cat.sh <TV_IP>          install (or update) her and turn her on
#   sh cat.sh <TV_IP> off      put her away (she stays installed; "on" brings her back)
#   sh cat.sh <TV_IP> remove   uninstall her
#
# If mycat/cat.json exists (made by tools/catify.py from a photo), she gets that cat's colours.
set -e
cd "$(dirname "$0")"

TARGET="$1"; WHAT="${2:-on}"
[ -n "$TARGET" ] || { echo "Usage: sh cat.sh <TV_IP> [on|off|remove]" >&2; exit 1; }
command -v adb >/dev/null 2>&1 || {
  echo "adb is missing. Install it first:" >&2
  echo "  Mac:     brew install android-platform-tools" >&2
  echo "  Windows: https://developer.android.com/tools/releases/platform-tools" >&2
  exit 1; }

case "$TARGET" in
  *.*.*.*:*) TV=$TARGET ;;
  *.*.*.*)   TV=$TARGET:5555 ;;
  *)         TV=$TARGET ;;
esac
case "$TV" in *:*) adb connect "$TV" >/dev/null || true ;; esac
state=$(adb -s "$TV" get-state 2>&1 || true)
case "$state" in
  device) ;;
  *unauthorized*) echo "Your TV is asking \"Allow debugging?\". Press Allow (tick \"Always allow\"), then run this again." >&2; exit 1 ;;
  *) echo "Can't reach $TV. Check the TV is on, on the same Wi-Fi, and has USB/network debugging turned on." >&2; exit 1 ;;
esac

PKG=com.petcat.overlay
ME=$PKG/com.ansub.tvhome.PetActivity

case "$WHAT" in
  off)
    adb -s "$TV" shell am start -n "$ME" --es cat off >/dev/null
    echo "She's away. 'sh cat.sh $TARGET' brings her back."
    exit 0 ;;
  remove)
    adb -s "$TV" uninstall "$PKG" >/dev/null && echo "Removed."
    exit 0 ;;
esac

APK=out/petcat.apk
[ -f "$APK" ] || APK=release/petcat.apk
[ -f "$APK" ] || { echo "No petcat.apk: run build.sh, or pull the repo again for release/petcat.apk." >&2; exit 1; }

echo "Installing… If your TV shows a Google Play Protect message, follow it on the TV to allow the install."
if ! out=$(adb -s "$TV" install -r "$APK" 2>&1); then
  case "$out" in
    *UPDATE_INCOMPATIBLE*) adb -s "$TV" uninstall "$PKG" >/dev/null; adb -s "$TV" install "$APK" ;;
    *) echo "$out" >&2; exit 1 ;;
  esac
fi

# Two permissions TVs have no settings page for: drawing over the home screen, and seeing which
# app is in front (so she hides during apps and films). Both are granted here, with adb.
adb -s "$TV" shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1 || true
adb -s "$TV" shell appops set "$PKG" GET_USAGE_STATS allow >/dev/null 2>&1 || true

# Her colours, from mycat/cat.json if there is one.
set --
if [ -f mycat/cat.json ]; then
  COAT=$(grep -o '"coat"[[:space:]]*:[[:space:]]*"[^"]*"' mycat/cat.json | sed 's/.*:[[:space:]]*"\(.*\)"/\1/')
  [ -n "$COAT" ] && set -- --es coat "\"$COAT\""
fi
adb -s "$TV" shell am start -n "$ME" "$@" >/dev/null
echo "Done. Press Home on your remote: she's along the bottom of the screen."
[ -f mycat/cat.json ] || echo "Make her look like your cat: see mycat/README.md"
