# Not possible, or not allowed

Things this lab will not do on the K-55S30, and why. Anything new that promises "brightness unlock",
"OLED blacks" or "system-wide audio" goes on this list until Sony documents it and independent
measurements on this exact model show it is safe.

## Hardware limits (software cannot change them)

| Wish | Why not |
|---|---|
| OLED-style black (pixels switched off) | LCD with one backlight. Frame Dimming dims the whole screen at once |
| Local dimming zones | The panel has none ("Frame Dimming" in Sony's spec). Software cannot add LEDs or zones |
| 100/120 Hz motion, VRR | Native 50 Hz panel; VRR is "-" in the spec |
| More peak brightness than Sony's settings allow | Limited by LED drive current, power supply and heat. The only safe levers are the user menu: Brightness, Power saving off, Peak luminance (if present), light sensor off |
| Deep bass, more loudness | Two 10 W full-range speakers, no woofer. An equaliser can rebalance, not add output. Use eARC with a soundbar for real bass |
| Real Atmos height effects from the TV alone | The TV decodes Atmos but has no up-firing or height speakers |
| CalMAN auto-calibration | Sony lists it as "-" for this model |

## Not allowed (safety contract)

| Approach | Why |
|---|---|
| Service menu white balance, backlight drive, panel or EEPROM edits | Can damage the panel or shorten LED life; no reliable way back. tv-calibration's service-menu steps are for other brands and are skipped |
| Root, unlocked bootloader, `/system` or SELinux changes | Breaks security updates and DRM (Netflix/Prime drop to low resolution or stop working) |
| Cross-model or downgraded firmware, patched OTAs | Can brick the TV. Only the Sony-listed update for K-55S30 through the TV's own updater (`firmware.md`) |
| Turning off thermal, power, DRM, HDMI, update or other core Sony packages | The TV relies on them. The repo's `cleanup/sony-bravia.txt` only turns off promos and demos |
| JamesDSPManager | Needs root (Magisk) |
| RootlessJamesDSP | Not prohibited by itself, but it can't reach apps that block audio capture (streaming apps), adds latency (lip sync), and its workaround patches apps to remove capture restrictions, which is off limits. Not installed |
| Forcing HDR on SDR content, or a fake HDR signal | Wrong tone curve; looks brighter but loses detail and colour accuracy |
| Applying a Mac LUT or ICC profile "to the TV" | It only affects what the Mac plays. The TV's own apps never see it |

## Claimed, not found

No public, reproducible, model-specific method to raise K-55S30 LED output beyond Sony's settings was found
(as of 2026-10-20). Not finding one is not proof none exists; it is the reason not to try one.
