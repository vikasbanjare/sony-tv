package com.ansub.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;

// What makes the home screen feel alive, without any animation: a sky that follows the real sun,
// a greeting, festival days, and first focus on the app usually opened at this time of day.
final class Alive {
    private Alive() {}

    // Sky keyframes by the sun's height in degrees: top, middle and bottom of the screen, a glow
    // (color and center), and how bright the stars are. Everything stays dark so white text reads.
    private static final double[] ELEVATION = {-18, -6, 0, 8, 25};
    private static final int[][] SKY = {
            // top         middle      bottom      glow        stars
            {0xFF060914, 0xFF04060D, 0xFF020308, 0x2A3A4C9C, 255}, // night
            {0xFF0B0F26, 0xFF1A1033, 0xFF2A1426, 0x40FF6A3D, 120}, // twilight
            {0xFF1A1838, 0xFF3A1A3A, 0xFF5A2A2A, 0x66FF8A4C, 0},   // sunrise, sunset
            {0xFF1B2A4A, 0xFF2A2440, 0xFF4A2E2A, 0x50FFB060, 0},   // golden hour
            {0xFF0F2B45, 0xFF0C1F33, 0xFF08131F, 0x3070C8FF, 0},   // day
    };
    // Glow centers as fractions of the screen: on the horizon at sunrise and sunset, high up by day.
    private static final float[][] GLOW_AT = {{0.85f, -0.1f}, {0.5f, 1.15f}, {0.5f, 1.1f}, {0.5f, 1.0f}, {0.1f, -0.12f}};

    // Festival days: name, then month (1-12) and day of each date it falls on. Fixed-date ones list
    // one date for every year; Diwali and Holi follow the moon, so each year is listed.
    private static final String[][] FESTIVALS = {
            {"New Year", "1/1"},
            {"Republic Day", "1/26"},
            {"Independence Day", "8/15"},
            {"Christmas", "12/24", "12/25"},
            {"Diwali", "2026/11/7", "2026/11/8", "2026/11/9", "2027/10/28", "2027/10/29", "2027/10/30",
                    "2028/10/16", "2028/10/17", "2028/10/18", "2029/11/4", "2029/11/5", "2029/11/6",
                    "2030/10/25", "2030/10/26", "2030/10/27"},
            {"Holi", "2027/3/21", "2027/3/22", "2028/3/10", "2028/3/11", "2029/2/27", "2029/2/28",
                    "2029/3/1", "2030/3/19", "2030/3/20"},
    };

    static String festival(Calendar c) {
        int y = c.get(Calendar.YEAR), m = c.get(Calendar.MONTH) + 1, d = c.get(Calendar.DAY_OF_MONTH);
        String md = m + "/" + d, ymd = y + "/" + md;
        for (String[] f : FESTIVALS)
            for (int i = 1; i < f.length; i++) if (f[i].equals(md) || f[i].equals(ymd)) return f[0];
        return null;
    }

    static String greeting(Calendar c, String festival, String name) {
        String hello;
        if ("Christmas".equals(festival)) hello = "Merry Christmas";
        else if (festival != null) hello = "Happy " + festival;
        else {
            int h = c.get(Calendar.HOUR_OF_DAY);
            hello = h >= 5 && h < 12 ? "Good morning" : h >= 12 && h < 17 ? "Good afternoon"
                    : h >= 17 && h < 23 ? "Good evening" : "Up late";
        }
        return name == null || name.isEmpty() ? hello : hello + ", " + name;
    }

    // Where the TV is, for the sun: saved with --es place "lat,lon", else a guess from the time zone.
    static double[] place(SharedPreferences prefs) {
        String saved = prefs.getString("place", null);
        if (saved != null) try {
            String[] p = saved.split(",");
            return new double[] {Double.parseDouble(p[0].trim()), Double.parseDouble(p[1].trim())};
        } catch (Exception ignored) {}
        TimeZone tz = TimeZone.getDefault();
        if (tz.getID().equals("Asia/Kolkata") || tz.getID().equals("Asia/Calcutta")) return new double[] {22.0, 78.0};
        return new double[] {30.0, tz.getRawOffset() / 3600000.0 * 15};
    }

    // The sun's height above the horizon in degrees (a standard low-precision formula, good to a
    // fraction of a degree, which is plenty for picking colors).
    static double sunElevation(long millis, double lat, double lon) {
        double d = millis / 86400000.0 - 10957.5; // days since noon UTC on 1 January 2000
        double g = Math.toRadians(357.529 + 0.98560028 * d);
        double q = 280.459 + 0.98564736 * d;
        double l = Math.toRadians(q + 1.915 * Math.sin(g) + 0.020 * Math.sin(2 * g));
        double e = Math.toRadians(23.439 - 0.00000036 * d);
        double ra = Math.atan2(Math.cos(e) * Math.sin(l), Math.cos(l));
        double dec = Math.asin(Math.sin(e) * Math.sin(l));
        double gmst = 18.697374558 + 24.06570982441908 * d; // hours
        double ha = Math.toRadians(gmst * 15 + lon) - ra;
        double la = Math.toRadians(lat);
        return Math.toDegrees(Math.asin(Math.sin(la) * Math.sin(dec) + Math.cos(la) * Math.cos(dec) * Math.cos(ha)));
    }

