package com.ansub.tvhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;

import java.util.HashSet;
import java.util.Set;

// "Just the cat": a see-through window over the TV's own home screen with the cat living along its
// bottom edge. She is only shown while a home screen is in front (checked every couple of seconds
// through usage stats), so she never sits over an app or a film, and she goes away when the screen
// does. No key presses are taken; the remote works exactly as before.
public final class PetService extends Service {
    static final String PREFS = "petcat";
    private static final String CHANNEL = "petcat";
    private static final long POLL = 1500;

    private WindowManager wm;
    private ClockCat cat;
    private boolean added, screenOn = true;
    private String front = "";
    private final Set<String> homes = new HashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            check();
            handler.postDelayed(this, POLL);
        }
    };

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            screenOn = !Intent.ACTION_SCREEN_OFF.equals(i.getAction());
            show(screenOn && homes.contains(front));
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        PackageManager pm = getPackageManager();
        for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0))
            homes.add(ri.activityInfo.packageName);
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screen, f);
        foreground();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getBooleanExtra("stop", false)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (cat == null) {
            cat = new ClockCat(this, android.graphics.Typeface.DEFAULT);
            int h = getResources().getDisplayMetrics().heightPixels;
            cat.setFree(h * 0.09f);
        }
        cat.setCoat(Coat.parse(getSharedPreferences(PREFS, 0).getString("coat", null)));
        handler.removeCallbacks(poll);
        handler.post(poll);
        return START_STICKY;
    }

    // Who's in front right now, from the last app brought forward.
    private void check() {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return;
        long now = System.currentTimeMillis();
        UsageEvents events = usm.queryEvents(now - 20000, now);
        UsageEvents.Event e = new UsageEvents.Event();
        String last = null;
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            int type = e.getEventType();
            if (type == UsageEvents.Event.MOVE_TO_FOREGROUND || (Build.VERSION.SDK_INT >= 29 && type == UsageEvents.Event.ACTIVITY_RESUMED))
                last = e.getPackageName();
        }
        if (last != null) front = last;
        // With no usage access at all (not granted), nothing ever comes back: show her anyway.
        boolean granted = !front.isEmpty();
        show(screenOn && (!granted || homes.contains(front)));
    }

    private void show(boolean on) {
        if (cat == null) return;
        if (on == added) return;
        try {
            if (on) {
                int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT);
                lp.gravity = Gravity.TOP | Gravity.START;
                wm.addView(cat, lp);
                cat.start();
            } else {
                cat.stop();
                wm.removeView(cat);
            }
            added = on;
        } catch (Exception ignored) {
            // No overlay permission yet: cat.sh grants it; nothing to do until then.
        }
    }

    private void foreground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Pet Cat", NotificationManager.IMPORTANCE_MIN);
            nm.createNotificationChannel(ch);
            b = new Notification.Builder(this, CHANNEL);
        } else b = new Notification.Builder(this);
        b.setContentTitle("Pet Cat").setContentText("Your cat is on the home screen")
                .setSmallIcon(android.R.drawable.ic_menu_view).setOngoing(true);
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, b.build(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(1, b.build());
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(poll);
        unregisterReceiver(screen);
        show(false);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }

    static void setCoat(Context c, String coat) {
        SharedPreferences.Editor e = c.getSharedPreferences(PREFS, 0).edit();
        if (coat == null || coat.trim().isEmpty() || coat.equalsIgnoreCase("none")) e.remove("coat");
        else e.putString("coat", coat.trim());
        e.apply();
    }
}
