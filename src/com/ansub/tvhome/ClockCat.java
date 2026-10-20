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
import android.view.View;
import android.view.ViewParent;

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
// wobbles, grooms a paw, yawns, hops, or takes a short nap. Now and then she jumps down onto the tops of
// the app tiles and hops along them, from one app to the next, and later jumps back up to the clock.
// A button press wakes her, she looks toward what you moved to, and sometimes she goes over to it.
//
// All of it is drawn on its own see-through surface over the page, by its own low-priority thread, so
// the home screen never redraws for it and the remote stays as quick as without it. The page holds the
// clock's place with slot(), and this draws the digits wherever that slot is.
final class ClockCat extends SurfaceView implements SurfaceHolder.Callback {
    static final int SIT = 0, WALK = 1, YAWN = 2, HOP = 3, SLEEP = 4, WAKE = 5, BAT = 6, GROOM = 7, JUMP = 8;
    // Her coat, from the photo: a warm grey-tan tabby with dark stripes, apricot patches, a white
    // chest, chin and paws, green eyes, a pink nose and a dark ringed tail.
    private static final int FUR = 0xFF9B8569, FUR_LIGHT = 0xFFBBA88B, FUR_DARK = 0xFF2F2823, STRIPE = 0xC22F2823,
            ORANGE = 0xFFD39153, PEACH = 0xFFE3AC70, WHITE = 0xFFF4EFE6, EYE = 0xFF9DBE58, PUPIL = 0xFF17151A, NOSE = 0xFFE09598;

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
    private float speed, stride, face = 1, turn = 1; // walking: pace, paw phase, which way she faces
    private long stoppedAt;

    // Where she can stand: ledge 0 is the top of the digits, the rest are the tops of the app tiles.
    // All of it in the clock's own coordinates; (ox, oy) is where the clock sits on this surface.
    private volatile View[] tiles = new View[0];
    private volatile float ox, oy;
    private final Slot slot;
    private int on, goal = -1, hopTo = -1;
    private float jx, jy, jLand; // a jump: where from, and where along the landing ledge (0 to 1)
    private long jAt;
    private float jFor;
    private final float[] lg = new float[3]; // left, right, top of a ledge

