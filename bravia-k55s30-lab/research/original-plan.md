# Sony BRAVIA 3 K-55S30 — Picture, HDR, Brightness & Audio Optimization Research + Agent Execution Plan

**Updated:** 20 October 2026  
**Status:** Hardware model **confirmed by owner’s photograph of the rear product label**. This document supersedes the previous generic “BRAVIA BF1” research file.  
**Objective:** Improve perceived image quality, accurate black levels, shadow detail, HDR handling, visible brightness within approved controls, motion and sound quality; validate with repeatable tests and retain only improvements.

> **Safety contract:** Do not root, unlock, cross-flash, alter service-menu calibration, change backlight drive/current/voltage, defeat thermal limits, bypass DRM, disable safety mechanisms, or install unverified firmware. Do not blindly install every repo on the TV. A GitHub repo is not proof that the feature is compatible. One controlled experiment at a time.

## 1. Verified device identity — no longer speculative

| Field | Value | Basis |
|---|---|---|
| Retail model | **K-55S30** | Owner-supplied photograph of rear label reading `MODEL NO. K-55S30` |
| Product series | **Sony BRAVIA 3 (S30), model year 2024** | Sony India official support/specifications |
| Screen size | **55-inch / 54.6-inch measured** | Sony India |
| Google/Android About model string | **BRAVIA BF1** | Owner-supplied Android About screenshot |
| Android TV OS | **12** | Same screenshot |
| Android security patch | **5 September 2026** | Same screenshot |
| Android OS build ID | **STT2.230811.001** | Same screenshot; **not** Sony OTA firmware version |
| Kernel | **5.4.296-g1f163d78d073-dirty** | Same screenshot |
| Retail serial number | Intentionally excluded | Not necessary for optimization |

**IMPORTANT:** `BRAVIA BF1` is the model identifier exposed by the Android About interface and can identify a software family, but **K-55S30** is the specific retail model for firmware and hardware research. The screenshots do **not** establish the installed Sony firmware package version. Check that separately in Help / System Information / software version or the firmware update UI.

## 2. Model-specific hardware — official Sony India specifications

| Feature | Verified K-55S30 capability | Consequence |
|---|---|---|
| Resolution | 3840 × 2160 (4K LCD) | Source must be high quality; compression is often the bottleneck |
| Backlight | **Direct LED** | No per-pixel light emission |
| Backlight dimming | **Frame Dimming** | **Not** multi-zone full-array local dimming. Cannot program in non-existent local-dimming zones |
| Official India panel refresh | **50 Hz** (HDMI accepts compatible 60 Hz signals) | Do not advertise native 120 Hz; inspect motion behavior per input |
| Image processor | 4K HDR Processor X1 | Sony processing/upscaling already present |
| Processing | 4K X-Reality PRO, TRILUMINOS PRO, Live Colour, Dynamic Contrast Enhancer | Tune individually; do not assume max is best |
| HDR formats | **HDR10, HLG, Dolby Vision** | Dolby Vision must be tested with actual DV content; don't force generic SDR settings onto HDR |
| Modes | Vivid, Standard, Cinema, Game, Graphics, Photo, Professional; Dolby Vision Bright/Dark/etc. | Use distinct presets for room, SDR/HDR/DV and gaming |
| Sensor | Light sensor | Disable temporarily for repeatable evaluation; adjust later for user preference |
| Speakers | Two full-range X-Balanced bass-reflex speakers, **10 W + 10 W** | EQ/dialogue tuning possible; physical deep bass and max clean loudness limited |
| Audio support | Dolby Audio, Dolby Atmos, supported DTS formats | Codec support does not imply true discrete Atmos speaker array |
| Audio ports | HDMI **eARC/ARC**, digital audio output | External compatible soundbar/AVR is a major possible upgrade |
| CalMAN auto calibration | Not specified as supported (Sony shows `-`) | Do not assume CalMAN TV auto-calibration APIs |
| Brightness / panel subtype | **No independently verified measured peak nits, VA-vs-IPS/ADS subtype or native contrast ratio** | Do not make up numbers; measure or cite rigorous model-specific tests |

**Primary source:** https://www.sony.co.in/electronics/support/televisions-projectors-lcd-tvs-android-/k-55s30/specifications  
**Support:** https://www.sony.co.in/electronics/support/televisions-projectors-lcd-tvs-android-/k-55s30

## 3. Correct firmware target — major correction to earlier research

