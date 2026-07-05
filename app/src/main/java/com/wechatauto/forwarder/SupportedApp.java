package com.wechatauto.forwarder;

import androidx.annotation.Nullable;

/**
 * Registry of the messaging apps whose notifications we relay to Android Auto.
 * Add a new entry here to support another app.
 */
public enum SupportedApp {
    WECHAT("com.tencent.mm", "微信", true),
    TEAMS("com.microsoft.teams", "Teams", false);

    /** Android package of the source app. */
    public final String packageName;
    /** Human-readable label shown to the user and used as a car-title prefix. */
    public final String label;
    /**
     * Whether to apply WeChat's text quirks in the fallback parser
     * (the "[n条]" unread counter and "sender: body" group split).
     */
    public final boolean wechatHeuristics;

    SupportedApp(String packageName, String label, boolean wechatHeuristics) {
        this.packageName = packageName;
        this.label = label;
        this.wechatHeuristics = wechatHeuristics;
    }

    @Nullable
    public static SupportedApp fromPackage(String pkg) {
        if (pkg == null) {
            return null;
        }
        for (SupportedApp app : values()) {
            if (app.packageName.equals(pkg)) {
                return app;
            }
        }
        return null;
    }
}