    // The top edge of the digits, column by column in clock pixels (NONE where there's no digit), so
    // she can stand on the curve of a 0, balance on the point of a 1 and cling to the slope of a 4.
    private static final float NONE = 1e9f;
    private float[] roof = new float[0];
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
        slot = new Slot(c);
        setTextSize(200);
    }

    // Holds the clock's place in the page's layout, and tells the clock where that is.
    final class Slot extends View {
        Slot(Context c) { super(c); }

        @Override
        protected void onMeasure(int w, int h) {
            synchronized (lock) {
                setMeasuredDimension(Math.round(text.measureText(time()) + pad * 2), Math.round(base + pad));
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            float x = 0, y = 0;
            View v = this;
            ViewParent page = ClockCat.this.getParent();
            while (v != null && v != page) {
                x += v.getLeft();
                y += v.getTop();
                ViewParent up = v.getParent();
                v = up instanceof View ? (View) up : null;
            }
            ox = x;
            oy = y;
        }
    }

    View slot() { return slot; }

    // The tiles she can jump onto. Call again whenever the tiles are rebuilt.
    void setTiles(View[] views) {
        synchronized (lock) {
            tiles = views;
            if (on > views.length || state == JUMP) { on = 0; x = -1; state = SIT; }
            goal = hopTo = -1;
        }
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
        if (slot != null) slot.requestLayout();
    }

    float getTextSizePx() { return size; }

    // How far the digits start in from the view's left edge (the glow's room): line them up with this.
    int glowPad() { return Math.round(pad); }

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
    // If she's out on the tiles, she sometimes goes over to the one that has focus.
    void poke(float lookAtX, View focused) {
        synchronized (lock) {
            long t = SystemClock.uptimeMillis();
            if (state == SLEEP) set(WAKE, t);
            if (x >= 0) lookTo = Math.max(-1f, Math.min(1f, (lookAtX - ox - x) / (catH * 3)));
            lookUntil = t + 3000;
            View[] ts = tiles;
            if (on > 0 && state != SLEEP && rnd.nextFloat() < 0.45f)
                for (int i = 0; i < ts.length; i++) if (ts[i] == focused) goal = i + 1;
        }
    }

    static String debugTime; // tests only: show this instead of the time

    private String time() {
        if (debugTime != null) return debugTime;
        return new SimpleDateFormat(is24 ? "H:mm" : "h:mm", Locale.getDefault()).format(new Date());
    }

    // ---- The digits ------------------------------------------------------------------------------

    private void digits(long t) {
        if (t - lastCheck > 500 || now == null) {
            lastCheck = t;
            String s = time();
            if (!s.equals(shown) || now == null) {
                boolean minute = !shown.isEmpty() && !s.equals(shown);
                if (s.length() != shown.length()) post(slot::requestLayout); // e.g. 9:59 to 10:00
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
        scanRoof(s, at, w, h);
        return b;
    }

    private void scanRoof(String s, float[] at, int w, int h) {
        float[] r = new float[w];
        java.util.Arrays.fill(r, NONE);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(text);
        p.setColor(0xFFFFFFFF);
        for (int i = 0; i < s.length(); i++)
            if (s.charAt(i) != ':') c.drawText(s, i, i + 1, at[i], base, p);
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        b.recycle();
        int top = Math.max(0, Math.round(capTop) - 6);
        for (int x = 0; x < w; x++)
            for (int y = top; y < h; y++)
                if ((px[y * w + x] >>> 24) > 110) { r[x] = y; break; }
        roof = r;
    }

    private float sample(float x) {
        int i = Math.round(x);
        for (int k = 0; k < 4; k++) {
            if (i - k >= 0 && i - k < roof.length && roof[i - k] != NONE) return roof[i - k];
            if (i + k >= 0 && i + k < roof.length && roof[i + k] != NONE) return roof[i + k];
        }
        return NONE;
    }

    // The highest point under her stance at x (clock pixels), or the cap height off the digits.
    private float roofFloor(float x) {
        int a = Math.max(0, Math.round(x - catH * 0.22f)), b = Math.min(roof.length - 1, Math.round(x + catH * 0.22f));
        float m = NONE;
        for (int i = a; i <= b; i++) if (roof[i] < m) m = roof[i];
        return m == NONE ? capTop : m;
    }

    // How the surface under her tilts: the rise per pixel, positive when it climbs to the right.
    private float roofSlope(float x) {
        float d = catH * 0.2f, l = sample(x - d), rr = sample(x + d);
        if (l == NONE || rr == NONE) return 0;
        return (l - rr) / (2 * d);
    }

    // How much of the digit is under her paws: small on the point of a 1, wide on the bar of a 7.
    private float roofSupport(float x) {
        float f = roofFloor(x);
        int a = Math.max(0, Math.round(x - catH * 0.22f)), b = Math.min(roof.length - 1, Math.round(x + catH * 0.22f));
        int n = 0;
        for (int i = a; i <= b; i++) if (roof[i] - f < catH * 0.1f) n++;
        return n;
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
            c.clipRect(l, 0, r, base + pad);
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

    // Ledge i into lg: its left and right (with room for her) and its top. False if it isn't there.
    private boolean ledge(int i) {
        if (i == 0) {
            if (xs.length < 2) return false;
            lg[0] = xs[0] + catH * 0.32f;
            lg[1] = xs[xs.length - 1] - catH * 0.32f;
            lg[2] = capTop;
            return true;
        }
        View[] ts = tiles;
        if (i < 1 || i > ts.length) return false;
        View v = ts[i - 1];
        if (v == null || !v.isShown() || v.getWidth() < catH * 1.4f) return false;
        float x0 = 0, y0 = 0;
        View u = v;
        ViewParent page = getParent();
        while (u != null && u != page) {
            x0 += u.getLeft() + u.getTranslationX();
            y0 += u.getTop() + u.getTranslationY();
            ViewParent up = u.getParent();
            u = up instanceof View ? (View) up : null;
        }
        if (u == null) return false;
        float w = v.getWidth(), h = v.getHeight(), sx = v.getScaleX(), sy = v.getScaleY();
        lg[0] = x0 + w * (1 - sx) / 2 - ox + catH * 0.3f;
        lg[1] = x0 + w * (1 + sx) / 2 - ox - catH * 0.3f;
        lg[2] = y0 + h * (1 - sy) / 2 - oy;
        return true;
    }

    private float left() { return ledge(on) ? lg[0] : 0; }
    private float right() { return ledge(on) ? lg[1] : 0; }
    private float floor() { return floorOf(on, x); }
    private float floorOf(int ledge, float at) { return ledge == 0 ? roofFloor(at) : ledge(ledge) ? lg[2] : capTop; }

    private float digitCenter(int i) { return (xs[i] + xs[i + 1]) / 2; }

    // The next ledge to jump to on the way to goal: one short hop at a time, found by a little search.
    private int nextHop(int from, int to) {
        int n = tiles.length + 1;
        float[][] at = new float[n][];
        for (int i = 0; i < n; i++) if (ledge(i)) at[i] = lg.clone();
        if (at[from] == null || at[to] == null) return -1;
        int[] back = new int[n];
        java.util.Arrays.fill(back, -2);
        back[from] = -1;
        int[] queue = new int[n];
        int head = 0, tail = 0;
        queue[tail++] = from;
        while (head < tail) {
            int i = queue[head++];
            if (i == to) break;
            for (int j = 0; j < n; j++) {
                if (back[j] != -2 || at[j] == null) continue;
                float gap = Math.max(0, Math.max(at[j][0] - at[i][1], at[i][0] - at[j][1]));
                if (gap < catH * 2.6f && Math.abs(at[j][2] - at[i][2]) < catH * 3.4f) {
                    back[j] = i;
                    queue[tail++] = j;
                }
            }
        }
        if (back[to] == -2) return -1;
        int j = to;
        while (back[j] != from) j = back[j];
        return j;
    }

    void frame(Canvas c) {
        long t = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0.04f : Math.min(0.1f, (t - lastFrame) / 1000f);
        lastFrame = t;
        lastDt = dt;
        digits(t);
        if (now == null) return;
        c.translate(ox, oy);
        drawDigits(c, t);
        drawColon(c, t, dt);
        think(t, dt);
        drawCat(c, t);
    }

    private void think(long t, float dt) {
        if (xs.length < 2) return;
        if (x < 0) { on = 0; x = digitCenter(shown.length() - 1); }
        if (on > 0 && !ledge(on) && state != JUMP) { on = 0; x = digitCenter(shown.length() - 1); set(SIT, t); }
        float l = left(), r = right();
        if (t > lookUntil) lookTo = 0;
        look += (lookTo - look) * Math.min(1f, dt * 6);
        float p = (t - since) / 1000f;
        switch (state) {
            case SIT:
                if (x < l || x > r) { target = Math.max(l, Math.min(r, x)); speed = 0; set(WALK, t); break; }
                if (goal >= 0 && goal != on) { if (t - stoppedAt > 350) hop(t); break; }
                goal = -1;
                if (t > nextMove) choose(t);
                break;
            case WALK: {
                // Speed up from a standstill, slow down on arrival, and step in time with the distance.
                float gap = Math.abs(target - x);
                float want = catH * 1.25f * Math.min(1f, gap / (catH * 0.5f) + 0.25f);
                speed += (want - speed) * Math.min(1f, dt * 5);
                float step = speed * dt;
                face = Math.signum(target - x);
                if (gap <= step) {
                    x = target;
                    speed = 0;
                    stoppedAt = t;
                    if (hopTo >= 0) jump(t);
                    else if (swipes > 0) { set(BAT, t); swipeAt = t + 150; } // walked over to bat the colon
                    else set(SIT, t);
                } else {
                    x += face * step;
                    stride += step / (catH * 0.32f) * (float) Math.PI;
                }
                break;
            }
            case JUMP: {
                if (p > CROUCH + jFor) { // landed
                    on = hopTo;
                    hopTo = -1;
                    if (ledge(on)) x = lg[0] + (lg[1] - lg[0]) * jLand;
                    stoppedAt = t;
                    set(SIT, t);
                    nextMove = t + (goal >= 0 && goal != on ? 500 : 2500 + rnd.nextInt(3000));
                }
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
        if (state == BAT) face = Math.signum(colonX - x);
        turn += (face - turn) * Math.min(1f, dt * 9); // turns round smoothly instead of flipping
        if (t > blinkAt + 150) blinkAt = t + 2200 + rnd.nextInt(3500);
    }

    // On the way to goal: walk to the edge nearest the next ledge, then jump.
    private void hop(long t) {
        int n = nextHop(on, goal);
        if (n < 0) { goal = -1; return; }
        if (!ledge(n)) { goal = -1; return; }
        float nl = lg[0], nr = lg[1];
        float land = Math.max(nl, Math.min(nr, x));
        if (land == nl && nr > nl) land = Math.min(nr, nl + catH * 0.2f);
        if (land == nr && nr > nl) land = Math.max(nl, nr - catH * 0.2f);
        jLand = nr > nl ? (land - nl) / (nr - nl) : 0.5f;
        ledge(on);
        float off = Math.max(lg[0], Math.min(lg[1], land));
        hopTo = n;
        swipes = 0;
        if (Math.abs(off - x) > catH * 0.1f) { target = off; speed = 0; set(WALK, t); }
        else jump(t);
    }

    private void jump(long t) {
        jx = x;
        jy = floor();
        if (!ledge(hopTo)) { hopTo = -1; set(SIT, t); return; }
        float tx = lg[0] + (lg[1] - lg[0]) * jLand;
        face = tx == x ? face : Math.signum(tx - x);
        float d = (float) Math.hypot(tx - x, floorOf(hopTo, tx) - jy);
        jFor = 0.36f + d / (catH * 9f);
        jAt = t;
        set(JUMP, t);
    }

    private float napFor;
    private static final float CROUCH = 0.45f; // seconds she gathers herself before a jump

    private void choose(long t) {
        nextMove = t + 4000 + rnd.nextInt(5000);
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        boolean night = hour >= 23 || hour < 6;
        float r = rnd.nextFloat();
        if (r < (night ? 0.35f : 0.1f) && (on > 0 || Math.abs(roofSlope(x)) < 0.4f)) { napFor = 20 + rnd.nextInt(night ? 100 : 25); set(SLEEP, t); return; }
        int n = tiles.length;
        r = rnd.nextFloat();
        if (on > 0) { // out on the tiles
            if (r < 0.4f) { // over to another app, often the next one along
                int g = rnd.nextFloat() < 0.6f ? on + (rnd.nextBoolean() ? 1 : -1) : 1 + rnd.nextInt(n);
                if (g >= 1 && g <= n && g != on) goal = g;
            } else if (r < 0.62f) goal = 0; // back up to the clock
            else if (r < 0.8f) set(GROOM, t);
            else if (r < 0.92f) set(YAWN, t);
            else set(HOP, t);
            return;
        }
        if (r < 0.22f && n > 0) { goal = 1 + rnd.nextInt(n); return; } // down onto the apps
        r = rnd.nextFloat();
        if (r < 0.38f) { // to another digit
            int i;
            do i = rnd.nextInt(shown.length()); while (shown.charAt(i) == ':');
            float spot = rnd.nextFloat() < 0.5f ? 0 : (rnd.nextFloat() - 0.5f) * (xs[i + 1] - xs[i]) * 0.75f;
            target = Math.max(left(), Math.min(right(), digitCenter(i) + spot));
            if (Math.abs(target - x) > catH * 0.2f) { swipes = 0; speed = 0; set(WALK, t); }
        } else if (r < 0.6f && colonX > 0) { // go and bat the colon
            float side = x < colonX ? -1 : 1;
            target = Math.max(left(), Math.min(right(), colonX + side * catH * 0.62f));
            swipes = 3;
            speed = 0;
            set(WALK, t);
        } else if (r < 0.75f) set(GROOM, t);
        else if (r < 0.88f) set(YAWN, t);
        else set(HOP, t);
    }

    private float pitch; // in the air: her nose up or down along the arc, in degrees
    private static final float[] ph = {0, (float) Math.PI, (float) Math.PI * 1.25f, (float) Math.PI * 0.25f}; // paw beats
    private final float[] pawX = new float[4], pawY = new float[4];

    private void drawCat(Canvas c, long t) {
        if (x < 0) return;
        float s = catH, p = (t - since) / 1000f, time = t / 1000f;
        float floor = floor(), cx = x;
        float lift = 0;
        boolean air = false;
        if (state == HOP) lift = (float) Math.sin(Math.PI * Math.min(1f, p / 0.6f)) * 0.3f;
        if (state == WALK && on == 0 && colonX > 0) { // hop over the colon
            float a = colonX - s * 0.45f, b = colonX + s * 0.45f;
            if (x > a && x < b) lift = (float) Math.sin(Math.PI * (x - a) / (b - a)) * 0.32f;
        }
        if (state == JUMP && p > CROUCH) { // in the air: an arc from the takeoff to the landing ledge
            float k = Math.min(1f, (p - CROUCH) / jFor);
            float e = k * k * (3 - 2 * k);
            float tx = jx, ty = jy;
            if (ledge(hopTo)) { tx = lg[0] + (lg[1] - lg[0]) * jLand; ty = floorOf(hopTo, tx); }
            // Jumping up she springs high; jumping down she pushes out and drops.
            float peak = Math.min(jy, ty) - s * ((ty > jy + s * 0.5f ? 0.2f : 0.45f) + Math.abs(tx - jx) / (s * 12));
            float cy = 2 * peak - (jy + ty) / 2;
            cx = jx + (tx - jx) * e;
            floor = (1 - k) * (1 - k) * jy + 2 * k * (1 - k) * cy + k * k * ty;
            float vx = (tx - jx) * 6 * k * (1 - k), vy = 2 * (1 - k) * (cy - jy) + 2 * k * (ty - cy);
            pitch = Math.max(-38f, Math.min(28f, (float) Math.toDegrees(Math.atan2(-vy, Math.abs(vx) + s * 0.6f))));
            air = true;
        }
        // On the digits the ground isn't flat: she stands square to it, hangs from a steep slope
        // and balances on a point.
        boolean still = state == SIT || state == GROOM || state == YAWN || state == WAKE;
        float slope = on == 0 && !air ? roofSlope(cx) : 0;
        boolean hang = on == 0 && !air && still && Math.abs(slope) > 1.2f;
        boolean narrow = on == 0 && !air && still && roofSupport(cx) < s * 0.14f;
        float tilt = (float) Math.toDegrees(Math.atan(slope));

        if (!air && !hang) { // a soft shadow under her
            float sh = 1 - lift * 1.5f;
            fill.setShader(new RadialGradient(cx, floor, s * 0.42f * sh, 0x66000000, 0, Shader.TileMode.CLAMP));
            c.save();
            c.scale(1, 0.16f, cx, floor);
            c.drawCircle(cx, floor, s * 0.42f * sh, fill);
            c.restore();
            fill.setShader(null);
        }

        c.save();
        c.translate(cx, floor - lift * s);
        c.scale(s, s); // from here on, 1 = her height sitting; y goes up as it gets negative
        if (hang) hanging(c, slope > 0 ? -1 : 1, time);
        else {
            if (!air) c.rotate(-Math.max(-35f, Math.min(35f, tilt)));
            if (narrow) c.rotate((float) Math.sin(time * 4.5f) * 3.5f); // keeping her balance
            if (state == SLEEP) asleep(c, time, p);
            else if (state == WALK || state == JUMP) standing(c, t, time, p, air);
            else sitting(c, t, time, p, narrow);
        }
        c.restore();
        if (state == SLEEP) zs(c, s, floor, time);
    }

    // Sitting, like the photo: side-on with her head turned to you, her tail round her front paws.
    private void sitting(Canvas c, long t, float time, float p, boolean narrow) {
        float stretch = state == WAKE ? (float) Math.sin(Math.PI * Math.min(1f, p / 1.2f)) : 0;
        float breathe = 1 + 0.012f * (float) Math.sin(time * 2.2f);
        float settle = 0; // a little squash as she lands or stops
        long stopped = t - stoppedAt;
        if (stopped < 320) settle = (float) Math.sin(Math.PI * stopped / 320f) * 0.06f;
        float mirror = turn >= 0 ? -1 : 1;
        float sx = mirror * (0.6f + 0.4f * Math.abs(turn));
        c.save();
        c.scale(sx * (1 + stretch * 0.12f + settle), (1 - stretch * 0.1f - settle) * breathe, 0, 0);

        // Tail: from the rump, along the floor and round the front; the tip flicks.
        float sway = (float) Math.sin(time * (narrow ? 5f : 1.6f)) * (narrow ? 0.09f : 0.05f);
        path.reset();
        path.moveTo(0.33f, -0.12f);
        path.cubicTo(0.5f, 0.02f, 0.2f, 0.1f, -0.1f, 0.07f);
        path.cubicTo(-0.25f, 0.06f, -0.34f + sway, 0.03f, -0.43f + sway * 1.6f, -0.05f + Math.abs(sway));
        tail(c, -0.43f + sway * 1.6f, -0.05f + Math.abs(sway));
        ellipse(c, WHITE, 0.12f, -0.025f, 0.07f, 0.04f); // a back paw peeping out

        // Body: the chest rises to the head at the front; a big round haunch at the back.
        path.reset();
        path.moveTo(-0.2f, -0.62f);
        path.cubicTo(0.05f, -0.64f, 0.3f, -0.58f, 0.36f, -0.4f);
        path.cubicTo(0.42f, -0.25f, 0.4f, -0.04f, 0.3f, 0f);
        path.lineTo(-0.1f, 0f);
        path.cubicTo(-0.22f, 0f, -0.27f, -0.3f, -0.25f, -0.45f);
        path.cubicTo(-0.25f, -0.55f, -0.23f, -0.62f, -0.2f, -0.62f);
        fill.setShader(new LinearGradient(0, -0.64f, 0, 0, FUR_LIGHT, FUR, Shader.TileMode.CLAMP));
        c.drawPath(path, fill);
        fill.setShader(null);
        c.save();
        c.clipPath(path);
        ellipse(c, ORANGE, 0.16f, -0.53f, 0.14f, 0.08f);
        ellipse(c, ORANGE, 0.33f, -0.24f, 0.1f, 0.14f);
        ellipse(c, PEACH, 0.02f, -0.3f, 0.07f, 0.09f);
        ellipse(c, PEACH, 0.22f, -0.08f, 0.09f, 0.06f);
        ellipse(c, WHITE, -0.17f, -0.42f, 0.1f, 0.2f); // her white bib
        line.setColor(STRIPE); // tabby bands over the back and haunch
        line.setStrokeWidth(0.035f);
        for (int i = 0; i < 4; i++) {
            float bx = 0.02f + i * 0.1f;
            path.reset();
            path.moveTo(bx, -0.6f);
            path.quadTo(bx + 0.14f, -0.35f, bx + 0.08f, -0.06f);
            c.drawPath(path, line);
        }
        c.restore();
        rim(c);

        // Front legs, side by side at the front: one up to swipe at the colon or to lick.
        float swipe = 0;
        if (state == BAT) {
            long sw = t - swipeAt;
            if (sw >= 0 && sw < 360) swipe = (float) Math.sin(Math.PI * sw / 360f);
        }
        float groom = state == GROOM ? (float) Math.min(1, Math.sin(Math.PI * Math.min(1f, p / 2.4f)) * 1.6f) : 0;
        float fx = -0.21f - swipe * 0.22f, fy = -swipe * 0.26f, bx = -0.1f, by = 0;
        if (groom > 0) { fx = -0.21f - groom * 0.06f; fy = -groom * 0.5f; }
        leg(c, FUR, -0.12f, -0.38f, bx, by, 0.02f);
        leg(c, FUR, -0.17f, -0.4f, fx, fy, 0.02f);
        ellipse(c, WHITE, bx, by - 0.02f, 0.07f, 0.045f);
        ellipse(c, WHITE, fx, fy - 0.02f, 0.07f, 0.045f);

        // Head: tilts toward where she's looking; back for a yawn; down to lick a paw.
        boolean yawn = state == YAWN;
        float y = yawn ? (float) Math.sin(Math.PI * Math.min(1f, p / 1.6f)) : 0;
        float lookAt = state == BAT ? -0.8f : look * mirror; // in her own facing
        c.save();
        c.translate(-0.2f, -0.8f - y * 0.02f + groom * 0.03f);
        c.scale(1.08f, 1.08f);
        c.rotate(lookAt * 8 - y * 4 + groom * 10);
        boolean blink = t >= blinkAt && t < blinkAt + 150;
        float open = yawn || stretch > 0.2f || groom > 0.3f ? 0.08f : blink ? 0.1f : 1f;
        head(c, open, y, lookAt, groom);
        c.restore();
        c.restore();
    }

    // Up on all fours, side-on: walking, crouching to spring, stretched out in the air, and landing.
    private void standing(Canvas c, long t, float time, float p, boolean air) {
        boolean walk = state == WALK;
        float pace = walk ? Math.min(1f, speed / (catH * 1.25f)) : 0;
        float crouch = state == JUMP && !air ? Math.min(1f, p / 0.2f) : 0;
        float k = air ? Math.min(1f, (p - CROUCH) / jFor) : 0;
        float reach = air ? Math.max(0f, (k - 0.6f) / 0.4f) : 0; // front legs come down to land
        float push = air ? Math.max(0f, 1 - k / 0.25f) : 0;      // back legs still pushing off
        float rise = walk ? Math.min(1f, (t - since) / 220f) : 1; // gets up as she sets off
        float wiggle = crouch > 0.5f ? (float) Math.sin(time * 24) * 0.012f : 0; // the pounce wiggle
        float mirror = turn >= 0 ? -1 : 1;
        float sx = mirror * (0.6f + 0.4f * Math.abs(turn));
        float bob = Math.abs((float) Math.sin(stride)) * 0.03f * pace;
        float down = crouch * 0.15f + (1 - rise) * 0.12f;
        c.save();
        c.scale(sx, 1, 0, 0);
        c.translate(0, -bob + down);
        if (air) {
            c.translate(0, -0.45f);
            c.rotate(pitch);
            c.scale(1 + 0.18f * (1 - reach), 0.94f);
            c.translate(0, 0.45f);
        }

        // Legs: a walk, the near pair over the body and the far pair behind it. Front legs at
        // -0.2, back legs at 0.22; each paw swings forward and lifts on its own beat.
        float[] px = pawX, py = pawY;
        for (int i = 0; i < 4; i++) {
            boolean front = i < 2;
            float bx = front ? -0.2f : 0.22f;
            float ang = stride + ph[i];
            px[i] = bx + (float) Math.cos(ang) * 0.13f * pace + (i % 2 == 0 ? 0 : 0.03f);
            py[i] = -Math.max(0, (float) Math.sin(ang)) * 0.1f * pace - down;
            if (crouch > 0) { if (!front) px[i] -= 0.1f * crouch; else px[i] -= 0.03f * crouch; }
            if (air) {
                if (front) { px[i] = -0.4f + reach * 0.08f + (i % 2) * 0.05f; py[i] = -0.42f + reach * 0.38f; }
                else { px[i] = 0.5f - reach * 0.18f - push * 0.1f + (i % 2) * 0.04f; py[i] = -0.3f + push * 0.25f + reach * 0.05f; }
            }
        }
        for (int i = 1; i < 4; i += 2) leg(c, Alive.mix(FUR, FUR_DARK, 0.3f), i < 2 ? -0.18f : 0.2f, -0.42f - down, px[i], py[i], i < 2 ? 0.04f : -0.06f);

        // Tail: up in a curl as she walks, low and twitching in the crouch, straight out in the air.
        float sway = (float) Math.sin(time * 5) * 0.06f;
        path.reset();
        path.moveTo(0.34f, -0.5f);
        float tipX, tipY;
        if (air) { tipX = 0.9f; tipY = -0.56f + sway * 0.3f - reach * 0.2f; path.cubicTo(0.55f, -0.52f, 0.72f, -0.5f - reach * 0.15f, tipX, tipY); }
        else if (crouch > 0) { tipX = 0.72f + wiggle * 4; tipY = -0.4f; path.cubicTo(0.5f, -0.42f, 0.62f, -0.28f, tipX, tipY); }
        else { tipX = 0.46f + sway; tipY = -0.97f; path.cubicTo(0.5f, -0.58f, 0.62f, -0.82f, tipX, tipY); }
        tail(c, tipX, tipY);

        // Body: lit from above, apricot patches along the back, white below, tabby bands.
        c.save();
        c.translate(wiggle, 0);
        path.reset();
        path.moveTo(-0.3f, -0.5f);
        path.cubicTo(-0.12f, -0.67f, 0.18f, -0.69f, 0.36f, -0.52f);
        path.cubicTo(0.44f, -0.44f, 0.42f, -0.3f, 0.3f, -0.28f);
        path.cubicTo(0.1f, -0.23f, -0.14f, -0.23f, -0.3f, -0.3f);
        path.cubicTo(-0.4f, -0.34f, -0.4f, -0.45f, -0.3f, -0.5f);
        fill.setShader(new LinearGradient(0, -0.68f, 0, -0.24f, FUR_LIGHT, FUR, Shader.TileMode.CLAMP));
        c.drawPath(path, fill);
        fill.setShader(null);
        c.save();
        c.clipPath(path);
        ellipse(c, ORANGE, 0.12f, -0.62f, 0.17f, 0.09f);
        ellipse(c, ORANGE, 0.32f, -0.42f, 0.1f, 0.1f);
        ellipse(c, PEACH, -0.14f, -0.4f, 0.07f, 0.08f);
        ellipse(c, PEACH, 0.08f, -0.36f, 0.08f, 0.06f);
        ellipse(c, WHITE, -0.04f, -0.26f, 0.24f, 0.07f);
        ellipse(c, WHITE, -0.3f, -0.4f, 0.08f, 0.12f);
        line.setColor(STRIPE);
        line.setStrokeWidth(0.035f);
        for (int i = 0; i < 4; i++) {
            float bx = -0.16f + i * 0.14f;
            path.reset();
            path.moveTo(bx, -0.68f);
            path.quadTo(bx + 0.04f, -0.46f, bx - 0.02f, -0.3f);
            c.drawPath(path, line);
        }
        c.restore();
        rim(c);
        c.restore();

        for (int i = 0; i < 4; i += 2) leg(c, FUR, i < 2 ? -0.2f : 0.22f, -0.42f - down, px[i], py[i], i < 2 ? 0.04f : -0.06f);
        for (int i = 0; i < 4; i++) ellipse(c, i % 2 == 0 ? WHITE : 0xFFD9D3C8, px[i], py[i] - 0.02f, 0.065f, 0.04f);

        // Head: out in front, down a little in the crouch, eyes wide in the air.
        c.save();
        c.translate(-0.37f - (air ? 0.04f : 0), -0.6f + crouch * 0.06f - down * 0.3f);
        c.scale(0.88f, 0.88f);
        c.rotate(crouch * 6 - pace * 3);
        boolean blink = t >= blinkAt && t < blinkAt + 150;
        head(c, air ? 1f : blink ? 0.1f : 1f, 0, -0.8f, 0);
        c.restore();
        c.restore();
    }

    // Hanging by one front paw from a steep slope (the side of a 4), the rest of her dangling.
    private void hanging(Canvas c, int dir, float time) {
        float swing = (float) Math.sin(time * 1.8f) * 5;
        c.save();
        c.scale(dir, 1);
        c.rotate(swing);
        leg(c, FUR, 0.12f, 0.42f, 0, 0, -0.02f); // the hooked leg, paw at the origin
        ellipse(c, WHITE, 0, 0, 0.07f, 0.045f);
        // the free front leg and the back legs hang loose and sway
        float loose = (float) Math.sin(time * 2.3f) * 0.03f;
        leg(c, Alive.mix(FUR, FUR_DARK, 0.3f), 0.3f, 0.48f, 0.4f + loose, 0.78f, 0.02f);
        ellipse(c, 0xFFD9D3C8, 0.4f + loose, 0.78f, 0.06f, 0.04f);
        leg(c, FUR, 0.14f, 0.95f, 0.1f - loose, 1.24f, 0.03f);
        leg(c, Alive.mix(FUR, FUR_DARK, 0.3f), 0.3f, 0.95f, 0.34f + loose, 1.22f, 0.03f);
        ellipse(c, WHITE, 0.1f - loose, 1.24f, 0.065f, 0.04f);
        ellipse(c, 0xFFD9D3C8, 0.34f + loose, 1.22f, 0.065f, 0.04f);
        path.reset(); // tail
        path.moveTo(0.36f, 0.95f);
        path.cubicTo(0.56f, 1.1f, 0.62f + loose * 2, 1.32f, 0.46f + loose * 3, 1.46f);
        tail(c, 0.46f + loose * 3, 1.46f);
        path.reset(); // body, hanging below her shoulder
        oval.set(0.0f, 0.4f, 0.42f, 1.04f);
        path.addOval(oval, Path.Direction.CW);
        fill.setShader(new LinearGradient(0.42f, 0, 0, 0, FUR_LIGHT, FUR, Shader.TileMode.CLAMP));
        c.drawPath(path, fill);
        fill.setShader(null);
        c.save();
        c.clipPath(path);
        ellipse(c, ORANGE, 0.36f, 0.62f, 0.1f, 0.14f);
        ellipse(c, WHITE, 0.12f, 0.64f, 0.1f, 0.2f);
        line.setColor(STRIPE);
        line.setStrokeWidth(0.035f);
        for (int i = 0; i < 3; i++) {
            float by = 0.55f + i * 0.14f;
            path.reset();
            path.moveTo(0.42f, by);
            path.quadTo(0.3f, by + 0.06f, 0.2f, by + 0.02f);
            c.drawPath(path, line);
        }
        c.restore();
        rim(c);
        c.save(); // head, looking at you
        c.translate(0.14f, 0.34f);
        c.scale(0.95f, 0.95f);
        c.rotate(-8);
        long t = SystemClock.uptimeMillis();
        boolean blink = t >= blinkAt && t < blinkAt + 150;
        head(c, blink ? 0.1f : 1f, 0, look * -dir, 0);
        c.restore();
        c.restore();
    }

    // A leg from (ax, ay) to the paw at (px, py), bending a little at the knee.
    private void leg(Canvas c, int color, float ax, float ay, float px, float py, float bend) {
        path.reset();
        path.moveTo(ax, ay);
        path.quadTo((ax + px) / 2 + bend, (ay + py) / 2, px, py);
        line.setColor(color);
        line.setStrokeWidth(0.09f);
        c.drawPath(path, line);
        line.setColor(STRIPE); // a couple of tabby bars on the leg
        line.setStrokeWidth(0.03f);
        for (int i = 1; i <= 2; i++) {
            float k = i / 3.6f, lx = ax + (px - ax) * k, ly = ay + (py - ay) * k;
            c.drawLine(lx - 0.035f, ly, lx + 0.035f, ly, line);
        }
    }

    // Draws the tail along `path`: fur, dark rings, and a dark tip at (tx, ty).
    private void tail(Canvas c, float tx, float ty) {
        stroke(c, FUR_DARK, 0.09f);
        stroke(c, FUR, 0.068f);
        line.setColor(FUR_DARK);
        line.setStrokeWidth(0.075f);
        line.setPathEffect(new android.graphics.DashPathEffect(new float[] {0.04f, 0.085f}, 0.02f));
        c.drawPath(path, line);
        line.setPathEffect(null);
        ellipse(c, FUR_DARK, tx, ty, 0.05f, 0.05f);
    }

    private void head(Canvas c, float open, float yawn, float lookAt, float groom) {
        for (int side = -1; side <= 1; side += 2) { // big ears, dark-edged, pink inside; one apricot
            ear(c, side, FUR_DARK, 1.08f);
            ear(c, side, side < 0 ? Alive.mix(FUR, ORANGE, 0.6f) : FUR, 0.98f);
            ear(c, side, 0xC9ECB9BC, 0.62f);
        }
        path.reset();
        path.addOval(new RectF(-0.27f, -0.215f, 0.27f, 0.225f), Path.Direction.CW);
        fill.setShader(new RadialGradient(-0.05f, -0.1f, 0.34f, FUR_LIGHT, FUR, Shader.TileMode.CLAMP));
        c.drawPath(path, fill);
        fill.setShader(null);
        c.save();
        c.clipPath(path);
        ellipse(c, ORANGE, -0.1f, -0.11f, 0.11f, 0.1f);      // the apricot blaze over one eye
        ellipse(c, PEACH, -0.04f, -0.2f, 0.05f, 0.05f);
        ellipse(c, PEACH, 0.19f, 0.07f, 0.08f, 0.07f);        // and an apricot cheek
        ellipse(c, WHITE, 0, 0.11f, 0.16f, 0.115f);           // white muzzle and chin
        line.setColor(STRIPE);
        line.setStrokeWidth(0.02f);
        for (int i = -1; i <= 1; i++) c.drawLine(i * 0.05f, -0.22f, i * 0.045f, -0.13f, line); // the tabby "M"
        c.drawLine(-0.1f, -0.22f, -0.085f, -0.15f, line);
        c.drawLine(0.1f, -0.22f, 0.085f, -0.15f, line);
        for (int side = -1; side <= 1; side += 2) { // stripes out from the eyes over the cheeks
            c.drawLine(side * 0.17f, -0.07f, side * 0.27f, -0.11f, line);
            c.drawLine(side * 0.18f, 0.0f, side * 0.28f, 0.0f, line);
        }
        c.restore();
        rim(c);

        for (int side = -1; side <= 1; side += 2) { // big round green eyes that follow the look
            float ex = side * 0.1f, ey = -0.015f;
            if (open < 0.3f) {
                path.reset();
                path.moveTo(ex - 0.055f, ey);
                path.quadTo(ex, ey + 0.04f, ex + 0.055f, ey);
                stroke(c, 0xFF2F2823, 0.016f);
                continue;
            }
            ellipse(c, 0xFF2F2823, ex, ey, 0.074f, 0.08f * open);
            fill.setShader(new RadialGradient(ex, ey + 0.01f, 0.07f, 0xFFDCE69A, EYE, Shader.TileMode.CLAMP));
            oval.set(ex - 0.065f, ey - 0.071f * open, ex + 0.065f, ey + 0.071f * open);
            c.drawOval(oval, fill);
            fill.setShader(null);
            ellipse(c, PUPIL, ex + lookAt * 0.025f, ey + 0.008f, 0.03f, 0.052f * open);
            ellipse(c, 0xF2FFFFFF, ex - 0.024f, ey - 0.028f * open, 0.016f, 0.016f);
            ellipse(c, 0x99FFFFFF, ex + 0.02f, ey + 0.03f * open, 0.008f, 0.008f);
        }
        path.reset(); // pink nose with a dark edge
        path.moveTo(-0.027f, 0.052f);
        path.lineTo(0.027f, 0.052f);
        path.lineTo(0, 0.082f);
        path.close();
        fill.setColor(NOSE);
        c.drawPath(path, fill);
        stroke(c, 0xB32F2823, 0.008f);
        if (yawn > 0.05f) {
            ellipse(c, 0xFF5A1E2C, 0, 0.12f, 0.05f * yawn, 0.055f * yawn);
            ellipse(c, NOSE, 0, 0.145f, 0.03f * yawn, 0.02f * yawn);
        } else if (groom > 0.3f) {
            ellipse(c, NOSE, 0.015f, 0.11f, 0.018f, 0.014f); // a little tongue
        } else {
            path.reset();
            path.moveTo(-0.04f, 0.095f);
            path.quadTo(-0.02f, 0.115f, 0, 0.09f);
            path.quadTo(0.02f, 0.115f, 0.04f, 0.095f);
            stroke(c, 0x99403832, 0.009f);
        }
        line.setColor(0x80FFFFFF); // whiskers
        line.setStrokeWidth(0.006f);
        for (int side = -1; side <= 1; side += 2)
            for (int i = 0; i < 3; i++)
                c.drawLine(side * 0.13f, 0.075f + i * 0.015f, side * 0.36f, 0.035f + i * 0.04f, line);
    }

    private void ear(Canvas c, int side, int color, float k) {
        float bx = side * 0.14f, by = -0.15f;
        path.reset();
        path.moveTo(bx + side * 0.11f * k, by + 0.09f * k);
        path.lineTo(bx + side * 0.085f * k, by - 0.19f * k);
        path.quadTo(bx + side * 0.07f * k, by - 0.215f * k, bx + side * 0.045f * k, by - 0.19f * k);
        path.lineTo(bx - side * 0.1f * k, by - 0.02f * k);
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
        fill.setShader(new LinearGradient(0, -0.34f, 0, 0, FUR_LIGHT, FUR, Shader.TileMode.CLAMP));
        c.drawPath(path, fill);
        fill.setShader(null);
        c.save();
        c.clipPath(path);
        ellipse(c, ORANGE, 0.16f, -0.24f, 0.17f, 0.12f);
        line.setColor(STRIPE);
        line.setStrokeWidth(0.035f);
        for (int i = 0; i < 4; i++) {
            float bx = -0.1f + i * 0.12f;
            path.reset();
            path.moveTo(bx, -0.34f);
            path.quadTo(bx + 0.05f, -0.18f, bx, -0.02f);
            c.drawPath(path, line);
        }
        c.restore();
        rim(c);
        path.reset(); // tail round the front
        path.moveTo(0.36f, -0.06f);
        path.quadTo(0.3f, 0.02f, -0.05f, -0.01f);
        tail(c, -0.05f, -0.01f);
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

    // A thin dark outline, so she reads cleanly on any sky.
    private void rim(Canvas c) {
        line.setColor(0xB3352E29);
        line.setStrokeWidth(0.012f);
        c.drawPath(path, line);
    }

    private void ellipse(Canvas c, int color, float cx, float cy, float rx, float ry) {
        fill.setColor(color);
        oval.set(cx - rx, cy - ry, cx + rx, cy + ry);
        c.drawOval(oval, fill);
    }
}
