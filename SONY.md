# TV Detox on a Sony BRAVIA

This repo is a copy of [Ansub/tv-detox](https://github.com/Ansub/tv-detox) (MIT, upstream commit `c8d555d`).
Everything below runs **from your laptop on the same Wi-Fi as the TV**. A cloud session can't reach your TV.

## 1. Prepare the TV

1. **Settings → System → About** (on older models: **Device Preferences → About**). Press OK on **Build** 7 times.
2. Go back to **Developer options** and turn on **USB debugging** (and **Network debugging** if it's there).
3. Find the TV's IP address under **Settings → Network & Internet**.

## 2. Prepare the laptop

```sh
# Mac
brew install android-platform-tools
# Windows: download SDK Platform Tools from developer.android.com, run the commands from Git Bash

git clone https://github.com/vikasbanjare/sony-tv.git
cd sony-tv
```

## 3. Install the home screen

```sh
sh install.sh <TV_IP>
```

The first time, the TV asks **"Allow debugging?"**. Tick "Always allow", press **Allow**, then run the command again.

On BRAVIA Google TVs, Home can open Google TV's **setup app** (`com.google.android.tungsten.setupwraith`) once
Google TV's own home screen is off. `install.sh` detects this and asks before turning the setup app off.
To say yes without being asked:

```sh
YES_SETUP=1 sh install.sh <TV_IP>
```

Pick your own apps or add settings shortcuts next to the clock:

```sh
sh install.sh <TV_IP> "com.google.android.youtube.tv,com.netflix.ninja"
sh install.sh <TV_IP> "" "bluetooth,sound,picture"
```

## The living home screen

This repo's build (`release/tvhome.apk`, installed by `install.sh`) adds, with no animations and no extra memory to speak of:

- **The background takes the colour of the app you're on**: red for Netflix, green for Spotify.
- **A sky that follows the real sun**: sunrise glow, daytime blue, sunset colours, stars at night, with soft hills on the horizon.
- **Tonight's moon**, in its real phase.
- **Live weather** once your city is set: clouds, rain, fog or snow in the sky, and "☂ Light rain · 24°" under the clock (from Open-Meteo, free, checked every 30 minutes).
- **A greeting**, a big thin clock, and the next sunrise or sunset (or the moon) under it.
- **Festival days**: Diwali lamps, Holi colours, Independence and Republic Day, Christmas snow, New Year, with a countdown in the month before.
- **It learns your habits.** Come back after a while and the cursor is already on the app you usually open at that time of day.

Set your name, and your city for the weather and the sun (latitude,longitude; this one is Mumbai):

```sh
adb shell am start -n com.ansub.tvhome/.MainActivity --es name Vikas --es place 19.07,72.88 --es stamp $(date +%s)
```

## Speed it up

[`sony-bravia.txt`](sony-bravia.txt) turns off Sony's promos, demos and other extras, and turns off animations.
Read it first and delete any line for an app you use, then:

```sh
sh check.sh <TV_IP> --fix sony-bravia.txt
```

Run the same command again whenever a Sony update turns something back on.

If holding a remote button only moves one step, check **Settings → Accessibility → Switch Access** is off.

## Keep it working / undo

```sh
sh check.sh <TV_IP>          # is the home screen still in charge after an update?
sh check.sh <TV_IP> --fix    # fix it
sh undo.sh <TV_IP>           # put the Sony home screen back and remove this one
```

Do the full cleanup (removing bloatware, animations, sleep timer) with Claude Code running **on your laptop**,
using the prompt in [README.md](README.md#get-started).
