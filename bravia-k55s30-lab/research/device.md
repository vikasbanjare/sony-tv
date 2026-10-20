# The TV: Sony BRAVIA 3 K-55S30

## Identity

| Field | Value | Source |
|---|---|---|
| Retail model | K-55S30 | Owner's photo of the rear label (re-checked 2026-10-20) |
| Market | India: the label carries the BIS mark (IS 616 / IEC 60065) and says "LED TV, 110 V – 240 V". So the "Asia Pacific" firmware on Sony India's page is the right one | Same photo |
| Series | BRAVIA 3 (S30), model year 2024 | Sony India specifications page |
| Android "About" model string | BRAVIA BF1 (software platform name, shared with other models) | Owner's photo of Settings > System > About |
| Android TV OS | 12; security patch 5 Sep 2026; build STT2.230811.001; kernel 5.4.296, built 31 Aug 2026 | Same photo |
| Sony firmware version | Very likely **6120900201** (latest): ADB shows build incremental `612090020`. Confirm on HOME > Help | `adb-diagnostics.sh`, 2026-10-20 |

The Android build ID is not the Sony firmware version. Only the number on the Help screen can be
compared with Sony's downloads page (`reports/firmware.md`).

The serial number and Netflix ESN are left out of this lab on purpose; nothing here needs them.

## Hardware (Sony India specifications page, read 2026-10-20)

| Item | Sony's wording | What it means for tuning |
|---|---|---|
| Panel | 4K LCD, 3840 × 2160 | — |
| Backlight | Direct LED | — |
| Dimming | **Frame Dimming** | The whole backlight dims together. No zones, so no zone-based black improvement is possible |
| Refresh rate | 50 Hz; "Motionflow XR 200 (Native 50 Hz)" | No 100/120 Hz panel. VRR "-" (not supported) |
| HDR | HDR10, HLG, Dolby Vision | Test each format with its own content |
| Picture modes | Vivid, Standard, Cinema, Game, Graphics, Photo, Professional; Dolby Vision Vivid/Bright/Dark/Game | — |
| Processing | 4K HDR Processor X1, 4K X-Reality PRO, TRILUMINOS PRO, Live Colour, Dynamic Contrast Enhancer, Dynamic Backlight Control | — |
| Sensor | Light | — |
| Speakers | Full Range (Bass Reflex) × 2, X-Balanced, 10 W + 10 W | No woofer; deep bass can't be added in software |
| Audio formats | Dolby Audio, Dolby Atmos; DTS Digital Surround, DTS Express, DTS-HD MA, DTS-HD HR, DTS:X | Decoding Atmos is not the same as having height speakers |
| Voice Zoom / Sound modes | "-" | Not listed for this model |
| HDMI | eARC/ARC, ALLM | A soundbar over eARC is the biggest possible sound upgrade |
| CalMAN auto calibration | "-" | No CalMAN auto-cal |
| PS5 features | Auto HDR Tone Mapping, Auto Genre Picture Mode | — |
| Platform | Google TV on Android TV | — |

## Read from this TV over ADB (2026-10-20)

| Item | Value | What it means |
|---|---|---|
| Product name / platform | `BRAVIA_BF1_4K_M_A`, board `merak` (MediaTek) | — |
| HDR types Android reports | 1, 2, 3 = **Dolby Vision, HDR10, HLG**. No HDR10+ | Matches Sony's spec |
| Android's max luminance figure | 100 (avg 50, min 0.05) | A placeholder Android passes to apps, **not** the panel's real brightness. Don't read nits from it |
| Display mode Android sees | 3840 × 2160 at 60 Hz, one mode only | Apps can't ask Android for 50 Hz or 24 Hz; Sony's video path decides that. Motion tests (P16, P17) are where this shows up |
| Menu resolution | Physical 3840 × 2160, menus drawn at 1920 × 1080 | Normal for Android TV and set by the TV itself, not by this repo. Video still plays in 4K. Leave it |
| Audio devices | TV speakers only; no HDMI audio system connected | `encoded_surround_output` is unset (Auto) |
| ARC / eARC port | **HDMI 3** is the only port with ARC | Plug a soundbar or AV receiver into HDMI 3 |
| Thermal status | 0 (normal) | — |
| `animator_duration_scale` | 0.6, although `sony-bravia.txt` sets 0 | Something on the TV puts it back. It only affects menu animations; `check.sh --fix` sets it again |
| REST API (port 80) | **Connection refused**, even with IP control on | No REST API on this TV; see `../reports/compatibility.md` |
| Simple IP control (port 20060) | Answers | Sony's basic network control is on |

## Seen on this TV (owner's `pm list packages -e`, 2026-10-20)

| Package | What it tells us |
|---|---|
| `com.mediatek.*` (tv.agent, system.pqrepository.overlay.config, wwtv.mediaplayer, …) | MediaTek TV chip platform. Picture-quality tables live in Sony/MediaTek system packages, not in user-reachable settings |
| `com.sony.dtv.ipcontrol`, `com.sony.dtv.ipcontrolaux` | Sony's IP control service is installed, so `scripts/bravia-rest-probe.sh` is worth running |
| `com.dolby.android.audio.service` | Dolby audio processing service (Dolby Audio / Atmos decoding) |
| `com.sony.dtv.ecodashboard` | Eco Dashboard (Power saving, light sensor) |
| `com.sony.dtv.sonyservicemode` | Sony's service menu app. **Never open or change it** (see `../reports/unavailable_features.md`) |
| `com.sony.dtv.smarthelp`, `com.sony.dtv.helpguidecontents` | Help screen and manual. Turned off by `sony-bravia.txt`; Help is where the firmware number is shown |

Not published by Sony and not measured here: peak brightness (nits), native contrast, panel type (VA or IPS).
This lab does not guess them.

## Menu items to look for (Sony Help Guide)

The Help Guide (https://helpguide.sony.net/tv/lga2/v1/en-142/index.html, released 20 Nov 2025) covers
**all** models in the family, so an item listed here might be missing on the K-55S30. Phase 4 records which ones exist.

Picture: Quick Settings > Settings > Display & Sound > Picture
- Picture mode, Auto picture mode, Light sensor, Brightness, Colour
- Advanced > Brightness: Brightness, Contrast, Gamma, Black level, Black adjust, Adv. contrast enhancer,
  Black equaliser (Game / DV Game only), Auto local dimming, Peak luminance
- Advanced > Colour: Colour, Hue, Colour temperature, Adv. colour temperature, Live Colour
- Advanced > Clarity: Sharpness, Reality Creation (Manual > Resolution), Random noise reduction, Digital noise reduction
- Advanced > Motion: Motion blur reduction (Game only), Motionflow (Custom > Smoothness, Clearness), Film mode
- Advanced > Video signal: HDR mode, HDMI video range
- Advanced > Reset

"Auto local dimming" and "Peak luminance" appear in the guide, but this model has Frame Dimming. If the
menu shows them, they control the whole-screen backlight, not zones.

Sound: Settings > Display & Sound > Audio
- Sound customisation: Surround, Surround effect, Dialogue enhancer, Equaliser
- Volume level: Volume offset, Dynamic range
- Auto Volume, Balance, TV position, Reset
- Audio output: Speakers, A/V sync, Digital audio out, Digital audio out volume

Network: Settings > Network & Internet
- Remote start; IP control ("Simple IP control"). No Pre-Shared Key setting appears anywhere in the guide.
