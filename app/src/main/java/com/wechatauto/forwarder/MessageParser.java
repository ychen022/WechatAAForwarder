package com.wechatauto.forwarder;

import android.app.Notification;
import android.content.Context;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a raw notification from a {@link SupportedApp} into a normalized message.
 *
 * Strategy:
 *  1. Prefer the notification's own {@code MessagingStyle} when present — this gives
 *     structured sender / body / group info.
 *  2. Fall back to title/text parsing, applying WeChat's quirks when relevant.
 */
public final class MessageParser {

    /** Leading unread-count marker, e.g. "[2条]" or "[99+条]". */
    private static final Pattern COUNT_PREFIX = Pattern.compile("^\\[\\d+\\+?条\\]\\s*");

    /** Group line "Sender: body" (ASCII or full-width colon). */
    private static final Pattern GROUP_LINE =
            Pattern.compile("^([^:：]{1,32})[:：]\\s?(.+)$", Pattern.DOTALL);

    private MessageParser() {}

    public static class Parsed {
        public SupportedApp app;
        public String conversationKey;
        public String conversationTitle;
        public String senderName;
        public String body;
        public boolean group;
        public long postTime;
        public String sbnKey;
        public android.app.PendingIntent replyIntent;
        public android.app.RemoteInput replyRemoteInput;
    }

    public static Parsed parse(Context ctx, StatusBarNotification sbn) {
        if (sbn == null) {
            return null;
        }
        SupportedApp app = SupportedApp.fromPackage(sbn.getPackageName());
        if (app == null || !Prefs.isAppEnabled(ctx, app)) {
            return null;
        }

        Notification n = sbn.getNotification();
        if (n == null) {
            return null;
        }
        // Drop persistent foreground ("running") notifications, bundle summaries
        // and call notifications.
        if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0
                || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
            return null;
        }
        if (Notification.CATEGORY_CALL.equals(n.category)) {
            return null;
        }

        Parsed p = fromMessagingStyle(app, n);
        if (p == null) {
            p = fromTitleText(ctx, app, n);
        }
        if (p == null) {
            return null;
        }

        p.app = app;
        p.postTime = sbn.getPostTime() > 0 ? sbn.getPostTime() : System.currentTimeMillis();
        p.sbnKey = sbn.getKey();
        String base = p.group ? p.conversationTitle : p.senderName;
        if (TextUtils.isEmpty(base)) {
            base = p.conversationTitle;
        }
        p.conversationKey = app.name() + "|" + base;
        captureReplyAction(n, p);
        return p;
    }

    /** Captures WeChat's own inline-reply action, if present, for best-effort reply. */
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

    /** Uses the notification's own MessagingStyle when present. */
    private static Parsed fromMessagingStyle(SupportedApp app, Notification n) {
        NotificationCompat.MessagingStyle style =
                NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n);
        if (style == null) {
            return null;
        }
        List<NotificationCompat.MessagingStyle.Message> messages = style.getMessages();
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        NotificationCompat.MessagingStyle.Message last = messages.get(messages.size() - 1);
        String body = last.getText() == null ? "" : last.getText().toString().trim();
        if (TextUtils.isEmpty(body)) {
            return null;
        }

        boolean group = style.isGroupConversation();
        String convTitle = style.getConversationTitle() == null
                ? "" : style.getConversationTitle().toString().trim();

        Person person = last.getPerson();
        String sender = person != null && person.getName() != null
                ? person.getName().toString().trim() : "";
        if (TextUtils.isEmpty(sender)) {
            sender = TextUtils.isEmpty(convTitle) ? app.label : convTitle;
        }

        Parsed p = new Parsed();
        p.group = group;
        p.senderName = sender;
        p.body = body;
        p.conversationTitle = group
                ? (TextUtils.isEmpty(convTitle) ? sender : convTitle)
                : sender;
        return p;
    }

    /** Fallback: parse the plain title / text of the notification. */
    private static Parsed fromTitleText(Context ctx, SupportedApp app, Notification n) {
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

        boolean group = false;
        String sender = title;
        String body = text;

        if (app.wechatHeuristics) {
            // Drop WeChat's multi-conversation summary ("x个联系人发来y条消息").
            if (text.matches(".*\\d+\\s*[个位]联系人.*")) {
                return null;
            }
            // Strip the "[n条]" unread-count prefix.
            Matcher cm = COUNT_PREFIX.matcher(text);
            if (cm.find()) {
                body = text.substring(cm.end()).trim();
            }
            if (TextUtils.isEmpty(body)) {
                return null;
            }
            if (Prefs.isGroupSplitEnabled(ctx)) {
                Matcher gm = GROUP_LINE.matcher(body);
                if (gm.matches()) {
                    String maybeSender = gm.group(1).trim();
                    String maybeBody = gm.group(2).trim();
                    if (!TextUtils.isEmpty(maybeSender) && !TextUtils.isEmpty(maybeBody)
                            && maybeSender.length() <= 20 && !maybeSender.contains(" ")) {
                        group = true;
                        sender = maybeSender;
                        body = maybeBody;
                    }
                }
            }
        }

        Parsed p = new Parsed();
        p.group = group;
        p.senderName = sender;
        p.body = body;
        p.conversationTitle = title;
        return p;
    }

    private static String asString(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }
}
