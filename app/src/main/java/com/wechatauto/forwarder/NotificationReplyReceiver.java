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
 * Handles the two car actions on our relayed notification:
 *  - REPLY: required by Android Auto for the message to appear at all. We attempt
 *    to proxy the reply into WeChat's own inline-reply action if it exists; WeChat
 *    currently exposes none, so we record the reply as undeliverable instead of
 *    pretending it was sent.
 *  - MARK_AS_READ: dismisses WeChat's original notification and our relayed one.
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

        Conversation conv = ConversationStore.get(context).getByNotificationId(notifId);
        CarNotificationForwarder forwarder = new CarNotificationForwarder(context);

        if (ACTION_REPLY.equals(action)) {
            CharSequence reply = getReplyText(intent);
            if (conv != null && !TextUtils.isEmpty(reply)) {
                boolean delivered = sendReplyToWeChat(context, conv, reply);
                long now = System.currentTimeMillis();
                if (delivered) {
                    conv.addMessage(new Conversation.Msg(null, reply.toString(), now, true));
                } else {
                    // WeChat can't accept a third-party reply: don't pretend it sent.
                    conv.addMessage(new Conversation.Msg(
                            null, context.getString(R.string.reply_undeliverable), now, true));
                }
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
     * Fires WeChat's own inline-reply PendingIntent with the reply text. Best-effort:
     * only works while this process is alive and only if WeChat's notification exposed
     * a reply action (current WeChat does not). Returns true only if actually sent.
     */
    private boolean sendReplyToWeChat(Context context, Conversation conv, CharSequence reply) {
        PendingIntent pi = conv.wechatReplyIntent;
        android.app.RemoteInput ri = conv.wechatReplyRemoteInput;
        if (pi == null || ri == null) {
            return false;
        }
        try {
            Intent fillIn = new Intent();
            Bundle bundle = new Bundle();
            bundle.putCharSequence(ri.getResultKey(), reply);
            android.app.RemoteInput.addResultsToIntent(
                    new android.app.RemoteInput[]{ri}, fillIn, bundle);
            pi.send(context, 0, fillIn);
            return true;
        } catch (PendingIntent.CanceledException ignored) {
            return false;
        }
    }
}
