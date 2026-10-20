package com.ansub.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;

// What makes the home screen feel alive. The scene (sky by the real sun, weather, the moon in
// tonight's phase, hills, festival colors, film grain) is painted once into a bitmap off the main
// thread and only repainted when something in it changes, so nothing animates and moving between
// apps stays as fast as before. The glow in the focused app's color is drawn on top of it.
final class Alive {
    private Alive() {}

    // ---- Festivals ------------------------------------------------------------------------------

    // Name, the line shown under the clock on the day, days it starts before and lasts after, then
    // its dates: month/day every year, or year/month/day for the ones that follow the moon.
    private static final String[][] FESTIVALS = {
            {"New Year", "Welcome to the new year", "0", "0", "1/1"},
            {"Republic Day", "Jai Hind", "0", "0", "1/26"},
            {"Independence Day", "Jai Hind", "0", "0", "8/15"},
            {"Christmas", "Season's greetings", "1", "0", "12/25"},
            {"Diwali", "Festival of lights", "1", "1", "2026/11/8", "2027/10/29", "2028/10/17", "2029/11/5", "2030/10/26"},
            {"Holi", "Festival of colours", "1", "0", "2027/3/22", "2028/3/11", "2029/2/28", "2030/3/20"},
    };

    // Days from today to each date of each festival, nearest first: [festival index, days].
    private static int[] nearest(Calendar today, boolean window) {
        int[] best = null;
        Calendar day = noon(today);
        for (int f = 0; f < FESTIVALS.length; f++) {
            int before = Integer.parseInt(FESTIVALS[f][2]), after = Integer.parseInt(FESTIVALS[f][3]);
            for (int i = 4; i < FESTIVALS[f].length; i++) {
                String[] p = FESTIVALS[f][i].split("/");
                boolean yearly = p.length == 2;
                for (int dy = 0; dy <= (yearly ? 1 : 0); dy++) { // a yearly date can be next year
                    Calendar c = noon(today);
                    if (yearly) c.set(today.get(Calendar.YEAR) + dy, Integer.parseInt(p[0]) - 1, Integer.parseInt(p[1]));
                    else c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
                    int days = Math.round((c.getTimeInMillis() - day.getTimeInMillis()) / 86400000f);
                    boolean ok = window ? days >= -after && days <= before : days > before;
                    if (ok && (best == null || Math.abs(days) < Math.abs(best[1]))) best = new int[] {f, days};
                }
            }
        }
        return best;
    }

    private static Calendar noon(Calendar c) {
        Calendar n = (Calendar) c.clone();
        n.set(Calendar.HOUR_OF_DAY, 12);
        n.set(Calendar.MINUTE, 0);
        n.set(Calendar.SECOND, 0);
        n.set(Calendar.MILLISECOND, 0);
        return n;
    }

    // The festival on today, if any.
    static String festival(Calendar now) {
        int[] f = nearest(now, true);
        return f == null ? null : FESTIVALS[f[0]][0];
    }

