package com.wechatauto.forwarder;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.core.app.RemoteInput;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

/**
 * Handles the actions the user triggers from Android Auto:
 *  - REPLY: proxies the typed/spoken text back into WeChat's own reply action.
 *  - MARK_AS_READ: dismisses WeChat's original notification and ours.
 */
public class NotificationReplyReceiver extends BroadcastReceiver {

    public static final String ACTION_REPLY = "com.wechatauto.forwarder.ACTION_REPLY";
    public static final String ACTION_MARK_READ = "com.wechatauto.forwarder.ACTION_MARK_READ";
    public static final String EXTRA_NOTIFICATION_ID = "extra_notification_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        int notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1);
        if (action == null || notifId < 0) {
            return;
        }

        ConversationStore store = ConversationStore.get(context);
        Conversation conv = store.getByNotificationId(notifId);
        CarNotificationForwarder forwarder = new CarNotificationForwarder(context);

        if (ACTION_REPLY.equals(action)) {
            CharSequence reply = getReplyText(intent);
            if (conv != null && !TextUtils.isEmpty(reply)) {
                sendReplyToWeChat(context, conv, reply);
                // Reflect the sent reply so Android Auto shows it in the thread.
                long now = System.currentTimeMillis();
                conv.addMessage(new Conversation.Msg(null, reply.toString(), now, true));
                conv.lastPostTime = now;
                forwarder.post(conv);
            }
        } else if (ACTION_MARK_READ.equals(action)) {
            if (conv != null) {
                MessageNotificationListenerService.cancelSourceNotification(conv.wechatSbnKey);
                conv.clearMessages();
            }
            forwarder.cancel(notifId);
        }

        // Let the UI refresh if it is open.
        LocalBroadcastManager.getInstance(context).sendBroadcast(
                new Intent(MessageNotificationListenerService.ACTION_MESSAGE_FORWARDED));
    }

    private CharSequence getReplyText(Intent intent) {
        Bundle results = RemoteInput.getResultsFromIntent(intent);
        if (results == null) {
            return null;
        }
        return results.getCharSequence(CarNotificationForwarder.REMOTE_INPUT_KEY);
    }

    /**
     * Fires WeChat's own inline-reply PendingIntent with the reply text, which
     * makes WeChat actually send the message. Best-effort: only works while this
     * process is alive (the captured PendingIntent lives in memory) and only if
     * WeChat's notification exposed a reply action.
     */
    private void sendReplyToWeChat(Context context, Conversation conv, CharSequence reply) {
        PendingIntent pi = conv.wechatReplyIntent;
        android.app.RemoteInput ri = conv.wechatReplyRemoteInput;
        if (pi == null || ri == null) {
            return;
        }
        try {
            Intent fillIn = new Intent();
            Bundle bundle = new Bundle();
            bundle.putCharSequence(ri.getResultKey(), reply);
            android.app.RemoteInput.addResultsToIntent(
                    new android.app.RemoteInput[]{ri}, fillIn, bundle);
            pi.send(context, 0, fillIn);
        } catch (PendingIntent.CanceledException ignored) {
            // WeChat's notification/action is no longer valid.
        }
    }
}
