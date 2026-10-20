# Tests

One change at a time, compared against the baseline, kept only if it is better.

## The clips

`sh scripts/make-patterns.sh` writes these to `tests/patterns/` (not committed; a few hundred MB, most of it the motion clips).
Copy the folder to a USB stick (exFAT) and play it in the TV's **Media Player**. That tests the
TV's own processing, the same path Netflix and YouTube go through. Play the same files from the
Mac with mpv to test the HDMI path.

Easiest: start each clip from the Mac. It opens on the TV by itself (copied over Wi-Fi the first time):

```sh
sh scripts/play-on-tv.sh <TV_IP> sdr-black-clipping
```

Or copy them yourself:

- **USB stick.** macOS formats exFAT itself: Disk Utility > the stick > Erase > Format **ExFAT**,
  Scheme **Master Boot Record**. Then drag `tests/patterns` onto it.
- **Over Wi-Fi, no stick.** Copy them into the TV's own Movies folder (about 450 MB, check free space first):
  ```sh
  adb shell df -h /sdcard
  adb push tests/patterns /sdcard/Movies/k55-patterns
  ```
  Open them in the TV's **Media Player** app (Movies > k55-patterns). Delete them when done:
  `adb shell rm -r /sdcard/Movies/k55-patterns`.

| File | What it shows | Correct result |
|---|---|---|
| `sdr-black-clipping.mp4` | 20 bars, code 12 to 31 left to right, on a code-16 background | Bars 1–5 (12–16) invisible. Bar 6 (17) just visible, bars after it each a little lighter |
| `sdr-white-clipping.mp4` | 20 bars, code 216 to 254 in steps of 2, on a code-235 background | Bars 1–10 (216–234) all distinguishable from the background. Bars above 235 may look like the background, which is fine |
| `sdr-gray-ramp.mp4` | Smooth black-to-white ramp | No coloured tint, no visible bands or stair steps |
| `sdr-gray-steps.mp4` | 11 steps, 0 % to 100 % | Each step distinct, all neutral grey (no pink or green cast) |
| `sdr-motion-pan-50p.mp4` | Scrolling test card, 50 fps | Smooth, readable text, no tearing |
| `sdr-motion-pan-24p.mp4` | Scrolling test card, 23.976 fps | Even judder (no irregular stutter); Motionflow trades judder for "soap-opera" look |
| `sdr-motion-pan-24p-1080.mp4`, `sdr-motion-pan-50p-1080.mp4` | The same in 1080p | Use these if the 4K ones show black or stutter in the TV's player (`ONLY=motion sh scripts/make-patterns.sh` remakes just the motion clips) |
| `hdr10-pq-steps.mp4` | HDR10. Top: 0, 0.01, 0.02, 0.05, 0.1, 0.2, 0.5, 1, 2, 5 nits. Bottom: 100, 200, 300, 400, 500, 600, 800, 1000, 1500, 2000 nits | More visible top steps = better shadow detail. The bottom steps merge where the TV runs out of brightness; that point should not move to the left after a change |
| `audio-1khz-minus20dbfs` | 1 kHz tone at -20 dBFS | Use with a phone sound meter app to match levels between tests |
| `audio-pink-noise` | Pink noise, peaks about -21 dBFS | Even, smooth hiss; check balance and TV position settings |
| `audio-sweep-20hz-20khz` | 20 s rising tone at -18 dBFS | Note where bass appears and any buzz or rattle |
| `audio-bass-steps` | 40, 50, 63, 80, 100, 125, 160 Hz, 4 s each, -20 dBFS | Raise the volume slowly; the first step that buzzes is where an equaliser bass cut helps |
| `audio-left-right` | 2 s left, 2 s right, 2 s both | Left speaker, right speaker, then centred |

The HDR10 clip says MaxCLL 1000 nits. Sony does not publish a peak brightness for the K-55S30, so
which bottom steps merge is a result to write down, not a fault.

## Before every test

1. Same room light as the baseline photo for that row (day / evening / dark). Write it in the log.
2. Light sensor off (test P03) so the TV doesn't change brightness on its own between shots.
3. Phone on a fixed stand, same spot, **exposure and white balance locked** (iPhone: long-press to
   lock AE/AF, then drag the sun down; Android: Pro mode with fixed ISO, shutter and WB).
   Photos support what you saw; they don't measure brightness or black level.
4. For audio, match the level first with the 1 kHz tone and a sound meter app, at the seat.
   Louder always sounds "better", so an unmatched comparison means nothing.

## Run a test

1. Pick the next `planned` row in `tests.csv` (P01, P02, ...). Each changes one menu item.
2. Look at the clip with the original setting, then the new one, then the original again.
3. Score 1–5 and decide keep / revert. If the image just got darker, check the black-clipping clip
   before calling it "better blacks": crushed bars mean lost shadow detail.
4. `sh scripts/log-experiment.sh` writes the result as a `T` row. Revert at once if worse.
5. Stop and revert straight away if: black or white bars disappear that were visible, HDR stops being
   detected, colours shift, audio crackles or clips, lip sync drifts, the TV gets unusually hot, or
   apps crash.

## Scores

| Column | 1 | 5 |
|---|---|---|
| dark_detail | bars crushed or blacks grey | every bar from 17 visible, background stays black |
| bright_highlights | highlights clipped or dim | highlight steps separate, image bright enough for the room |
| color | tinted or oversaturated | neutral greys, natural skin |
| artifacts | halos, banding, noise, smearing | none visible at seating distance |

Repeat the kept settings after a reboot and on a second source (an app and an HDMI input) before
writing them into `reports/final_presets.md`.
