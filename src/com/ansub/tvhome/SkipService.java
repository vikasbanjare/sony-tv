package com.ansub.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

// Presses "Skip Intro", "Skip Recap", "Skip Credits" and "Next Episode" the moment they appear.
//
// It listens for screen changes in streaming apps only (half a second apart at most), looks through
// what's on screen for a button with one of those labels, and clicks it. It takes no key events,
// so the remote is untouched. Works wherever an app shows its buttons to Android's accessibility
// layer (Apple TV, Prime Video, Hotstar, SonyLIV, Zee5, Crunchyroll...). Apps that draw their own
// screens (Netflix, YouTube) show nothing here: skip.sh check tells you which is which.
public final class SkipService extends AccessibilityService {
    static final String PREFS = "skip";
    static final String[] APPS = {
            "com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "in.startv.hotstar", "com.disney.disneyplus",
            "com.apple.atve.androidtv.appletv", "com.sonyliv", "com.graymatrix.did", "com.crunchyroll.crunchyroid",
            "com.jio.media.stb.ondemand", "tv.airtel.xstream.tvapp", "com.wbd.stream", "com.hbo.hbonow",
            "com.hulu.livingroomplus", "com.peacocktv.peacockandroid", "com.cbs.ott", "com.google.android.youtube.tv",
            "com.mxtech.videoplayer.television", "com.stremio.one"};
    // What to press. "Watch credits" and "Skip to ..." menus are not these.
    private static final Pattern SKIP = Pattern.compile(
            "^\\s*(skip\\s*(intro|recap|opening|credits|ad|ads|advert|preview|summary)?|next\\s*(episode|ep)|play\\s*next(\\s*episode)?)\\s*[>›»]?\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NEXT = Pattern.compile("next|play", Pattern.CASE_INSENSITIVE);

    private final Set<String> apps = new HashSet<>();
    private boolean next = true;
    private long lastAt;
    private String lastText = "";

    private Thread link;
    private volatile boolean running;

    @Override
    protected void onServiceConnected() {
        load();
        running = true;
        link = new Thread(this::keepHelperRunning, "skip-link");
        link.setDaemon(true);
        link.start();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 500;
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.packageNames = apps.toArray(new String[0]);
        setServiceInfo(info);
    }

    private void load() {
        SharedPreferences p = getSharedPreferences(PREFS, 0);
        apps.clear();
        apps.addAll(Arrays.asList(APPS));
        String extra = p.getString("apps", "");
        for (String a : extra.split("[,\\s]+")) if (!a.isEmpty()) apps.add(a);
        next = p.getBoolean("next", true);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        CharSequence pkg = e.getPackageName();
        if (pkg == null || !apps.contains(pkg.toString())) return;
        long t = SystemClock.uptimeMillis();
        if (t - lastAt < 400) return; // the same screen, a moment later
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        try {
            AccessibilityNodeInfo hit = find(root, 0);
            if (hit != null) {
                String text = label(hit);
                // The same button again within a few seconds means the press didn't take: leave it.
                if (text.equalsIgnoreCase(lastText) && t - lastAt < 6000) return;
                if (press(hit)) { lastAt = t; lastText = text; }
                hit.recycle();
            }
        } finally {
            root.recycle();
        }
    }

    private static String label(AccessibilityNodeInfo n) {
        CharSequence s = n.getText();
        if (s == null || s.length() == 0) s = n.getContentDescription();
        return s == null ? "" : s.toString();
    }

    // The first skip-type button on screen, searching at most a few hundred nodes.
    private AccessibilityNodeInfo find(AccessibilityNodeInfo n, int depth) {
        if (n == null || depth > 24) return null;
        String text = label(n);
        if (!text.isEmpty() && text.length() < 24 && SKIP.matcher(text).matches()) {
            if (next || !NEXT.matcher(text).find()) {
                if (n.isVisibleToUser()) return AccessibilityNodeInfo.obtain(n);
            }
        }
        int count = n.getChildCount();
        for (int i = 0; i < count && i < 60; i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c == null) continue;
            AccessibilityNodeInfo hit = find(c, depth + 1);
            c.recycle();
            if (hit != null) return hit;
        }
        return null;
    }

    // Click it, or the nearest clickable thing it sits in.
    private boolean press(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo cur = AccessibilityNodeInfo.obtain(n);
        for (int up = 0; cur != null && up < 5; up++) {
            if (cur.isClickable() && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { cur.recycle(); return true; }
            AccessibilityNodeInfo parent = cur.getParent();
            cur.recycle();
            cur = parent;
        }
        if (cur != null) cur.recycle();
        // Not clickable to accessibility: focus it and try once more; some TV buttons take it then.
        if (n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        return false;
    }

    @Override public void onInterrupt() {}

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        running = false;
        if (link != null) link.interrupt();
        return super.onUnbind(intent);
    }

    // For apps that show nothing to accessibility (Netflix, Apple TV): connects to the TV's own
    // adb (Network debugging) and keeps SkipHelper running there with the shell's powers. It
    // reconnects when the link drops. The TV asks "Allow debugging?" once for this app.
    private void keepHelperRunning() {
        int wait = 5000;
        while (running) {
            Adb adb = null;
            try {
                adb = new Adb(this);
                // The first time, the TV shows "Allow debugging?": give the person two minutes.
                if (!adb.connect("127.0.0.1", 5555, 120000)) {
                    android.util.Log.w("Skip", "adb refused; is Network debugging on, and was it allowed on the TV?");
                    adb.close();
                    Thread.sleep(60000);
                    continue;
                }
                String apk = getApplicationInfo().sourceDir;
                android.util.Log.i("Skip", "connected to the TV's adb; starting the helper");
                Adb.Stream s = adb.open("shell:CLASSPATH=" + apk + " app_process /system/bin com.ansub.tvhome.SkipHelper");
                wait = 5000;
                byte[] chunk;
                while (running && (chunk = s.read()) != null) {
                    String text = new String(chunk, "UTF-8").trim();
                    if (!text.isEmpty()) android.util.Log.i("Skip", text);
                }
                android.util.Log.w("Skip", "helper ended");
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                android.util.Log.w("Skip", "adb: " + e);
            } finally {
                if (adb != null) adb.close();
            }
            try { Thread.sleep(wait); } catch (InterruptedException e) { return; }
            wait = Math.min(wait * 2, 120000);
        }
    }
}
