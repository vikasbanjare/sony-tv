package com.ansub.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

// The big clock, and a little cat that lives on top of it.
//
// The digits are filled with a gradient that ends in the focused app's color and glow softly; they're
// drawn once a minute into a bitmap and reused. When the minute changes, the digits that changed roll:
// the old one slides down and fades as the new one drops in. The colon's dots breathe with the seconds.
//
// The cat (a calico, after Vikas's) sits on the digits, swishes her tail and blinks, and every few
// seconds does something: walks to another digit (hopping over the colon), bats at the colon so its dot
// wobbles, grooms a paw, yawns, hops, or takes a short nap. A button press wakes her, and she looks
// toward what you moved to.
//
// All of it is drawn on its own small surface by its own low-priority thread, so the home screen never
// redraws for it and the remote stays as quick as without it.
final class ClockCat extends SurfaceView implements SurfaceHolder.Callback {
    static final int SIT = 0, WALK = 1, YAWN = 2, HOP = 3, SLEEP = 4, WAKE = 5, BAT = 6, GROOM = 7;
    private static final int FUR = 0xFF8A7F75, FUR_DARK = 0xFF5B524A, ORANGE = 0xFFD38C4F, WHITE = 0xFFF4F1EB,
            EYE = 0xFFB9D46B, PUPIL = 0xFF17151A, NOSE = 0xFFE89AA4;

    private final Object lock = new Object();
    private final Random rnd = new Random();
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blit = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();
    private final Rect bounds = new Rect();
    private Thread thread;
    private volatile boolean running, surfaceReady;
    private volatile boolean is24;

    // Layout, from the text size: the cat's room above the digits, the glow's room around them.
    private float size, catH, headroom, pad, base, capTop;
    private int accent = Alive.NEUTRAL;

    // The time as drawn, the one before (for the roll), and their cached digits.
    private String shown = "", before = "";
    private Bitmap now, prev;
    private long rollAt, lastCheck;
    private float[] xs = new float[0], prevXs = new float[0]; // where each character starts
    private float colonX = -1, colonR, colonTop, colonBottom;
    private float wobble, wobbleV; // the colon's top dot, after a bat

    // The cat.
    int state = SIT;
    float x = -1;
    private float target, look, lookTo, lastDt;
    private long since, nextMove, blinkAt, lookUntil, swipeAt, lastFrame;
    private int swipes;

    private final Runnable loop = () -> {
        SurfaceHolder h = getHolder();
        while (running && surfaceReady) {
            long t0 = SystemClock.uptimeMillis();
            Canvas c = null;
            try {
                c = android.os.Build.VERSION.SDK_INT >= 26 ? h.lockHardwareCanvas() : h.lockCanvas();
                if (c != null) {
                    c.drawColor(0, PorterDuff.Mode.CLEAR);
                    synchronized (lock) { frame(c); }
                }
            } catch (Exception ignored) {
            } finally {
                if (c != null) try { h.unlockCanvasAndPost(c); } catch (Exception ignored) {}
            }
            long wait = (state == SLEEP && t0 - rollAt > 600 ? 100 : 40) - (SystemClock.uptimeMillis() - t0);
            if (wait > 0) try { Thread.sleep(wait); } catch (InterruptedException e) { return; }
        }
    };

    ClockCat(Context c, Typeface face) {
        super(c);
        setZOrderOnTop(true); // above the home screen, still below menus and dialogs
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        getHolder().addCallback(this);
        text.setTypeface(face);
        text.setLetterSpacing(-0.035f);
        text.setFontFeatureSettings("tnum");
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        is24 = android.text.format.DateFormat.is24HourFormat(c);
        long t = SystemClock.uptimeMillis();
        since = t;
        nextMove = t + 5000;
        blinkAt = t + 2000;
        setTextSize(200);
    }

    // ---- Size and layout -------------------------------------------------------------------------

