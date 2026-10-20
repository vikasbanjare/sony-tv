# Rollback

How to put everything back. The baseline (`baselines/baseline.md` and its photos) is the list of original values.

| What changed | How to undo |
|---|---|
| One picture setting | Set it back to the value in `baselines/baseline.md` (the `rollback` column of `tests/tests.csv` says which) |
| All picture settings of a mode | Settings > Display & Sound > Picture > Advanced settings > **Reset**. It resets the picture mode you're in; check other inputs separately |
| Sound settings | Settings > Display & Sound > Audio > **Reset** |
| Light sensor, Power saving | Set back to the baseline values |
| ADB access | Developer options > USB debugging (or Wireless debugging) > Off. Turn it back on when you next run `check.sh` or `install.sh` |
| IP control (if turned on for the REST probe) | Settings > Network & Internet > IP control > Off |
| Mac playback | Stop using `--config-dir=config/mpv`; nothing on the Mac is changed outside this folder |
| Mac display output | System Settings > Displays > back to the previous resolution, refresh rate and HDR toggle |
| Firmware update | **No rollback.** Sony offers no downgrade. That is why only the Sony-listed K-55S30 version is installed (`firmware.md`) |
| TV Detox home screen and speed-up changes (rest of this repo) | `sh undo.sh <TV_IP>` from the repo root; apps are re-enabled with `adb shell pm enable <package>` |

The lab's scripts change nothing on the TV: `adb-diagnostics.sh` and `bravia-rest-probe.sh` only read,
`make-patterns.sh`, `clone-repos.sh`, `env-check.sh` and `log-experiment.sh` only touch files on the Mac.

Last resort, loses all settings and apps: Settings > System > About > Reset > Factory reset.
