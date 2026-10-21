#!/bin/sh
# Skips intros, recaps and credits for you: presses "Skip Intro", "Skip Recap", "Next Episode" the
# moment they appear, in any app that shows them.
#
#   sh skip.sh <TV_IP>            install (or update) and turn on
#   sh skip.sh <TV_IP> off        turn off (stays installed)
#   sh skip.sh <TV_IP> remove     uninstall
#   sh skip.sh <TV_IP> check      while a skip button is on screen: will it work in this app?
#   sh skip.sh <TV_IP> snap       save what the pixel-watcher sees right now (skip-snap.png)
#   sh skip.sh <TV_IP> status     is the pixel-watcher running, and what has it done lately?
#   sh skip.sh <TV_IP> next off   only intros, recaps and credits, never "Next Episode"
set -e
cd "$(dirname "$0")"

TARGET="$1"; WHAT="${2:-on}"; ARG="$3"
[ -n "$TARGET" ] || { echo "Usage: sh skip.sh <TV_IP> [on|off|remove|check|next on|off]" >&2; exit 1; }
command -v adb >/dev/null 2>&1 || { echo "adb is missing: brew install android-platform-tools" >&2; exit 1; }
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

PKG=com.petcat.skip
SVC=$PKG/com.ansub.tvhome.SkipService
sh_tv() { adb -s "$TV" shell "$@" </dev/null | tr -d '\r'; }

# The list of accessibility services Android runs, with ours added or taken out.
services() { sh_tv settings get secure enabled_accessibility_services | sed 's/^null$//'; }
enable() {
  cur=$(services)
  case ":$cur:" in *":$SVC:"*) ;; *) cur="${cur:+$cur:}$SVC" ;; esac
  sh_tv settings put secure enabled_accessibility_services "$cur" >/dev/null
  sh_tv settings put secure accessibility_enabled 1 >/dev/null
}
disable() {
  cur=$(services | tr ':' '\n' | grep -v "^$SVC$" | paste -sd: - || true)
  sh_tv settings put secure enabled_accessibility_services "${cur:-}" >/dev/null
  [ -n "$cur" ] || sh_tv settings put secure accessibility_enabled 0 >/dev/null
}

case "$WHAT" in
  snap)
    APKP=$(sh_tv pm path "$PKG" | sed 's/^package://' | head -1)
    [ -n "$APKP" ] || { echo "Skip isn't installed: sh skip.sh $TARGET" >&2; exit 1; }
    echo "Taking a look at the screen…"
    sh_tv "CLASSPATH=$APKP app_process /system/bin com.ansub.tvhome.SkipHelper snap /sdcard/skip-snap.png"
    adb -s "$TV" pull /sdcard/skip-snap.png skip-snap.png >/dev/null && sh_tv rm -f /sdcard/skip-snap.png >/dev/null
    echo "Saved skip-snap.png (the red box is where it looks for the skip pill)."
    exit 0 ;;
  status)
    echo "Last from the pixel-watcher:"
    adb -s "$TV" logcat -d -s Skip:* 2>/dev/null | tail -8 | sed 's/^/  /'
    if sh_tv "ps -A -o ARGS 2>/dev/null | grep -q SkipHelper"; then echo "Watcher: running"
    else echo "Watcher: not running. Look at the TV: it may be asking \"Allow debugging?\" (tick Always allow, press Allow)."; fi
    exit 0 ;;
  off) disable; echo "Skip is off. 'sh skip.sh $TARGET' turns it back on."; exit 0 ;;
  remove) disable; adb -s "$TV" uninstall "$PKG" >/dev/null && echo "Removed."; exit 0 ;;
  next)
    adb -s "$TV" shell am start -n "$PKG/com.ansub.tvhome.SkipActivity" --es next "${ARG:-on}" >/dev/null
    echo "Next Episode: ${ARG:-on}"; exit 0 ;;
  check)
    echo "Looking at what's on the TV screen now…"
    sh_tv uiautomator dump /sdcard/petcat-ui.xml >/dev/null 2>&1 || true
    dump=$(sh_tv cat /sdcard/petcat-ui.xml 2>/dev/null || true)
    sh_tv rm -f /sdcard/petcat-ui.xml >/dev/null 2>&1 || true
    app=$(printf '%s' "$dump" | grep -o 'package="[^"]*"' | sort | uniq -c | sort -rn | head -1 | sed 's/.*package="\([^"]*\)"/\1/')
    hits=$(printf '%s' "$dump" | grep -o -i -E '(text|content-desc)="[^"]*(skip|next episode)[^"]*"' | sort -u || true)
    [ -n "$app" ] && echo "App in front: $app"
    labels=$(printf '%s' "$dump" | grep -o -E '(text|content-desc)="[^"]+"' | sed 's/^[a-z-]*="//; s/"$//' | grep -v '^$' | sort -u | head -12 | paste -sd'|' - || true)
    if [ -n "$hits" ]; then
      echo "This app shows its buttons. Skip will work here:"; printf '%s\n' "$hits" | sed 's/^/  /'
    elif [ -z "$dump" ] || ! printf '%s' "$dump" | grep -q 'class='; then
      echo "This app draws its own screen and shows nothing to Android. Skip can't press anything here."
    elif [ -z "$labels" ]; then
      echo "This app shows its layout but no words from it. Skip can't see its buttons here."
    else
      echo "No skip button on screen right now. Start an episode, wait for 'Skip Intro' to show, then run this again."
      echo "What it can see now: $labels"
    fi
    exit 0 ;;
esac

APK=out/skip.apk
[ -f "$APK" ] || APK=release/skip.apk
[ -f "$APK" ] || { echo "No skip.apk: run build.sh, or pull the repo again for release/skip.apk." >&2; exit 1; }
echo "Installing… If your TV shows a Google Play Protect message, follow it on the TV to allow the install."
if ! out=$(adb -s "$TV" install -r "$APK" 2>&1); then
  case "$out" in
    *UPDATE_INCOMPATIBLE*) adb -s "$TV" uninstall "$PKG" >/dev/null; adb -s "$TV" install "$APK" ;;
    *) echo "$out" >&2; exit 1 ;;
  esac
fi
enable
echo "Done. Intros, recaps and credits get skipped as they appear."
echo
echo "Now look at the TV. It asks \"Allow debugging?\" for Skip itself: tick \"Always allow\" and press Allow."
echo "That lets it skip in Netflix and Apple TV too, and it's remembered across restarts."
echo "Check with: sh skip.sh $TARGET status"
