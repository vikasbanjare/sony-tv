# Sound

## What the hardware is

Two full-range bass-reflex speakers, 10 W each, X-Balanced. Dolby Audio and Atmos, DTS up to DTS:X decoding.
eARC/ARC on HDMI. Sony's spec page shows "-" for Voice Zoom and Sound modes (Sony India, read 2026-10-20).

So: dialogue clarity and balance can be improved; deep bass and loudness can't be added. Atmos is
decoded, then played on two forward speakers.

## What the menus offer (Help Guide; check which exist on this TV)

Surround, Surround effect, Dialogue enhancer, Equaliser, Volume offset, Dynamic range, Auto Volume,
Balance, TV position, A/V sync, Digital audio out, Speakers (TV / audio system), Reset.

## How to test

1. **Level-match first.** Play `audio-1khz-minus20dbfs` and set the volume so a phone sound meter app reads the same
   at your seat for every comparison (about 65–70 dB is comfortable). Louder always sounds better; only
   matched comparisons count.
2. **Channels.** `audio-left-right`: left, right, then centred. If not, check Balance and the source.
3. **Placement.** Set TV position to the real mounting (stand or wall). Listen to `audio-pink-noise`: it should
   sound even, not boomy.
4. **Buzz limit.** `audio-bass-steps`, volume up slowly. Note the first step that buzzes or rattles. If it's a step
   you hear in normal films, a small equaliser cut there (test P26) helps more than any boost.
5. **Dialogue.** Same speech clip, level-matched: Dialogue enhancer off/low/high (P25), Surround on/off (P24).
6. **Night.** An action film at low volume: Dynamic range on/off (P28).
7. Record each result with `sh scripts/log-experiment.sh`.

Stop at once on crackling, clipping or lip-sync drift, and revert the last change.

## Mac playback

`config/mpv/mpv.conf` sends stereo to the TV, folds surround tracks down without clipping, and has two filters to
A/B with the keyboard: **F1** dialogue lift (+3 dB at 2.5 kHz), **F2** night compressor. Starting points only;
keep them only if the level-matched test says so (P34).

## Biggest upgrade available

A soundbar or AV receiver on the TV's eARC port (Settings > Audio output > Speakers: Audio system). That is
real extra hardware; no software on the TV will match it.

## Not used

Android system-wide DSP apps (RootlessJamesDSP, JamesDSPManager). See `unavailable_features.md`.
