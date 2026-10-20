package com.ansub.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// A calico tabby cat that lives on the home screen and plays with a ball of yarn in the focused app's color.
//
// It isn't a drawing played back: it's a little skeleton moved by simple physics (procedural
// animation). Each paw stays planted until the body has moved past it, then steps, in a diagonal gait;
// the legs bend with two-bone IK; the tail follows with a lag; the head turns to what it's watching.
// The ball rolls, slows, falls off ledges and bounces. The cat walks along floors (the bottom of the
// screen, the tops of the bottom row of tiles, the top of the clock) and jumps between them.
//
// What it does: chases the ball and bats it, sometimes crouches and pounces, sits and looks around,
// wanders, and curls up to sleep when the remote has been quiet a while. A button press wakes it, and
// it looks at what you moved to. It runs on its own timer (it still moves when the TV's animations
// are off) and stops whenever the home screen isn't showing.
final class Pet extends View {

    // A floor it can stand on: from x0 to x1 at height y, in this view's pixels.
    static final class Floor {
        final float x0, x1, y;
        Floor(float x0, float x1, float y) { this.x0 = x0; this.x1 = x1; this.y = y; }
        boolean same(Floor o) { return Math.abs(x0 - o.x0) < 1 && Math.abs(x1 - o.x1) < 1 && Math.abs(y - o.y) < 1; }
    }

    // A calico tabby, like Vikas's cat: grey-brown with dark stripes, orange patches, a white chest and
    // paws, tall ears, yellow-green eyes, and a dark banded tail.
    private static final int FUR = 0xFF8F8173, FUR_DARK = 0xFF3F362F, FUR_FAR = 0xFF6E6257, CREAM = 0xFFF3F0EA,
            ORANGE = 0xFFC98A4E, PINK = 0xFFE7A3A3, EYE = 0xFFC2CB6E, PUPIL = 0xFF1C1A1F;
    private static final long NAP_AFTER = 150 * 1000; // quiet remote this long: time for a nap

    // What it's doing right now, and what it's up to overall.
    private static final int STAND = 0, SIT = 1, LIE = 2, MOVE = 3, JUMP = 4, BAT = 5, CROUCH = 6;
    private static final int PLAY = 0, REST = 1, WANDER = 2, SLEEP = 3;

    private final List<Floor> floors = new ArrayList<>();
    private final Random rnd = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();
    private boolean running;
    private float L; // its body length in pixels; everything about it is in fractions of this

    // The cat.
    float cx = -1, gy;          // the point on the ground under its middle
    int dir = 1;                // facing right (1) or left (-1)
    private float vx;           // walking speed, pixels a second
    private Floor floor;        // what it's standing on (null in the air)
    int mode = STAND, goal = REST;
    private long modeAt, goalAt, goalFor, lastPoke, blinkAt, lookUntil, lastTick;
    private float targetX;      // where it's walking to on this floor
    private Floor jumpTo;       // where a jump lands
    private float jx0, jy0, jx1, jy1, jumpTime;
    private int bats, batsWanted;
    private boolean hit;        // the swipe connected
    private float lookX, lookY; // what it's looking at
    // How much it's sitting, lying, crouching (eased toward what the mode wants), and a walk cycle.
    private float sit, lie, crouch, phase;
    // Paws: 0 front near, 1 front far, 2 back near, 3 back far.
    private final float[] fx = new float[4], fy = new float[4], sx = new float[4], sy = new float[4], tx = new float[4];
    private final long[] stepAt = new long[4];
    private final boolean[] stepping = new boolean[4];
    private float stepDur;

