package com.wechatauto.forwarder;

import android.app.Notification;
import android.app.PendingIntent;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts a usable message out of a raw WeChat notification and captures
 * WeChat's own inline-reply action (so we can proxy replies back to it).
 */
public final class WeChatMessageParser {

    public static final String WECHAT_PACKAGE = "com.tencent.mm";

    /** Leading unread-count marker, e.g. "[2条]" or "[99+条]". */
    private static final Pattern COUNT_PREFIX = Pattern.compile("^\\[\\d+\\+?条\\]\\s*");

    /** Group line "Sender: body" (ASCII or full-width colon). */
    private static final Pattern GROUP_LINE =
            Pattern.compile("^([^:：]{1,32})[:：]\\s?(.+)$", Pattern.DOTALL);

    private WeChatMessageParser() {}

    public static class Parsed {
        public String conversationKey;
        public String conversationTitle;
        public String senderName;
        public String body;
        public boolean group;
        public long postTime;
        public String sbnKey;
        public PendingIntent replyIntent;
        public android.app.RemoteInput replyRemoteInput;
    }

    public static Parsed parse(StatusBarNotification sbn, boolean groupSplitEnabled) {
        if (sbn == null || !WECHAT_PACKAGE.equals(sbn.getPackageName())) {
            return null;
        }
        Notification n = sbn.getNotification();
        if (n == null) {
            return null;
        }
        // Ignore the persistent "WeChat is running" foreground notification and
        // the bundling group-summary notification.
        if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0) {
            return null;
        }
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
            return null;
        }

        Bundle extras = n.extras;
        if (extras == null) {
            return null;
        }

        String title = asString(extras.getCharSequence(Notification.EXTRA_TITLE));
        String text = asString(extras.getCharSequence(Notification.EXTRA_TEXT));
        if (TextUtils.isEmpty(text)) {
            text = asString(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        }
        if (TextUtils.isEmpty(title) || TextUtils.isEmpty(text)) {
            return null;
        }

        // Drop WeChat's multi-conversation summary ("x个联系人发来y条消息").
        if (isSummaryText(text)) {
            return null;
        }

        // Strip the "[n条]" unread-count prefix if present.
        Matcher cm = COUNT_PREFIX.matcher(text);
        if (cm.find()) {
            text = text.substring(cm.end()).trim();
        }
        if (TextUtils.isEmpty(text)) {
            return null;
        }

        Parsed p = new Parsed();
        p.conversationKey = title;
        p.conversationTitle = title;
        p.postTime = sbn.getPostTime() > 0 ? sbn.getPostTime() : System.currentTimeMillis();
        p.sbnKey = sbn.getKey();

        boolean group = false;
        String sender = title;
        String body = text;

        if (groupSplitEnabled) {
            Matcher gm = GROUP_LINE.matcher(text);
            if (gm.matches()) {
                String maybeSender = gm.group(1).trim();
                String maybeBody = gm.group(2).trim();
                // A group sender name is short and space-free (Chinese names, nicknames).
                if (!TextUtils.isEmpty(maybeSender) && !TextUtils.isEmpty(maybeBody)
                        && maybeSender.length() <= 20 && !maybeSender.contains(" ")) {
                    group = true;
                    sender = maybeSender;
                    body = maybeBody;
                }
            }
        }

        p.group = group;
        p.senderName = sender;
        p.body = body;

        captureReplyAction(n, p);
        return p;
    }

    private static String asString(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }

    private static boolean isSummaryText(String text) {
        return text.matches(".*\\d+\\s*[个位]联系人.*");
    }

    private static void captureReplyAction(Notification n, Parsed p) {
        if (n.actions == null) {
            return;
        }
        for (Notification.Action action : n.actions) {
            android.app.RemoteInput[] inputs = action.getRemoteInputs();
            if (inputs != null && inputs.length > 0) {
                p.replyIntent = action.actionIntent;
                p.replyRemoteInput = inputs[0];
                return;
            }
        }
    }
}
