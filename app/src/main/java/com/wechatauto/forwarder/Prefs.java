package com.wechatauto.forwarder;

import android.content.Context;
import android.content.SharedPreferences;

/** Small wrapper around SharedPreferences for user toggles. */
public final class Prefs {
    private static final String FILE = "wechat_auto_prefs";
    private static final String KEY_FORWARDING = "forwarding_enabled";
    private static final String KEY_GROUP_SPLIT = "group_split_enabled";
    private static final String KEY_APP_PREFIX = "app_enabled_";
    private static final String KEY_CAR_GATE = "forward_only_when_car_connected";

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean isForwardingEnabled(Context c) {
        return sp(c).getBoolean(KEY_FORWARDING, true);
    }

    public static void setForwardingEnabled(Context c, boolean v) {
        sp(c).edit().putBoolean(KEY_FORWARDING, v).apply();
    }

    /** Per-source-app switch. */
    public static boolean isAppEnabled(Context c, SupportedApp app) {
        return sp(c).getBoolean(KEY_APP_PREFIX + app.name(), true);
    }

    public static boolean isGroupSplitEnabled(Context c) {
        return sp(c).getBoolean(KEY_GROUP_SPLIT, true);
    }

    public static void setGroupSplitEnabled(Context c, boolean v) {
        sp(c).edit().putBoolean(KEY_GROUP_SPLIT, v).apply();
    }

    /** When true (default), only forward while connected to Android Auto / a car. */
    public static boolean isForwardOnlyWhenCarConnected(Context c) {
        return sp(c).getBoolean(KEY_CAR_GATE, true);
    }

    public static void setForwardOnlyWhenCarConnected(Context c, boolean v) {
        sp(c).edit().putBoolean(KEY_CAR_GATE, v).apply();
    }
}
