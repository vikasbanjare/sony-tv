package com.ansub.tvhome;

import java.util.Locale;

// What a cat looks like: her coat pattern and colours. Made from a photo by tools/catify.py, which
// writes the spec string this parses, e.g.  tabby;8f7f6c;342c26;c9905b;f3eee5;9fb24f
// (pattern; fur; stripes; patches; white; eyes, colours as hex without #). Anything missing gets
// the default, which is Vikas's cat.
final class Coat {
    static final int TABBY = 0, SOLID = 1, TUXEDO = 2;

    final int pattern;
    final int fur, furLight, furShade, stripe, patch, patchLight, white, eye, eyeLight, nose, ear;
    final boolean bands, patches, bib, socks;

    static final Coat DEFAULT = new Coat(TABBY, 0xFF8F7F6C, 0xFF342C26, 0xFFC9905B, 0xFFF3EEE5, 0xFF9FB24F);

    Coat(int pattern, int fur, int stripe, int patch, int white, int eye) {
        this.pattern = pattern;
        this.fur = fur;
        this.stripe = stripe;
        this.patch = patch;
        this.white = white;
        this.eye = eye;
        furLight = Alive.mix(fur, 0xFFFFFFFF, 0.3f);
        furShade = Alive.mix(fur, 0xFF201A16, 0.38f);
        patchLight = Alive.mix(patch, 0xFFFFFFFF, 0.3f);
        eyeLight = Alive.mix(eye, 0xFFFFFFCC, 0.45f);
        // A dark cat has a dark nose; a light one has a pink nose and pink in her ears.
        float light = luma(fur);
        nose = Alive.mix(0xFFD48D8A, 0xFF3A2F2C, Math.max(0, 0.6f - light) * 1.6f);
        ear = Alive.mix(0xFFD9A3A0, fur, Math.max(0, 0.5f - light));
        bands = pattern == TABBY;
        patches = pattern == TABBY && !same(patch, fur);
        bib = pattern != SOLID;
        socks = pattern != SOLID;
    }

    static Coat parse(String spec) {
        if (spec == null || spec.trim().isEmpty()) return DEFAULT;
        String[] p = spec.trim().split("[;,\\s]+");
        int pattern = TABBY;
        String kind = p[0].toLowerCase(Locale.ROOT);
        if (kind.startsWith("sol") || kind.startsWith("blac") || kind.startsWith("grey") || kind.startsWith("gray") || kind.startsWith("gin")) pattern = SOLID;
        if (kind.startsWith("tux")) pattern = TUXEDO;
        Coat d = DEFAULT;
        int fur = hex(p, 1, d.fur), stripe = hex(p, 2, d.stripe), patch = hex(p, 3, d.patch), white = hex(p, 4, d.white), eye = hex(p, 5, d.eye);
        if (pattern == TUXEDO && luma(fur) > 0.45f) fur = Alive.mix(fur, 0xFF17151A, 0.6f); // a tuxedo is a dark coat with white
        return new Coat(pattern, fur, stripe, patch, white, eye);
    }

    private static int hex(String[] p, int i, int fallback) {
        if (i >= p.length || p[i].isEmpty()) return fallback;
        try { return 0xFF000000 | (int) Long.parseLong(p[i].replace("#", ""), 16); } catch (NumberFormatException e) { return fallback; }
    }

    private static boolean same(int a, int b) {
        return Math.abs(((a >> 16) & 255) - ((b >> 16) & 255)) + Math.abs(((a >> 8) & 255) - ((b >> 8) & 255)) + Math.abs((a & 255) - (b & 255)) < 60;
    }

    static float luma(int c) {
        return (0.299f * ((c >> 16) & 255) + 0.587f * ((c >> 8) & 255) + 0.114f * (c & 255)) / 255f;
    }
}
