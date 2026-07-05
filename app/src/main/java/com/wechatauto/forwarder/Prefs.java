package com.wechatauto.forwarder;

import android.content.Context;
import android.content.SharedPreferences;

/** Small wrapper around SharedPreferences for user toggles. */
public final class Prefs {
    private static final String FILE = "wechat_auto_prefs";
    private static final String KEY_FORWARDING = "forwarding_enabled";
    private static final String KEY_GROUP_SPLIT = "group_split_enabled";
    private static final String KEY_APP_PREFIX = "app_enabled_";
    private static final String KEY_REPLY_CAP = "reply_capability";

    /** Reply-capability of the source app's notifications. */
    public static final int REPLY_UNKNOWN = -1;
    public static final int REPLY_NONE = 0;        // no reply action at all
    public static final int REPLY_OPENS_APP = 1;   // has actions but no inline RemoteInput
    public static final int REPLY_INLINE = 2;      // exposes an inline-reply RemoteInput

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

    public static void setAppEnabled(Context c, SupportedApp app, boolean v) {
        sp(c).edit().putBoolean(KEY_APP_PREFIX + app.name(), v).apply();
    }

    public static boolean isGroupSplitEnabled(Context c) {
        return sp(c).getBoolean(KEY_GROUP_SPLIT, true);
    }

    public static void setGroupSplitEnabled(Context c, boolean v) {
        sp(c).edit().putBoolean(KEY_GROUP_SPLIT, v).apply();
    }

    /** Last observed reply capability of a source app's notification. */
    public static int getReplyCapability(Context c) {
        return sp(c).getInt(KEY_REPLY_CAP, REPLY_UNKNOWN);
    }

    public static void setReplyCapability(Context c, int v) {
        sp(c).edit().putInt(KEY_REPLY_CAP, v).apply();
    }
}
