# Baseline

Fill this in **before changing anything**, after any firmware update. Take a photo of every menu page too and keep
the photos in `backups/` (not committed). Write the value exactly as the menu shows it. If an item isn't in the menu, write "not present".
That is useful too: it tells us which Help Guide features this model has.

Date: ____  Sony firmware (HOME > Help): ____  Room: day / evening / dark

## Picture: one column per input you use

| Setting | Netflix / YouTube app | HDMI 1 (____) | HDMI 2 (____) | Dolby Vision content |
|---|---|---|---|---|
| Picture mode | | | | |
| Auto picture mode | | | | |
| Light sensor | | | | |
| Brightness | | | | |
| Contrast | | | | |
| Gamma | | | | |
| Black level | | | | |
| Black adjust | | | | |
| Adv. contrast enhancer | | | | |
| Auto local dimming (present?) | | | | |
| Peak luminance (present?) | | | | |
| Colour | | | | |
| Hue | | | | |
| Colour temperature | | | | |
| Live Colour | | | | |
| Sharpness | | | | |
| Reality Creation | | | | |
| Random noise reduction | | | | |
| Digital noise reduction | | | | |
| Motionflow | | | | |
| Film mode | | | | |
| HDR mode | | | | |
| HDMI video range | | | | |

## Power and system

| Setting | Value |
|---|---|
| Power saving / Eco | |
| HDMI signal format (Settings > Channels & Inputs > External inputs) per port | |
| USB / Wireless debugging | |
| IP control | |

## Sound

| Setting | Value |
|---|---|
| Surround / Surround effect | |
| Dialogue enhancer | |
| Equaliser | |
| Volume offset | |
| Dynamic range | |
| Auto Volume | |
| Balance | |
| TV position | |
| Speakers (TV / audio system) | |
| Digital audio out | |
| A/V sync | |

## Machine-read facts

`sh scripts/adb-diagnostics.sh <TV_IP>:<PORT>` and `sh scripts/bravia-rest-probe.sh <TV_IP>` save their output next to
this file. Read them before sharing.

## Baseline scores (same scoring as tests/README.md)

| Clip | Room | dark 1–5 | bright 1–5 | colour 1–5 | artifacts 1–5 | Notes (e.g. "bar 17 invisible") |
|---|---|---|---|---|---|---|
| sdr-black-clipping | dark | | | | | |
| sdr-white-clipping | evening | | | | | |
| sdr-gray-steps | evening | | | | | |
| sdr-motion-pan-24p | evening | | | | | |
| hdr10-pq-steps | dark | | | | | |
| a film scene you know well | evening | | | | | |
| a bright daylight scene | day | | | | | |
