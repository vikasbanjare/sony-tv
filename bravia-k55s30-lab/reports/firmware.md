# Firmware

Status 2026-10-20: **Sony now lists firmware 6120900201 for the K-55S30.** This corrects section 3 of the
original plan, which said the page listed v6120800301 and that 6120900201 was for other BF1 models.

| | |
|---|---|
| Latest on Sony India's K-55S30 page | **6120900201**, released 07/10/2026, "for Asia Pacific" |
| K-55S30 named as applicable | **Yes**, with the other S30 sizes (K-43/50/55/65/75/85S30, and B variants) |
| Earlier version named in the plan | 6120800301 (8 Apr 2026). No longer on the page |
| What it fixes (Sony's words) | "Improves performance and stability of overall UI operation", "Improves general TV performance". Earlier fixes: IPTV audio mute, Zoom app, IPTV stability, AirPlay features |
| Picture or sound changes listed | None |
| Installed version on this TV | **Very likely 6120900201 (the latest).** ADB on 2026-10-20: `ro.build.version.incremental = 612090020`, which is 6120900201 without its last digit. Confirm on HOME > Help |

The plan's rule was: don't use 6120900201 "unless Sony explicitly adds this model to its supported list".
Sony has now added it, so the condition is met for an Asia Pacific (India) TV.

## What to do

1. HOME > **Help**. Note the number under the model name in `baselines/baseline.md`.
   If you applied `sony-bravia.txt`, Help is turned off (`com.sony.dtv.smarthelp`). Turn it on just to look,
   then off again:
   ```sh
   adb shell pm enable com.sony.dtv.smarthelp
   # read the number on HOME > Help, then:
   sh check.sh <TV_IP> --fix sony-bravia.txt     # from the repo root; turns it off again
   ```
   The ADB diagnostics also save `ro.build.version.incremental` and the build fingerprint; on some Sony TVs the
   firmware number appears there, but only the Help screen is Sony's documented place.
2. If it is **6120900201 or higher**: nothing to do.
3. If lower: Settings > System > About > **System software update** and let the TV update itself.
   Keep it plugged in and don't turn it off until it restarts on its own.
4. Only if the TV's own updater finds nothing: follow the USB steps on Sony's
   [firmware page](https://www.sony.co.in/electronics/support/lcd-tvs-android-bravia-3-s30-/k-55s30/software/00357875) exactly.
5. Take the baseline photos (Phase 4) **after** updating, so before/after comparisons don't mix firmware versions.
6. Afterwards: `sh check.sh <TV_IP>` from the repo root, since an update can turn the TV Detox home screen off.

## Rules

- No downgrade, cross-flash, patched or unsigned packages, ever. There is no supported way back.
- The Android security patch date (Sep 2026) says nothing about the Sony version; compare the Help number only.
- Firmware notes don't mention picture or sound changes, so don't expect the update alone to change the image.