**Use the K-55S30 model-specific Sony India downloads page, not the generic BF1 update previously mentioned.**

- Verified model page: https://www.sony.co.in/electronics/support/televisions-projectors-lcd-tvs-android-/k-55s30/downloads
- Sony currently lists **Firmware update v6120800301 for Asia Pacific**, release date **8 April 2026**, on that model page.
- The old report's BF1 update **v6120900201 (October 2026)** was associated with *other BF1-platform retail models*. **Do not recommend, download or flash it for K-55S30 unless Sony explicitly adds this model to its supported list.**
- The September 2026 **security patch date is not proof** that the Sony firmware version is older or newer than v6120800301; compare the TV's actual Sony firmware version and official applicability requirements.
- Best method: **Settings → System → About → System software update**, using the TV’s built-in update checker; or follow the Sony model-specific published instructions only.
- Never downgrade, cross-flash, override package signature/model checking or interrupt an update.

## 4. Physical limits and realistic goals

1. **OLED-style pixel-off blacks are impossible** on this Direct LED LCD. Frame dimming adjusts backlight output at a large/whole-image scale; the TV cannot independently black out pixels or arbitrary backlight regions.
2. Software may improve **perceived black depth** through proper black-level range, gamma/EOTF, room brightness, contrast management and cleaner source material. Crushing shadow detail is not an improvement.
3. Brightness: maximize **within Sony-approved user settings** (Power Saving, light sensor, backlight/picture Brightness, supported Peak Luminance if available). There is **no verified safe K-55S30 brightness-overdrive exploit** beyond hardware design limits.
4. Unofficial service-menu drive changes, LED current tweaks, EEPROM changes or thermal bypass may shorten lifetime/damage TV. **Prohibited.**
5. Upscaling and tone mapping can improve *external playback* using a Mac/PC; software running on Mac normally cannot replace Sony's processing of protected Netflix/Prime/Disney+/other internal apps.
6. Audio EQ and positioning can improve clarity, but the 2×10 W speakers cannot gain genuine subwoofer bass or larger hardware amplifier power via software.

## 5. Links: verified references and open-source project inventory

### Official Sony

