package com.wechatauto.forwarder;

import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

/**
 * Listens to every posted notification, keeps only notifications from
 * {@link SupportedApp}s (WeChat), and re-posts them as Android Auto messaging
 * notifications in real time. Receive/read-only.
 */
public class MessageNotificationListenerService extends NotificationListenerService {

    public static final String ACTION_STATE_CHANGED =
            "com.wechatauto.forwarder.STATE_CHANGED";
    public static final String ACTION_MESSAGE_FORWARDED =
            "com.wechatauto.forwarder.MESSAGE_FORWARDED";

    private static volatile MessageNotificationListenerService instance;
    private static volatile boolean connected = false;

    public static boolean isConnected() {
        return connected;
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
        connected = true;
        broadcastState();
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        connected = false;
        if (instance == this) {
            instance = null;
        }
        broadcastState();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (instance == this) {
            instance = null;
            connected = false;
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !Prefs.isForwardingEnabled(this)) {
            return;
        }

        MessageParser.Parsed parsed = MessageParser.parse(this, sbn);
        if (parsed == null) {
            return;
        }

        // Gate: only relay while connected to Android Auto / a car head unit.
        if (Prefs.isForwardOnlyWhenCarConnected(this)
                && !CarConnectionHelper.isCarConnected(this)) {
            return;
        }

        ConversationStore store = ConversationStore.get(this);
        Conversation conv = store.getOrCreate(
                parsed.conversationKey, parsed.conversationTitle, parsed.group);
        conv.appLabel = parsed.app.label;
        conv.wechatSbnKey = parsed.sbnKey;
        conv.wechatReplyIntent = parsed.replyIntent;
        conv.wechatReplyRemoteInput = parsed.replyRemoteInput;

        // De-duplicate repeated posts of the same latest message.
        if (parsed.body.equals(conv.lastBody)
                && Math.abs(parsed.postTime - conv.lastPostTime) < 1500) {
            return;
        }

        conv.lastBody = parsed.body;
        conv.lastPostTime = parsed.postTime;
        conv.addMessage(new Conversation.Msg(
                parsed.senderName, parsed.body, parsed.postTime, false));

        new CarNotificationForwarder(this).post(conv);

        store.addRecent(new ForwardedMessage(
                parsed.app.label, parsed.conversationTitle, parsed.senderName,
                parsed.body, parsed.postTime, parsed.group));

        LocalBroadcastManager.getInstance(this)
                .sendBroadcast(new Intent(ACTION_MESSAGE_FORWARDED));
    }

    private void broadcastState() {
        LocalBroadcastManager.getInstance(this)
                .sendBroadcast(new Intent(ACTION_STATE_CHANGED));
    }

    /** Cancels the source app's original notification (used for "mark as read"). */
    public static void cancelSourceNotification(String key) {
        MessageNotificationListenerService s = instance;
        if (s != null && key != null) {
            try {
                s.cancelNotification(key);
            } catch (Exception ignored) {
                // Listener may have been disconnected.
            }
        }
    }
}
