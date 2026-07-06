package com.wechatauto.forwarder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * In-memory state for one WeChat conversation (a contact or a group).
 * Keeps a rolling window of recent messages so we can rebuild the
 * MessagingStyle notification that Android Auto reads.
 */
public class Conversation {

    public static class Msg {
        public final String sender;   // null == sent by "me"
        public final String body;
        public final long time;
        public final boolean fromSelf;

        public Msg(String sender, String body, long time, boolean fromSelf) {
            this.sender = sender;
            this.body = body;
            this.time = time;
            this.fromSelf = fromSelf;
        }
    }

    private static final int MAX_MESSAGES = 25;

    public final String key;
    public String title;
    public boolean group;
    public String appLabel;
    public final int notificationId;

    private final Deque<Msg> messages = new ArrayDeque<>();

    /** Last forwarded body + time, used to de-duplicate WeChat's repeated posts. */
    public String lastBody;
    public long lastPostTime;

    /** Key of the source app's original notification, for "mark as read" cancel.
     *  Valid only while this app's process is alive. */
    public String wechatSbnKey;

    /** WeChat's own inline-reply hooks, if its notification exposed any. Used for
     *  best-effort reply delivery. Valid only while this process is alive. */
    public android.app.PendingIntent wechatReplyIntent;
    public android.app.RemoteInput wechatReplyRemoteInput;

    public Conversation(String key, String title, boolean group, int notificationId) {
        this.key = key;
        this.title = title;
        this.group = group;
        this.notificationId = notificationId;
    }

    public synchronized void addMessage(Msg m) {
        messages.addLast(m);
        while (messages.size() > MAX_MESSAGES) {
            messages.removeFirst();
        }
    }

    public synchronized void clearMessages() {
        messages.clear();
    }

    public synchronized List<Msg> snapshot() {
        return new ArrayList<>(messages);
    }
}
