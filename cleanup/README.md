# Clean-up lists

One list per TV brand of apps that are safe to turn off: promos, demos, help apps, things that watch what you watch. Nothing the TV needs to start.

```sh
sh check.sh <TV_IP> --fix cleanup/sony-bravia.txt
```

Read the list first and delete any line for something you use. Apps are turned off, never deleted. Turn one back on with `adb shell pm enable <package>`.

| List | For |
|---|---|
| [`sony-bravia.txt`](sony-bravia.txt) | Sony BRAVIA (Google TV). Tested on a BRAVIA 3 K-55S30. |
| [`google-tv.txt`](google-tv.txt) | Google's own extras on any Google TV. |

## Make one for your TV

```sh
adb shell pm list packages -e | sort
```

Anything with `promo`, `demo`, `sponsor`, `recommend`, `ads`, `samba`, `feedback` or `games` in it is usually safe. Leave alone: `com.google.android.gms`, `com.android.vending`, settings, systemui, setup apps, the keyboard, Bluetooth, HDMI/tuner (`tvinput`), anything with `framework`, `service` or `provider` you don't understand. If in doubt, leave it on.

Put one `off <package>` per line in a text file and run `check.sh --fix` with it. Please send the list back as a pull request so others get it.
