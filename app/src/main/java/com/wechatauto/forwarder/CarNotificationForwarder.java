package com.wechatauto.forwarder;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;

/**
 * Builds and posts the Android Auto-compatible MessagingStyle notification for a
 * conversation. Android Auto reads these aloud and offers voice reply because
 * they use category=MESSAGE plus REPLY / MARK_AS_READ semantic actions.
 */
public class CarNotificationForwarder {

    public static final String CHANNEL_ID = "wechat_car_messages";
    public static final String REMOTE_INPUT_KEY = "key_car_reply";

    private final Context context;

    public CarNotificationForwarder(Context context) {
        this.context = context.getApplicationContext();
        ensureChannel();
    }

    private void ensureChannel() {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(context.getString(R.string.channel_desc));
            nm.createNotificationChannel(channel);
        }
    }

    /** Posts (or updates) the car notification for the given conversation. */
    public void post(Conversation conv) {
        Person self = new Person.Builder()
                .setName(context.getString(R.string.self_name))
                .setKey("self")
                .build();

        NotificationCompat.MessagingStyle style = new NotificationCompat.MessagingStyle(self);
        style.setGroupConversation(conv.group);
        String displayTitle = (conv.appLabel != null && !conv.appLabel.isEmpty())
                ? conv.appLabel + " · " + conv.title
                : conv.title;
        style.setConversationTitle(displayTitle);

        for (Conversation.Msg m : conv.snapshot()) {
            Person person = m.fromSelf
                    ? null
                    : new Person.Builder().setName(m.sender).setKey(m.sender).build();
            style.addMessage(m.body, m.time, person);
        }

        int notifId = conv.notificationId;

        RemoteInput remoteInput = new RemoteInput.Builder(REMOTE_INPUT_KEY)
                .setLabel(context.getString(R.string.action_reply))
                .build();

        NotificationCompat.Action replyAction =
                new NotificationCompat.Action.Builder(
                        R.drawable.ic_reply,
                        context.getString(R.string.action_reply),
                        buildReplyPendingIntent(notifId))
                        .addRemoteInput(remoteInput)
                        .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                        .setShowsUserInterface(false)
                        .setAllowGeneratedReplies(true)
                        .build();

        NotificationCompat.Action markReadAction =
                new NotificationCompat.Action.Builder(
                        R.drawable.ic_check,
                        context.getString(R.string.action_mark_read),
                        buildMarkReadPendingIntent(notifId))
                        .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                        .setShowsUserInterface(false)
                        .build();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_message)
                .setStyle(style)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setWhen(conv.lastPostTime > 0 ? conv.lastPostTime : System.currentTimeMillis())
                .setContentIntent(buildContentIntent())
                .addAction(replyAction)
                .addAction(markReadAction);

        NotificationManagerCompat nmc = NotificationManagerCompat.from(context);
        try {
            nmc.notify(notifId, builder.build());
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS not granted (Android 13+); nothing else to do.
        }
    }

    public void cancel(int notifId) {
        NotificationManagerCompat.from(context).cancel(notifId);
    }

    private PendingIntent buildReplyPendingIntent(int notifId) {
        Intent intent = new Intent(context, NotificationReplyReceiver.class)
                .setAction(NotificationReplyReceiver.ACTION_REPLY)
                .putExtra(NotificationReplyReceiver.EXTRA_NOTIFICATION_ID, notifId);
        // Must be mutable so the system can inject the RemoteInput reply text.
        return PendingIntent.getBroadcast(context, notifId * 10 + 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
    }

    private PendingIntent buildMarkReadPendingIntent(int notifId) {
        Intent intent = new Intent(context, NotificationReplyReceiver.class)
                .setAction(NotificationReplyReceiver.ACTION_MARK_READ)
                .putExtra(NotificationReplyReceiver.EXTRA_NOTIFICATION_ID, notifId);
        return PendingIntent.getBroadcast(context, notifId * 10 + 2, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent buildContentIntent() {
        Intent intent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
