# The TV: Sony BRAVIA 3 K-55S30

## Identity

| Field | Value | Source |
|---|---|---|
| Retail model | K-55S30 | Owner's photo of the rear label |
| Series | BRAVIA 3 (S30), model year 2024 | Sony India specifications page |
| Android "About" model string | BRAVIA BF1 (software platform name, shared with other models) | Owner's screenshot |
| Android TV OS | 12; security patch 5 Sep 2026; build STT2.230811.001; kernel 5.4.296 | Owner's screenshot |
| Sony firmware version | **Not yet read.** HOME > Help: the number under the model name | To do in Phase 0 |

The Android build ID is not the Sony firmware version. Only the number on the Help screen can be
compared with Sony's downloads page (`reports/firmware.md`).

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
