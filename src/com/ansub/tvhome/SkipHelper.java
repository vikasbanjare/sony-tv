package com.ansub.tvhome;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.IBinder;
import android.os.SystemClock;
import android.view.InputEvent;
import android.view.KeyEvent;

import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

// Runs with the shell's powers (started by SkipService over the TV's own adb with app_process).
// While Netflix, Apple TV or another listed app is in front it takes a tiny screenshot once a
// second, looks for the bright "Skip Intro" / "Next Episode" pill in the bottom-right corner, and
// presses OK: those pills are always the highlighted button while they're on screen.
//
//   app_process ... com.ansub.tvhome.SkipHelper            run
//   app_process ... com.ansub.tvhome.SkipHelper snap FILE  save one capture, the search area marked
public final class SkipHelper {
    static final int W = 320, H = 180;
    // Where these apps put their skip pills: the bottom-right.
    static final int RX0 = 170, RY0 = 118, RX1 = 314, RY1 = 174;
    static final Set<String> APPS = new HashSet<>(Arrays.asList(SkipService.APPS));

    public static void main(String[] args) throws Exception {
        allowHiddenApis();
        if (args.length > 0 && args[0].equals("snap")) {
            Bitmap b = capture();
            if (b == null) { System.out.println("capture failed"); return; }
            mark(b);
            try (FileOutputStream o = new FileOutputStream(args[1])) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
            Rect pill = findPill(b);
            System.out.println(pill == null ? "no pill seen" : "pill at " + pill);
            return;
        }
        if (args.length > 0 && args[0].equals("press")) { pressOk(); return; }
        System.out.println("skip helper running");
        long lastPress = 0, lastFront = 0;
        String front = "";
        int seen = 0;
        while (true) {
            long t = SystemClock.uptimeMillis();
            if (t - lastFront > 2000) { front = frontApp(); lastFront = t; }
            if (!APPS.contains(front)) { seen = 0; Thread.sleep(1500); continue; }
            Bitmap b = capture();
            if (b != null) {
                Rect pill = findPill(b);
                b.recycle();
                // The same pill twice in a row, a second apart, before pressing: a flash isn't a button.
                seen = pill != null ? seen + 1 : 0;
                if (seen >= 2 && t - lastPress > 15000) {
                    pressOk();
                    System.out.println("pressed OK for " + pill + " in " + front);
                    lastPress = t;
                    seen = 0;
                }
            }
            Thread.sleep(1000);
        }
    }

    // ---- Finding the pill -----------------------------------------------------------------------

    // A bright, filled, wide-but-not-tall blob in the corner: the highlighted skip button.
    static Rect findPill(Bitmap b) {
        int w = RX1 - RX0, h = RY1 - RY0;
        boolean[] bright = new boolean[w * h];
        int[] px = new int[w];
        for (int y = 0; y < h; y++) {
            b.getPixels(px, 0, w, RX0, RY0 + y, w, 1);
            for (int x = 0; x < w; x++) {
                int c = px[x];
                int r = (c >> 16) & 255, g = (c >> 8) & 255, bl = c & 255;
                bright[y * w + x] = r > 175 && g > 175 && bl > 175;
            }
        }
        // Connected bright blobs, by flood fill.
        boolean[] done = new boolean[w * h];
        int[] stack = new int[w * h];
        for (int start = 0; start < w * h; start++) {
            if (!bright[start] || done[start]) continue;
            int sp = 0, n = 0, minX = w, maxX = -1, minY = h, maxY = -1;
            stack[sp++] = start;
            done[start] = true;
            while (sp > 0) {
                int i = stack[--sp];
                int x = i % w, y = i / w;
                n++;
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
                int[] nb = {i - 1, i + 1, i - w, i + w};
                for (int j : nb) {
                    if (j < 0 || j >= w * h || done[j] || !bright[j]) continue;
                    if ((j == i - 1 && x == 0) || (j == i + 1 && x == w - 1)) continue;
                    done[j] = true;
                    stack[sp++] = j;
                }
            }
            int bw = maxX - minX + 1, bh = maxY - minY + 1;
            float fill = n / (float) (bw * bh);
            // A pill: wider than tall, a readable size, mostly filled (text knocks a little out).
            if (bw >= 24 && bw <= 120 && bh >= 7 && bh <= 26 && bw >= bh * 2.2f && fill > 0.55f)
                return new Rect(RX0 + minX, RY0 + minY, RX0 + maxX, RY0 + maxY);
        }
        return null;
    }

    private static void mark(Bitmap b) {
        for (int x = RX0; x <= RX1; x++) { b.setPixel(x, RY0, 0xFFFF0000); b.setPixel(x, RY1, 0xFFFF0000); }
        for (int y = RY0; y <= RY1; y++) { b.setPixel(RX0, y, 0xFFFF0000); b.setPixel(RX1, y, 0xFFFF0000); }
    }

    // ---- Shell-only powers, through hidden APIs ------------------------------------------------