    // The sky for this sun height, plus festival colors on top. radius is the glow size in pixels.
    static Drawable sky(double elevation, boolean morning, String festival, int radius) {
        int k = 0;
        while (k < ELEVATION.length - 2 && elevation > ELEVATION[k + 1]) k++;
        float t = (float) ((elevation - ELEVATION[k]) / (ELEVATION[k + 1] - ELEVATION[k]));
        t = Math.max(0f, Math.min(1f, t));
        int[] a = SKY[k], b = SKY[k + 1];
        float gx = lerp(GLOW_AT[k][0], GLOW_AT[k + 1][0], t), gy = lerp(GLOW_AT[k][1], GLOW_AT[k + 1][1], t);
        // The sun comes up on one side and goes down on the other.
        if (elevation < 10 && !morning) gx = 1f - gx;

        List<Drawable> layers = new ArrayList<>();
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {mix(a[0], b[0], t), mix(a[1], b[1], t), mix(a[2], b[2], t)});
        layers.add(base);
        layers.add(radial(mix(a[3], b[3], t), gx, gy, radius));
        layers.add(radial(0x1E6A3CAA, 1.0f, 1.1f, radius * 4 / 5)); // a little plum for depth
        int stars = Math.round(lerp(a[4], b[4], t));
        if (stars > 8) layers.add(new Sparkles(0xFFFFFFFF, stars * 150 / 255, 140, 7, 0f));

        if ("Diwali".equals(festival)) {
            layers.add(radial(0x55FFB347, 0.15f, 1.05f, radius));
            layers.add(radial(0x55FF8C2E, 0.85f, 1.05f, radius));
            layers.add(new Sparkles(0xFFFFC56B, 230, 90, 2, 0.55f)); // rows of little lamps low down
        } else if ("Holi".equals(festival)) {
            layers.add(radial(0x45FF3CAC, 0.1f, 0.1f, radius));
            layers.add(radial(0x4000D4FF, 0.9f, 0.2f, radius));
            layers.add(radial(0x40FFD400, 0.3f, 1.0f, radius));
            layers.add(radial(0x4030E070, 0.95f, 0.95f, radius));
        } else if ("Independence Day".equals(festival) || "Republic Day".equals(festival)) {
            layers.add(radial(0x50FF9933, 0.0f, -0.05f, radius));
            layers.add(radial(0x20FFFFFF, 0.5f, 0.5f, radius * 3 / 5));
            layers.add(radial(0x50138808, 1.0f, 1.05f, radius));
        } else if ("Christmas".equals(festival)) {
            layers.add(radial(0x40D42A2A, 0.05f, 0.0f, radius));
            layers.add(radial(0x40107A3A, 0.95f, 1.0f, radius));
            layers.add(new Sparkles(0xFFFFFFFF, 200, 160, 11, 0f)); // snow
        } else if ("New Year".equals(festival)) {
            layers.add(radial(0x40A040FF, 0.9f, 0.0f, radius));
            layers.add(new Sparkles(0xFFFFD27A, 230, 120, 13, 0f));
        }
        for (Drawable d : layers) if (d instanceof GradientDrawable) ((GradientDrawable) d).setDither(true);
        return new LayerDrawable(layers.toArray(new Drawable[0]));
    }

    private static GradientDrawable radial(int color, float cx, float cy, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setColors(new int[] {color, color & 0x00FFFFFF});
        g.setGradientCenter(cx, cy);
        g.setGradientRadius(r);
        return g;
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private static int mix(int a, int b, float t) {
        int out = 0;
        for (int s = 0; s < 32; s += 8)
            out |= Math.round(lerp((a >>> s) & 0xFF, (b >>> s) & 0xFF, t)) << s;
        return out;
    }

    // Small dots in fixed places (the same seed always gives the same sky): stars, lamps or snow.
    // top is the fraction of the screen they start below. Drawn only when the window redraws.
    private static final class Sparkles extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int count, seed, alpha;
        private final float top;

        Sparkles(int color, int alpha, int count, int seed, float top) {
            paint.setColor(color);
            this.alpha = alpha;
            this.count = count;
            this.seed = seed;
            this.top = top;
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            Random r = new Random(seed);
            float unit = b.width() / 1920f;
            for (int i = 0; i < count; i++) {
                float x = b.left + r.nextFloat() * b.width();
                float y = b.top + (top + r.nextFloat() * (1 - top)) * b.height();
                paint.setAlpha(Math.round(alpha * (0.25f + 0.75f * r.nextFloat())));
                c.drawCircle(x, y, unit * (1.2f + 2.2f * r.nextFloat() * r.nextFloat()), paint);
            }
        }

        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter f) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // Habits: how often each app was opened in each 3-hour part of the day. Counts halve once one
    // gets big, so new habits take over from old ones.
    private static final String HABITS = "habits";

    static void opened(Context c, String pkg) {
        SharedPreferences p = c.getSharedPreferences(HABITS, 0);
        String slot = slot(Calendar.getInstance());
        int n = p.getInt(slot + pkg, 0) + 1;
        SharedPreferences.Editor e = p.edit();
        if (n > 40) {
            for (Map.Entry<String, ?> en : p.getAll().entrySet())
                if (en.getKey().startsWith(slot) && en.getValue() instanceof Integer)
                    e.putInt(en.getKey(), (Integer) en.getValue() / 2);
            n /= 2;
        }
        e.putInt(slot + pkg, n).apply();
    }

    // The app on the home screen opened most at this time of day, once it has been opened a few times.
    static String usual(Context c, List<String> apps) {
        SharedPreferences p = c.getSharedPreferences(HABITS, 0);
        String slot = slot(Calendar.getInstance()), best = null;
        int most = 2;
        for (String a : apps) {
            int n = p.getInt(slot + a, 0);
            if (n > most) { most = n; best = a; }
        }
        return best;
    }

    private static String slot(Calendar c) { return c.get(Calendar.HOUR_OF_DAY) / 3 + "|"; }
}
