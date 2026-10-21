# Your cat

Put a photo of your cat here as `cat.jpg` (frame her in the middle, on a plain background if you can), then:

```sh
pip3 install pillow
python3 tools/catify.py mycat/cat.jpg --coat tabby --eyes green --name Mishti
```

- `--coat` is what she is: `tabby` (stripes), `solid` (one colour: black, grey, ginger, white), or `tuxedo` (dark with a white chest and paws).
- `--eyes` is `green`, `gold`, `blue`, `copper`, `hazel`, or any hex colour.
- `--name` goes in the greeting on the home screen.

It writes `mycat/cat.json` with her colours and prints them. If one looks wrong, edit the file (any hex colour works).

Then run `sh install.sh <TV_IP>` or `sh cat.sh <TV_IP>` again: both read `cat.json` and send her colours to the TV.

`cat.jpg` and `cat.json` are yours; git ignores them, so they stay out of the repo when you share it.
