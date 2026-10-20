package com.ansub.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

// Opening "Pet Cat" from the apps row turns her on. cat.sh uses it too:
//   am start -n com.petcat.overlay/com.ansub.tvhome.PetActivity --es coat "tabby;..."   her colours
//   am start -n com.petcat.overlay/com.ansub.tvhome.PetActivity --es cat off             put her away
public final class PetActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent in = getIntent();
        if (in != null && in.hasExtra("coat")) PetService.setCoat(this, in.getStringExtra("coat"));
        Intent svc = new Intent(this, PetService.class);
        if (in != null && "off".equalsIgnoreCase(in.getStringExtra("cat"))) {
            svc.putExtra("stop", true);
            startService(svc);
        } else if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
        else startService(svc);
        finish();
    }
}
