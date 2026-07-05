package com.wechatauto.forwarder;

/** Immutable record of one forwarded message, used for the on-screen history. */
public class ForwardedMessage {
    public final String conversationTitle;
    public final String sender;
    public final String body;
    public final long timestamp;
    public final boolean group;

    public ForwardedMessage(String conversationTitle, String sender, String body,
                            long timestamp, boolean group) {
        this.conversationTitle = conversationTitle;
        this.sender = sender;
        this.body = body;
        this.timestamp = timestamp;
        this.group = group;
    }
}
