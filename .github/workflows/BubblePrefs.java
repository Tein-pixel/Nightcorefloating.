package com.android.settings.overlay;

import android.content.Context;
import android.content.SharedPreferences;

final class BubblePrefs {
    static final String FILE = "nc_prefs";
    static final int DEFAULT_COLOR = 0xFF00E5FF;
    static final int[] COLORS = {
            0xFF00E5FF, 0xFFFF2E88, 0xFFB388FF, 0xFF00E676,
            0xFFFFAB00, 0xFFFF5252, 0xFFFFFFFF, 0xFF448AFF
    };

    private BubblePrefs() {}

    static SharedPreferences sp(Context c) { return c.getSharedPreferences(FILE, Context.MODE_PRIVATE); }
    static int shape(Context c) { return sp(c).getInt("shape", 0); }          // 0 lingkaran, 1 kotak, 2 bulat, 3 segitiga
    static int color(Context c) { return sp(c).getInt("color", DEFAULT_COLOR); }
    static int alpha(Context c) { return sp(c).getInt("alpha", 100); }        // 20..100 (%)
    static int size(Context c) { return sp(c).getInt("size", 44); }           // 32..72 (dp)
}
