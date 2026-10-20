#!/bin/sh
# Makes the test clips used in tests/README.md: picture patterns (SDR and HDR10)
# and audio tracks. Everything is generated locally by ffmpeg, so there is
# nothing to download and nothing copyrighted.
#
#   sh scripts/make-patterns.sh            all clips into tests/patterns/
#   DUR=10 sh scripts/make-patterns.sh     shorter clips (seconds), for a quick try
#
# Play them from a USB stick in the TV's own media player (tests the TV's
# processing, the one that Netflix/YouTube also go through) and from the Mac
# with mpv over HDMI. Keep the volume LOW the first time you play the audio clips.
set -e

command -v ffmpeg >/dev/null || { echo "ffmpeg is missing. On the Mac: brew install ffmpeg" >&2; exit 1; }
cd "$(dirname "$0")/.."
OUT=tests/patterns
DUR=${DUR:-60}
mkdir -p "$OUT"

W=3840; H=2160
LOOP="loop=loop=-1:size=1"
FF="ffmpeg -nostdin -hide_banner -loglevel error -y" # -nostdin: never read the keyboard or pasted commands
SDR="-c:v libx264 -preset slow -crf 4 -profile:v high -level 5.1 -pix_fmt yuv420p \
 -color_range tv -colorspace bt709 -color_primaries bt709 -color_trc bt709 -movflags +faststart"
HDR="-c:v libx265 -preset medium -crf 4 -pix_fmt yuv420p10le -tag:v hvc1 -movflags +faststart \
 -x265-params log-level=error:hdr10=1:repeat-headers=1:range=limited:colorprim=bt2020:transfer=smpte2084:colormatrix=bt2020nc:master-display=G(13250,34500)B(7500,3000)R(34000,16000)WP(15635,16450)L(10000000,1):max-cll=1000,400"

# Builds a geq expression: equal-width columns across the frame, one value each.
columns() { # values...
  n=$#; i=0; e=""
  for v in "$@"; do e="$e+eq(trunc(X*$n/W),$i)*$v"; i=$((i + 1)); done
  echo "${e#+}"
}
# 10-bit limited-range PQ code value for a luminance in nits (SMPTE ST 2084).
pq() {
  awk -v L="$1" 'BEGIN { m1=0.1593017578125; m2=78.84375; c1=0.8359375; c2=18.8515625; c3=18.6875
    y=(L/10000)^m1; printf "%d", 64 + 876*((c1+c2*y)/(1+c3*y))^m2 + 0.5 }'
}
sdr() { # name, luma expression (8-bit code values, 16 = black, 235 = white)
  # geq is slow at 4K, so it draws one frame and loop repeats it.
  $FF -f lavfi -i "color=c=black:s=${W}x$H:r=50:d=0.02,format=yuv420p,geq=lum='$2':cb=128:cr=128,$LOOP" -t "$DUR" $SDR "$OUT/$1.mp4"
  echo "  $OUT/$1.mp4"
}

echo "SDR picture patterns (BT.709, limited range)"
# Black level: 20 bars, code 12 to 31 from left to right, middle third of the screen.
# Bars 12-16 must look identical to the background (16). Bar 17 should be just visible.
sdr sdr-black-clipping "if(between(Y,H/3,2*H/3),$(columns 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31),16)"
# Whites: 20 bars, code 216 to 254 in steps of 2. Bars up to 234 must all be told apart from 235.
sdr sdr-white-clipping "if(between(Y,H/3,2*H/3),$(columns 216 218 220 222 224 226 228 230 232 234 236 238 240 242 244 246 248 250 252 254),235)"
# Smooth ramp, black to white, for banding and gamma "stair steps".
sdr sdr-gray-ramp "16+trunc(X*220/W)"
# 11-step greyscale, 0% to 100% in 10% steps: tint (white balance) and gamma by eye.
sdr sdr-gray-steps "$(columns 16 38 60 82 104 126 147 169 191 213 235)"

