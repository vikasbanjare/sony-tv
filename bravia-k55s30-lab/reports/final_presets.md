# Presets

## Your settings (tested on the TV, 2026-10-20)

Everything below was checked with the test clips in a dark room. "As found" means the TV was already
right and nothing needed changing.

**Films and series (SDR):** Display & Sound > Picture
- Picture mode: **Professional** (neutral greys; Standard and Vivid tint everything blue). Use **Cinema** if
  Professional feels too dim in daytime.
- Brightness, Black level, Gamma, Black adjust: **as found** (shadow and highlight detail both correct)
- Motion: Motionflow **Custom**, Smoothness **1**, Clearness **Min**, Film mode **High** (24p and 50p smooth)

**HDR10 / HLG:** settings **as found** (shadow detail visible, highlights distinct up to about 1000 nits).

**Dolby Vision (not tested with real DV content yet):** Dolby Vision **Dark** in the evening,
Dolby Vision **Bright** in daytime. Check with a Dolby Vision title on Netflix or Prime.

**Sound:** Display & Sound > Audio
- TV position: **Wall mount** (correct)
- Surround **Auto**, Surround effect **minimum**, Equaliser **flat** (no rattle on the bass test)
- Dialogue enhancer: minimum now; try **High** on a talking scene at the same volume and keep it if voices are clearer
- Auto Volume: On is handy for TV channels and adverts, Off for films

**Not possible on this TV** (and why): `unavailable_features.md`. Biggest upgrade left: a soundbar on **HDMI 3** (eARC).

---


**Nothing here has been tested on the TV yet.** These are the starting points the tests in
`../tests/tests.csv` begin from. After each test, replace "start from" with what won, and the test ID that proved it.
A setting with no test ID is a guess.

Sony keeps picture settings separately for each picture mode and input, so set them on the input you
use (each app, each HDMI port) and check the other inputs afterwards.

## SDR, evening / dark room (films, series)

| Setting | Start from | Decided by | Kept value |
|---|---|---|---|
| Picture mode | Cinema; compare Professional | P01, P02, T003 | **Professional** (neutral grey in T003; Standard/Vivid tint blue) |
| Light sensor | Off | P03 | |
| Brightness | Low enough that black bars don't glow | P10 | |
| Black level, Gamma | Default, then checked on the clips | P06, P07, T004, T005 | **Leave as found** (black and white detail both correct in Professional) |
| Black adjust, Adv. contrast enhancer | Off, then Low | P08, P09 | |
| Colour temperature | Expert 1 | P11 | |
| Live Colour | Off | P12 | |
| Reality Creation | Auto for 1080p, off for clean 4K | P13 | |
| Noise reduction | Off for 4K, Low for poor broadcast | P14 | |
| Sharpness | Default or lower, no halos | P15 | |
| Motionflow / Film mode | Off or Custom with Smoothness 1; Film mode Auto | P16, P17, T006, T007 | **Custom, Smoothness 1, Clearness Min, Film mode High** (24p and 50p both smooth in T007) |

## SDR, daytime / bright room

Same as above, then: Standard or Cinema with higher Brightness (P10), Power saving off (P04),
Light sensor back on if the room light changes a lot during the day.

## HDR10 / HLG

| Setting | Start from | Decided by | Kept value |
|---|---|---|---|
| HDR mode | Auto | P20 | |
| Picture mode | Cinema vs Professional | P22, T008 | **As found** (dark steps visible, highlights distinct to ~1000 nits) |
| Brightness | Max (HDR needs it) | P22 | |
| Peak luminance / Auto local dimming | Only if the menu has them | P18, P19 | |

## Dolby Vision

| Room | Start from | Decided by | Kept value |
|---|---|---|---|
| Day | Dolby Vision Bright | P21 | |
| Evening / dark | Dolby Vision Dark | P21 | |

## Game (Mac or console)

Game mode on that HDMI input, ALLM on the source, HDMI video range matched (P05, P23).

## Sound, TV speakers

| Setting | Start from | Decided by | Kept value |
|---|---|---|---|
| TV position | Match the real stand or wall mount | P29 | **Wall mount** (TV is on the wall; already set) |
| Surround | Off for speech, compare On for films | P24 | As found: **Auto**, Surround effect minimum. Try Off if voices feel distant |
| Dialogue enhancer | Low | P25 | As found: minimum. Try High on a dialogue scene at the same volume |
| Equaliser | Flat, then a small cut at the first buzzing bass step | P26 | As found: **flat**. Change only if the bass-steps clip buzzes |
| Auto Volume | On for TV channels, Off for films | P27 | |
| Dynamic range | On at night | P28 | |

## Mac playback

`mpv --config-dir=config/mpv --profile=k55-sdr-tv <file>`; add `--profile=k55-night` late at night.
Keep or drop debanding and the HDR passthrough profile after P32 and P33.
