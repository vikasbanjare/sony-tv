package com.ansub.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.TreeMap;
import java.util.List;

public class MainActivity extends Activity {

    // Top-row apps, in this order of preference:
    //  1. a list saved at install time:  am start -n com.ansub.tvhome/.MainActivity --es apps "pkg1,pkg2"
    //     (--es apps auto  goes back to 2; add --es stamp <unique> so it's applied only once, see saveConfig)
    //  2. apps.txt (built in as Apps.LIST), topped up with other TV apps installed on this TV.
    // Missing apps are skipped. Topping up stops at six apps, but a chosen list can be any length:
    // apps wrap into rows of six, like Apple TV, and the page scrolls down once they don't all fit.
    // On the TV, hold OK on an app (or press Menu) to move or remove it, or add another; that saves list 1.
    // A network tile always sits at the top right, level with the clock. Settings shortcuts can join
    // it there, off until picked the same way:
    //   --es quick "bluetooth,sound,picture"
    // (--es quick none  takes them away again; "wifi" and "ethernet" are the network tile now).
    // The greeting can use a name and the sky can follow a place (see Alive), set the same way:
    //   --es name "Vikas"   --es place "19.07,72.88"   (none takes either away)
    private static final String PREFS = "config";
    private static final List<String> NOT_APPS = Arrays.asList("com.android.vending", "com.android.tv.settings",
            "com.android.tv", "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx",
            "com.google.android.leanbacklauncher");
    private static final String PLAY_STORE = "com.android.vending";
    private static final String SETTINGS = "settings";
    private static final String ADD = "add";
    private static final String[] EDIT_ITEMS = {"Move", "Replace", "Remove from home", "Uninstall"};
    private static final String[] EDIT_ITEMS_SYSTEM = {"Move", "Replace", "Remove from home", "App info"};
    private static final int PER_ROW = 6, MAX_INPUTS = 4;

    // Settings shortcuts: name, label, icon. Tile keys are QUICK + name.
    private static final String QUICK = "quick:";
    private static final String[] QUICK_NAMES = {"bluetooth", "sound", "picture"};
    private static final String[] QUICK_LABELS = {"Bluetooth", "Sound", "Picture"};
    private static final int[][] QUICK_ICONS = {LineIcon.BLUETOOTH, LineIcon.SOUND, LineIcon.PICTURE};
    private static final String NETWORK = "network";
    private static final int OFFLINE = 0, ETHERNET = 4; // network states; 1 to 3 are Wi-Fi bars
    // TCL keeps its own Picture and Sound panels in a service; Android's Sound page is missing there.
    private static final String TCL_SHOW_WINDOW = "com.tcl.settings.SHOW_WINDOW";

    // Layout is designed at 1920x1080 and scaled to the TV's real UI size by px().
    private static final int MARGIN = 96, GAP = 36, RADIUS = 22;
    private static final int ROW1_TOP = 604, ROW_STEP = 224;
    // More rows push the grid up, but never into the clock: it starts this far below it. With a second
    // row the clock gets smaller, so the bottom row still fits on screen.
    private static final int HEADER_TOP = 40, HEADER_GAP = 64, CLOCK_BIG = 250, CLOCK_SMALL = 210;
    private static final int ICON_TOP = 100, ICON_BUTTON = 72, ICON_SIZE = 40, ICON_GAP = 12; // level with the greeting
    private float scale = 1f;

    private static Typeface regular, semibold, light;

    private FrameLayout root; // the page, inside a vertical scroller
    private int tileW, tileH, gridTop; // app grid geometry, set by buildTiles
    private final List<View> tiles = new ArrayList<>();
    private boolean dirty = true;
    private java.util.Locale shownLocale; // the TV's language (and region) when the page was built
    private String focusedKey;
    private final List<String> rowApps = new ArrayList<>(); // the top row as shown, in order
    private String movingKey;   // the app being moved with left/right, if any
    private TextView moveHint;  // shown above the apps while moving
    private boolean longPressed; // OK was held: swallow the key-up so the app doesn't open

