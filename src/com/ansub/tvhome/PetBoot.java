package com.ansub.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

// She comes back after the TV restarts.
public final class PetBoot extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) return;
        Intent svc = new Intent(c, PetService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(svc);
            else c.startService(svc);
        } catch (Exception ignored) {}
    }
}
