<div align="center">

# Pet Cat

### Your cat, living on your TV.

She sits on the clock, naps on the apps, hangs off the 4 and jumps down to whatever you pick.
Drawn from a photo of *your* cat. Works on any Android TV or Google TV.

<img src="docs/pet-cat.gif" alt="The cat on the home screen" width="100%">

</div>

<table>
<tr>
<td width="33%" valign="top"><b>🐈 Just the cat</b><br>Keep your TV exactly as it is. She walks along the bottom of your home screen. One command on, one command off.</td>
<td width="33%" valign="top"><b>🕰️ Or the whole home screen</b><br>A big glowing clock she lives on, a sky that follows the real sun and weather, your apps, nothing else.</td>
<td width="33%" valign="top"><b>📸 Your own cat</b><br>Drop in a photo and she's drawn in your cat's colours and pattern, with her name in the greeting.</td>
</tr>
</table>

Everything is drawn in code, no images and no video, by one small thread that stops the moment you open an app. Your remote stays as quick as before.

## What you need

- An Android TV or Google TV (Sony, TCL, Hisense, Philips, Xiaomi, Chromecast with Google TV, Nvidia Shield…). Not Fire TV, Roku, Samsung or LG.
- A laptop on the same Wi-Fi, with `adb`:
  - Mac: `brew install android-platform-tools`
  - Windows: [SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools), then use Git Bash.
- Developer mode on the TV: **Settings → System → About** (or **Device Preferences → About**), press OK on **Build** 7 times, then turn on **USB debugging** in **Developer options**. The TV's IP address is under **Settings → Network & Internet**.

```sh
git clone https://github.com/vikasbanjare/pet-cat.git
cd pet-cat
```

The first time, the TV asks **"Allow debugging?"**. Tick "Always allow", press **Allow**, and run the command again.

## 1. Just the cat

```sh
sh cat.sh <TV_IP>
```

Press Home. She's along the bottom of your home screen: walking, sitting, grooming, yawning, napping. She shows only while the home screen is in front and never over an app or a film.

```sh
sh cat.sh <TV_IP> off       # put her away for now
sh cat.sh <TV_IP> on        # and back
sh cat.sh <TV_IP> remove    # uninstall
```

## 2. The whole home screen

```sh
sh install.sh <TV_IP>
```

Say **y** when it asks to turn off the TV's own home screen (it's only turned off, never deleted; `undo.sh` brings it back).

What you get:

- **The clock she lives on.** She reads the shape of each digit: she balances on the point of a 1, drapes over a 0 or an 8 with her legs hanging down, hangs from the slope of a 4 by one paw, lies along the bar of a 5 or 7. When the minute changes under her, she's on the new digit.
- **She jumps down to your apps** and hops along them, and comes to the one you move to.
- **A sky that follows the real sun**, the moon in its real phase, and live weather once you set your city. Festival days get lamps, colours or snow.
- **The background takes the colour of the app you're on.** So does the light on her back.
- **Your apps, your HDMI inputs, Settings.** Hold OK on an app to move, replace or remove it.

Your name and city (latitude,longitude; this is Mumbai):

```sh
adb shell am start -n com.ansub.tvhome/.MainActivity --es name Vikas --es place 19.07,72.88 --es stamp $(date +%s)
```

Pick the apps yourself, or add settings shortcuts next to the clock:

```sh
sh install.sh <TV_IP> "com.google.android.youtube.tv,com.netflix.ninja"
sh install.sh <TV_IP> "" "bluetooth,sound,picture"
```

```sh
sh check.sh <TV_IP>          # is the home screen still in charge after a TV update?
sh check.sh <TV_IP> --fix    # fix it
sh undo.sh <TV_IP>           # put the TV's own home screen back and remove this one
```

## 3. Make her your cat

Put a photo of your cat at `mycat/cat.jpg` (her in the middle, plain background if you can), then:

```sh
pip3 install pillow
python3 tools/catify.py mycat/cat.jpg --coat tabby --eyes green --name Mishti
```

- `--coat`: `tabby` (stripes), `solid` (black, grey, ginger, white…), or `tuxedo` (dark with a white chest and paws).
- `--eyes`: `green`, `gold`, `blue`, `copper`, `hazel`, or a hex colour.

