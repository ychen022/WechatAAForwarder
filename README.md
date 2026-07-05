# WeChat Auto — 微信消息转发到 Android Auto

WeChat (微信) does not integrate with Android Auto, so its messages never show up
on the car screen or get read aloud. **WeChat Auto** bridges that gap: it reads
WeChat notifications on your phone in real time and re-publishes them as
Android Auto‑compatible *messaging* notifications, so incoming WeChat messages are
**displayed and read aloud** in Android Auto — and you can **reply by voice**.

> This app does not modify WeChat and does not need root. It only relays the
> notifications WeChat already posts.

---

## How it works

Android Auto supports two kinds of third‑party apps: **media** apps and
**messaging** apps. Messaging apps do *not* use the Car App Library — instead
Android Auto surfaces and reads out any app that:

1. declares `<uses name="notification"/>` via the
   `com.google.android.gms.car.application` manifest metadata, and
2. posts `NotificationCompat.MessagingStyle` notifications (category `MESSAGE`)
   with a **Reply** (`RemoteInput`) action and a **Mark as read** action.

WeChat itself does neither, so it never appears in the car. This app does both on
WeChat's behalf:

```
┌──────────┐   posts    ┌───────────────────────────────┐   re-post as   ┌──────────────┐
│  WeChat  │ ─────────▶ │ WeChatNotificationListener      │ ─ MessagingStyle ▶ │ Android Auto │
│(com.tencent.mm)│ notif │  → parse → forward              │  (reply/markread) │  read + show │
└──────────┘            └───────────────────────────────┘                └──────────────┘
                                       ▲  reply text (voice/typed)              │
                                       └────────────────────────────────────────┘
```

### Components
| Class | Responsibility |
|-------|----------------|
| `WeChatNotificationListenerService` | Reads `com.tencent.mm` notifications live (`onNotificationPosted`) and forwards them. |
| `WeChatMessageParser` | Extracts conversation/sender/body, strips the `[n条]` unread counter, drops the "WeChat is running" and summary notifications, detects group messages, and captures WeChat's own inline‑reply action. |
| `CarNotificationForwarder` | Builds & posts the `MessagingStyle` car notification (category `MESSAGE`, `SEMANTIC_ACTION_REPLY` + `SEMANTIC_ACTION_MARK_AS_READ`). |
| `NotificationReplyReceiver` | Handles the Auto reply (proxies your text back into WeChat's reply `RemoteInput`) and *mark as read* (dismisses the WeChat + relayed notifications). |
| `ConversationStore` / `Conversation` | Per‑conversation state, de‑duplication, and the on‑screen history. |
| `MainActivity` | Enables notification access, requests `POST_NOTIFICATIONS`, toggles, a **test message** button, and a live status panel. |

---

## Requirements
- Android 8.0 (API 26) or newer.
- Android Auto set up on the phone + a head unit (or the "Desktop Head Unit" for testing).
- WeChat installed, with notifications enabled and message content shown on the lock/notification screen.

## Build
Open the project in Android Studio (Ladybug or newer) and press **Run**, or from a
terminal:

```bash
./gradlew assembleDebug        # Windows: gradlew.bat assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Toolchain: AGP 8.7.2 · Gradle 8.9 · compileSdk 35 · minSdk 26 · Java 17.

## First‑time setup on the phone
1. Launch **WeChat Auto** and tap **开启通知使用权 (Open notification access)**.
   Enable "WeChat Auto" in the system *Notification access* list.
2. Grant the **通知发送权限 (POST_NOTIFICATIONS)** prompt (Android 13+).
3. Make sure **转发 (Forwarding)** is on.
4. Tap **发送测试消息 (Send test message)** and connect to Android Auto — you should
   see/hear the test message on the car screen. This confirms the pipeline before a
   real WeChat message arrives.
5. Keep WeChat Auto installed; the listener runs in the background automatically.

The status panel shows, at a glance, whether: notification access is granted, the
listener is connected, `POST_NOTIFICATIONS` is granted, and forwarding is on.

## Using it
- When a WeChat message arrives, Android Auto shows it and (for new messages) reads
  it aloud. Tap the message or use the Assistant to **hear**, **reply**, or
  **mark as read**.
- **Reply** is proxied straight into WeChat's own inline‑reply action, so your
  spoken reply is sent as a normal WeChat message — best‑effort (see limitations).
- **拆分群消息发送者 (Split group sender)**: when on, group messages formatted as
  `发送者: 内容` are shown with the sender as the speaker and the group as the
  conversation title (nicer TTS). Turn off if your contacts' messages get split
  incorrectly.

---

## Limitations & notes
- **Message content must be visible in the WeChat notification.** If WeChat is set
  to hide message text on the lock screen (e.g. "你收到了一条消息"), only that
  placeholder can be forwarded. Enable message previews in WeChat's notification
  settings for full content.
- **Reply back to WeChat is best‑effort.** It works only while this app's process is
  alive and only if WeChat's notification exposed an inline‑reply action (it usually
  does). If it can't reply, marking as read still works. Sending images/voice/stickers
  is not supported — text only.
- **Group sender detection is heuristic** (`发送者: 内容`). It can occasionally
  mis‑split a 1:1 message that contains a colon; toggle it off if needed.
- Rapid duplicate posts of the same latest message are de‑duplicated within ~1.5s.
- **Privacy:** everything is processed on‑device. The app has **no internet
  permission** and sends nothing anywhere; it only re‑posts local notifications.
- Some OEM skins aggressively kill background listeners — exclude WeChat Auto from
  battery optimization if forwarding stops after a while.

## Troubleshooting
- *Nothing appears in Android Auto:* verify all four status items are ✅, then use
  **Send test message** while connected. If the test works but WeChat doesn't, check
  WeChat's own notification settings (previews enabled, not silenced).
- *Listener shows disconnected:* re‑toggle notification access for WeChat Auto in
  system settings; some devices require toggling it off/on after an update.
