# Add project specific ProGuard rules here.
# Keep notification listener + receiver entry points.
-keep class com.wechatauto.forwarder.WeChatNotificationListenerService { *; }
-keep class com.wechatauto.forwarder.NotificationReplyReceiver { *; }