- [K-55S30 technical specifications](https://www.sony.co.in/electronics/support/televisions-projectors-lcd-tvs-android-/k-55s30/specifications)
- [K-55S30 downloads / approved firmware](https://www.sony.co.in/electronics/support/televisions-projectors-lcd-tvs-android-/k-55s30/downloads)
- [K-55S30 support hub](https://www.sony.co.in/electronics/support/televisions-projectors-lcd-tvs-android-/k-55s30)
- [K-55S30 online manuals](https://www.sony.co.in/electronics/support/televisions-projectors-lcd-tvs-android-/k-55s30/manuals)
- [How Sony TV updates are checked](https://www.sony.co.in/electronics/support/televisions-projectors-lcd-tvs-android-/k-55s30/articles/00276489)

### GitHub / open-source — investigate in one **Mac workspace**, not by installing everything on the TV

| Tool or project | URL | Role | Compatibility / precautions |
|---|---|---|---|
| Sony Bravia ADB Scripts | https://github.com/supermarsx/sony-bravia-adb-scripts | TV read-only diagnostics, navigation/automation | User has Android 12; test network debug availability. Do not run debloat/write actions by default. |
| Sony Bravia REST API reference | https://github.com/cmos486/Bravia-REST-API | Research discovery of possible Sony network picture controls | Professional/other Sony models may differ; never assume consumer K-55S30 supports methods. |
| mpv | https://github.com/mpv-player/mpv | External video playback, quality and A/B comparisons | Mac/PC output over HDMI; not systemwide built-in streaming enhancement. |
| libplacebo | https://github.com/haasn/libplacebo | HDR tone mapping, gamut mapping, debanding, colour processing | Library/rendering engine, not Android TV firmware or APK. |
| TV calibration project | https://github.com/jweber93/tv-calibration | Guided test/calibration workflow | Inspect maintained status and exact dependencies first; meter recommended. |
| ArgyllCMS | https://github.com/argyllcms/ArgyllCMS | Measuring and calibrating with instruments | Requires supported colorimeter; LUT must be applied in compatible pipeline. |
| DisplayCAL Python 3 fork | https://github.com/eoyilmaz/displaycal-py3 | Colour profiling GUI | Check Mac/Apple Silicon installability; do not claim TV internal apps will use generated profiles. |
| RootlessJamesDSP | https://github.com/timschneeb/RootlessJamesDSP | Experimental Android digital signal processing | **Not validated on K-55S30 or protected apps.** Optional isolated testing only; don't circumvent DRM. |
| JamesDSPManager | https://github.com/james34602/JamesDSPManager | Advanced Android DSP research | Root/system modifications may be required; research-only, no root. |

**Evaluate repos for activity, licensing, architecture, releases, dependencies and safety.** Do not treat a repository README as evidence of a measurable improvement on the K-55S30.

## 6. Picture optimization experiments — evaluate separately

### Mandatory baseline before modifying

1. Photograph every current menu page and log all current SDR, HDR, Dolby Vision, HDMI and audio settings separately.
2. Note lighting conditions: bright daylight and dim evening. Disable ambient sensor temporarily for comparable tests.
3. Pick at least five repeatable scenes: near-black shadow steps, starfield/dark credits, bright outdoor daylight, natural skin tone, moving pan, high-quality Dolby Vision if available.
4. Use a fixed camera position with **locked exposure/white balance** for comparison. Phone pictures are *supporting* evidence, not accurate measurements of brightness or black level.
5. Prefer an instrument for objective gamma, grayscale, deltaE and peak luminance. If absent, explicitly mark visual results subjective.

### Initial preset candidates — *not factory truth or calibration claims*

| Dimension | Baseline / candidate | Caution |
|---|---|---|
| SDR movie | **Cinema** or **Professional** | Compare factory defaults first; avoid Vivid if accuracy matters |
| SDR backlight / picture Brightness | Adjust for room, rather than always max | Sony labels differ; separate from video-signal `Black level` |
| Black level | **Keep default initially** | Reducing until blacks vanish usually crushes shadows |
| Gamma | Default then slight step darker (e.g. −1 **only if control exists**) | Validate dark patch visibility |
| Black Adjust / Advanced Contrast Enhancer | Off then Low and compare | Often changes tone mapping, can crush detail or pump brightness |
| Noise reduction | Off for clean 4K sources; test Low for poor broadcast | Aggressive NR removes fine detail |
| Sharpness / Reality Creation | Default, then moderate adjustments for lower-resolution source | Do not introduce halos or fake detail |
| Motionflow | Off/Cinematic/Custom small values; compare | Avoid soap-opera effect and stutter tradeoffs |
| Dolby Vision | Compare **Dolby Vision Dark** vs **Bright** in corresponding room light | Never judge DV by SDR picture profile |
| Energy savings | Turn off or minimize restrictive mode during peak-brightness testing | Restoring power saving afterward is a choice; power use may rise |
| Light sensor | Off during controlled tests | On may be preferable in real-world variable lighting |
| HDMI range | Match source and display Limited/Full / auto | Mismatch creates gray blacks or crushed shadow |

### Test matrices and scores

Create a CSV/Markdown experiment log with `test_id, source, resolution, HDR_format, room_lighting, original_setting, changed_setting, instrument_or_subjective, dark_detail_1_5, bright_highlights_1_5, color_1_5, artifacts_1_5, result, rollback`.

Measure and test separately: black floor and near-black clipping; perceived bright scenes; highlights/tonemapping; skin tones; grey ramp banding; compression artifact handling; movement; HDMI chroma/range; audio dialogue and bass distortion. Repeat tests after reboot and across internal vs HDMI sources before claiming systemwide effects.

## 7. Brightness research and safe method

- Check **Eco Dashboard / Power Saving** and light-sensor control. Change only from Sony user-facing settings and document power/eye-comfort tradeoffs.
- During daytime HDR tests, select the appropriate HDR/Dolby Vision Bright picture mode where exposed; test native HDR metadata detection, don't force a false HDR signal.
- Do not confuse increasing gamma or clipping highlights with increasing measured luminance. Do not increase TV current, backlight PWM limits, factory EEPROM, voltage, temperature ceiling, or hidden engineering settings.
- No documented, reproducible, safe `K-55S30` brightness-unlock repository was verified. If a future claimed exploit is found, it is **research only** unless Sony endorses it and repeatable, independent measurements demonstrate safety.

## 8. Audio optimization — sound is included

K-55S30: two full-range X-Balanced/bass-reflex speakers with **10 W + 10 W**, Dolby Audio/Atmos and compatible DTS support according to Sony India. **Atmos decoding alone does not equal room-filling height channels.**

1. Record sound baseline with fixed program/volume, TV placement and listening distance. Capture dialogue, music, bass-heavy scenes, quiet/loud transitions and sibilance.
2. Look for user-facing Sound mode, equalizer, surround/3D surround, Dolby processing, dialog/voice enhancement and advanced auto-volume or dynamic range settings. **Do not assume every option exists**; the model specs do not promise `Voice Zoom` or specific EQ bands.
3. Trial Speech/Cinema/Standard modes individually. Dialogue clarity can benefit from restrained midrange changes; aggressive boosted bass may distort small speakers. Log changes and level-match recordings.
4. Check output format/processing when using TV speakers versus eARC soundbar. For multichannel content, correctly configured HDMI eARC/ARC and an external soundbar/subwoofer offers a bigger improvement than unverified on-TV DSP.
5. For Mac playback, test external player EQ/normalization in supported, controlled local content playback; avoid clipping. macOS systemwide DSP may require a separate supported tool/virtual audio driver and careful latency tests.
6. RootlessJamesDSP and JamesDSPManager are **experimental** and not established to work in protected TV apps; do not root, bypass DRM, sideload unknown builds or claim global systemwide processing.

## 9. Agent instructions: paste this section into Claude Code / Codex

### Mission

> Build a reproducible **K-55S30 quality optimization lab** on my **MacBook Pro M3**. Discover, clone/inventory and inspect all linked repositories; **do not blindly run or install all of them onto my Sony TV**. Use safe, model-matched, reversible controls. Run tests one by one, compare with a baseline, keep improvements and revert worse changes. Goal: best accurate image quality, perceived blacks, usable brightness, HDR/Dolby Vision presentation, motion, dialogue intelligibility and sound balance possible on this hardware. My Sony BRAVIA 3 K-55S30 has Android TV OS 12 and its Android model string is BRAVIA BF1. The TV is a Direct LED, Frame Dimming LCD, not OLED and not local-dimming Mini LED.

### Operating rules

1. **Phase 0 / Confirm:** Read current Sony India K-55S30 support, specs and firmware applicability. Check TV firmware version separately from Android security patch/build. Never suggest BF1 model-family OTA as interchangeable.
2. **Phase 1 / Environment:** Detect Apple Silicon macOS; verify Homebrew/git/ADB and approved dependencies. Create `bravia-k55s30-lab/` with `research/`, `repos/`, `config/`, `baselines/`, `tests/`, `reports/`, `backups/`, `scripts/` and `README.md`. Record git commit IDs/licenses. Treat remote scripts as untrusted: review before execution.
3. **Phase 2 / Inventory:** Clone candidate repositories into `repos/` (optionally only shallow clone). Build a compatibility table `repo | purpose | maintenance | licenses | requirements | K-55S30 evidence | applicability | risk`. Some are libraries and desktop tools, not TV apps. Skip unsafe/incompatible installations.
4. **Phase 3 / ADB, read-only first:** Ask me before enabling Android debugging or connecting. Pair using the pairing/debug ports shown on TV, **never assume port 5555**. Collect `adb shell getprop ro.product.model`, `ro.build.fingerprint`, `ro.build.version.release`, and other **non-sensitive** diagnostic settings. Don't dump serial numbers/tokens/private logs. Before any write, explain exact impact and request confirmation.
5. **Phase 4 / Current picture baseline:** Save manual/photographed settings for SDR, HDR, DV, HDMI and TV built-in playback separately. Design objective or repeatable A/B test clips, fixed scene timestamps and room-light conditions. Make baseline `baseline.md` and `tests.csv`.
6. **Phase 5 / User-facing Sony controls:** Adjust **one** picture control at a time in TV menu; log original setting, trial, visual/measured outcome, keep/revert. Compare Cinema vs Professional, gamma, signal black level, power saving, sensor, HDR mode, advanced contrast, motion, Reality Creation and NR. Don't confuse a darker image with better black floor.
7. **Phase 6 / Mac+HDMI pipeline:** Install **mpv** using a package manager; assess libplacebo renderer and inspect actual version/features. Set correct 4K signal format, display refresh/colour range/HDR transfer if supported. Generate profiles for controlled local, legal clips; compare source fidelity and shadow/highlight clipping. External processing ≠ global Android TV processing.
8. **Phase 7 / Calibration:** If a supported colorimeter is available, evaluate ArgyllCMS/DisplayCAL/TV calibration tool and establish grayscale/gamma, colour Delta E, luminance benchmarks and a LUT **applied only in a compatible pipeline**. If no meter, don't fabricate nits/Delta E or call subjective adjustments calibrated.
9. **Phase 8 / Sound:** Measure dialogue intelligibility, loudness consistency and distortion; try TV-supported presets/EQ/dynamic-range one at a time; evaluate external Mac playback EQ. Do not install system-level JamesDSP if root/capture rights or compatibility are missing.
10. **Phase 9 / Iterate:** Repeat top-performing configurations for SDR daylight, SDR night, HDR10, Dolby Vision, gaming and audio. Keep best *per mode* and create a simple preset-switching guide or optional dashboard if Sony actually supports remote settings writes.
11. **Phase 10 / Deliver:** Produce `reports/compatibility.md`, `reports/firmware.md`, `reports/before_after.md`, `reports/final_presets.md`, `reports/audio.md`, `reports/unavailable_features.md`, `reports/rollback.md` and reproducible scripts only where safe.

### Mandatory approvals and stop conditions

- Ask my permission before any TV write, installation on TV, ADB command other than read-only diagnostics, update, or user-account access.
- Never flash unsupported/cross-model firmware, patch signed OTAs, root, change `/system`, alter SELinux, change DRM components, service-menu calibration/backlight drive, or hidden thermal/power limits.
- Never disable Sony critical system packages, HDMI/DRM components, thermal management, security update services or firmware update services.
- Stop and rollback if black patches clip, whites clip, HDR metadata breaks, colours shift unnaturally, audio clips/crackles, AV sync worsens, TV heats abnormally, apps crash or stability declines.
- Any “brightness hack”, “OLED conversion” or universal DRM-stream filter without credible model-specific verification goes under `unavailable_features.md`, not the install queue.

### Starter commands (Mac only; do not run unknown scripts automatically)

```bash
# Mac prerequisites, after user's consent:
brew install git android-platform-tools mpv

# Build a workspace; inspect each GitHub repository before running its code:
mkdir -p bravia-k55s30-lab/{research,repos,config,baselines,tests,reports,backups,scripts}
cd bravia-k55s30-lab

# After TV is set to wireless debugging and shows the relevant addresses:
adb pair TV_IP:PAIRING_PORT
adb connect TV_IP:DEBUG_PORT
adb shell getprop ro.product.model
adb shell getprop ro.build.fingerprint
adb shell getprop ro.build.version.release
```

**Never invent ports or connect until the TV shows its real pairing data.** ADB gets device metadata, not physical peak nits, local-dimming zone count or panel type.

## 10. Acceptance criteria and final realistic expectations

| Category | Target | Evidence required |
|---|---|---|
| SDR near-black | More visible correct dark detail without lifted grey or crushed blacks | Shadow-step chart with fixed viewing |
| Daytime brightness | Highest comfortable available Sony user-mode luminance | Same scene/ambient light, preferably meter |
| HDR / Dolby Vision | Better detail in highlights and shadows with proper format | Controlled HDR/DV clips and correct signal reporting |
| Upscaling | More natural textures, fewer halos | Repeatable same low-resolution source |
| Motion | Smoothest acceptable motion without unnatural interpolation | Sample sports pan and film pan |
| Audio dialogue | Clearer vocals without distortion | Level-matched speech sample |
| Bass/loudness | Most balanced clean playback within speaker hardware | Several volumes, no clipping |
| Stability | No new crashes, broken streaming, overheating or security regression | Multi-day normal usage |

**Not feasible via software:** true OLED black/off pixels, adding local dimming zones, genuine 120 Hz panel, safe bypass of factory backlight power limits, additional loudspeaker drivers or subwoofer output from nonexistent hardware.

## 11. Source integrity notes

- Sony model-specific official pages are authoritative for hardware and supported updates; retail-model name on the rear label overrides a generic Android device string for hardware identification.
- Official India spec reports 50 Hz native refresh; other geographic specification pages may use 60 Hz nomenclature. Do not confuse HDMI input acceptance with native 120 Hz hardware.
- The user-provided September 2026 security patch can be newer than a listed Sony firmware release date. Software-version comparison requires the OTA/PKG version, not only the Android build string.
- Other BF1-series models, including K-43S22M2 and K-55S25M2, are **not** acceptable proxies for K-55S30 firmware or panel measurements.
- No public model-specific verified exploit safely boosting K-55S30 physical LED output beyond Sony specifications was identified. Absence of evidence is not proof that no such claim exists; it is a reason **not to deploy** one.
