# WeChat Auto — 微信消息转发到 Android Auto

WeChat (微信) does not integrate with Android Auto, so its messages never show up on
the car screen or get read aloud. **WeChat Auto** bridges that gap: it reads WeChat
notifications on your phone in real time and re-publishes them as Android
Auto‑compatible *messaging* notifications, so incoming messages are **displayed and
read aloud** in Android Auto.

> This app does not modify WeChat and does not need root. It only relays the
> notifications WeChat already posts. It has **no internet permission**.

## Can I reply to WeChat from the car?
Short answer: **you can speak a reply, but it can't reach WeChat** — so in practice this
is a **receive + read‑aloud** app. Two hard platform facts, neither fixable here:

1. **Android Auto never shows custom app UI on the car screen.** Third‑party messaging
   apps can only use notifications + the system's built‑in voice flow; you cannot pop
   open your own Activity (buttons / text boxes) on the car display. A custom
   "record voice → edit text → send" screen on the car is impossible by design
   (driver‑distraction rules).
2. **WeChat exposes no reply hook.** Unlike WhatsApp/Telegram/Signal, WeChat's Android
   notifications provide **no** `RemoteInput` direct‑reply action, and personal WeChat
   has **no public send API**. So even though Android Auto captures a spoken reply for
   us, there's no supported way to inject that text back into WeChat.

**Why the notification still has a "Reply" action:** Android Auto only surfaces a
MessagingStyle notification (shows it and reads it aloud) when it carries **both** a
reply action *and* a mark‑as‑read action. Without the reply action Auto shows
"No new messages". So the reply action must exist for messages to appear at all — but
when you use it, the app tries WeChat's own reply hook (none exists) and then clearly
marks the reply **未发送 (undeliverable)** rather than pretending it sent.

The only conceivable way to actually send would be an **AccessibilityService** that
drives WeChat's UI (open chat, type, tap send). It's fragile, can't run on the car
screen, is unsafe while driving, and is ToS‑questionable — so it is intentionally
**not** implemented. (Ask if you ever want it as a phone‑only, parked‑use experiment.)

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
┌───────────────┐  posts   ┌──────────────────────────────────┐  re-post as       ┌──────────────┐
│    WeChat     │ ───────▶ │ MessageNotificationListenerService │ ─ MessagingStyle ─▶ │ Android Auto │
│(com.tencent.mm)│  notif  │   → parse → forward                │ (reply*+mark-read)│  read + show │
└───────────────┘          └──────────────────────────────────┘                    └──────────────┘
                                            * reply action is required by Auto to show the
                                              message; delivery into WeChat isn't supported
```

### Components
| Class | Responsibility |
|-------|----------------|
| `SupportedApp` | Registry of relayed apps (currently WeChat) + parsing flags. Adding another app is one line. |
| `MessageNotificationListenerService` | Reads supported apps' notifications live (`onNotificationPosted`) and forwards them. |
| `MessageParser` | Prefers the notification's own `MessagingStyle`; falls back to title/text parsing with WeChat's quirks (`[n条]` counter, `sender: body` group split). Filters ongoing/summary/call notifications; captures WeChat's reply hook if any. |
| `CarNotificationForwarder` | Builds & posts the `MessagingStyle` car notification (category `MESSAGE`, `SEMANTIC_ACTION_REPLY` + `SEMANTIC_ACTION_MARK_AS_READ` — both required for Auto to surface it). |
| `NotificationReplyReceiver` | Handles *reply* (best‑effort into WeChat; marked undeliverable when WeChat has no reply hook) and *mark as read* (dismisses WeChat's original notification and the relayed one). |
| `ConversationStore` / `Conversation` | Per‑conversation state, de‑duplication, and the on‑screen history. |
| `MainActivity` | Enables notification access, requests `POST_NOTIFICATIONS`, toggles, a **test message** button, and a live status panel. |

---

## Requirements
- Android 8.0 (API 26) or newer.
- Android Auto set up on the phone + a head unit (or the "Desktop Head Unit" for testing).
- WeChat installed, with notifications enabled and message content shown on the
  lock/notification screen.

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
   real message arrives.
5. Keep WeChat Auto installed; the listener runs in the background automatically.

The status panel shows, at a glance, whether: notification access is granted, the
listener is connected, `POST_NOTIFICATIONS` is granted, and forwarding is on.

## Using it
- When a message arrives, Android Auto shows it and (for new messages) reads it
  aloud. Use **Mark as read** to clear it from the car (this also dismisses WeChat's
  notification on the phone).
- A **Reply** action appears (Android Auto requires it to show the message), but replies
  can't reach WeChat — a spoken reply is marked **未发送 (undeliverable)**. See
  "Can I reply to WeChat from the car?" above.
- **拆分群消息发送者 (Split group sender)**: when on, group messages formatted as
  `发送者: 内容` are shown with the sender as the speaker and the group as the
  conversation title (nicer TTS). Turn off if your contacts' messages get split
  incorrectly.

---

## Limitations & notes
- **Message content must be visible in the notification.** If WeChat hides message
  text on the lock screen (e.g. "你收到了一条消息"), only that placeholder can be
  forwarded. Enable message previews in WeChat's notification settings.
- **Reply can't reach WeChat.** The notification carries a Reply action only because
  Android Auto requires one to surface the message; WeChat has no reply hook and Auto
  forbids custom car UI, so replies are marked undeliverable — see the section above.
- **Group sender detection is heuristic** (`发送者: 内容`); it can occasionally
  mis‑split a 1:1 message that contains a colon — toggle it off if needed.
- Rapid duplicate posts of the same latest message are de‑duplicated within ~1.5s.
- **Privacy:** everything is processed on‑device. The app has **no internet
  permission** and sends nothing anywhere; it only re‑posts local notifications.
- Some OEM skins aggressively kill background listeners — exclude WeChat Auto from
  battery optimization if forwarding stops after a while.

## Troubleshooting
- *Nothing appears in Android Auto:* verify the status items are ✅, then use
  **Send test message** while connected. If the test works but WeChat doesn't, check
  WeChat's own notification settings (previews enabled, not silenced).
- *Listener shows disconnected:* re‑toggle notification access for WeChat Auto in
  system settings; some devices require toggling it off/on after an update.
