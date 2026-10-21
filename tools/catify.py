#!/usr/bin/env python3
"""Turns a photo of your cat into mycat/cat.json, which the apps use to draw her in her colours.

    python3 tools/catify.py mycat/cat.jpg --coat tabby --eyes green --name Mishti

--coat   tabby | solid | tuxedo      (what she is; the photo can't tell reliably)
--eyes   green | gold | blue | copper | hazel, or a hex colour like 9fb24f
--name   her name, for the greeting on the home screen

Needs Pillow:  pip3 install pillow

How it reads the photo: it looks at the middle of the picture (so frame her in the centre, on a
plain background if you can), groups the colours, and takes the main fur colour, the darkest
colour as her stripes, the most orange/warm colour as her patches, and the lightest as her white.
Then it prints the result so you can see if it got her right, and you can edit cat.json by hand.
"""
import argparse
import json
import os
import sys

try:
    from PIL import Image
except ImportError:
    sys.exit("Pillow is needed:  pip3 install pillow")

EYES = {"green": "9fb24f", "gold": "d9a640", "yellow": "d9c040", "blue": "6fa8dc", "copper": "c8742e",
        "hazel": "a89048", "amber": "d99a2b", "grey": "9aa8a0"}


def rgb_to_hsv(r, g, b):
    r, g, b = r / 255, g / 255, b / 255
    mx, mn = max(r, g, b), min(r, g, b)
    d = mx - mn
    if d == 0:
        h = 0
    elif mx == r:
        h = (60 * ((g - b) / d) + 360) % 360
    elif mx == g:
        h = 60 * ((b - r) / d) + 120
    else:
        h = 60 * ((r - g) / d) + 240
    s = 0 if mx == 0 else d / mx
    return h, s, mx


def kmeans(pixels, k=8, rounds=12):
    # Plain k-means on a small sample; good enough for a palette.
    import random
    random.seed(7)
    centres = random.sample(pixels, k)
    for _ in range(rounds):
        groups = [[] for _ in centres]
        for p in pixels:
            i = min(range(k), key=lambda j: sum((p[n] - centres[j][n]) ** 2 for n in range(3)))
            groups[i].append(p)
        centres = [tuple(sum(c[n] for c in g) // len(g) for n in range(3)) if g else centres[i] for i, g in enumerate(groups)]
    return [(c, len(g)) for c, g in zip(centres, groups) if g]


def hexof(c):
    return "%02x%02x%02x" % c


def main():
    ap = argparse.ArgumentParser(description="Make mycat/cat.json from a photo of your cat.")
    ap.add_argument("photo")
    ap.add_argument("--coat", default="tabby", choices=["tabby", "solid", "tuxedo"])
    ap.add_argument("--eyes", default="green")
    ap.add_argument("--name", default="")
    ap.add_argument("--out", default=os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "mycat", "cat.json"))
    a = ap.parse_args()

    im = Image.open(a.photo).convert("RGB")
    w, h = im.size
    # The middle 60% of the picture, where the cat should be.
    im = im.crop((int(w * 0.2), int(h * 0.2), int(w * 0.8), int(h * 0.8))).resize((120, 120))
    pixels = list(im.getdata())
    clusters = kmeans(pixels)
    total = sum(n for _, n in clusters)
    # Drop anything that is probably background: tiny groups.
    clusters = [(c, n) for c, n in clusters if n > total * 0.03]

    by_size = sorted(clusters, key=lambda cn: -cn[1])
    by_value = sorted(clusters, key=lambda cn: rgb_to_hsv(*cn[0])[2])
    fur = by_size[0][0]
    stripe = by_value[0][0]
    white = by_value[-1][0]
    warm = [cn for cn in clusters if 10 <= rgb_to_hsv(*cn[0])[0] <= 45 and rgb_to_hsv(*cn[0])[1] > 0.3]
    patch = max(warm, key=lambda cn: rgb_to_hsv(*cn[0])[1])[0] if warm else fur
    if a.coat == "solid":
        white = fur
        patch = fur
    if rgb_to_hsv(*white)[2] < 0.7:  # no real white in the picture: use a soft white
        white = (243, 238, 229)

    eyes = EYES.get(a.eyes.lower(), a.eyes.lstrip("#"))
    coat = ";".join([a.coat, hexof(fur), hexof(stripe), hexof(patch), hexof(white), eyes])
    out = {"name": a.name, "coat": coat,
           "colours": {"fur": hexof(fur), "stripes": hexof(stripe), "patches": hexof(patch), "white": hexof(white), "eyes": eyes}}
    os.makedirs(os.path.dirname(a.out), exist_ok=True)
    with open(a.out, "w") as f:
        json.dump(out, f, indent=2)
        f.write("\n")
    print("Wrote", a.out)
    print("  coat:    ", a.coat)
    for k, v in out["colours"].items():
        print("  %-8s #%s" % (k + ":", v))
    print("If a colour looks wrong, edit cat.json (any hex colour works), then run install.sh or cat.sh again.")


if __name__ == "__main__":
    main()
