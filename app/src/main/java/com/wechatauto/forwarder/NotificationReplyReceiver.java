package com.wechatauto.forwarder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

/**
 * Handles the only car action this receive/read-only app offers: MARK_AS_READ.
 * It dismisses WeChat's original notification and our relayed one.
 */
public class NotificationReplyReceiver extends BroadcastReceiver {

    public static final String ACTION_MARK_READ = "com.wechatauto.forwarder.ACTION_MARK_READ";
    public static final String EXTRA_NOTIFICATION_ID = "extra_notification_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_MARK_READ.equals(intent.getAction())) {
            return;
        }
        int notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1);
        if (notifId < 0) {
            return;
        }

        Conversation conv = ConversationStore.get(context).getByNotificationId(notifId);
        if (conv != null) {
            MessageNotificationListenerService.cancelSourceNotification(conv.wechatSbnKey);
            conv.clearMessages();
        }
        new CarNotificationForwarder(context).cancel(notifId);

        LocalBroadcastManager.getInstance(context).sendBroadcast(
                new Intent(MessageNotificationListenerService.ACTION_MESSAGE_FORWARDED));
    }
}
