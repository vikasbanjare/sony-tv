#!/bin/sh
# Screenshots and screen recordings of the TV, for sharing.
#
#   sh tools/record.sh <TV_IP> shot              saves shots/home-<time>.png  (full 4K/1080p screenshot)
#   sh tools/record.sh <TV_IP> video [seconds]   records the screen (default 60 s, max 180), saves shots/cat-<time>.mp4
#   sh tools/record.sh <TV_IP> gif FILE.mp4      turns a recording into a README-sized GIF (needs ffmpeg)
#
# Tips for a good clip: press Home first, then while it records move the cursor across the apps (the
# sky takes each app's colour), leave the cursor still for a while so the cat does her own things,
# and record a few takes; she picks at random.
set -e
cd "$(dirname "$0")/.."
TARGET="$1"; WHAT="${2:-shot}"; ARG="$3"
[ -n "$TARGET" ] || { echo "Usage: sh tools/record.sh <TV_IP> shot|video [seconds]|gif FILE.mp4" >&2; exit 1; }
case "$TARGET" in
  *.*.*.*:*) TV=$TARGET ;;
  *.*.*.*)   TV=$TARGET:5555 ;;
  *)         TV=$TARGET ;;
esac
mkdir -p shots
STAMP=$(date +%Y%m%d-%H%M%S)

case "$WHAT" in
  shot)
    adb connect "$TV" >/dev/null 2>&1 || true
    adb -s "$TV" exec-out screencap -p > "shots/home-$STAMP.png"
    echo "Saved shots/home-$STAMP.png" ;;
  video)
    SECS="${ARG:-60}"; [ "$SECS" -le 180 ] || SECS=180
    adb connect "$TV" >/dev/null 2>&1 || true
    echo "Recording $SECS seconds… press Home on the remote and move around the apps."
    adb -s "$TV" shell screenrecord --time-limit "$SECS" --bit-rate 16000000 /sdcard/petcat-rec.mp4
    adb -s "$TV" pull /sdcard/petcat-rec.mp4 "shots/cat-$STAMP.mp4" >/dev/null
    adb -s "$TV" shell rm -f /sdcard/petcat-rec.mp4
    echo "Saved shots/cat-$STAMP.mp4" ;;
  gif)
    [ -f "$ARG" ] || { echo "Give the recording: sh tools/record.sh <TV_IP> gif shots/cat-....mp4" >&2; exit 1; }
    command -v ffmpeg >/dev/null || { echo "ffmpeg is needed: brew install ffmpeg" >&2; exit 1; }
    ffmpeg -loglevel error -y -i "$ARG" -t 20 -vf "fps=15,scale=1280:-1:flags=lanczos,split[s0][s1];[s0]palettegen=max_colors=128[p];[s1][p]paletteuse=dither=bayer" docs/pet-cat.gif
    echo "Saved docs/pet-cat.gif (first 20 s). Commit it and it shows at the top of the README." ;;
  *) echo "Unknown: $WHAT" >&2; exit 1 ;;
esac