    // The ball.
    float bx = -1, by;
    private float bvx, bvy, spin;
    private Floor ballFloor;
    private int accent = Alive.NEUTRAL;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            invalidate();
            handler.postDelayed(this, mode == LIE ? 100 : 33);
        }
    };

    // Fur: short strokes fixed in the body's own coordinates (along the spine, across it), so the
    // texture moves with the cat. Seeded, so it's the same cat every time.
    private static final int FUR_N = 170;
    private final float[] furT = new float[FUR_N], furS = new float[FUR_N], furLen = new float[FUR_N];
    private final int[] furCol = new int[FUR_N];
    private static final int[] GREYS = {0xFFAA9C8B, 0xFF8C7F71, 0xFF6F6358, 0xFF4E443C, 0xFFC0B4A4, 0xFF3B332D};
    private static final int[] ORANGES = {0xFFD69C61, 0xFFBE8045, 0xFFE2B17A};

    Pet(Context c) {
        super(c);
        Random r = new Random(42);
        for (int i = 0; i < FUR_N; i++) {
            furT[i] = r.nextFloat();
            furS[i] = r.nextFloat() * 2 - 1; // -1 the back, 1 the belly
            furLen[i] = 0.03f + r.nextFloat() * 0.03f;
            boolean patch = furT[i] < 0.38f && furS[i] < 0.1f || furT[i] > 0.48f && furT[i] < 0.72f && furS[i] < -0.35f;
            furCol[i] = patch ? ORANGES[r.nextInt(ORANGES.length)] : GREYS[r.nextInt(GREYS.length)];
            if (furS[i] > 0.55f && furT[i] > 0.6f) furCol[i] = 0xFFF1EDE6; // white under the chest
        }
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        long now = SystemClock.uptimeMillis();
        lastPoke = modeAt = goalAt = now;
        goalFor = 4000;
        blinkAt = now + 2000;
    }

    void start() {
        if (running) return;
        running = true;
        lastTick = 0;
        handler.post(tick);
    }

    void stop() {
        running = false;
        handler.removeCallbacks(tick);
    }

    void setAccent(int color) { accent = color; }

    // The floors, from the screen's layout. The first is the ground along the bottom of the screen.
    void setFloors(List<Floor> list, float bodyLength) {
        L = bodyLength;
        boolean same = list.size() == floors.size();
        for (int i = 0; same && i < list.size(); i++) same = list.get(i).same(floors.get(i));
        if (same) return;
        floors.clear();
        floors.addAll(list);
        if (floors.isEmpty()) return;
        Floor ground = floors.get(0);
        if (cx < 0 || floor != null && !floors.contains(floor)) { // a fresh start, on the ground
            floor = ground;
            cx = ground.x0 + (ground.x1 - ground.x0) * 0.7f;
            gy = ground.y;
            plant();
        }
        if (bx < 0 || ballFloor != null && !floors.contains(ballFloor)) {
            bx = cx - L * 1.4f;
            by = ground.y - ballR();
            ballFloor = ground;
            bvx = bvy = 0;
        }
        if (jumpTo != null && !floors.contains(jumpTo)) jumpTo = ground;
    }

    // The remote was used: wake up, and look at lookAt (this view's pixels) for a few seconds.
    void poke(float lookAtX, float lookAtY) {
        long now = SystemClock.uptimeMillis();
        lastPoke = now;
        lookX = lookAtX;
        lookY = lookAtY;
        lookUntil = now + 3000;
        if (goal == SLEEP) setGoal(REST, now, 5000);
    }

    private float ballR() { return L * 0.11f; }

    private void setMode(int m, long now) {
        if (mode != m) modeAt = now;
        mode = m;
    }

    private void setGoal(int g, long now, long forMs) {
        goal = g;
        goalAt = now;
        goalFor = forMs;
        if (g == PLAY) { bats = 0; batsWanted = 4 + rnd.nextInt(7); }
        if (g == WANDER) {
            Floor f = pickFloor();
            jumpTo = f;
            targetX = f.x0 + L * 0.4f + rnd.nextFloat() * Math.max(0, f.x1 - f.x0 - L * 0.8f);
        }
    }

    // Mostly the ground, sometimes a tile top or the clock.
    private Floor pickFloor() {
        if (floors.size() == 1 || rnd.nextFloat() < 0.5f) return floors.get(0);
        return floors.get(1 + rnd.nextInt(floors.size() - 1));
    }

    // ---- Each frame ------------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas c) {
        if (floors.isEmpty() || L <= 0 || cx < 0) return;
        long now = SystemClock.uptimeMillis();
        float dt = lastTick == 0 ? 0.033f : Math.min(0.1f, (now - lastTick) / 1000f);
        lastTick = now;
        ball(dt);
        think(now);
        body(now, dt);
        drawShadow(c);
        drawBall(c);
        drawCat(c, now);
    }

    private void ball(float dt) {
        float r = ballR(), g = 26 * L;
        if (ballFloor != null) {
            by = ballFloor.y - r;
            bx += bvx * dt;
            bvx *= (float) Math.pow(0.45, dt); // rolls to a stop
            if (Math.abs(bvx) < L * 0.05f) bvx = 0;
            if (bx < ballFloor.x0 - r * 0.2f || bx > ballFloor.x1 + r * 0.2f) { ballFloor = null; bvy = 0; } // off the edge
        } else {
            float prevBottom = by + r;
            bvy += g * dt;
            bx += bvx * dt;
            by += bvy * dt;
            for (Floor f : floors) {
                if (prevBottom <= f.y + 1 && by + r >= f.y && bx >= f.x0 && bx <= f.x1) {
                    by = f.y - r;
                    if (bvy > L * 3) { bvy = -bvy * 0.38f; bvx *= 0.8f; } // a bounce
                    else { bvy = 0; ballFloor = f; }
                    break;
                }
            }
        }
        int w = getWidth();
        if (bx < r) { bx = r; bvx = Math.abs(bvx) * 0.6f; }
        if (w > 0 && bx > w - r) { bx = w - r; bvx = -Math.abs(bvx) * 0.6f; }
        if (getHeight() > 0 && by > getHeight()) { bx = floors.get(0).x0 + w / 2f; by = floors.get(0).y - r; ballFloor = floors.get(0); bvx = 0; }
        spin += bvx * dt / r;
    }

    private void think(long now) {
        if (mode == JUMP) return; // in the air: nothing to decide until it lands
        if (goal != SLEEP && now - lastPoke > NAP_AFTER) setGoal(SLEEP, now, Long.MAX_VALUE);
        boolean done = now - goalAt > goalFor;
        switch (goal) {
            case SLEEP:
                vx = 0;
                setMode(LIE, now);
                return;
            case REST:
                vx = 0;
                if (mode == MOVE || mode == BAT || mode == CROUCH || mode == LIE) setMode(STAND, now);
                if (mode == STAND && now - modeAt > 900) setMode(SIT, now);
                if (done) {
                    float r = rnd.nextFloat();
                    setGoal(r < 0.55f ? PLAY : r < 0.85f ? WANDER : REST, now, r < 0.85f ? 60000 : 6000 + rnd.nextInt(8000));
                }
                return;
            case WANDER:
                if (goTo(jumpTo, targetX, now, false)) setGoal(REST, now, 6000 + rnd.nextInt(9000));
                else if (done) setGoal(REST, now, 4000);
                return;
            case PLAY:
                play(now);
                if (done) setGoal(REST, now, 5000);
        }
    }

    private void play(long now) {
        if (ballFloor == null) { // the ball is in the air: watch it come down
            vx *= 0.8f;
            if (mode != BAT) setMode(STAND, now);
            return;
        }
        if (mode == BAT || mode == CROUCH) return; // finishing a swipe or a pounce
        float dx = bx - cx;
        int side = dx >= 0 ? 1 : -1;
        float standAt = bx - side * L * 0.55f;
        if (ballFloor != floor || Math.abs(dx) > L * 0.75f) {
            goTo(ballFloor, standAt, now, Math.abs(dx) > L * 3);
            return;
        }
        vx = 0;
        dir = side;
        if (Math.abs(bvx) > L * 0.6f) { setMode(STAND, now); return; } // let it roll
        if (bats >= batsWanted) { setGoal(REST, now, 6000 + rnd.nextInt(8000)); return; }
        bats++;
        hit = false;
        setMode(rnd.nextFloat() < 0.25f ? CROUCH : BAT, now);
    }

    // Walks (or runs) to x on floor f, jumping there if it's another floor. True once it's there.
    private boolean goTo(Floor f, float x, long now, boolean run) {
        if (f == null) return true;
        x = Math.max(f.x0 + L * 0.35f, Math.min(f.x1 - L * 0.35f, x));
        if (f != floor) {
            // Walk to the best spot to jump from, then jump.
            float from = Math.max(floor.x0 + L * 0.35f, Math.min(floor.x1 - L * 0.35f, x));
            if (Math.abs(from - cx) > L * 0.15f) { walk(from, now, run); return false; }
            startJump(f, x, now);
            return false;
        }
        if (Math.abs(x - cx) < L * 0.08f) { vx = 0; if (mode == MOVE) setMode(STAND, now); return true; }
        walk(x, now, run);
        return false;
    }

    private void walk(float x, long now, boolean run) {
        targetX = x;
        dir = x > cx ? 1 : -1;
        float speed = (run ? 2.6f : 1.1f) * L;
        float want = dir * speed * Math.min(1f, Math.abs(x - cx) / (L * 0.5f) + 0.25f);
        vx += (want - vx) * 0.15f;
        setMode(MOVE, now);
    }

    private void startJump(Floor to, float x, long now) {
        jumpTo = to;
        jx0 = cx;
        jy0 = gy;
        jx1 = x;
        jy1 = to.y;
        dir = jx1 >= jx0 ? 1 : -1;
        float dy = Math.abs(jy1 - jy0), dx = Math.abs(jx1 - jx0);
        jumpTime = Math.max(0.45f, Math.min(1.0f, 0.4f + dy / (L * 16) + dx / (L * 25)));
        crouch = 1;
        floor = null;
        vx = 0;
        setMode(JUMP, now);
    }

    private void body(long now, float dt) {
        float wantSit = 0, wantLie = 0, wantCrouch = 0;
        float p = (now - modeAt) / 1000f;
        switch (mode) {
            case SIT: wantSit = 1; break;
            case LIE: wantLie = 1; break;
            case CROUCH: {
                // Wiggle, then pounce at the ball.
                wantCrouch = 1;
                if (p > 0.7f) {
                    Floor f = ballFloor != null ? ballFloor : floor;
                    startJump(f, bx - dir * L * 0.3f, now);
                    hit = false;
                }
                break;
            }
            case BAT:
                if (!hit && p > 0.16f && Math.abs(bx - cx) < L * 0.9f) {
                    hit = true;
                    bvx = dir * L * (4f + rnd.nextFloat() * 5f);
                    if (rnd.nextFloat() < 0.35f && ballFloor != null) { bvy = -L * (3 + rnd.nextFloat() * 4); ballFloor = null; }
                }
                if (p > 0.38f) setMode(STAND, now);
                break;
            case JUMP: {
                float q = Math.min(1f, p / jumpTime);
                cx = jx0 + (jx1 - jx0) * q;
                float apex = L * 0.5f + Math.max(0, jy0 - jy1) * 0.12f;
                gy = jy0 + (jy1 - jy0) * q - 4 * apex * q * (1 - q);
                if (q >= 1) {
                    floor = jumpTo;
                    gy = floor.y;
                    crouch = 0.8f; // lands soft
                    plant();
                    // A pounce that lands near the ball sends it off.
                    if (goal == PLAY && ballFloor == floor && Math.abs(bx - cx) < L * 0.8f)
                        bvx = dir * L * (3 + rnd.nextFloat() * 3);
                    setMode(STAND, now);
                }
                break;
            }
            case MOVE:
                cx += vx * dt;
                if (floor != null) cx = Math.max(floor.x0 + L * 0.3f, Math.min(floor.x1 - L * 0.3f, cx));
                break;
        }
        float k = Math.min(1f, dt * 5);
        sit += (wantSit - sit) * k;
        lie += (wantLie - lie) * Math.min(1f, dt * 2.5f);
        crouch += (wantCrouch - crouch) * Math.min(1f, dt * (wantCrouch > crouch ? 8 : 4));
        if (mode != MOVE) vx *= 0.8f;
        phase += Math.abs(vx) * dt / (L * 0.45f) * (float) Math.PI;
        if (floor != null) gy = floor.y;
        feet(now);
    }

    // ---- Legs ------------------------------------------------------------------------------------

    // Where each leg joins the body (x from the middle, in body lengths) and its paw rests.
    private static final float[] HOME = {0.19f, 0.24f, -0.22f, -0.17f};

    private void plant() {
        for (int i = 0; i < 4; i++) {
            fx[i] = cx + dir * HOME[i] * L;
            fy[i] = gy;
            stepping[i] = false;
        }
    }

    private void feet(long now) {
        boolean air = mode == JUMP;
        float fast = Math.abs(vx) / L;
        stepDur = fast > 1.8f ? 0.16f : 0.26f;
        for (int i = 0; i < 4; i++) {
            if (air) continue; // drawn from the body while flying
            float home = cx + dir * HOME[i] * L;
            if (sit > 0.5f || lie > 0.5f) { // tucked in: paws ease to their resting places
                float want = cx + dir * (i < 2 ? (lie > 0.5f ? 0.3f : 0.16f + (i == 1 ? 0.04f : 0)) : (lie > 0.5f ? -0.12f : -0.02f + (i == 3 ? 0.04f : 0))) * L;
                fx[i] += (want - fx[i]) * 0.2f;
                fy[i] = gy;
                stepping[i] = false;
                continue;
            }
            if (stepping[i]) {
                float q = (now - stepAt[i]) / (stepDur * 1000);
                if (q >= 1) { stepping[i] = false; fx[i] = tx[i]; fy[i] = gy; }
                else {
                    fx[i] = sx[i] + (tx[i] - sx[i]) * q;
                    fy[i] = gy - (float) Math.sin(Math.PI * q) * L * 0.08f;
                }
                continue;
            }
            fy[i] = gy;
            float lead = home + vx * stepDur * 0.5f;
            float reach = (fast > 0.1f ? 0.12f : 0.07f) * L;
            float gap = Math.abs(fx[i] - lead);
            if (gap > reach && (!partnerStepping(i) || gap > 0.32f * L)) {
                stepping[i] = true;
                stepAt[i] = now;
                sx[i] = fx[i];
                sy[i] = fy[i];
                tx[i] = lead + vx * stepDur * 0.9f;
            }
        }
    }

    // Diagonal pairs step together: front near with back far, front far with back near.
    private boolean partnerStepping(int i) {
        int[] other = i == 0 || i == 3 ? new int[] {1, 2} : new int[] {0, 3};
        return stepping[other[0]] || stepping[other[1]];
    }

    // ---- Drawing ---------------------------------------------------------------------------------

    private void drawShadow(Canvas c) {
        Floor under = floor != null ? floor : jumpTo;
        float y = under != null ? under.y : gy, h = Math.max(0, y - gy);
        float s = Math.max(0.3f, 1 - h / (L * 3));
        c.save();
        c.scale(1, 0.12f, cx, y);
        fill.setShader(new RadialGradient(cx, y, L * 0.5f * s, Alive.fade(0xFF000000, 0.45f * s), 0, Shader.TileMode.CLAMP));
        c.drawCircle(cx, y, L * 0.5f * s, fill);
        fill.setShader(null);
        c.restore();
        float r = ballR();
        if (bx >= 0) {
            Floor bf = ballFloor != null ? ballFloor : below(bx, by);
            if (bf != null) {
                float bs = Math.max(0.3f, 1 - (bf.y - by - r) / (L * 3));
                fill.setColor(0x47000000);
                fill.setAlpha(Math.round(0x47 * bs));
                oval.set(bx - r * bs, bf.y - r * 0.25f, bx + r * bs, bf.y + r * 0.25f);
                c.drawOval(oval, fill);
            }
        }
    }

    private Floor below(float x, float y) {
        Floor best = null;
        for (Floor f : floors) if (x >= f.x0 && x <= f.x1 && f.y >= y && (best == null || f.y < best.y)) best = f;
        return best;
    }

    // The ball of yarn: the app's color, wound threads that turn as it rolls, a loose end.
    private void drawBall(Canvas c) {
        if (bx < 0) return;
        float r = ballR();
        fill.setColor(accent | 0xFF000000);
        c.drawCircle(bx, by, r, fill);
        c.save();
        c.rotate((float) Math.toDegrees(spin), bx, by);
        line.setColor(0x66FFFFFF);
        line.setStrokeWidth(r * 0.13f);
        for (int i = -1; i <= 1; i++) {
            oval.set(bx - r * 0.85f, by - r * (0.4f + 0.28f * i), bx + r * 0.85f, by + r * (0.4f - 0.28f * i));
            c.drawArc(oval, 195, 150, false, line);
        }
        line.setColor(0x40000000);
        oval.set(bx - r * 0.7f, by - r * 0.7f, bx + r * 0.7f, by + r * 0.7f);
        c.drawArc(oval, 20, 120, false, line);
        c.restore();
        line.setColor(accent | 0xFF000000);
        line.setStrokeWidth(r * 0.14f);
        path.reset();
        path.moveTo(bx, by + r * 0.95f);
        path.quadTo(bx - r * 0.9f, by + r * 1.15f, bx - r * 1.9f, by + r);
        c.drawPath(path, line);
    }

    private void drawCat(Canvas c, long now) {
        float t = now / 1000f;
        float breathe = (float) Math.sin(t * 2.1f) * 0.008f;
        float bob = mode == MOVE ? (float) Math.abs(Math.sin(phase)) * 0.02f : 0;
        float lowered = 0.13f * crouch;
        // Hip and shoulder, from the middle on the ground (x in the facing direction, y up), blended
        // between standing, sitting and lying.
        float hipX = blend(-0.2f, -0.12f, -0.22f), hipY = blend(0.5f, 0.17f, 0.17f) - lowered + bob;
        float shX = blend(0.2f, 0.13f, 0.2f), shY = blend(0.52f, 0.58f, 0.19f) - lowered * 1.1f + bob + breathe;
        if (mode == JUMP) { // stretched out in flight
            float q = Math.min(1f, (now - modeAt) / 1000f / jumpTime);
            float tilt = (jy1 < jy0 ? 0.12f : -0.1f) * (float) Math.sin(Math.PI * q) * 1.6f * (q < 0.5f ? 1 : -1);
            shY += tilt;
            hipY -= tilt;
            hipX -= 0.04f;
            shX += 0.04f;
        }
        float Hx = cx + dir * hipX * L, Hy = gy - hipY * L, Sx = cx + dir * shX * L, Sy = gy - shY * L;

        // What it's looking at: the ball while playing, what you moved to after a press, else ahead.
        long tnow = now;
        float lx, ly;
        if (tnow < lookUntil) { lx = lookX; ly = lookY; }
        else if (goal == PLAY || (mode == SIT && bx >= 0 && Math.abs(bx - cx) < L * 4)) { lx = bx; ly = by; }
        else { lx = cx + dir * L * 3; ly = gy - L * 0.6f; }

        tail(c, Hx, Hy, t);
        legs(c, true, Hx, Hy, Sx, Sy, now);   // far side
        torso(c, Hx, Hy, Sx, Sy);
        legs(c, false, Hx, Hy, Sx, Sy, now);  // near side
        head(c, Sx, Sy, lx, ly, now, t);
        if (mode == LIE) zs(c, Sx, Sy, t);
    }

    private float blend(float stand, float sitting, float lying) {
        float v = stand + (sitting - stand) * sit;
        return v + (lying - v) * lie;
    }

    private void torso(Canvas c, float Hx, float Hy, float Sx, float Sy) {
        float rH = L * 0.19f, rS = L * (0.205f + 0.01f * sit);
        float ex = Sx - Hx, ey = Sy - Hy, len = (float) Math.hypot(ex, ey);
        float ux = ex / len, uy = ey / len;
        float nx = uy, ny = -ux; // a normal; flip it to point up
        if (ny > 0) { nx = -nx; ny = -ny; }
        float mx = (Hx + Sx) / 2, my = (Hy + Sy) / 2;
        path.reset();
        path.moveTo(Hx + nx * rH, Hy + ny * rH);
        path.quadTo(mx + nx * (rH + rS) * 0.48f, my + ny * (rH + rS) * 0.48f, Sx + nx * rS, Sy + ny * rS);
        path.lineTo(Sx - nx * rS, Sy - ny * rS);
        path.quadTo(mx - nx * (rH + rS) * 0.6f, my - ny * (rH + rS) * 0.6f, Hx - nx * rH, Hy - ny * rH); // the belly
        path.close();
        Path body = new Path(path), round = new Path();
        round.addCircle(Hx, Hy, rH, Path.Direction.CW);
        round.addCircle(Sx, Sy, rS, Path.Direction.CW);
        body.op(round, Path.Op.UNION);

        // Lit from above: lighter along the back, darker under the belly.
        float r = Math.max(rH, rS);
        fill.setShader(new LinearGradient(mx + nx * r * 1.1f, my + ny * r * 1.1f, mx - nx * r, my - ny * r,
                new int[] {0xFFAD9F8E, FUR, 0xFF5E5349}, new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(body, fill);
        fill.setShader(null);

        c.save();
        c.clipPath(body);
        // Calico patches with soft edges, and the white chest.
        softSpot(c, Hx + nx * rH * 0.35f - ux * rH * 0.1f, Hy + ny * rH * 0.35f - uy * rH * 0.1f, rH * 1.15f, 0xF0CF8D50);
        softSpot(c, Hx + ux * len * 0.62f + nx * rS * 0.7f, Hy + uy * len * 0.62f + ny * rS * 0.7f, rS * 0.75f, 0xD9C98648);
        softSpot(c, Sx - nx * rS * 0.55f + ux * rS * 0.4f, Sy - ny * rS * 0.55f + uy * rS * 0.4f, rS * 0.85f, 0xFFF3F0EA);
        // Fur.
        line.setStrokeWidth(L * 0.011f);
        for (int i = 0; i < FUR_N; i++) {
            float t = furT[i], rr = rH + (rS - rH) * t, off = furS[i] * rr * 0.95f;
            float px = Hx + ex * t - nx * off, py = Hy + ey * t - ny * off;
            float l = furLen[i] * L;
            // Hair lies back along the body and a little downward.
            float hx = -ux * 0.8f - nx * 0.35f, hy = -uy * 0.8f - ny * 0.35f;
            line.setColor(furCol[i]);
            line.setAlpha(150);
            c.drawLine(px, py, px + hx * l, py + hy * l, line);
        }
        // Tabby stripes: tapered, slightly curved, coming down from the spine.
        for (int i = 0; i < 6; i++) {
            float t = 0.08f + i * 0.155f, rr = rH + (rS - rH) * t;
            float px = Hx + ex * t + nx * rr * 1.02f, py = Hy + ey * t + ny * rr * 1.02f;
            float depth = rr * (0.75f + 0.25f * ((i * 37) % 5) / 4f);
            path.reset();
            path.moveTo(px - ux * L * 0.025f, py - uy * L * 0.025f);
            path.quadTo(px - nx * depth * 0.5f + ux * L * 0.03f, py - ny * depth * 0.5f + uy * L * 0.03f,
                    px - nx * depth - ux * L * 0.01f, py - ny * depth - uy * L * 0.01f);
            path.quadTo(px - nx * depth * 0.5f + ux * L * 0.055f, py - ny * depth * 0.5f + uy * L * 0.055f,
                    px + ux * L * 0.025f, py + uy * L * 0.025f);
            path.close();
            fill.setColor(0xB83A312A);
            c.drawPath(path, fill);
        }
        // Shade under the belly, and a soft rim of light along the back.
        fill.setShader(new LinearGradient(mx - nx * r * 0.2f, my - ny * r * 0.2f, mx - nx * r * 1.05f, my - ny * r * 1.05f,
                0x00000000, 0x59000000, Shader.TileMode.CLAMP));
        c.drawPath(body, fill);
        fill.setShader(null);
        line.setShader(new LinearGradient(mx + nx * r, my + ny * r, mx, my, 0x55FFF4E0, 0x00FFF4E0, Shader.TileMode.CLAMP));
        line.setStrokeWidth(L * 0.03f);
        c.drawPath(body, line);
        line.setShader(null);
        c.restore();
    }

    // A soft-edged round patch of color.
    private void softSpot(Canvas c, float x, float y, float r, int color) {
        fill.setShader(new RadialGradient(x, y, r, new int[] {color, color, color & 0x00FFFFFF},
                new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, fill);
        fill.setShader(null);
    }

    private void legs(Canvas c, boolean far, float Hx, float Hy, float Sx, float Sy, long now) {
        for (int i = 0; i < 4; i++) {
            if ((i == 1 || i == 3) != far) continue;
            boolean front = i < 2;
            float jx = front ? Sx : Hx, jy = (front ? Sy : Hy) + L * 0.05f;
            float px = fx[i], py = fy[i];
            if (mode == JUMP) { // in flight: front paws reach forward, back paws trail
                px = jx + dir * (front ? 0.22f : -0.24f) * L;
                py = jy + L * 0.24f;
            }
            if (mode == BAT && i == 0) { // the swipe
                float q = Math.min(1f, (now - modeAt) / 380f), s = (float) Math.sin(Math.PI * q);
                px = jx + dir * (0.12f + 0.3f * s) * L;
                py = jy + (0.42f - 0.36f * s) * L;
            }
            float a = (front ? 0.26f : 0.25f) * L, b = (front ? 0.27f : 0.3f) * L;
            float dx = px - jx, dy = py - jy;
            if (Math.abs(dx) + Math.abs(dy) < 0.5f) dy = 1;
            float d = (float) Math.hypot(dx, dy);
            d = Math.max(Math.abs(a - b) + 1, Math.min(a + b - 0.5f, d));
            float base = (float) Math.atan2(dy, dx);
            float off = (float) Math.acos(Math.max(-1, Math.min(1, (a * a + d * d - b * b) / (2 * a * d))));
            // Elbows point back, knees point forward.
            float s = front ? dir : -dir;
            float kx = jx + a * (float) Math.cos(base + s * off), ky = jy + a * (float) Math.sin(base + s * off);
            float ex = jx + dx / (float) Math.hypot(dx, dy) * d, ey = jy + dy / (float) Math.hypot(dx, dy) * d;
            int col = far ? FUR_FAR : FUR;
            line.setColor(col);
            line.setStrokeWidth(L * (front ? 0.11f : 0.14f));
            c.drawLine(jx, jy, kx, ky, line);
            line.setStrokeWidth(L * 0.08f);
            c.drawLine(kx, ky, ex, ey, line);
            if (!far) {
                line.setColor(FUR_DARK);
                line.setStrokeWidth(L * 0.022f);
                for (float q = 0.3f; q < 0.8f; q += 0.25f) {
                    float bx0 = kx + (ex - kx) * q, by0 = ky + (ey - ky) * q;
                    c.drawLine(bx0 - L * 0.035f, by0, bx0 + L * 0.035f, by0 - L * 0.01f, line);
                }
            }
            fill.setColor(far ? 0xFFDCD8D0 : CREAM);
            oval.set(ex - L * 0.065f + dir * L * 0.02f, ey - L * 0.04f, ex + L * 0.065f + dir * L * 0.02f, ey + L * 0.012f);
            c.drawOval(oval, fill);
        }
    }

    // The tail: a chain of segments, its shape eased between standing (up in a curve), sitting
    // (wrapped round the paws) and lying (curled round the front), with a sway that travels along it.
    private void tail(Canvas c, float Hx, float Hy, float t) {
        float moving = Math.min(1f, Math.abs(vx) / L);
        float start = blend(205 + 15 * moving, 140, 170), curl = blend(8 - 4 * moving, -13, -17);
        float sway = blend(10, 6, 3) * (mode == LIE ? 0.4f : 1);
        int n = 9;
        float seg = L * 0.085f, x = Hx - dir * L * 0.14f, y = Hy - L * 0.05f, ang = start;
        float w0 = L * 0.075f;
        for (int i = 0; i < n; i++) {
            float a = ang + (float) Math.sin(t * (2 + 3 * moving) - i * 0.55f) * sway * (i + 1) / n;
            float rad = (float) Math.toRadians(a);
            float nx = x + dir * seg * (float) Math.cos(rad), ny = Math.min(gy - w0 * 0.3f, y + seg * (float) Math.sin(rad));
            float w = w0 * (1.25f - 0.45f * i / n);
            line.setColor(i >= n - 2 ? 0xFF2E2722 : i % 2 == 1 ? 0xFF4A4038 : 0xFF7E7164);
            line.setStrokeWidth(w);
            c.drawLine(x, y, nx, ny, line);
            line.setColor(0x33FFF4E0); // light catching the top of the fur
            line.setStrokeWidth(w * 0.35f);
            c.drawLine(x, y - w * 0.25f, nx, ny - w * 0.25f, line);
            x = nx;
            y = ny;
            ang += curl;
        }
    }

    private void head(Canvas c, float Sx, float Sy, float lx, float ly, long now, float t) {
        float hx = Sx + dir * L * blend(0.18f, 0.1f, 0.25f), hy = Sy - L * blend(0.23f, 0.28f, 0.02f);
        // Neck.
        line.setColor(FUR);
        line.setStrokeWidth(L * 0.2f);
        c.drawLine(Sx, Sy - L * 0.04f, hx, hy, line);
        // Nod toward what it's watching: up or down, within limits.
        float pitch = (float) Math.toDegrees(Math.atan2(ly - hy, Math.abs(lx - hx) + L));
        pitch = Math.max(-30, Math.min(35, pitch)) * (1 - lie * 0.7f);
        boolean behind = (lx - hx) * dir < -L * 0.5f; // looking back over its shoulder
        int face = behind && mode != MOVE && mode != JUMP ? -dir : dir;
        boolean blink = now >= blinkAt && now < blinkAt + 150;
        if (now > blinkAt + 150) blinkAt = now + 2200 + rnd.nextInt(4000);
        float open = mode == LIE ? 0 : blink ? 0.1f : 1;

        c.save();
        c.translate(hx, hy);
        c.scale(face * L * 1.22f, L * 1.22f);
        c.rotate(pitch);
        // Far ear, head, near ear.
        tri(FUR_FAR, -0.02f, -0.09f, 0.05f, -0.3f, 0.12f, -0.07f);
        drawTri(c);
        fill.setShader(new RadialGradient(-0.03f, -0.08f, 0.24f, new int[] {0xFFB1A391, FUR, 0xFF655A4F},
                new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        oval.set(-0.17f, -0.145f, 0.17f, 0.145f);
        c.drawOval(oval, fill);
        fill.setShader(null);
        line.setStrokeWidth(0.008f); // fur along the cheek
        for (int i = 0; i < 9; i++) {
            float a = (float) Math.toRadians(100 + i * 12);
            line.setColor(i % 3 == 0 ? 0xAA4A4038 : 0x99B5A796);
            c.drawLine(0.15f * (float) Math.cos(a), 0.13f * (float) Math.sin(a), 0.19f * (float) Math.cos(a), 0.16f * (float) Math.sin(a), line);
        }
        tri(FUR, -0.14f, -0.06f, -0.09f, -0.31f, 0.01f, -0.11f);
        drawTri(c);
        tri(PINK, -0.115f, -0.085f, -0.088f, -0.25f, -0.03f, -0.11f);
        drawTri(c);
        line.setColor(0xB3FFFFFF); // tufts of white hair inside the ear
        line.setStrokeWidth(0.006f);
        c.drawLine(-0.1f, -0.1f, -0.085f, -0.17f, line);
        c.drawLine(-0.08f, -0.1f, -0.075f, -0.16f, line);
        c.drawLine(-0.06f, -0.105f, -0.065f, -0.15f, line);
        // An orange blaze on the forehead and dark tabby lines above the eye.
        fill.setColor(ORANGE);
        oval.set(-0.02f, -0.15f, 0.09f, -0.06f);
        c.drawOval(oval, fill);
        line.setColor(FUR_DARK);
        line.setStrokeWidth(0.018f);
        c.drawLine(-0.06f, -0.13f, -0.03f, -0.06f, line);
        c.drawLine(-0.1f, -0.11f, -0.075f, -0.05f, line);
        c.drawLine(-0.14f, -0.07f, -0.11f, -0.02f, line);
        // A white muzzle and throat, nose, mouth, whiskers.
        fill.setColor(CREAM);
        oval.set(-0.04f, 0.04f, 0.14f, 0.16f);
        c.drawOval(oval, fill);
        fill.setColor(CREAM);
        oval.set(0.05f, -0.01f, 0.2f, 0.1f);
        c.drawOval(oval, fill);
        oval.set(0.165f, -0.002f, 0.205f, 0.03f);
        fill.setShader(new LinearGradient(0, -0.002f, 0, 0.03f, 0xFFF0B2B2, 0xFFB76E72, Shader.TileMode.CLAMP));
        c.drawOval(oval, fill);
        fill.setShader(null);
        line.setColor(0x99503A30);
        line.setStrokeWidth(0.012f);
        path.reset();
        path.moveTo(0.19f, 0.03f);
        path.quadTo(0.17f, 0.07f, 0.14f, 0.06f);
        c.drawPath(path, line);
        line.setColor(0x8CFFFFFF);
        line.setStrokeWidth(0.007f);
        c.drawLine(0.15f, 0.045f, 0.33f, 0.0f, line);
        c.drawLine(0.15f, 0.055f, 0.34f, 0.055f, line);
        c.drawLine(0.15f, 0.065f, 0.32f, 0.11f, line);
        // The eye: open, mid-blink, or shut asleep.
        if (open < 0.3f) {
            line.setColor(0xFF5A3A28);
            line.setStrokeWidth(0.016f);
            path.reset();
            path.moveTo(0.02f, -0.02f);
            path.quadTo(0.06f, 0.005f, 0.1f, -0.02f);
            c.drawPath(path, line);
        } else {
            fill.setColor(FUR_DARK);
            oval.set(0.012f, -0.062f * open, 0.108f, 0.016f * open);
            oval.offset(0, -0.02f + 0.022f * (1 - open));
            c.drawOval(oval, fill);
            oval.set(0.02f, -0.055f * open, 0.1f, 0.01f * open);
            oval.offset(0, -0.02f + 0.022f * (1 - open));
            fill.setShader(new RadialGradient(oval.centerX(), oval.centerY(), 0.045f, new int[] {0xFFE3E68A, EYE, 0xFF6F7F3A},
                    new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            c.drawOval(oval, fill);
            fill.setShader(null);
            fill.setColor(PUPIL);
            oval.set(0.055f, -0.07f * open, 0.08f, 0.0f);
            oval.offset(0, -0.0f);
            c.drawOval(oval, fill);
            fill.setColor(0xF2FFFFFF);
            c.drawCircle(0.045f, -0.047f, 0.01f, fill);
            fill.setColor(0x80FFFFFF);
            c.drawCircle(0.082f, -0.012f, 0.005f, fill);
        }
        c.restore();
    }

    private final float[] triPts = new float[6];
    private int triColor;

    private void tri(int color, float x0, float y0, float x1, float y1, float x2, float y2) {
        triColor = color;
        triPts[0] = x0; triPts[1] = y0; triPts[2] = x1; triPts[3] = y1; triPts[4] = x2; triPts[5] = y2;
    }

    // A triangle with its tip rounded off: an ear.
    private void drawTri(Canvas c) {
        float[] p = triPts;
        path.reset();
        path.moveTo(p[0], p[1]);
        path.lineTo(p[0] + (p[2] - p[0]) * 0.85f, p[1] + (p[3] - p[1]) * 0.85f);
        path.quadTo(p[2], p[3], p[2] + (p[4] - p[2]) * 0.15f, p[3] + (p[5] - p[3]) * 0.15f);
        path.lineTo(p[4], p[5]);
        path.close();
        fill.setColor(triColor);
        c.drawPath(path, fill);
    }

    // Little z's drifting up while it sleeps.
    private void zs(Canvas c, float Sx, float Sy, float t) {
        fill.setTextSize(L * 0.16f);
        fill.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        for (int i = 0; i < 3; i++) {
            float k = (t * 0.3f + i / 3f) % 1f;
            fill.setColor(Alive.fade(0xFFFFFFFF, (float) Math.sin(Math.PI * k) * 0.8f));
            c.drawText("z", Sx + dir * L * (0.25f + k * 0.2f), Sy - L * (0.3f + k * 0.45f), fill);
        }
    }
}