    // A small screenshot straight from the compositor: no permission dialog for the shell.
    static Bitmap capture() {
        try {
            Class<?> sc = Class.forName("android.view.SurfaceControl");
            IBinder display = null;
            try { display = (IBinder) sc.getMethod("getInternalDisplayToken").invoke(null); } catch (Exception ignored) {}
            if (display == null) { // Android 13+: by physical display id
                Class<?> dc = Class.forName("android.view.SurfaceControl");
                long[] ids = (long[]) dc.getMethod("getPhysicalDisplayIds").invoke(null);
                display = (IBinder) dc.getMethod("getPhysicalDisplayToken", long.class).invoke(null, ids[0]);
            }
            Bitmap hw;
            try { // Android 12+
                Class<?> argsCls = Class.forName("android.view.SurfaceControl$DisplayCaptureArgs");
                Class<?> builderCls = Class.forName("android.view.SurfaceControl$DisplayCaptureArgs$Builder");
                Object builder = builderCls.getConstructor(IBinder.class).newInstance(display);
                builderCls.getMethod("setSize", int.class, int.class).invoke(builder, W, H);
                Object args = builderCls.getMethod("build").invoke(builder);
                Object shot = sc.getMethod("captureDisplay", argsCls).invoke(null, args);
                hw = (Bitmap) shot.getClass().getMethod("asBitmap").invoke(shot);
            } catch (ClassNotFoundException e) { // Android 10, 11
                Method m;
                try {
                    m = sc.getMethod("screenshot", IBinder.class, Rect.class, int.class, int.class, boolean.class, int.class);
                    hw = (Bitmap) m.invoke(null, display, new Rect(), W, H, false, 0);
                } catch (NoSuchMethodException e2) {
                    m = sc.getMethod("screenshot", IBinder.class, Rect.class, int.class, int.class, int.class);
                    hw = (Bitmap) m.invoke(null, display, new Rect(), W, H, 0);
                }
            }
            if (hw == null) return null;
            Bitmap soft = hw.copy(Bitmap.Config.ARGB_8888, false);
            hw.recycle();
            return soft;
        } catch (Throwable e) {
            System.out.println("capture: " + e);
            return null;
        }
    }

    static void pressOk() {
        try {
            Class<?> imCls = Class.forName("android.hardware.input.InputManager");
            Object im = imCls.getMethod("getInstance").invoke(null);
            Method inject = imCls.getMethod("injectInputEvent", InputEvent.class, int.class);
            long now = SystemClock.uptimeMillis();
            KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0, 0, -1, 0, 0, android.view.InputDevice.SOURCE_KEYBOARD);
            KeyEvent up = new KeyEvent(now, now + 30, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0, 0, -1, 0, 0, android.view.InputDevice.SOURCE_KEYBOARD);
            inject.invoke(im, down, 2); // INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH
            inject.invoke(im, up, 2);
        } catch (Throwable e) {
            try { Runtime.getRuntime().exec(new String[] {"input", "keyevent", "23"}).waitFor(); } catch (Exception ignored) {}
        }
    }

    // The app in front, from the activity manager.
    static String frontApp() {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            IBinder b = (IBinder) sm.getMethod("getService", String.class).invoke(null, "activity_task");
            Class<?> stub = Class.forName("android.app.IActivityTaskManager$Stub");
            Object atm = stub.getMethod("asInterface", IBinder.class).invoke(null, b);
            java.util.List<?> tasks = (java.util.List<?>) atm.getClass().getMethod("getTasks", int.class).invoke(atm, 1);
            if (tasks == null || tasks.isEmpty()) return "";
            Object info = tasks.get(0);
            android.content.ComponentName top = (android.content.ComponentName) info.getClass().getField("topActivity").get(info);
            return top == null ? "" : top.getPackageName();
        } catch (Throwable e) {
            try { // older Android
                Process p = Runtime.getRuntime().exec(new String[] {"sh", "-c", "dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity'"});
                java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
                String line = r.readLine();
                p.waitFor();
                if (line == null) return "";
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("([a-zA-Z0-9_.]+)/").matcher(line);
                return m.find() ? m.group(1) : "";
            } catch (Exception e2) {
                return "";
            }
        }
    }

    // Lets this (non-app) process call the framework's hidden methods.
    private static void allowHiddenApis() {
        try {
            Method forName = Class.class.getDeclaredMethod("forName", String.class);
            Method getDeclaredMethod = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);
            Class<?> vmRuntime = (Class<?>) forName.invoke(null, "dalvik.system.VMRuntime");
            Method getRuntime = (Method) getDeclaredMethod.invoke(vmRuntime, "getRuntime", null);
            Method setExemptions = (Method) getDeclaredMethod.invoke(vmRuntime, "setHiddenApiExemptions", new Class[] {String[].class});
            setExemptions.invoke(getRuntime.invoke(null), new Object[] {new String[] {"L"}});
        } catch (Throwable ignored) {}
    }
}