    // The badge under the clock for festivals: today's line, or a countdown in the last 30 days.
    static String festivalBadge(Calendar now) {
        int[] f = nearest(now, true);
        if (f != null) return "✦  " + FESTIVALS[f[0]][1];
        f = nearest(now, false);
        if (f == null || f[1] > 30) return null;
        return "✦  " + FESTIVALS[f[0]][0] + (f[1] == 1 ? " tomorrow" : " in " + f[1] + " days");
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

    // ---- Sun and moon ---------------------------------------------------------------------------

    // Where the TV is: saved with --es place "lat,lon", else a guess from the time zone (good
    // enough for the sky, not for the weather).
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
    // fraction of a degree).
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

    // When the sun next rises or sets, found by stepping through the next day two minutes at a time.
    // Returns the time, positive for a sunrise and negative for a sunset, or 0 if neither happens.
    static long nextSunEvent(long now, double lat, double lon) {
        double h = -0.833; // the sun's top edge on the horizon
        double prev = sunElevation(now, lat, lon);
        for (long t = now + 120000; t < now + 86400000L; t += 120000) {
            double e = sunElevation(t, lat, lon);
            if (prev < h && e >= h) return t;
            if (prev >= h && e < h) return -t;
            prev = e;
        }
        return 0;
    }

    // Days since the last new moon (0 to 29.5).
    static double moonAge(long millis) {
        double days = (millis - 947182440000L) / 86400000.0; // a new moon: 6 January 2000, 18:14 UTC
        return ((days % 29.530588853) + 29.530588853) % 29.530588853;
    }

    static double moonLit(double age) { return (1 - Math.cos(age / 29.530588853 * 2 * Math.PI)) / 2; }

    static String moonText(double age) {
        int lit = (int) Math.round(moonLit(age) * 100);
        if (lit >= 97) return "Full moon";
        if (lit <= 3) return "New moon";
        return (age < 14.77 ? "Waxing" : "Waning") + " moon, " + lit + "% full";
    }

    // ---- Weather (Open-Meteo: free, no account) -------------------------------------------------

    static final class Weather {
        final int code, cloud;
        final double temp;
        Weather(int code, double temp, int cloud) { this.code = code; this.temp = temp; this.cloud = cloud; }
        boolean rain() { return code >= 51 && code <= 67 || code >= 80 && code <= 82 || code >= 95; }
        boolean storm() { return code >= 95; }
        boolean snow() { return code >= 71 && code <= 77 || code == 85 || code == 86; }
        boolean fog() { return code == 45 || code == 48; }
        boolean overcast() { return cloud >= 70 || rain() || snow() || fog(); }
    }

    // Runs on a background thread. Returns null when offline or the service doesn't answer.
    static Weather fetchWeather(double lat, double lon) {
        HttpURLConnection c = null;
        try {
            URL u = new URL(String.format(Locale.ROOT, "https://api.open-meteo.com/v1/forecast?latitude=%.3f"
                    + "&longitude=%.3f&current=temperature_2m,weather_code,cloud_cover", lat, lon));
            c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            for (String line; (line = r.readLine()) != null; ) sb.append(line);
            r.close();
            JSONObject cur = new JSONObject(sb.toString()).getJSONObject("current");
            return new Weather(cur.getInt("weather_code"), cur.getDouble("temperature_2m"), cur.optInt("cloud_cover", 0));
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    static String weatherBadge(Weather w) {
        int code = w.code;
        String what = code == 0 ? "☀  Clear" : code == 1 ? "☀  Mostly clear" : code == 2 ? "⛅  Partly cloudy"
                : code == 3 ? "☁  Cloudy" : w.fog() ? "☁  Fog" : code >= 51 && code <= 57 ? "☂  Drizzle"
                : code == 61 || code == 80 ? "☂  Light rain" : code == 65 || code == 82 ? "☂  Heavy rain"
                : w.storm() ? "⚡  Thunderstorm" : w.snow() ? "❄  Snow" : "☂  Rain";
        boolean f = "US".equals(Locale.getDefault().getCountry());
        return what + " · " + Math.round(f ? w.temp * 9 / 5 + 32 : w.temp) + "°";
    }

    // ---- The scene ------------------------------------------------------------------------------

    // Sky keyframes by the sun's height in degrees: top, middle and bottom of the screen, the glow
    // near the sun, and how bright the stars are. Everything stays dark so white text reads.
    private static final double[] ELEVATION = {-18, -6, 0, 8, 25};
    private static final int[][] SKY = {
            // top         middle      bottom      glow        stars
            {0xFF070A18, 0xFF05070F, 0xFF020308, 0x383A5ABE, 255}, // night
            {0xFF0B0F26, 0xFF1A1033, 0xFF2A1426, 0x55FF6A3D, 110}, // twilight
            {0xFF191634, 0xFF2C1838, 0xFF3E1C24, 0x8CFF783C, 0},   // sunrise, sunset
            {0xFF1B2140, 0xFF2A2A4A, 0xFF4A3434, 0x80FFAA6E, 0},   // golden hour
            {0xFF0E2A44, 0xFF0C2036, 0xFF08141F, 0x4778C8FF, 0},   // day
    };
    // Where that glow sits, as fractions of the screen, in the morning (the sun rises on the left;
    // in the evening it's mirrored to the right).
    private static final float[][] GLOW_AT = {{0.1f, -0.1f}, {0.2f, 1.15f}, {0.12f, 1.1f}, {0.12f, 1.08f}, {0.08f, -0.1f}};

    // The three sky colors (top, middle, bottom) for this sun height: also the quick placeholder.
    static int[] skyColors(double elevation) {
        int k = key(elevation);
        float t = frac(elevation, k);
        return new int[] {mix(SKY[k][0], SKY[k + 1][0], t), mix(SKY[k][1], SKY[k + 1][1], t), mix(SKY[k][2], SKY[k + 1][2], t)};
    }

    private static int key(double e) {
        int k = 0;
        while (k < ELEVATION.length - 2 && e > ELEVATION[k + 1]) k++;
        return k;
    }

    private static float frac(double e, int k) {
        return Math.max(0f, Math.min(1f, (float) ((e - ELEVATION[k]) / (ELEVATION[k + 1] - ELEVATION[k]))));
    }

    // Paints the whole scene at w x h. Runs off the main thread.
    static Bitmap paint(int w, int h, double sun, boolean morning, String festival, double moonAge, Weather weather) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float u = w / 1920f; // design pixels to real ones
        int k = key(sun);
        float t = frac(sun, k);
        int[] sky = skyColors(sun);
        boolean cloudy = weather != null && weather.overcast();

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h, sky, new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        // Glow near the sun's side of the sky, and a little plum low on the right for depth.
        float gx = lerp(GLOW_AT[k][0], GLOW_AT[k + 1][0], t), gy = lerp(GLOW_AT[k][1], GLOW_AT[k + 1][1], t);
        if (!morning) gx = 1f - gx;
        int glow = mix(SKY[k][3], SKY[k + 1][3], t);
        if (cloudy) glow = fade(glow, 0.5f);
        glow(c, glow, gx * w, gy * h, 1300 * u, 820 * u);
        glow(c, 0x1E6A3CAA, w, 1.1f * h, 900 * u, 700 * u);

        int stars = Math.round(lerp(SKY[k][4], SKY[k + 1][4], t));
        if (cloudy) stars = 0;
        else if (weather != null && weather.cloud > 30) stars = stars / 2;
        if (stars > 8) dots(c, 0xFFFFFFFF, stars * 160 / 255, 170, 7, 0f, 0.57f, 0.6f * u, 1.4f * u, false);

        double lit = moonLit(moonAge);
        if (sun < -4 && lit > 0.04 && !cloudy) moon(c, 0.81f * w, 0.23f * h, 54 * u, lit, moonAge < 14.77);

        festival(c, festival, w, h, u);

        if (weather != null) {
            if (cloudy || weather.cloud > 40) {
                int a = weather.storm() ? 0x66 : cloudy ? 0x4C : 0x30;
                int tone = weather.storm() ? 0x5A6478 : 0x96A5BE;
                glow(c, a << 24 | tone, 0.15f * w, 0.0f, 650 * u, 260 * u);
                glow(c, (a - 6) << 24 | tone, 0.62f * w, -0.03f * h, 760 * u, 280 * u);
                glow(c, (a - 12) << 24 | tone, 0.95f * w, 0.17f * h, 480 * u, 200 * u);
            }
            if (weather.fog()) {
                p.setColor(0x26A0A8B8);
                c.drawRect(0, 0, w, h, p);
            }
            if (weather.rain()) streaks(c, w, h, u, weather.storm() ? 420 : 260);
            if (weather.snow()) dots(c, 0xFFFFFFFF, 210, 220, 11, 0f, 1f, 1.2f * u, 3.2f * u, false);
        }

        hills(c, w, h, sky);

        // Darker edges and a darker bottom keep the eye on the apps and the text easy to read.
        Paint v = new Paint(Paint.DITHER_FLAG);
        RadialGradient vig = new RadialGradient(0, 0, 1, new int[] {0, 0, 0x8C000000}, new float[] {0f, 0.55f, 1f},
                Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        m.setScale(1.3f * w, 1.1f * h * 1.0f);
        m.postTranslate(0.4f * w, 0.35f * h);
        vig.setLocalMatrix(m);
        v.setShader(vig);
        c.drawRect(0, 0, w, h, v);
        v.setShader(new LinearGradient(0, 0.5f * h, 0, h, 0, 0x61000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0.5f * h, w, h, v);

        grain(c, w, h);
        return bmp;
    }

    private static void festival(Canvas c, String f, int w, int h, float u) {
        if (f == null) return;
        switch (f) {
            case "Diwali":
                glow(c, 0x9EFFAA3C, 0.18f * w, 1.08f * h, 900 * u, 520 * u);
                glow(c, 0x38FF963C, 0.5f * w, 0f, 1000 * u, 600 * u);
                glow(c, 0x8CFF7828, 0.82f * w, 1.08f * h, 900 * u, 520 * u);
                dots(c, 0xFFFFD58A, 255, 110, 3, 0.59f, 1f, 1.2f * u, 3.6f * u, true); // little lamps
                break;
            case "Holi":
                glow(c, 0x55FF3CAC, 0.1f * w, 0.1f * h, 1000 * u, 700 * u);
                glow(c, 0x5000D4FF, 0.9f * w, 0.2f * h, 1000 * u, 700 * u);
                glow(c, 0x50FFD400, 0.3f * w, h, 1000 * u, 700 * u);
                glow(c, 0x5030E070, 0.95f * w, 0.95f * h, 1000 * u, 700 * u);
                break;
            case "Independence Day":
            case "Republic Day":
                glow(c, 0x66FF9933, 0f, -0.05f * h, 1100 * u, 700 * u);
                glow(c, 0x1FFFFFFF, 0.5f * w, 0.5f * h, 700 * u, 500 * u);
                glow(c, 0x66138808, w, 1.05f * h, 1100 * u, 700 * u);
                break;
            case "Christmas":
                glow(c, 0x50D42A2A, 0.05f * w, 0f, 1000 * u, 700 * u);
                glow(c, 0x50107A3A, 0.95f * w, h, 1000 * u, 700 * u);
                dots(c, 0xFFFFFFFF, 190, 180, 11, 0f, 1f, 1.2f * u, 3.4f * u, false); // snow
                break;
            case "New Year":
                glow(c, 0x50A040FF, 0.9f * w, 0f, 1000 * u, 700 * u);
                dots(c, 0xFFFFD27A, 230, 130, 13, 0f, 1f, 1f * u, 3f * u, true);
                break;
        }
    }

    // A soft elliptical glow fading out by 70% of its size.
    private static void glow(Canvas c, int color, float cx, float cy, float rx, float ry) {
        int clear = color & 0x00FFFFFF;
        RadialGradient g = new RadialGradient(0, 0, 1, new int[] {color, clear, clear}, new float[] {0f, 0.7f, 1f},
                Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        m.setScale(rx, ry);
        m.postTranslate(cx, cy);
        g.setLocalMatrix(m);
        Paint p = new Paint(Paint.DITHER_FLAG);
        p.setShader(g);
        c.drawRect(cx - rx, cy - ry, cx + rx, cy + ry, p);
    }

    // Dots in fixed places (the same seed always gives the same sky): stars, lamps or snow, between
    // the fractions top and bottom of the height. halo adds a warm glow around each one.
    private static void dots(Canvas c, int color, int alpha, int count, int seed, float top, float bottom,
                             float rMin, float rMax, boolean halo) {
        int w = c.getWidth(), h = c.getHeight();
        Random r = new Random(seed);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < count; i++) {
            float x = r.nextFloat() * w, y = (top + r.nextFloat() * (bottom - top)) * h;
            float a = 0.25f + 0.75f * r.nextFloat(), rad = rMin + (rMax - rMin) * r.nextFloat() * r.nextFloat();
            if (halo) glow(c, Math.round(alpha * a * 0.45f) << 24 | 0xFFB347, x, y, rad * 5, rad * 5);
            p.setColor(color);
            p.setAlpha(Math.round(alpha * a));
            c.drawCircle(x, y, rad, p);
        }
    }

    // The moon as it looks tonight: the lit part is half a disc plus or minus half an ellipse.
    private static void moon(Canvas c, float cx, float cy, float r, double lit, boolean waxing) {
        glow(c, 0x4DCFD8FF, cx, cy, r * 7, r * 7);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0x12FFFFFF);
        c.drawCircle(cx, cy, r, p); // the dark side, barely there
        float k = (float) (2 * lit - 1), rx = Math.abs(k) * r;
        Path path = new Path();
        path.arcTo(new RectF(cx - r, cy - r, cx + r, cy + r), -90, 180);
        path.arcTo(new RectF(cx - rx, cy - r, cx + rx, cy + r), 90, k > 0 ? 180 : -180);
        path.close();
        c.save();
        if (!waxing) c.scale(-1, 1, cx, cy); // waning: lit on the left
        p.setShader(new RadialGradient(cx - 0.2f * r, cy - 0.3f * r, 1.3f * r, 0xFFFFFAF0, 0xFFD9DBE6, Shader.TileMode.CLAMP));
        p.setAlpha(242);
        c.drawPath(path, p);
        c.clipPath(path);
        p.setShader(null);
        p.setColor(0x0F000000);
        c.drawCircle(cx + 0.25f * r, cy - 0.2f * r, 0.16f * r, p); // a couple of seas
        c.drawCircle(cx + 0.45f * r, cy + 0.3f * r, 0.11f * r, p);
        c.restore();
    }

    // Three layers of soft hills on the horizon, lit a little by the sky behind them.
    private static void hills(Canvas c, int w, int h, int[] sky) {
        float sx = w / 1920f, sy = h / 1080f;
        float[][] curves = {
                {560, 220, 500, 360, 470, 520, 520, 680, 570, 820, 610, 1000, 540, 1180, 470, 1340, 430, 1560, 500, 1700, 540, 1820, 560, 1920, 520},
                {640, 260, 580, 420, 600, 600, 560, 780, 520, 980, 520, 1180, 590, 1380, 640, 1600, 660, 1920, 590},
                {720, 300, 680, 520, 700, 760, 660, 980, 640, 1200, 640, 1440, 700, 1600, 720, 1760, 740, 1920, 700},
        };
        int[] colors = {fade(mix(sky[0], 0xFFFFFFFF, 0.06f), 0.55f), fade(mix(sky[0], 0xFF000000, 0.1f), 0.75f),
                mix(sky[2], 0xFF000000, 0.3f)};
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < 3; i++) {
            float[] pt = curves[i];
            Path path = new Path();
            path.moveTo(0, pt[0] * sy);
            // A smooth line through the points: each bend eases into the next.
            float px = 0, py = pt[0];
            for (int j = 1; j + 1 < pt.length; j += 2) {
                float x = pt[j], y = pt[j + 1];
                path.cubicTo((px + (x - px) / 2) * sx, py * sy, (px + (x - px) / 2) * sx, y * sy, x * sx, y * sy);
                px = x;
                py = y;
            }
            path.lineTo(w, h);
            path.lineTo(0, h);
            path.close();
            p.setColor(colors[i]);
            c.drawPath(path, p);
        }
    }

    private static void streaks(Canvas c, int w, int h, float u, int count) {
        Random r = new Random(11);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStrokeWidth(1.3f * u);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < count; i++) {
            float x = r.nextFloat() * (w + 200 * u) - 100 * u, y = r.nextFloat() * h, l = (18 + 30 * r.nextFloat()) * u;
            p.setColor(0xFFCFE3FF);
            p.setAlpha(Math.round(255 * (0.08f + 0.18f * r.nextFloat())));
            c.drawLine(x, y, x - l * 0.25f, y + l, p);
        }
    }

    // Fine film grain: hides banding in the gradients and makes them look less flat.
    private static void grain(Canvas c, int w, int h) {
        Random r = new Random(5);
        int n = 128;
        int[] px = new int[n * n];
        for (int i = 0; i < px.length; i++) {
            int a = r.nextInt(18);
            px[i] = a << 24 | (r.nextBoolean() ? 0xFFFFFF : 0);
        }
        Bitmap tile = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888);
        Paint p = new Paint();
        p.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        c.drawRect(0, 0, w, h, p);
    }

