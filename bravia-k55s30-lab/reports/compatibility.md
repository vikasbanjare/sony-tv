# Compatibility

Every project from the plan, cloned at the commit in `../config/repos.tsv` and read on 2026-10-20.
None was run against the TV. "Evidence" means something in the project that mentions the K-55S30,
BRAVIA 3 or the BF1 platform; for every project the answer is **none**.

| Project | What it actually is | Last commit | Licence | Needs | K-55S30 evidence | Use here | Risk |
|---|---|---|---|---|---|---|---|
| [sony-bravia-adb-scripts](https://github.com/supermarsx/sony-bravia-adb-scripts) | PowerShell 7 menu of ADB actions: remote keys, app launch, debloat, settings, reboot. The `.sh` file only starts `pwsh` | 2026-04-08 | MIT | PowerShell 7, adb | None | **Read only, don't run.** `scripts/adb-diagnostics.sh` covers the read-only part without the write actions next to it | Medium: debloat and settings writes one menu item away |
| [Bravia-REST-API](https://github.com/cmos486/Bravia-REST-API) | Home Assistant integration for Sony's *Professional* display REST API: picture mode, brightness, power saving, sound output | 2026-08-23 | MIT | Home Assistant 2024.1+, TV with IP control and a Pre-Shared Key | None. It reads model, firmware, **serial and MAC** | **Wait for the probe.** Its method list is what `scripts/bravia-rest-probe.sh` asks for (reads only). Only if the TV answers is a preset switcher possible | Low for reads; writes not allowed without approval |
| [mpv](https://github.com/mpv-player/mpv) | Video player (0.41 in development) | 2026-10-18 | GPL-2.0+ / LGPL-2.1+ | `brew install mpv` | n/a (runs on the Mac) | **Yes**, `config/mpv/` | Low. Only affects files played from the Mac |
| [libplacebo](https://github.com/haasn/libplacebo) | Rendering library behind mpv's `vo=gpu-next`: scaling, tone mapping, debanding | 2026-10-19 | LGPL-2.1+ | Comes with brew's mpv | n/a | **Yes, through mpv.** Nothing to install separately | Low |
| [tv-calibration](https://github.com/jweber93/tv-calibration) | Web app that guides a meter-based calibration (ColourSpace ZRO or ArgyllCMS `spotread`), with Dogegen or LightSpace Connect as pattern source. Its prepare step includes service-menu instructions for some profiles | 2026-10-03 | MIT | A colorimeter, a pattern generator (Dogegen is Windows), Docker or Python | None. Its TV profiles are Hisense U8G, TCL 7105X, Vizio V4K55M and LG OLED55B7A; no Sony profile, and its ADB auto-apply targets those | **Only with a meter**, and only its user-menu steps. Skip any service-menu step | Medium if service-menu advice is followed |
| ArgyllCMS | Meter drivers and calibration tools. **No GitHub repo exists** at the plan's address; it is published at https://www.argyllcms.com/ | — | AGPL (per its site; not checked here) | A supported meter; `brew install argyll-cms` | n/a | **Only with a meter**. A LUT it makes only applies to Mac playback, never to the TV's apps | Low |
| [displaycal-py3](https://github.com/eoyilmaz/displaycal-py3) | Profiling GUI on ArgyllCMS. Has an Apple Silicon (arm64) installer | 2026-07-29 | GPL-3.0 | A meter, ArgyllCMS | n/a | **Only with a meter**; profiles the Mac's output, not the TV's internal apps | Low |
| [RootlessJamesDSP](https://github.com/timschneeb/RootlessJamesDSP) | Android app that processes other apps' audio through internal audio capture, no root. Needs Android 10+ | 2026-07-25 | GPL-3.0 | Sideloading an APK on the TV, ADB permission grants | None. Its own README: apps that block audio capture stay unprocessed, latency goes up. Its Spotify workaround patches apps to remove capture restrictions | **No.** Not installed. TV streaming apps are the kind that block capture, added latency hurts lip sync, and the app-patching workaround is off limits | High for what it would need |
| [JamesDSPManager](https://github.com/james34602/JamesDSPManager) | Older system-wide DSP for Android 5–10, installed with Magisk (root) | 2025-06-18 | GPL-2.0 (`Main/LICENSE`) | Root | None | **No.** Needs root | Prohibited (root) |

## Sony's own remote-control interfaces

| Interface | What's known | Status |
|---|---|---|
| Professional REST API (`/sony/video` getPictureQualitySettings etc.) | Documented for BRAVIA Professional Displays only. Needs a Pre-Shared Key for most methods | **Unknown on K-55S30.** The Help Guide has no PSK setting, and one unverified report says BRAVIA 3 dropped it. Run `scripts/bravia-rest-probe.sh` |
| Simple IP control | Named in the K-55S30 Help Guide (Settings > Network & Internet > IP control) | Present. Its command set was not reviewed here; no picture-quality commands are assumed |
| ADB | Developer options > Wireless debugging (Android 11+) | Works on Android TV 12 in general. Gives device facts (`scripts/adb-diagnostics.sh`), not panel brightness, zone count or panel type |

## Decision

Use now: mpv + libplacebo on the Mac; the lab's own read-only scripts.
Use only with a colorimeter: ArgyllCMS, DisplayCAL, tv-calibration (user-menu steps only).
Decide after the probe: the REST API for preset switching.
Not used: sony-bravia-adb-scripts (as a tool), RootlessJamesDSP, JamesDSPManager. See `unavailable_features.md`.