    // The sky, greeting and the line under the clock follow the time: checked every few minutes while
    // the home screen shows. The scene (see Alive) is painted off the main thread, and only again when
    // the sun has moved a degree, the weather or the day changed.
    private TextView greeting, sunLine, badge;
    private LinearLayout header; // greeting, clock and the line under it
    private ClockCat clock; // the big clock, with the cat that lives on it
    private LinearLayout info; // greeting, date line and badge, beside the clock
    private String sceneKey;
    private final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
    private Alive.Weather weather;
    private long weatherAt, weatherTried; // last answer, last try
    // The window background: the painted scene, with two glows in the focused app's color on top.
    private LayerDrawable backdrop;
    private GradientDrawable accentTop, accentMid;
    private int accentNow = Alive.NEUTRAL;
    private android.animation.ValueAnimator accentAnim;
    private final java.util.Map<String, Integer> accents = new java.util.HashMap<>();
    private long awaySince; // when the home screen was last left, to know when to suggest an app
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            refreshAlive();
            handler.postDelayed(this, 5 * 60 * 1000);
        }
    };

    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { dirty = true; }
    };

    // The network tile follows the connection live: Wi-Fi bars, Ethernet or Offline. Only its icon and
    // label change, so nothing is rebuilt.
    private ImageView netIcon;
    private int netState = -1;
    private final ConnectivityManager.NetworkCallback networkChanged = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network n) { runOnUiThread(MainActivity.this::refreshNetwork); }
        @Override public void onLost(Network n) { runOnUiThread(MainActivity.this::refreshNetwork); }
        // Also fires when the Wi-Fi signal gets stronger or weaker.
        @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) {
            runOnUiThread(MainActivity.this::refreshNetwork);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        saveConfig(getIntent());
        loadFonts();
        scale = getResources().getDisplayMetrics().widthPixels / 1920f;

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.setClipChildren(false);
        root = new FrameLayout(this);
        root.setClipChildren(false);
        scroller.addView(root, new FrameLayout.LayoutParams(-1, -2));

        backdrop();

        // The big clock with its cat (see ClockCat), a thin line, then stacked beside it: the
        // greeting, the date with sunrise, sunset or the moon, and a badge for the weather or a
        // festival. Side by side, so the clock stays big with two rows of apps. Lined up along the
        // bottom of the digits; the clock's box reaches up above them for the cat.
        header = new LinearLayout(this);
        header.setGravity(Gravity.BOTTOM);
        shownLocale = java.util.Locale.getDefault();
        clock = new ClockCat(this, light);
        clock.setCoat(Coat.parse(getSharedPreferences(PREFS, 0).getString("coat", null)));
        clock.setTextSize(px(CLOCK_BIG));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
        cp.leftMargin = -clock.glowPad(); // the digits line up with the apps; the glow sticks out
        header.addView(clock.slot(), cp); // the clock itself is drawn over the page, so the cat can roam

        View rule = new View(this);
        rule.setBackgroundColor(0x33FFFFFF);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(Math.max(1, px(2)), px(150));
        rp.leftMargin = px(44);
        rp.rightMargin = px(44);
        header.addView(rule, rp);

        info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        greeting = text(px(44), Color.WHITE, semibold);
        greeting.setIncludeFontPadding(false);
        info.addView(greeting);
        LinearLayout line = new LinearLayout(this);
        String day = Strings.datePattern();
        line.addView(clock(day, day, px(28), 0xA8FFFFFF, regular));
        sunLine = text(px(28), 0xA8FFFFFF, regular);
        line.addView(sunLine);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = px(14);
        info.addView(line, lp);
        badge = text(px(24), 0xF2FFFFFF, regular);
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(px(999));
        pill.setColor(0x1AFFFFFF);
        pill.setStroke(Math.max(1, px(1)), 0x1FFFFFFF);
        badge.setBackground(pill);
        badge.setPadding(px(18), px(7), px(18), px(7));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
        bp.topMargin = px(20);
        info.addView(badge, bp);
        header.addView(info);
        alignInfo();
        refreshAlive();
        root.addView(header, at(px(MARGIN), px(HEADER_TOP), -2, -2));
        root.addView(clock, new FrameLayout.LayoutParams(-1, -1));

        moveHint = text(px(24), 0xA8FFFFFF, regular);
        moveHint.setText(Strings.t("Use the arrows to move it, then press OK"));
        moveHint.setVisibility(View.GONE);
        root.addView(moveHint, at(px(MARGIN), px(ROW1_TOP - 64), -2, -2));

        setContentView(scroller);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(packagesChanged, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(packagesChanged, f);

        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) try {
            if (android.os.Build.VERSION.SDK_INT >= 24) cm.registerDefaultNetworkCallback(networkChanged);
            else cm.registerNetworkCallback(new NetworkRequest.Builder().build(), networkChanged);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packagesChanged);
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) try { cm.unregisterNetworkCallback(networkChanged); } catch (Exception ignored) {}
        handler.removeCallbacks(tick);
        if (accentAnim != null) accentAnim.cancel();
        worker.shutdownNow();
        clock.stop();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (saveConfig(intent)) buildTiles();
    }

    // Saves an app list passed with --es apps "..." and shortcuts passed with --es quick "...".
    // Returns true if it changed.
    // When Android kills the home screen in the background, it can start it again with the same old
    // intent; the stamp install.sh sends stops that from undoing changes made on the TV since.
    private boolean saveConfig(Intent intent) {
        String apps = intent == null ? null : intent.getStringExtra("apps");
        String quick = intent == null ? null : intent.getStringExtra("quick");
        String name = intent == null ? null : intent.getStringExtra("name");
        String place = intent == null ? null : intent.getStringExtra("place");
        String coat = intent == null ? null : intent.getStringExtra("coat");
        if (apps == null && quick == null && name == null && place == null && coat == null) return false;
        SharedPreferences prefs = getSharedPreferences(PREFS, 0);
        String stamp = intent.getStringExtra("stamp");
        if (stamp != null && stamp.equals(prefs.getString("stamp", null))) return false;
        SharedPreferences.Editor e = prefs.edit();
        if (stamp != null) e.putString("stamp", stamp);
        if (apps != null) {
            apps = apps.trim();
            if (apps.isEmpty() || apps.equalsIgnoreCase("auto")) e.remove("apps");
            else e.putString("apps", apps);
        }
        if (quick != null) {
            quick = quick.trim();
            if (quick.isEmpty() || quick.equalsIgnoreCase("none")) e.remove("quick");
            else e.putString("quick", quick);
        }
        for (String[] kv : new String[][] {{"name", name}, {"place", place}, {"coat", coat}}) {
            if (kv[1] == null) continue;
            String v = kv[1].trim();
            if (v.isEmpty() || v.equalsIgnoreCase("none")) e.remove(kv[0]);
            else e.putString(kv[0], v);
        }
        e.apply();
        if (place != null) { weatherAt = 0; weatherTried = 0; weather = null; } // look again for the new place
        if (coat != null && clock != null) clock.setCoat(Coat.parse(prefs.getString("coat", null)));
        sceneKey = null;
        dirty = true;
        return true;
    }

    private List<String> appList(PackageManager pm) {
        List<String> out = new ArrayList<>();
        String saved = getSharedPreferences(PREFS, 0).getString("apps", null);
        String[] wanted = saved != null ? saved.split("[,\\s]+") : Apps.LIST;
        for (String p : wanted) if (!p.isEmpty() && !out.contains(p) && launchIntent(pm, p) != null) out.add(p);
        if (saved == null) {
            // Top up with other TV apps people installed (or updated), skipping launchers and system tools.
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                if (out.size() >= PER_ROW) break;
                String p = ri.activityInfo.packageName;
                int flags = ri.activityInfo.applicationInfo.flags;
                boolean stockSystem = (flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        && (flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
                if (stockSystem || out.contains(p) || NOT_APPS.contains(p) || p.equals(getPackageName())) continue;
                out.add(p);
            }
        }
        return out;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Rebuild only when apps were installed, removed or updated.
        if (dirty) {
            dirty = false;
            buildTiles();
        }
        refreshNetwork();
        handler.removeCallbacks(tick);
        tick.run();
        clock.start();
        // Back after a while: start on the app usually opened at this time of day, if there is one.
        // A quick trip away keeps focus where it was.
        View f = getCurrentFocus();
        String usual = movingKey == null && System.currentTimeMillis() - awaySince > 15 * 60 * 1000
                ? Alive.usual(this, rowApps) : null;
        View u = usual == null ? null : tile(usual);
        if (u != null) u.requestFocus();
        else if (f == null && !tiles.isEmpty()) tiles.get(0).requestFocus();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        clock.stop();
        awaySince = System.currentTimeMillis();
        super.onPause();
    }

    private static final int SCENE = 1, MOON = 2; // the backdrop layers the painted scene and moon go in
    private boolean festive;

    // The window background starts as a plain gradient of the sky, until the scene is painted.
    private void backdrop() {
        double[] at = Alive.place(getSharedPreferences(PREFS, 0));
        GradientDrawable sky = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                Alive.skyColors(Alive.sunElevation(System.currentTimeMillis(), at[0], at[1])));
        accentTop = new GradientDrawable();
        accentMid = new GradientDrawable();
        float[][] where = {{0.80f, 0.08f, 1000}, {0.62f, 0.52f, 800}};
        GradientDrawable[] glows = {accentTop, accentMid};
        for (int i = 0; i < 2; i++) {
            glows[i].setGradientType(GradientDrawable.RADIAL_GRADIENT);
            glows[i].setGradientCenter(where[i][0], where[i][1]);
            glows[i].setGradientRadius(px((int) where[i][2]));
            glows[i].setDither(true);
        }
        backdrop = new LayerDrawable(new Drawable[] {sky, accentTop, accentMid, new ColorDrawable(Color.TRANSPARENT)});
        backdrop.setId(0, SCENE);
        backdrop.setId(3, MOON);
        setAccent(Alive.NEUTRAL);
        getWindow().setBackgroundDrawable(backdrop);
    }

    // The glows take the focused app's color, softer on festival days so the festival shows.
    private void setAccent(int color) {
        accentNow = color;
        int clear = color & 0x00FFFFFF;
        accentTop.setColors(new int[] {Alive.fade(color | 0xFF000000, festive ? 0.22f : 0.5f), clear});
        accentMid.setColors(new int[] {Alive.fade(color | 0xFF000000, 0.16f), clear});
        if (clock != null) clock.setAccent(color);
    }

    // The greeting, line and badge sit level with the middle of the digits.
    private void alignInfo() {
        int cap = Math.round(clock.getTextSizePx() * 0.71f), pad = clock.glowPad();
        info.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        View rule = header.getChildAt(1);
        LinearLayout.LayoutParams ip = (LinearLayout.LayoutParams) info.getLayoutParams();
        LinearLayout.LayoutParams rp = (LinearLayout.LayoutParams) rule.getLayoutParams();
        ip.bottomMargin = pad + Math.max(0, (cap - info.getMeasuredHeight()) / 2);
        rp.height = Math.round(cap * 0.85f);
        rp.bottomMargin = pad + (cap - rp.height) / 2;
        info.setLayoutParams(ip);
        rule.setLayoutParams(rp);
    }

    // Any button wakes the cat, and it looks at whatever has focus once the press has moved it.
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        boolean handled = super.dispatchKeyEvent(e);
        if (clock != null && e.getAction() == KeyEvent.ACTION_DOWN) {
            View f = getCurrentFocus();
            int[] at = new int[2], me = new int[2];
            clock.getLocationOnScreen(me);
            float lookAt = me[0];
            if (f != null) {
                f.getLocationOnScreen(at);
                lookAt = at[0] + f.getWidth() / 2f;
            }
            clock.poke(lookAt - me[0], f);
        }
        return handled;
    }

    private void accentTo(int color) {
        if (accentAnim != null) accentAnim.cancel();
        if (color == accentNow) return;
        accentAnim = android.animation.ValueAnimator.ofArgb(accentNow, color);
        accentAnim.setDuration(350);
        accentAnim.addUpdateListener(a -> setAccent((Integer) a.getAnimatedValue()));
        accentAnim.start();
    }

    private void refreshAlive() {
        Calendar now = Calendar.getInstance();
        long ms = now.getTimeInMillis();
        SharedPreferences prefs = getSharedPreferences(PREFS, 0);
        final String festival = Alive.festival(now);
        greeting.setText(Alive.greeting(now, festival, prefs.getString("name", null)));
        final double[] at = Alive.place(prefs);
        final double sun = Alive.sunElevation(ms, at[0], at[1]);
        final boolean morning = now.get(Calendar.HOUR_OF_DAY) < 12;
        final double moon = Alive.moonAge(ms);
        if (festive != (festival != null)) {
            festive = festival != null;
            setAccent(accentNow);
        }

        // Next to the date: the next sunrise or sunset, or the moon in the evening.
        String sunText = null;
        if (sun < -0.833 && now.get(Calendar.HOUR_OF_DAY) >= 12 && Alive.moonLit(moon) > 0.04) {
            sunText = Alive.moonText(moon);
        } else {
            long next = Alive.nextSunEvent(ms, at[0], at[1]);
            if (next != 0) sunText = (next > 0 ? "Sunrise " : "Sunset ")
                    + android.text.format.DateFormat.getTimeFormat(this).format(new java.util.Date(Math.abs(next)));
        }
        sunLine.setText(sunText == null ? "" : "  ·  " + sunText);

        // The weather, once a place is set: every 30 minutes, or 10 after a failed try. An answer
        // older than 3 hours (the TV was offline) isn't shown.
        if (!prefs.contains("place")) weather = null;
        else if (ms - weatherAt > 30 * 60000 && ms - weatherTried > 10 * 60000) {
            weatherTried = ms;
            worker.execute(() -> {
                final Alive.Weather got = Alive.fetchWeather(at[0], at[1]);
                if (got != null) runOnUiThread(() -> {
                    weather = got;
                    weatherAt = System.currentTimeMillis();
                    refreshAlive();
                });
            });
        }
        if (weather != null && ms - weatherAt > 3 * 3600000L) weather = null;

        // The badge: the festival on its day, else the weather, else a festival coming up.
        String upcoming = Alive.festivalBadge(now);
        String b = festival != null ? upcoming : weather != null ? Alive.weatherBadge(weather) : upcoming;
        badge.setText(b);
        badge.setVisibility(b == null ? View.GONE : View.VISIBLE);

        final Alive.Weather w = weather;
        String key = Math.round(sun) + "|" + morning + "|" + festival + "|" + Math.round(moon)
                + "|" + (w == null ? "-" : w.code + "/" + w.cloud / 20);
        if (key.equals(sceneKey)) return;
        sceneKey = key;
        // Painted at 1920 wide at most: on a 4K screen it's scaled up, which a soft sky doesn't mind.
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        final int sw = Math.min(dm.widthPixels, 1920), sh = Math.round(dm.heightPixels * (sw / (float) dm.widthPixels));
        final boolean moonUp = Alive.moonUp(sun, moon, w);
        final float u = dm.widthPixels / 1920f;
        worker.execute(() -> {
            final Bitmap scene, disc;
            try {
                scene = Alive.paint(sw, sh, sun, morning, festival, moon, w);
                disc = moonUp ? Alive.moon(u, moon) : null;
            } catch (Throwable t) { return; } // out of memory: keep the scene there is
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                Drawable sc = new BitmapDrawable(getResources(), scene);
                Drawable mn = disc == null ? new ColorDrawable(Color.TRANSPARENT) : Alive.moonLayer(disc);
                sc.setBounds(backdrop.getBounds()); // a swapped-in layer doesn't always get its size
                mn.setBounds(backdrop.getBounds());
                backdrop.setDrawableByLayerId(SCENE, sc);
                backdrop.setDrawableByLayerId(MOON, mn);
                backdrop.invalidateSelf();
            });
        });
    }

    // The TV's language changed: start over so the labels and the date follow it.
    @Override
    public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        if (!java.util.Locale.getDefault().equals(shownLocale)) recreate();
    }

    @Override
    public void onBackPressed() { /* home screen: nothing to go back to */ }

    private void buildTiles() {
        // While tiles are swapped out, removing the focused one would hand focus down the row (and
        // overwrite focusedKey), so hold focus off until the new tiles are in.
        String want = focusedKey;
        root.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        for (View t : tiles) {
            View wrap = (View) t.getParent();
            ((ViewGroup) wrap.getParent()).removeView(wrap);
        }
        tiles.clear();
        rowApps.clear();
        movingKey = null;
        moveHint.setVisibility(View.GONE);

        PackageManager pm = getPackageManager();
        int m = px(MARGIN), g = px(GAP);
        int w = (getResources().getDisplayMetrics().widthPixels - 2 * m - g * (PER_ROW - 1)) / PER_ROW;
        int h = w * 9 / 16;
        tileW = w;
        tileH = h;

        List<String> apps = appList(pm);
        // Apps fill rows of six. With more rows the grid starts higher, so up to two rows of apps and
        // the bottom row still fit on screen; past that the page scrolls.
        int launchable = 0;
        for (String pkg : apps) if (launchIntent(pm, pkg) != null) launchable++;
        int rows = Math.max(1, (launchable + PER_ROW - 1) / PER_ROW);
        clock.setTextSize(px(rows > 1 ? CLOCK_SMALL : CLOCK_BIG));
        alignInfo();
        header.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int headerBottom = px(HEADER_TOP) + header.getMeasuredHeight();
        gridTop = Math.max(headerBottom + px(HEADER_GAP), px(ROW1_TOP) - (rows - 1) * px(ROW_STEP));
        int bottomTop = gridTop + rows * px(ROW_STEP);
        ((FrameLayout.LayoutParams) moveHint.getLayoutParams()).topMargin = gridTop - px(48); // in the gap under the clock
        root.setMinimumHeight(bottomTop + h + px(MARGIN));
        // The clock's surface covers the page, so the cat can get to every tile.
        clock.setLayoutParams(new FrameLayout.LayoutParams(getResources().getDisplayMetrics().widthPixels, bottomTop + h + px(MARGIN)));

        for (String pkg : apps) {
            Intent li = launchIntent(pm, pkg);
            if (li == null) continue;
            Drawable banner = null;
            try {
                banner = pm.getActivityBanner(li);
                if (banner == null) banner = pm.getApplicationBanner(pkg);
            } catch (Exception ignored) {}
            String label = pkg;
            try { label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); } catch (Exception ignored) {}
            // Apps without a TV banner get a plain tile with their normal icon.
            Drawable icon = null;
            if (banner == null) try { icon = pm.getApplicationIcon(pkg); } catch (Exception ignored) {}
            int i = rowApps.size();
            rowApps.add(pkg);
            addTile(root, pkg, label, banner, null, icon, gridX(i), gridY(i), w, h);
        }

        // Bottom row: Play Store, HDMI inputs, Settings and Add app, six at most.
        int col = 0;
        boolean store = !apps.contains(PLAY_STORE) && launchIntent(pm, PLAY_STORE) != null;
        List<TvInputInfo> inputs = hdmiInputs();
        int room = PER_ROW - 2 - (store ? 1 : 0);
        if (inputs.size() > room) inputs = inputs.subList(0, room);
        if (store)
            addTile(root, PLAY_STORE, "Play Store", null, LineIcon.PLAY, null, m + col++ * (w + g), bottomTop, w, h);
        for (TvInputInfo in : inputs)
            addTile(root, in.getId(), inputLabel(in), null, LineIcon.HDMI, null, m + col++ * (w + g), bottomTop, w, h);
        addTile(root, SETTINGS, Strings.t("Settings"), null, LineIcon.SETTINGS, null, m + col++ * (w + g), bottomTop, w, h);
        addTile(root, ADD, Strings.t("Add app"), null, LineIcon.PLUS, null, m + col * (w + g), bottomTop, w, h);

        // Plain icons at the top right, level with the date: any settings shortcuts picked, then the
        // network icon. Added last, so the first app keeps first focus; Up from the apps reaches them.
        List<Integer> quick = quickList();
        int ib = px(ICON_BUTTON), ig = px(ICON_GAP);
        // The last icon's right edge lines up with the apps' (its focus circle sticks out past them).
        int qx = getResources().getDisplayMetrics().widthPixels - m + (ib - px(ICON_SIZE)) / 2
                - (quick.size() + 1) * ib - quick.size() * ig;
        for (int q : quick) {
            addIcon(QUICK + QUICK_NAMES[q], Strings.t(QUICK_LABELS[q]), new LineIcon(QUICK_ICONS[q]), qx);
            qx += ib + ig;
        }
        netIcon = addIcon(NETWORK, Strings.t("Network"), null, qx);
        netState = -1;
        refreshNetwork();

        root.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        View restore = null;
        for (View t : tiles) if (t.getTag().equals(want)) restore = t;
        if (restore != null) restore.requestFocus();
        clock.setTiles(tiles.toArray(new View[0]));
    }

    private void addTile(ViewGroup parent, final String key, String label, Drawable banner, int[] lineIcon, Drawable appIcon,
                         int x, int y, int w, int h) {
        FrameLayout card = new FrameLayout(this);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(RADIUS));
        if (banner != null) {
            bg.setColor(0xFF1E1A1C);
            ImageView iv = new ImageView(this);
            Drawable fitted = fit(banner, w, h);
            iv.setImageDrawable(fitted);
            accents.put(key, Alive.accent(fitted));
            card.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            // Quiet "glass" tile: icon above label.
            bg.setColor(0x13FFFFFF);
            bg.setStroke(Math.max(1, px(2)), 0x17FFFFFF);
            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setGravity(Gravity.CENTER);
            ImageView iv = new ImageView(this);
            int size = lineIcon != null ? px(44) : px(64);
            if (lineIcon != null) iv.setImageDrawable(new LineIcon(lineIcon));
            else if (appIcon != null) {
                Drawable fitted = fit(appIcon, size, size);
                iv.setImageDrawable(fitted);
                accents.put(key, Alive.accent(fitted));
            }
            inner.addView(iv, new LinearLayout.LayoutParams(size, size));
            TextView t = text(px(24), 0xE0FFFFFF, regular);
            t.setText(label);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setGravity(Gravity.CENTER);
            t.setPadding(px(16), 0, px(16), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.topMargin = px(12);
            inner.addView(t, lp);
            card.addView(inner, new FrameLayout.LayoutParams(-1, -1));
        }
        card.setBackground(bg);
        card.setClipToOutline(true);
        card.setFocusable(true);
        card.setTag(key);

        // Banner tiles show their name underneath when focused.
        final TextView caption = banner == null ? null : text(px(24), Color.WHITE, regular);
        if (caption != null) {
            caption.setText(label);
            caption.setGravity(Gravity.CENTER_HORIZONTAL);
            caption.setVisibility(View.INVISIBLE);
        }

        final boolean glass = banner == null;
        // Focused: lifted, a white outline and a soft shine, a shadow in the app's own color, and the
        // background glows in it too. The rest sit a little dimmer so the focused one stands out.
        final Integer accent = accents.get(key);
        if (!glass) card.setAlpha(0.86f);
        if (android.os.Build.VERSION.SDK_INT >= 28 && accent != null) {
            card.setOutlineSpotShadowColor(accent);
            card.setOutlineAmbientShadowColor(accent);
        }
        card.setOnFocusChangeListener((v, has) -> {
            if (has) focusedKey = key;
            if (glass) bg.setColor(has ? 0x38FFFFFF : 0x13FFFFFF);
            else v.setAlpha(has ? 1f : 0.86f);
            if (!key.equals(movingKey)) ((FrameLayout) v).setForeground(has ? focusRing() : null);
            if (has) accentTo(accent != null ? accent : Alive.NEUTRAL);
            v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).translationZ(has ? px(26) : 0)
                    .setDuration(140).withLayer().start();
            if (caption != null) caption.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
        });
        final boolean editable = rowApps.contains(key);
        card.setOnClickListener(v -> open(key));
        if (editable) card.setOnLongClickListener(v -> { showEditMenu(key); return true; });
        card.setOnKeyListener((v, code, ev) -> {
            boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                    || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
            boolean down = ev.getAction() == KeyEvent.ACTION_DOWN;
            if (key.equals(movingKey)) return moveKey(key, code, ok, down);
            if (editable && code == KeyEvent.KEYCODE_MENU) {
                if (!down) showEditMenu(key);
                return true;
            }
            if (!ok) return false;
            if (down) {
                if (ev.getRepeatCount() == 0) longPressed = false;
                else if (editable && !longPressed && (ev.isLongPress()
                        || ev.getEventTime() - ev.getDownTime() >= ViewConfiguration.getLongPressTimeout())) {
                    longPressed = true;
                    showEditMenu(key);
                }
            } else if (!longPressed) {
                open(key);
            }
            return true;
        });

        FrameLayout wrap = new FrameLayout(this);
        wrap.setClipChildren(false);
        wrap.addView(card, new FrameLayout.LayoutParams(w, h));
        if (caption != null) {
            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(w, -2);
            cp.topMargin = h + px(22);
            wrap.addView(caption, cp);
        }
        parent.addView(wrap, at(x, y, w, -2));
        tiles.add(card);
    }

    // An icon on its own, no tile or label: a glass circle shows behind it only while it's focused.
    private ImageView addIcon(final String key, String label, Drawable icon, int x) {
        int b = px(ICON_BUTTON), size = px(ICON_SIZE);
        FrameLayout card = new FrameLayout(this);
        final GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.TRANSPARENT);
        card.setBackground(bg);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(icon);
        card.addView(iv, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        card.setFocusable(true);
        card.setTag(key);
        card.setContentDescription(label);
        card.setOnFocusChangeListener((v, has) -> {
            if (has) focusedKey = key;
            bg.setColor(has ? 0x38FFFFFF : Color.TRANSPARENT);
            v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).setDuration(140).withLayer().start();
        });
        card.setOnClickListener(v -> open(key));
        FrameLayout wrap = new FrameLayout(this);
        wrap.setClipChildren(false);
        wrap.addView(card, new FrameLayout.LayoutParams(b, b));
        root.addView(wrap, at(x, px(ICON_TOP), b, b));
        tiles.add(card);
        return iv;
    }

    private void open(String key) {
        Intent i;
        if (ADD.equals(key)) {
            showAddMenu(-1);
            return;
        } else if (SETTINGS.equals(key)) {
            i = new Intent(Settings.ACTION_SETTINGS);
        } else if (isInput(key)) {
            openInput(key);
            return;
        } else if (NETWORK.equals(key)) {
            openNetworkSettings();
            return;
        } else if (key.startsWith(QUICK)) {
            openQuick(key.substring(QUICK.length()));
            return;
        } else {
            i = launchIntent(getPackageManager(), key);
            if (i == null) return;
            Alive.opened(this, key);
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception ignored) {}
    }

    // Live TV apps an HDMI tile can open, in order. Droidlogic (Amlogic) TVs ship Live TV under their
    // own package, with TvActivity as its entry point.
    private static final String[][] LIVE_TV = {{"com.android.tv", "com.android.tv.MainActivity"},
            {"com.droidlogic.android.tv", "com.android.tv.TvActivity"}};
    private static final String INPUT_PLAYER = "com.google.android.tv.inputplayer";

    // Opens an HDMI input: the stock Live TV app first, then Droidlogic's, then whatever the TV picks.
    // Each one is tried only when the one before it can't start, so a TV where the stock app already
    // worked takes exactly the same path as before. The one exception: when the TV itself sends HDMI to
    // Google TV's input player, that goes first, because some Google TVs ship com.android.tv as a stub
    // that starts fine but only says "Hello World!" (issue #5).
    private void openInput(String id) {
        Intent view = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id));
        List<Intent> tries = new ArrayList<>();
        ResolveInfo def = getPackageManager().resolveActivity(view, PackageManager.MATCH_DEFAULT_ONLY);
        if (def != null && def.activityInfo != null && INPUT_PLAYER.equals(def.activityInfo.packageName))
            tries.add(new Intent(view).setClassName(INPUT_PLAYER, def.activityInfo.name));
        for (String[] live : LIVE_TV) {
            Intent t = new Intent(view).setClassName(live[0], live[1]);
            if (t.resolveActivity(getPackageManager()) != null) tries.add(t);
        }
        tries.add(view);
        for (Intent t : tries) {
            t.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(t);
                return;
            } catch (Exception ignored) {}
        }
    }

    // The shortcuts picked with --es quick, in that order, as indexes into QUICK_NAMES. A shortcut
    // this TV has no screen for is left out, so it never shows a tile that does nothing.
    private List<Integer> quickList() {
        List<Integer> out = new ArrayList<>();
        String saved = getSharedPreferences(PREFS, 0).getString("quick", null);
        if (saved == null) return out;
        for (String name : saved.split("[,\\s]+")) {
            int q = Arrays.asList(QUICK_NAMES).indexOf(name.toLowerCase(java.util.Locale.ROOT));
            if (q < 0 || out.contains(q) || quickIntents(QUICK_NAMES[q]).isEmpty()) continue;
            out.add(q);
        }
        return out;
    }

    // OFFLINE, 1 to 3 for Wi-Fi with that many bars, or ETHERNET (any other link, which on a TV is a cable).
    private int networkState() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return OFFLINE;
        try {
            int rssi = Integer.MIN_VALUE;
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                NetworkCapabilities c = cm.getNetworkCapabilities(cm.getActiveNetwork());
                if (c == null) return OFFLINE;
                if (!c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return ETHERNET;
                if (android.os.Build.VERSION.SDK_INT >= 29) rssi = c.getSignalStrength();
            } else {
                android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
                if (ni == null || !ni.isConnected()) return OFFLINE;
                if (ni.getType() != ConnectivityManager.TYPE_WIFI) return ETHERNET;
            }
            if (rssi == Integer.MIN_VALUE) { // Android 9 and older: ask the Wi-Fi service
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null && wm.getConnectionInfo() != null) rssi = wm.getConnectionInfo().getRssi();
            }
            return 1 + WifiManager.calculateSignalLevel(rssi, 3);
        } catch (Exception e) {
            return OFFLINE;
        }
    }

    private void refreshNetwork() {
        if (netIcon == null) return;
        int state = networkState();
        if (state == netState) return;
        netState = state;
        if (state == ETHERNET) {
            netIcon.setImageDrawable(new LineIcon(LineIcon.ETHERNET));
        } else {
            // The whole Wi-Fi fan dim, with the bars it has bright on top. Offline is a slash instead.
            LineIcon fan = new LineIcon(LineIcon.WIFI);
            fan.setAlpha(0x47);
            int[] top = state == OFFLINE ? LineIcon.SLASH : state == 1 ? LineIcon.WIFI_1
                    : state == 2 ? LineIcon.WIFI_2 : LineIcon.WIFI;
            netIcon.setImageDrawable(new LayerDrawable(new Drawable[] {fan, new LineIcon(top)}));
        }
        ((View) netIcon.getParent()).setContentDescription(
                state == OFFLINE ? Strings.t("Offline") : state == ETHERNET ? "Ethernet" : "Wi-Fi");
    }

    // Wi-Fi settings (on Android TV that page lists Ethernet too), else the TV's network page, else Settings.
    private void openNetworkSettings() {
        for (String a : new String[] {Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS,
                Settings.ACTION_SETTINGS}) {
            try {
                startActivity(new Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) {}
        }
    }

    // Screens a shortcut can open, best first, keeping only those this TV has.
    private List<Intent> quickIntents(String name) {
        List<Intent> all = new ArrayList<>();
        String tclType = name.equals("sound") ? "SoundPreset" : name.equals("picture") ? "picture" : null;
        if (tclType != null)
            all.add(new Intent(TCL_SHOW_WINDOW).setClassName("com.tcl.settings", "com.tcl.settings.ShowWindowService")
                    .putExtra("Type", tclType));
        switch (name) {
            case "bluetooth":
                all.add(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                all.add(new Intent("com.google.android.intent.action.CONNECT_INPUT")); // Android TV's pairing screen
                break;
            case "sound": all.add(new Intent(Settings.ACTION_SOUND_SETTINGS)); break;
            case "picture": all.add(new Intent(Settings.ACTION_DISPLAY_SETTINGS)); break;
        }
        PackageManager pm = getPackageManager();
        List<Intent> out = new ArrayList<>();
        for (Intent i : all) {
            boolean service = TCL_SHOW_WINDOW.equals(i.getAction());
            if (service ? pm.resolveService(i, 0) != null : i.resolveActivity(pm) != null) out.add(i);
        }
        return out;
    }

    // Opens a shortcut's screen: the first one that starts wins.
    private void openQuick(String name) {
        for (Intent i : quickIntents(name)) {
            try {
                if (TCL_SHOW_WINDOW.equals(i.getAction())) startService(i);
                else startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) {}
        }
    }

    interface Pick { void at(int which); }

    // A side panel in the home screen's own look: a title, then one row per choice, with the same
    // glass focus as the tiles. Back closes it. iconPkgs (or null) puts each app's icon by its name.
    private void panel(String title, final List<String> labels, final List<String> iconPkgs, final Pick pick) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(px(48), px(92), px(48), px(48));

        TextView head = text(px(40), Color.WHITE, semibold);
        head.setText(title);
        head.setSingleLine(true);
        head.setEllipsize(android.text.TextUtils.TruncateAt.END);
        head.setPadding(px(24), 0, px(24), 0);
        box.addView(head);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        GradientDrawable focus = new GradientDrawable();
        focus.setCornerRadius(px(RADIUS));
        focus.setColor(0x38FFFFFF);
        list.setSelector(focus);
        // Icons load as rows appear, drawn small to save RAM.
        final PackageManager pm = getPackageManager();
        final int size = px(48);
        final Drawable[] icons = new Drawable[labels.size()];
        list.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return labels.size(); }
            @Override public Object getItem(int pos) { return labels.get(pos); }
            @Override public long getItemId(int pos) { return pos; }
            @Override public View getView(int pos, View convert, ViewGroup parent) {
                TextView t = convert != null ? (TextView) convert : text(px(30), 0xE6FFFFFF, regular);
                t.setText(labels.get(pos));
                t.setSingleLine(true);
                t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                t.setGravity(Gravity.CENTER_VERTICAL);
                t.setMinHeight(px(88));
                t.setPadding(px(24), 0, px(24), 0);
                Drawable icon = null;
                if (iconPkgs != null) {
                    if (icons[pos] == null) {
                        try { icons[pos] = fit(pm.getApplicationIcon(iconPkgs.get(pos)), size, size); }
                        catch (Exception e) { icons[pos] = new ColorDrawable(Color.TRANSPARENT); }
                        icons[pos].setBounds(0, 0, size, size);
                    }
                    icon = icons[pos];
                }
                t.setCompoundDrawablesRelative(icon, null, null, null);
                t.setCompoundDrawablePadding(px(24));
                return t;
            }
        });
        list.setOnItemClickListener((parent, v, pos, id) -> {
            d.dismiss();
            pick.at(pos);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lp.topMargin = px(36);
        box.addView(list, lp);

        d.setContentView(box);
        Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(0xF7161113));
        w.setLayout(px(640), -1);
        w.setGravity(Gravity.END);
        w.setDimAmount(0.5f);
        d.show();
    }

    // A small menu that drops down under a tile, in the same glass as the tiles. Opens above the
    // tile when there's no room below. Back closes it.
    private void dropdown(View anchor, String[] items, final Pick pick) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = px(10), rowH = px(64);
        box.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(RADIUS));
        bg.setColor(0xFF231D1F);
        bg.setStroke(Math.max(1, px(2)), 0x17FFFFFF);
        box.setBackground(bg);
        float widest = 0; // some languages need more room than the tile is wide
        for (int i = 0; i < items.length; i++) {
            final int which = i;
            final TextView t = text(px(26), 0xD9FFFFFF, regular);
            t.setText(Strings.t(items[i]));
            t.setSingleLine(true);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding(px(22), 0, px(22), 0);
            widest = Math.max(widest, t.getPaint().measureText(t.getText().toString()) + 2 * px(22) + 2 * pad);
            final GradientDrawable focus = new GradientDrawable();
            focus.setCornerRadius(px(RADIUS - pad));
            focus.setColor(Color.TRANSPARENT);
            t.setBackground(focus);
            t.setFocusable(true);
            t.setOnFocusChangeListener((v, has) -> {
                focus.setColor(has ? 0x38FFFFFF : Color.TRANSPARENT);
                t.setTextColor(has ? Color.WHITE : 0xD9FFFFFF);
            });
            t.setOnClickListener(v -> {
                d.dismiss();
                pick.at(which);
            });
            box.addView(t, new LinearLayout.LayoutParams(-1, rowH));
        }
        d.setContentView(box);

        // Line it up with the tile as drawn (focused tiles are scaled up), below its name.
        int[] at = new int[2];
        anchor.getLocationOnScreen(at);
        int tileBottom = at[1] + Math.round(anchor.getHeight() * anchor.getScaleY());
        int w = Math.max(Math.max(Math.round(anchor.getWidth() * anchor.getScaleX()), px(320)), (int) Math.ceil(widest));
        int h = items.length * rowH + 2 * pad;
        int y = tileBottom + px(64);
        if (y + h > getResources().getDisplayMetrics().heightPixels - px(24)) y = at[1] - h - px(16);
        Window win = d.getWindow();
        win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        win.setGravity(Gravity.TOP | Gravity.START);
        android.view.WindowManager.LayoutParams lp = win.getAttributes();
        lp.x = at[0];
        lp.y = y;
        lp.width = w;
        lp.height = h;
        win.setAttributes(lp);
        d.show();
    }

    // Hold OK (or Menu) on a top-row app: move it, swap it for another, or take it off the home
    // screen. Removing only takes the tile away: the app stays installed and can be added back.
    // Apps the user installed can also be uninstalled; Android asks to confirm, and the tile goes
    // once the app is gone. Apps that came with the TV can't be, so they get App info instead,
    // where Android offers to turn them off.
    private void showEditMenu(final String key) {
        final int index = rowApps.indexOf(key);
        if (index < 0) return;
        final boolean user = userInstalled(key);
        dropdown(tile(key), user ? EDIT_ITEMS : EDIT_ITEMS_SYSTEM, which -> {
            if (which == 0) startMove(key);
            else if (which == 1) showAddMenu(index);
            else if (which == 3) {
                Uri pkg = Uri.parse("package:" + key);
                try { startActivity(user ? new Intent(Intent.ACTION_DELETE, pkg)
                        : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)); }
                catch (Exception ignored) {}
            } else {
                rowApps.remove(index);
                saveRow();
                focusedKey = rowApps.isEmpty() ? ADD : rowApps.get(Math.min(index, rowApps.size() - 1));
                buildTiles();
            }
        });
    }

    // Every launchable app that isn't on the top row yet, by name. The pick is added at the end,
    // or takes the place of the app at replaceIndex (-1 to add).
    private void showAddMenu(final int replaceIndex) {
        final PackageManager pm = getPackageManager();
        TreeMap<String, String> byLabel = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String p = ri.activityInfo.packageName;
                if (rowApps.contains(p) || byLabel.containsValue(p) || p.equals(getPackageName())
                        || (NOT_APPS.contains(p) && !p.equals(PLAY_STORE))) continue;
                String label = labelOf(p);
                if (!byLabel.containsKey(label)) byLabel.put(label, p);
            }
        }
        if (byLabel.isEmpty()) {
            panel(Strings.t("Every app is already here"), Collections.singletonList("OK"), null, which -> {});
            return;
        }
        final List<String> pkgs = new ArrayList<>(byLabel.values());
        String title = replaceIndex < 0 ? Strings.t("Add an app")
                : String.format(Strings.t("Replace %s"), labelOf(rowApps.get(replaceIndex)));
        panel(title, new ArrayList<>(byLabel.keySet()), pkgs, which -> {
            if (replaceIndex < 0) rowApps.add(pkgs.get(which));
            else rowApps.set(replaceIndex, pkgs.get(which));
            saveRow();
            focusedKey = pkgs.get(which);
            buildTiles();
        });
    }

    private String labelOf(String pkg) {
        PackageManager pm = getPackageManager();
        try { return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); }
        catch (Exception e) { return pkg; }
    }

    // Saves the top row as the app list, so it no longer follows apps.txt.
    private void saveRow() {
        getSharedPreferences(PREFS, 0).edit().putString("apps", android.text.TextUtils.join(",", rowApps)).apply();
    }

    // Where app number i sits in the grid.
    private int gridX(int i) {
        return px(MARGIN) + (i % PER_ROW) * (tileW + px(GAP));
    }

    private int gridY(int i) {
        return gridTop + (i / PER_ROW) * px(ROW_STEP);
    }

    // A soft shine from the top left and a white outline, drawn over the focused tile.
    private Drawable focusRing() {
        GradientDrawable shine = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0x38FFFFFF, 0x00FFFFFF, 0x00FFFFFF});
        shine.setCornerRadius(px(RADIUS));
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(px(RADIUS));
        ring.setStroke(Math.max(2, px(3)), 0xF2FFFFFF);
        return new LayerDrawable(new Drawable[] {shine, ring});
    }

    private View tile(String key) {
        for (View t : tiles) if (t.getTag().equals(key)) return t;
        return null;
    }

    // Move mode: the tile gets an outline, the arrows move it through the grid, OK or Back finishes.
    private void startMove(String key) {
        View card = tile(key);
        if (card == null) return;
        movingKey = key;
        GradientDrawable outline = new GradientDrawable();
        outline.setCornerRadius(px(RADIUS));
        outline.setStroke(px(4), Color.WHITE);
        ((FrameLayout) card).setForeground(outline);
        moveHint.setVisibility(View.VISIBLE);
        card.requestFocus();
    }

    private boolean moveKey(String key, int code, boolean ok, boolean down) {
        int step = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1
                : code == KeyEvent.KEYCODE_DPAD_UP ? -PER_ROW : code == KeyEvent.KEYCODE_DPAD_DOWN ? PER_ROW : 0;
        if (step != 0) {
            int from = rowApps.indexOf(key), to = from + step;
            if (down && to >= 0 && to < rowApps.size()) {
                // Slide the tiles into their new places: no rebuild, so it stays quick on slow TVs.
                rowApps.add(to, rowApps.remove(from));
                for (int i = Math.min(from, to); i <= Math.max(from, to); i++) {
                    View wrap = (View) tile(rowApps.get(i)).getParent();
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) wrap.getLayoutParams();
                    lp.leftMargin = gridX(i);
                    lp.topMargin = gridY(i);
                    wrap.setLayoutParams(lp);
                }
                // Scroll along with it once it has moved.
                final View card = tile(key);
                card.post(() -> card.requestRectangleOnScreen(
                        new android.graphics.Rect(0, 0, card.getWidth(), card.getHeight())));
            }
            return true;
        }
        if (ok || code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU) {
            if (!down) {
                movingKey = null;
                longPressed = false;
                ((FrameLayout) tile(key)).setForeground(focusRing()); // still focused
                moveHint.setVisibility(View.GONE);
                saveRow();
            }
            return true;
        }
        return true; // up/down and everything else stay put while moving
    }

    private static boolean isInput(String key) {
        return key.indexOf('/') > 0;
    }

    // The TV's own HDMI inputs (not devices behind them), in order, skipping hidden ones.
    private List<TvInputInfo> hdmiInputs() {
        List<TvInputInfo> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        for (TvInputInfo in : tim.getTvInputList()) {
            if (in.getType() == TvInputInfo.TYPE_HDMI && in.getParentId() == null && !in.isHidden(this)) out.add(in);
        }
        Collections.sort(out, (a, b) -> a.getId().compareTo(b.getId()));
        return out.size() > MAX_INPUTS ? out.subList(0, MAX_INPUTS) : out;
    }

    // The name set in Settings > Inputs (like "Bedroom"), else the TV's own name (like "HDMI 1").
    private String inputLabel(TvInputInfo in) {
        CharSequence custom = in.loadCustomLabel(this);
        if (custom != null && custom.length() > 0) return custom.toString();
        CharSequence label = in.loadLabel(this);
        return label != null && label.length() > 0 ? label.toString() : "HDMI";
    }

    private boolean userInstalled(String pkg) {
        try { return (getPackageManager().getApplicationInfo(pkg, 0).flags & ApplicationInfo.FLAG_SYSTEM) == 0; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }

    private static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    // Xiaomi TVs ship MiLan Pro but don't register it by name; every other TV gets Roboto.
    private static void loadFonts() {
        if (regular != null) return;
        regular = fromFile("/system/fonts/MILanPro_Normal.ttf", Typeface.create("sans-serif", Typeface.NORMAL));
        semibold = fromFile("/system/fonts/MILanPro_SemiBold.ttf", Typeface.create("sans-serif-medium", Typeface.NORMAL));
        light = Typeface.create("sans-serif-light", Typeface.NORMAL);
    }

    private static Typeface fromFile(String path, Typeface fallback) {
        try { return new File(path).exists() ? Typeface.createFromFile(path) : fallback; }
        catch (Exception e) { return fallback; }
    }

    // Draws a banner once into a tile-sized bitmap (center-cropped). Some apps ship huge banners
    // that Android 9 refuses to draw ("Canvas: trying to draw too large bitmap"); this also saves RAM.
    private Drawable fit(Drawable d, int w, int h) {
        try {
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
            if (iw <= 0 || ih <= 0) { iw = w; ih = h; }
            float s = Math.max((float) w / iw, (float) h / ih);
            int dw = Math.round(iw * s), dh = Math.round(ih * s);
            d.setBounds((w - dw) / 2, (h - dh) / 2, (w - dw) / 2 + dw, (h - dh) / 2 + dh);
            d.draw(new Canvas(b));
            return new BitmapDrawable(getResources(), b);
        } catch (Throwable t) {
            return d;
        }
    }

    private int px(int v) {
        return Math.round(v * scale);
    }

    private TextClock clock(String f12, String f24, int px, int color, Typeface tf) {
        TextClock c = new TextClock(this);
        c.setFormat12Hour(f12);
        c.setFormat24Hour(f24);
        c.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        c.setTextColor(color);
        c.setTypeface(tf);
        return c;
    }

    private TextView text(int px, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        t.setTextColor(color);
        if (tf != null) t.setTypeface(tf);
        return t;
    }

    private static FrameLayout.LayoutParams at(int x, int y, int w, int h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.leftMargin = x;
        lp.topMargin = y;
        return lp;
    }
}