    void setTextSize(float px) {
        synchronized (lock) {
            size = px;
            text.setTextSize(px);
            catH = px * 0.46f;
            headroom = catH * 1.55f;
            pad = px * 0.24f; // room for the glow
            text.getTextBounds("0", 0, 1, bounds);
            capTop = headroom;
            base = headroom - bounds.top;
            now = prev = null;
            shown = "";
        }
        requestLayout();
    }

    float getTextSizePx() { return size; }

    // How far the digits start in from the view's left edge (the glow's room): line them up with this.
    int glowPad() { return Math.round(pad); }

    @Override
    protected void onMeasure(int w, int h) {
        String t = time();
        setMeasuredDimension(Math.round(text.measureText(t) + pad * 2), Math.round(base + pad));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w > 0 && h > 0) getHolder().setFixedSize(w, h);
    }

    // ---- Running ---------------------------------------------------------------------------------

    void start() {
        is24 = android.text.format.DateFormat.is24HourFormat(getContext());
        running = true;
        go();
    }

    void stop() {
        running = false;
        end();
    }

    private void go() {
        if (!running || !surfaceReady || thread != null && thread.isAlive()) return;
        lastFrame = 0;
        thread = new Thread(loop, "clock-cat");
        thread.setPriority(Thread.MIN_PRIORITY); // the remote always comes first
        thread.start();
    }

    private void end() {
        Thread t = thread;
        thread = null;
        if (t != null) {
            t.interrupt();
            try { t.join(200); } catch (InterruptedException ignored) {}
        }
    }

    @Override public void surfaceCreated(SurfaceHolder h) { surfaceReady = true; go(); }
    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {}
    @Override public void surfaceDestroyed(SurfaceHolder h) { surfaceReady = false; end(); } // starts again with the surface

    void setAccent(int color) {
        synchronized (lock) {
            if (accent == color) return;
            accent = color;
            now = null; // redraw the digits in the new color
            prev = null;
        }
    }

    // A button was pressed: wake up, and look toward lookAtX (this view's pixels) for a few seconds.
    void poke(float lookAtX) {
        synchronized (lock) {
            long t = SystemClock.uptimeMillis();
            if (state == SLEEP) set(WAKE, t);
            if (x >= 0) lookTo = Math.max(-1f, Math.min(1f, (lookAtX - x) / (catH * 3)));
            lookUntil = t + 3000;
        }
    }

    private String time() {
        return new SimpleDateFormat(is24 ? "H:mm" : "h:mm", Locale.getDefault()).format(new Date());
    }

    // ---- The digits ------------------------------------------------------------------------------

    private void digits(long t) {
        if (t - lastCheck > 500 || now == null) {
            lastCheck = t;
            String s = time();
            if (!s.equals(shown) || now == null) {
                boolean minute = !shown.isEmpty() && !s.equals(shown);
                if (s.length() != shown.length()) post(this::requestLayout); // e.g. 9:59 to 10:00
                before = shown;
                prev = minute ? now : null;
                prevXs = xs;
                shown = s;
                xs = positions(s);
                now = render(s, xs);
                if (minute) rollAt = t;
                int ci = s.indexOf(':');
                if (ci >= 0) {
                    text.getTextBounds(":", 0, 1, bounds);
                    colonR = Math.max(1, bounds.width() / 2f);
                    colonX = xs[ci] + text.measureText(":") / 2;
                    colonTop = base + bounds.top + colonR;
                    colonBottom = base + bounds.bottom - colonR;
                } else colonX = -1;
            }
        }
    }

    private float[] positions(String s) {
        float[] out = new float[s.length() + 1];
        out[0] = pad;
        for (int i = 0; i < s.length(); i++) out[i + 1] = pad + text.measureText(s, 0, i + 1);
        return out;
    }

    private Shader digitShader() {
        int c = accent | 0xFF000000;
        return new LinearGradient(0, capTop, 0, base, new int[] {0xFFFFFFFF, Alive.mix(c, 0xFFFFFFFF, 0.62f), Alive.mix(c, 0xFFFFFFFF, 0.25f)},
                new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
    }

    // The digits (not the colon, which is drawn live), with their glow, into a bitmap.
    private Bitmap render(String s, float[] at) {
        int w = Math.max(1, Math.round(text.measureText(s) + pad * 2));
        int h = Math.max(1, Math.round(base + pad));
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        text.setShader(digitShader());
        text.setShadowLayer(size * 0.22f, 0, size * 0.025f, Alive.fade(accent | 0xFF000000, 0.45f));
        for (int i = 0; i < s.length(); i++)
            if (s.charAt(i) != ':') c.drawText(s, i, i + 1, at[i], base, text);
        text.setShader(null);
        text.clearShadowLayer();
        return b;
    }

    private void drawDigits(Canvas c, long t) {
        float p = Math.min(1f, (t - rollAt) / 520f);
        if (prev == null || p >= 1 || before.length() != shown.length()) {
            c.drawBitmap(now, 0, 0, null);
            return;
        }
        // Roll the digits that changed: the old one slides down and fades, the new one drops in.
        float e = 1 - (1 - p) * (1 - p) * (1 - p), drop = size * 0.35f;
        for (int i = 0; i < shown.length(); i++) {
            float l = xs[i] - pad * 0.5f, r = xs[i + 1] + pad * 0.5f;
            c.save();
            c.clipRect(l, 0, r, getHeight());
            if (shown.charAt(i) == before.charAt(i)) c.drawBitmap(now, 0, 0, null);
            else {
                blit.setAlpha(Math.round(255 * (1 - e)));
                c.drawBitmap(prev, 0, drop * e, blit);
                blit.setAlpha(Math.round(255 * e));
                c.drawBitmap(now, 0, -drop * (1 - e), blit);
            }
            c.restore();
        }
    }

    // The colon's dots: they breathe with the seconds, and the top one wobbles when the cat bats it.
    private void drawColon(Canvas c, long t, float dt) {
        if (colonX < 0) return;
        wobbleV += (-wobble * 180 - wobbleV * 9) * dt; // a damped spring
        wobble += wobbleV * dt;
        float sec = (System.currentTimeMillis() % 2000) / 2000f;
        float breathe = 0.6f + 0.4f * (0.5f + 0.5f * (float) Math.cos(sec * 2 * Math.PI));
        int c0 = accent | 0xFF000000;
        fill.setShader(new RadialGradient(colonX, (colonTop + colonBottom) / 2, (colonBottom - colonTop),
                Alive.fade(c0, 0.35f * breathe), c0 & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(colonX, (colonTop + colonBottom) / 2, colonBottom - colonTop, fill);
        fill.setShader(digitShader());
        fill.setAlpha(Math.round(255 * (0.55f + 0.45f * breathe)));
        c.drawCircle(colonX + wobble * 0.4f, colonTop - Math.abs(wobble), colonR, fill);
        c.drawCircle(colonX, colonBottom, colonR, fill);
        fill.setShader(null);
        fill.setAlpha(255);
    }

    // ---- The cat ---------------------------------------------------------------------------------

    private void set(int s, long t) {
        state = s;
        since = t;
    }

    // Where the cat can stand: over the digits, never over the colon.
    private float left() { return xs.length > 1 ? xs[0] + catH * 0.32f : 0; }
    private float right() { return xs.length > 1 ? xs[xs.length - 1] - catH * 0.32f : 0; }

    private float digitCenter(int i) { return (xs[i] + xs[i + 1]) / 2; }

    void frame(Canvas c) {
        long t = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0.04f : Math.min(0.1f, (t - lastFrame) / 1000f);
        lastFrame = t;
        lastDt = dt;
        digits(t);
        if (now == null) return;
        drawDigits(c, t);
        drawColon(c, t, dt);
        think(t, dt);
        drawCat(c, t);
    }

    private void think(long t, float dt) {
        if (xs.length < 2) return;
        if (x < 0) x = digitCenter(shown.length() - 1);
        float l = left(), r = right();
        if (t > lookUntil) lookTo = 0;
        look += (lookTo - look) * Math.min(1f, dt * 6);
        float p = (t - since) / 1000f;
        switch (state) {
            case SIT:
                if (x < l || x > r) { target = Math.max(l, Math.min(r, x)); set(WALK, t); break; }
                if (t > nextMove) choose(t);
                break;
            case WALK: {
                float step = catH * 1.2f * dt;
                if (Math.abs(target - x) <= step) {
                    x = target;
                    if (swipes > 0) { set(BAT, t); swipeAt = t + 150; } // walked over to bat the colon
                    else set(SIT, t);
                } else x += Math.signum(target - x) * step;
                break;
            }
            case BAT:
                // Three swipes at the colon; each one knocks the top dot.
                if (t - swipeAt > 520) {
                    if (--swipes > 0) swipeAt = t + 120;
                    else { set(SIT, t); nextMove = t + 3000 + rnd.nextInt(3000); }
                }
                if (t - swipeAt > 180 && t - swipeAt <= 180 + Math.round(dt * 1000)) {
                    wobbleV += Math.signum(colonX - x) * catH * 3.5f;
                }
                break;
            case YAWN: if (p > 1.6f) set(SIT, t); break;
            case HOP: if (p > 0.6f) set(SIT, t); break;
            case GROOM: if (p > 2.4f) set(SIT, t); break;
            case SLEEP: if (p > napFor) set(WAKE, t); break;
            case WAKE: if (p > 1.2f) { set(SIT, t); nextMove = t + 2500; } break;
        }
        if (t > blinkAt + 150) blinkAt = t + 2200 + rnd.nextInt(3500);
    }

    private float napFor;

    private void choose(long t) {
        nextMove = t + 4000 + rnd.nextInt(5000);
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        boolean night = hour >= 23 || hour < 6;
        float r = rnd.nextFloat();
        if (r < (night ? 0.35f : 0.1f)) { napFor = 20 + rnd.nextInt(night ? 100 : 25); set(SLEEP, t); return; }
        r = rnd.nextFloat();
        if (r < 0.38f) { // to another digit
            int i;
            do i = rnd.nextInt(shown.length()); while (shown.charAt(i) == ':');
            target = Math.max(left(), Math.min(right(), digitCenter(i)));
            if (Math.abs(target - x) > catH * 0.2f) { swipes = 0; set(WALK, t); }
        } else if (r < 0.6f && colonX > 0) { // go and bat the colon
            float side = x < colonX ? -1 : 1;
            target = Math.max(left(), Math.min(right(), colonX + side * catH * 0.62f));
            swipes = 3;
            set(WALK, t);
        } else if (r < 0.75f) set(GROOM, t);
        else if (r < 0.88f) set(YAWN, t);
        else set(HOP, t);
    }

    private void drawCat(Canvas c, long t) {
        if (x < 0) return;
        float s = catH, p = (t - since) / 1000f, time = t / 1000f;
        float floor = capTop;
        float lift = 0;
        if (state == HOP) lift = (float) Math.sin(Math.PI * Math.min(1f, p / 0.6f)) * 0.3f;
        if (state == WALK && colonX > 0) { // hop over the colon
            float a = colonX - s * 0.45f, b = colonX + s * 0.45f;
            if (x > a && x < b) lift = (float) Math.sin(Math.PI * (x - a) / (b - a)) * 0.32f;
        }
        // A soft shadow on the digits under her.
        float sh = 1 - lift * 1.5f;
        fill.setShader(new RadialGradient(x, floor, s * 0.42f * sh, 0x66000000, 0, Shader.TileMode.CLAMP));
        c.save();
        c.scale(1, 0.16f, x, floor);
        c.drawCircle(x, floor, s * 0.42f * sh, fill);
        c.restore();
        fill.setShader(null);

        c.save();
        c.translate(x, floor - lift * s);
        c.scale(s, s); // from here on, 1 = her height sitting; y goes up as it gets negative
        if (state == SLEEP) asleep(c, time, p);
        else sitting(c, t, time, p);
        c.restore();
        if (state == SLEEP) zs(c, s, floor, time);
    }

    private void sitting(Canvas c, long t, float time, float p) {
        boolean walk = state == WALK;
        float stretch = state == WAKE ? (float) Math.sin(Math.PI * Math.min(1f, p / 1.2f)) : 0;
        float breathe = 1 + 0.012f * (float) Math.sin(time * 2.2f);
        float bob = walk ? Math.abs((float) Math.sin(time * 10)) * 0.025f : 0;
        c.save();
        c.translate(0, -bob);
        c.scale(1 + stretch * 0.12f, (1 - stretch * 0.1f) * breathe, 0, 0);
        if (walk) c.rotate(Math.signum(target - x) * 3, 0, 0);

        // Tail: a curl that sways, banded, with a dark tip.
        float sway = (float) Math.sin(time * (walk ? 6 : 1.6f)) * (walk ? 0.08f : 0.13f);
        path.reset();
        path.moveTo(0.18f, -0.05f);
        path.cubicTo(0.44f, -0.02f, 0.52f + sway * 0.3f, -0.26f, 0.41f + sway * 0.7f, -0.46f + Math.abs(sway) * 0.1f);
        stroke(c, FUR_DARK, 0.085f);
        stroke(c, FUR, 0.062f);
        line.setColor(FUR_DARK);
        line.setStrokeWidth(0.07f);
        line.setPathEffect(new android.graphics.DashPathEffect(new float[] {0.035f, 0.07f}, 0));
        c.drawPath(path, line);
        line.setPathEffect(null);

        // Body: a pear, lit from above, with an orange patch on the back and a white chest.
        path.reset();
        path.moveTo(-0.27f, 0);
        path.cubicTo(-0.33f, -0.2f, -0.25f, -0.46f, 0, -0.5f);
        path.cubicTo(0.25f, -0.46f, 0.33f, -0.2f, 0.27f, 0);
        path.close();
        fill.setShader(new LinearGradient(0, -0.5f, 0, 0, 0xFF9B9086, 0xFF6F655C, Shader.TileMode.CLAMP));
        c.drawPath(path, fill);
        fill.setShader(null);
        c.save();
        c.clipPath(path);
        ellipse(c, ORANGE, 0.17f, -0.27f, 0.13f, 0.16f);
        ellipse(c, WHITE, 0, -0.2f, 0.13f, 0.21f);
        line.setColor(0x66423A33); // a few soft tabby stripes on the flanks
        line.setStrokeWidth(0.02f);
        for (int side = -1; side <= 1; side += 2)
            for (int i = 0; i < 3; i++)
                c.drawLine(side * 0.29f, -0.1f - i * 0.1f, side * 0.2f, -0.13f - i * 0.1f, line);
        c.restore();
        rim(c);

        // Front paws: stepping while she walks, one up to swipe or to groom.
        float lp = walk ? Math.max(0, (float) Math.sin(time * 10)) * 0.05f : 0;
        float rp = walk ? Math.max(0, -(float) Math.sin(time * 10)) * 0.05f : 0;
        float swipe = 0;
        int swipeSide = 0;
        if (state == BAT) {
            long sw = t - swipeAt;
            if (sw >= 0 && sw < 360) swipe = (float) Math.sin(Math.PI * sw / 360f);
            swipeSide = colonX > x ? 1 : -1;
        }
        float groom = state == GROOM ? (float) Math.min(1, Math.sin(Math.PI * Math.min(1f, p / 2.4f)) * 1.6f) : 0;
        float lx = -0.11f, ly = -0.025f - lp, rx = 0.11f, ry = -0.025f - rp;
        if (swipeSide < 0) { lx -= swipe * 0.16f; ly -= swipe * 0.2f; }
        if (swipeSide > 0) { rx += swipe * 0.16f; ry -= swipe * 0.2f; }
        if (groom > 0) { rx -= groom * 0.08f; ry -= groom * 0.48f; }
        legTo(c, lx, ly);
        legTo(c, rx, ry);
        ellipse(c, WHITE, lx, ly, 0.075f, 0.045f);
        ellipse(c, WHITE, rx, ry, 0.075f, 0.045f);

        // Head: tilts toward where she's looking; back for a yawn; down to lick a paw.
        boolean yawn = state == YAWN;
        float y = yawn ? (float) Math.sin(Math.PI * Math.min(1f, p / 1.6f)) : 0;
        float lookAt = state == BAT ? (colonX > x ? 0.8f : -0.8f) : look;
        c.save();
        c.translate(0, -0.66f - y * 0.02f + groom * 0.03f);
        c.rotate(lookAt * 8 - y * 4 + groom * 10);
        boolean blink = t >= blinkAt && t < blinkAt + 150;
        float open = yawn || stretch > 0.2f || groom > 0.3f ? 0.08f : blink ? 0.1f : 1f;
        head(c, open, y, lookAt, groom);
        c.restore();
        c.restore();
    }

    // A front leg from the shoulder down to the paw at (px, py).
    private void legTo(Canvas c, float px, float py) {
        line.setColor(FUR);
        line.setStrokeWidth(0.09f);
        c.drawLine(Math.signum(px) * 0.12f, -0.3f, px, py, line);
    }

    private void head(Canvas c, float open, float yawn, float lookAt, float groom) {
        for (int side = -1; side <= 1; side += 2) { // ears: the left one orange, like hers
            ear(c, side, side < 0 ? ORANGE : FUR, 1f);
            ear(c, side, 0x99F0A8B0, 0.62f);
        }
        path.reset();
        path.addOval(new RectF(-0.25f, -0.205f, 0.25f, 0.205f), Path.Direction.CW);
        fill.setShader(new RadialGradient(-0.05f, -0.1f, 0.3f, 0xFFA2978C, 0xFF7A7067, Shader.TileMode.CLAMP));
        c.drawPath(path, fill);
        fill.setShader(null);
        c.save();
        c.clipPath(path);
        ellipse(c, ORANGE, -0.13f, -0.13f, 0.12f, 0.09f); // the orange blaze on her forehead
        ellipse(c, WHITE, 0, 0.1f, 0.15f, 0.11f);          // white muzzle and chin
        line.setColor(0x80403832);
        line.setStrokeWidth(0.016f);
        for (int i = -1; i <= 1; i++) c.drawLine(i * 0.04f, -0.2f, i * 0.035f, -0.13f, line); // the "M" on her brow
        c.restore();
        rim(c);

        for (int side = -1; side <= 1; side += 2) { // eyes, with slit pupils that follow the look
            float ex = side * 0.095f, ey = -0.01f;
            if (open < 0.3f) {
                path.reset();
                path.moveTo(ex - 0.045f, ey);
                path.quadTo(ex, ey + 0.03f, ex + 0.045f, ey);
                stroke(c, 0xFF3B332D, 0.014f);
                continue;
            }
            ellipse(c, 0xFF3B332D, ex, ey, 0.058f, 0.064f * open);
            fill.setShader(new RadialGradient(ex, ey, 0.055f, 0xFFE3E98C, EYE, Shader.TileMode.CLAMP));
            oval.set(ex - 0.05f, ey - 0.056f * open, ex + 0.05f, ey + 0.056f * open);
            c.drawOval(oval, fill);
            fill.setShader(null);
            ellipse(c, PUPIL, ex + lookAt * 0.02f, ey + 0.006f, 0.014f, 0.045f * open);
            ellipse(c, 0xE6FFFFFF, ex - 0.017f, ey - 0.022f * open, 0.011f, 0.011f);
        }
        path.reset(); // nose
        path.moveTo(-0.024f, 0.05f);
        path.lineTo(0.024f, 0.05f);
        path.lineTo(0, 0.076f);
        path.close();
        fill.setColor(NOSE);
        c.drawPath(path, fill);
        if (yawn > 0.05f) {
            ellipse(c, 0xFF5A1E2C, 0, 0.115f, 0.05f * yawn, 0.055f * yawn);
            ellipse(c, NOSE, 0, 0.14f, 0.03f * yawn, 0.02f * yawn);
        } else if (groom > 0.3f) {
            ellipse(c, NOSE, 0.015f, 0.105f, 0.018f, 0.014f); // a little tongue
        } else {
            path.reset();
            path.moveTo(-0.04f, 0.09f);
            path.quadTo(-0.02f, 0.11f, 0, 0.085f);
            path.quadTo(0.02f, 0.11f, 0.04f, 0.09f);
            stroke(c, 0x99403832, 0.009f);
        }
        line.setColor(0x80FFFFFF); // whiskers
        line.setStrokeWidth(0.006f);
        for (int side = -1; side <= 1; side += 2)
            for (int i = 0; i < 3; i++)
                c.drawLine(side * 0.12f, 0.07f + i * 0.015f, side * 0.34f, 0.035f + i * 0.04f, line);
    }

    private void ear(Canvas c, int side, int color, float k) {
        float bx = side * 0.135f, by = -0.15f;
        path.reset();
        path.moveTo(bx + side * 0.1f * k, by + 0.09f * k);
        path.lineTo(bx + side * 0.075f * k, by - 0.165f * k);
        path.quadTo(bx + side * 0.065f * k, by - 0.19f * k, bx + side * 0.045f * k, by - 0.17f * k);
        path.lineTo(bx - side * 0.09f * k, by - 0.02f * k);
        path.close();
        fill.setColor(color);
        c.drawPath(path, fill);
    }

    // Curled up asleep: a loaf, head down on her paws, tail round the front, breathing slowly.
    private void asleep(Canvas c, float time, float p) {
        float settle = Math.min(1f, p / 0.6f);
        float breathe = 1 + 0.03f * (float) Math.sin(time * 1.4f);
        c.save();
        c.translate(0, (1 - settle) * -0.1f);
        path.reset();
        path.addOval(new RectF(-0.4f, -0.34f * breathe, 0.4f, 0), Path.Direction.CW);
        fill.setShader(new LinearGradient(0, -0.34f, 0, 0, 0xFF9B9086, 0xFF6F655C, Shader.TileMode.CLAMP));
        c.drawPath(path, fill);
        fill.setShader(null);
        c.save();
        c.clipPath(path);
        ellipse(c, ORANGE, 0.16f, -0.24f, 0.17f, 0.12f);
        c.restore();
        rim(c);
        path.reset(); // tail round the front
        path.moveTo(0.36f, -0.06f);
        path.quadTo(0.3f, 0.02f, -0.05f, -0.01f);
        stroke(c, FUR_DARK, 0.075f);
        stroke(c, FUR, 0.055f);
        ellipse(c, WHITE, -0.3f, -0.03f, 0.07f, 0.04f);
        c.save();
        c.translate(-0.24f, -0.25f);
        c.scale(0.82f, 0.82f);
        c.rotate(-8);
        head(c, 0.08f, 0, 0, 0);
        c.restore();
        c.restore();
    }

    private void zs(Canvas c, float s, float floor, float time) {
        fill.setTextSize(s * 0.22f);
        fill.setTypeface(Typeface.DEFAULT_BOLD);
        for (int i = 0; i < 3; i++) {
            float k = (time * 0.35f + i / 3f) % 1f;
            fill.setColor(Alive.fade(0xFFFFFFFF, (float) Math.sin(Math.PI * k) * 0.85f));
            c.drawText("z", x - s * 0.1f + k * s * 0.35f, floor - s * (0.5f + k * 0.55f), fill);
        }
    }

    private void stroke(Canvas c, int color, float width) {
        line.setColor(color);
        line.setStrokeWidth(width);
        c.drawPath(path, line);
    }

    // A thin edge in the app's color, so she stands out against the sky.
    private void rim(Canvas c) {
        line.setColor(Alive.fade(accent | 0xFF000000, 0.5f));
        line.setStrokeWidth(0.012f);
        c.drawPath(path, line);
    }

    private void ellipse(Canvas c, int color, float cx, float cy, float rx, float ry) {
        fill.setColor(color);
        oval.set(cx - rx, cy - ry, cx + rx, cy + ry);
        c.drawOval(oval, fill);
    }
}