It writes `mycat/cat.json` with her fur, stripe, patch, white and eye colours, and shows them. Not quite right? Edit the file. Then run `cat.sh` or `install.sh` again and she changes. More on this in [mycat/README.md](mycat/README.md).

## 4. Skip intros, recaps and credits

```sh
sh skip.sh <TV_IP>
```

From then on, when an app shows **Skip Intro**, **Skip Recap**, **Skip Credits** or **Next Episode**, it's pressed for you the moment it appears. It watches the screen's buttons through Android's accessibility layer, so it costs nothing while you watch and never touches the remote.

Two ways, side by side. In apps that show their buttons to Android (Prime Video, Hotstar, SonyLIV, Zee5, Crunchyroll…) it presses the button directly. Netflix, Apple TV and YouTube draw their own screens and show nothing, so for those it uses the TV's own adb (*Network debugging*, the same thing your laptop uses): after the install, the TV asks **"Allow debugging?"** for Skip itself. Tick *Always allow* and press **Allow**, once; it's remembered across restarts. From then on, while one of those apps is playing, it takes a tiny screenshot once a second, spots the highlighted Skip pill in the bottom-right corner, and presses OK.

```sh
sh skip.sh <TV_IP> status     # is the pixel-watcher running, and what did it press lately?
sh skip.sh <TV_IP> snap       # save what it sees right now, to check where it's looking
sh skip.sh <TV_IP> check      # with a Skip button on screen: does this app show its buttons?
```

```sh
sh skip.sh <TV_IP> next off   # only intros, recaps and credits; leave "Next Episode" to you
sh skip.sh <TV_IP> off        # pause it
sh skip.sh <TV_IP> remove     # uninstall
```

## 5. Clean up the TV (optional)

Most TVs come with promos, demos, "help" apps and things that watch what you watch. [`cleanup/`](cleanup) has lists of what's safe to turn off per brand. Read the list, delete any line for something you use, then:

```sh
sh check.sh <TV_IP> --fix cleanup/sony-bravia.txt
```

Run it again whenever a TV update turns something back on. Apps are turned off, never deleted: `adb shell pm enable <package>` turns one back on. Sony BRAVIA owners: see [docs/sony-bravia.md](docs/sony-bravia.md) for the full walkthrough, including the remote and speed fixes.

## Build it yourself

`build.sh` builds all three apps with plain `aapt2`, `javac`, `d8` and `apksigner`, no Gradle. Needs Java 17 and the Android SDK (platform 34, build-tools 34). `release/` has ready-made builds.

## Help

<details>
<summary><b>She isn't showing up</b></summary>
<br>

- Press Home on the remote; she only appears on the home screen.
- For "just the cat": run `sh cat.sh <TV_IP>` again. It grants the two permissions she needs (drawing over the home screen, and knowing which app is in front) with adb, because TVs have no settings page for them.
- Restart the TV once.

</details>

<details>
<summary><b>How do I turn on developer mode?</b></summary>
<br>

1. On the TV, open **Settings**, then **System** or **Device Preferences**, then **About**.
2. Press OK on **Build** 7 times until you see "You are now a developer."
3. Go back, open **Developer options** and turn on **USB debugging**. If you see **Network debugging** or **Wireless debugging**, turn that on too.
4. The first time your laptop connects, the TV asks "Allow debugging?" Tick "Always allow" and press **Allow**.

</details>

<details>
<summary><b>Does it slow the TV down?</b></summary>
<br>

No. She's drawn on her own layer by one low-priority thread at 25 frames a second, and that thread stops when you leave the home screen. The home screen itself never redraws for her. The whole thing uses about 20 MB.

</details>

<details>
<summary><b>The TV's IP address keeps changing</b></summary>
<br>

Give the TV a fixed address in your router's settings (look for **DHCP reservation** or **address binding**). The TV's MAC address is under **Settings → Network & Internet → (your network)**.

</details>

> [!NOTE]
> This is what works on our TVs, not professional advice. Use it at your own risk; you're responsible for your own TV.

## Credits

The home screen started from [Ansub/tv-detox](https://github.com/Ansub/tv-detox) (MIT). The cat is drawn after Vikas's cat. See [NOTICE.md](NOTICE.md).
