package com.ansub.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

// Settings for Skip, sent by skip.sh:
//   --es next off          don't press "Next Episode" (only intros, recaps and credits)
//   --es apps "pkg1,pkg2"  also watch these apps
public final class SkipActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent in = getIntent();
        if (in != null) {
            SharedPreferences.Editor e = getSharedPreferences(SkipService.PREFS, 0).edit();
            if (in.hasExtra("next")) e.putBoolean("next", !"off".equalsIgnoreCase(in.getStringExtra("next")));
            if (in.hasExtra("apps")) e.putString("apps", in.getStringExtra("apps"));
            e.apply();
        }
        finish();
    }
}