echo "SDR motion clips (the slow part: a few minutes)"
MOTION=$(echo "$SDR" | sed 's/-crf 4/-crf 16/') # moving detail at CRF 4 would be gigabytes
MDUR=$((DUR < 30 ? DUR : 30))
$FF -f lavfi -i "testsrc2=s=${W}x$H:r=50:d=$MDUR,scroll=horizontal=0.0015,format=yuv420p" $MOTION "$OUT/sdr-motion-pan-50p.mp4"
echo "  $OUT/sdr-motion-pan-50p.mp4"
$FF -f lavfi -i "testsrc2=s=${W}x$H:r=24000/1001:d=$MDUR,scroll=horizontal=0.003,format=yuv420p" $MOTION "$OUT/sdr-motion-pan-24p.mp4"
echo "  $OUT/sdr-motion-pan-24p.mp4"

echo "HDR10 picture patterns (BT.2020, PQ, MaxCLL 1000)"
dark="" ; bright=""
for n in 0 0.01 0.02 0.05 0.1 0.2 0.5 1 2 5; do dark="$dark $(pq "$n")"; done
for n in 100 200 300 400 500 600 800 1000 1500 2000; do bright="$bright $(pq "$n")"; done
# Top half: 0, 0.01, 0.02, 0.05, 0.1, 0.2, 0.5, 1, 2, 5 nits. Bottom half: 100 to 2000 nits.
# shellcheck disable=SC2086 # the lists are meant to split into separate values
$FF -f lavfi -i "color=c=black:s=${W}x$H:r=50:d=0.02,format=yuv420p10le,geq=lum='if(lt(Y,H/2),$(columns $dark),$(columns $bright))':cb=512:cr=512,$LOOP" -t "$DUR" $HDR "$OUT/hdr10-pq-steps.mp4"
echo "  $OUT/hdr10-pq-steps.mp4"

echo "Audio clips (48 kHz stereo; play at a low volume first)"
A="-ar 48000 -c:a pcm_s16le"
MONO2="pan=stereo|c0=c0|c1=c0" # same signal in both speakers, no level change
$FF -f lavfi -i "sine=f=1000:d=20,volume=-2dB,$MONO2" $A "$OUT/audio-1khz-minus20dbfs.wav"
$FF -f lavfi -i "anoisesrc=color=pink:amplitude=0.1:d=30,$MONO2" $A "$OUT/audio-pink-noise.wav"
# Logarithmic sweep 20 Hz to 20 kHz over 20 s at -18 dBFS: listen for rattles, buzz, and where the bass fades out.
$FF -f lavfi -i "aevalsrc='0.125*sin(2*PI*20*20/log(1000)*(exp(t*log(1000)/20)-1))':s=48000:d=20,$MONO2" $A "$OUT/audio-sweep-20hz-20khz.wav"
# Bass steps, 4 s each: 40 50 63 80 100 125 160 Hz at -20 dBFS. Raise the volume slowly; stop at the first buzz.
steps=""; n=0
for f in 40 50 63 80 100 125 160; do
  steps="${steps}sine=f=$f:d=4:sample_rate=48000,afade=t=in:d=0.05,afade=t=out:st=3.95:d=0.05[s$n];"; n=$((n + 1))
done
$FF -filter_complex "${steps}[s0][s1][s2][s3][s4][s5][s6]concat=n=7:v=0:a=1,volume=-2dB,$MONO2" $A "$OUT/audio-bass-steps.wav"
# Channel check: 2 s left only, 2 s right only, 2 s both.
$FF -f lavfi -i "aevalsrc='0.1*sin(2*PI*440*t)*lt(t,2)+0.1*sin(2*PI*440*t)*gte(t,4)|0.1*sin(2*PI*440*t)*gte(t,2)':s=48000:d=6" $A "$OUT/audio-left-right.wav"
# The same audio as MP4 (AAC), for TV media players that skip .wav files.
for f in "$OUT"/audio-*.wav; do
  $FF -f lavfi -i "color=c=black:s=1280x720:r=25" -i "$f" -shortest -c:v libx264 -pix_fmt yuv420p -c:a aac -b:a 320k "${f%.wav}.mp4"
done
ls "$OUT"/audio-*

echo "Done. Put $OUT on the TV (USB stick formatted exFAT, or: adb push $OUT /sdcard/Movies/k55-patterns)"
echo "and open it in the TV's Media Player. tests/README.md has the steps."