    // ---- The focused app's color ----------------------------------------------------------------

    static final int NEUTRAL = 0xFF8C9EFF;

    // The most vivid color in an app's banner (Netflix red, Spotify green), made bright enough to
    // glow. Banners with no strong color (all black, white or grey) get NEUTRAL.
    static int accent(Drawable banner) {
        if (!(banner instanceof BitmapDrawable)) return NEUTRAL;
        Bitmap small = Bitmap.createScaledBitmap(((BitmapDrawable) banner).getBitmap(), 32, 18, true);
        double[] weight = new double[12], rs = new double[12], gs = new double[12], bs = new double[12];
        float[] hsv = new float[3];
        for (int y = 0; y < 18; y++)
            for (int x = 0; x < 32; x++) {
                int c = small.getPixel(x, y);
                if (Color.alpha(c) < 128) continue;
                Color.colorToHSV(c, hsv);
                if (hsv[1] < 0.35f || hsv[2] < 0.3f) continue;
                int b = (int) (hsv[0] / 30) % 12;
                double wgt = hsv[1] * hsv[2] * hsv[2];
                weight[b] += wgt;
                rs[b] += Color.red(c) * wgt;
                gs[b] += Color.green(c) * wgt;
                bs[b] += Color.blue(c) * wgt;
            }
        int best = 0;
        for (int b = 1; b < 12; b++) if (weight[b] > weight[best]) best = b;
        if (weight[best] < 4) return NEUTRAL;
        int avg = Color.rgb((int) (rs[best] / weight[best]), (int) (gs[best] / weight[best]), (int) (bs[best] / weight[best]));
        Color.colorToHSV(avg, hsv);
        hsv[1] = Math.max(hsv[1], 0.65f);
        hsv[2] = Math.max(hsv[2], 0.85f);
        return Color.HSVToColor(hsv);
    }

    // ---- Colors ---------------------------------------------------------------------------------

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    static int mix(int a, int b, float t) {
        int out = 0;
        for (int s = 0; s < 32; s += 8)
            out |= Math.round(lerp((a >>> s) & 0xFF, (b >>> s) & 0xFF, t)) << s;
        return out;
    }

    static int fade(int color, float alpha) {
        return Math.round(Color.alpha(color) * alpha) << 24 | (color & 0x00FFFFFF);
    }

    // ---- Habits ---------------------------------------------------------------------------------

    // How often each app was opened in each 3-hour part of the day. Counts halve once one gets big,
    // so new habits take over from old ones.
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
