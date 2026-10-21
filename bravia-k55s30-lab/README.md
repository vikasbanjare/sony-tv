# K-55S30 picture and sound lab

A step-by-step way to get the best picture and sound out of a **Sony BRAVIA 3 K-55S30** with the settings Sony
gives you: test one change at a time, keep what's better, undo what isn't.

Runs on your **Mac**, on the same Wi-Fi as the TV. Nothing here roots, unlocks, flashes or service-menus the TV.
The scripts only read from the TV; every picture and sound change is made by you, with the remote.

> **Two corrections to the original plan (checked 2026-10-20):**
> 1. Sony's K-55S30 downloads page now lists firmware **6120900201** and names the K-55S30 as an applicable model.
>    See [`reports/firmware.md`](reports/firmware.md).
> 2. ArgyllCMS has no GitHub repository at the address in the plan. It's at https://www.argyllcms.com/.

## What's in here

| Folder | |
|---|---|
| [`research/`](research/) | The TV's verified specs, the menu items to look for, and every source with what it said |
| [`config/`](config/) | `repos.tsv` (projects, pinned commits, licences), `mpv/` (Mac player settings and A/B keys) |
| [`scripts/`](scripts/) | The tools below |
| [`baselines/`](baselines/) | `baseline.md`, the form for your current settings; script output lands here too |
| [`tests/`](tests/) | `README.md` (how to test and score), `tests.csv` (34 planned tests, your results) |
| [`reports/`](reports/) | compatibility, firmware, before/after, final presets, audio, not possible, rollback |
| `repos/`, `backups/` | Clones and photos. Kept out of git |

| Script | What it does | Touches the TV? |
|---|---|---|
| `scripts/env-check.sh` | Lists missing Mac tools | No |
| `scripts/clone-repos.sh` | Clones the reviewed projects into `repos/` to read | No |
| `scripts/make-patterns.sh` | Makes the test clips (black/white clipping, grey ramp, motion, HDR10 steps, audio) | No |
| `scripts/adb-diagnostics.sh` | Saves model, Android build, display modes, HDR and audio facts | Reads only |
| `scripts/bravia-rest-probe.sh` | Checks whether the TV answers Sony's REST API for picture/sound settings | Reads only |
| `scripts/play-on-tv.sh` | Opens a test clip on the TV from the Mac (copies it over first if needed) | Copies clips to the TV's Movies folder; nothing else |
| `scripts/log-experiment.sh` | Adds a test result to `tests/tests.csv` | No |

## Steps

Run everything from this folder: `cd bravia-k55s30-lab`.

**0. Firmware.** HOME > Help on the TV, note the version. Update through the TV's own updater if it's below
6120900201 ([`reports/firmware.md`](reports/firmware.md)). Do this first so the baseline isn't mixed across versions.

**1. Mac tools.**
```sh
sh scripts/env-check.sh
brew install git android-platform-tools ffmpeg mpv     # only what it says is missing
```

**2. Projects (optional, to read).** `sh scripts/clone-repos.sh`, then [`reports/compatibility.md`](reports/compatibility.md)
says what each one is and whether it applies. Don't run their code against the TV.

**3. Read the TV (optional).** Over Wi-Fi, the same way as `install.sh` and `check.sh` in this repo: USB debugging on
(see [`docs/sony-bravia.md`](../docs/sony-bravia.md)), Mac and TV on the same Wi-Fi, TV's IP from Settings > Network & Internet.
```sh
sh scripts/adb-diagnostics.sh <TV_IP>             # first time: press Allow on the TV, then run it again
sh scripts/bravia-rest-probe.sh <TV_IP>           # turn on Settings > Network & Internet > IP control first
```
It uses port 5555 like the rest of the repo. If you use Developer options > Wireless debugging instead, pair once with
`adb pair <TV_IP>:<PAIRING_PORT>` and pass the port it shows: `sh scripts/adb-diagnostics.sh <TV_IP>:<PORT>`.

**4. Baseline.** Fill in [`baselines/baseline.md`](baselines/baseline.md) and photograph every menu page.
Make the clips (`sh scripts/make-patterns.sh`), put `tests/patterns/` on the TV (USB stick, or `adb push` over Wi-Fi, see `tests/README.md`) and score them as they are.

**5. Picture and sound, one change at a time.** Work down `tests/tests.csv` (P01–P30) as
[`tests/README.md`](tests/README.md) describes. Log each with `sh scripts/log-experiment.sh`; revert anything worse at once.

**6. Mac playback.** `mpv --config-dir=config/mpv --profile=k55-sdr-tv <file>`, then tests P31–P34. This only changes
files played from the Mac; Netflix and other TV apps never go through it.

**7. Calibration (only with a colorimeter).** ArgyllCMS / DisplayCAL / tv-calibration, user-menu controls only.
Without a meter the results stay "subjective", never "calibrated".

**8. Write it up.** Kept settings go into [`reports/final_presets.md`](reports/final_presets.md), evidence into
[`reports/before_after.md`](reports/before_after.md). Re-check after a reboot and on a second input.

## Safety rules

- Ask before: any write to the TV, installing anything on it, any ADB command that isn't a read, any update.
- Never: root, unlock, cross-flash or downgrade firmware; service menu, backlight drive, thermal or power limits;
  disable DRM, HDMI, update or other core Sony packages.
- Stop and revert if black or white detail clips, HDR stops being detected, colours shift, audio crackles or
  drifts out of sync, the TV runs hot, or apps crash.
- "Brightness hacks", "OLED conversions" and system-wide streaming filters go in
  [`reports/unavailable_features.md`](reports/unavailable_features.md), not the install queue.

## What to expect

This is a Direct LED LCD with Frame Dimming (whole-screen dimming, no zones), a native 50 Hz panel and two
10 W speakers. Good settings give correct blacks without crushed shadows, the brightest comfortable picture
the menus allow, correct HDR and Dolby Vision, cleaner motion and clearer dialogue. They can't give OLED blacks,
local dimming, 120 Hz or deep bass ([why](reports/unavailable_features.md)).
