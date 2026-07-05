package com.wechatauto.forwarder;

import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

/**
 * Listens to every posted notification, keeps only WeChat message notifications,
 * and re-posts them as Android Auto messaging notifications in real time.
 */
public class WeChatNotificationListenerService extends NotificationListenerService {

    public static final String ACTION_STATE_CHANGED =
            "com.wechatauto.forwarder.STATE_CHANGED";
    public static final String ACTION_MESSAGE_FORWARDED =
            "com.wechatauto.forwarder.MESSAGE_FORWARDED";

    private static volatile WeChatNotificationListenerService instance;
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
        if (sbn == null
                || !WeChatMessageParser.WECHAT_PACKAGE.equals(sbn.getPackageName())
                || !Prefs.isForwardingEnabled(this)) {
            return;
        }

        WeChatMessageParser.Parsed parsed =
                WeChatMessageParser.parse(sbn, Prefs.isGroupSplitEnabled(this));
        if (parsed == null) {
            return;
        }

        ConversationStore store = ConversationStore.get(this);
        Conversation conv = store.getOrCreate(
                parsed.conversationKey, parsed.conversationTitle, parsed.group);

        // Always refresh the reply hooks with the latest notification.
        conv.wechatReplyIntent = parsed.replyIntent;
        conv.wechatReplyRemoteInput = parsed.replyRemoteInput;
        conv.wechatSbnKey = parsed.sbnKey;

        // De-duplicate WeChat's repeated posts of the same latest message.
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
                parsed.conversationTitle, parsed.senderName,
                parsed.body, parsed.postTime, parsed.group));

        LocalBroadcastManager.getInstance(this)
                .sendBroadcast(new Intent(ACTION_MESSAGE_FORWARDED));
    }

    private void broadcastState() {
        LocalBroadcastManager.getInstance(this)
                .sendBroadcast(new Intent(ACTION_STATE_CHANGED));
    }

    /** Cancels WeChat's original notification (used for "mark as read"). */
    public static void cancelWeChatNotification(String key) {
        WeChatNotificationListenerService s = instance;
        if (s != null && key != null) {
            try {
                s.cancelNotification(key);
            } catch (Exception ignored) {
                // Listener may have been disconnected.
            }
        }
    }
}
