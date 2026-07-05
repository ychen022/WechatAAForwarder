# WeChat Auto — 微信消息转发到 Android Auto

WeChat (微信) does not integrate with Android Auto, so its messages never show up on
the car screen or get read aloud. **WeChat Auto** bridges that gap: it reads WeChat
notifications on your phone in real time and re-publishes them as Android
Auto‑compatible *messaging* notifications, so incoming messages are **displayed and
read aloud** in Android Auto.

> This app does not modify WeChat and does not need root. It only relays the
> notifications WeChat already posts. It has **no internet permission**.

## Can I reply to WeChat from the car? (Read this)
**Receiving** WeChat messages in Android Auto works well. **Replying**, however, runs
into two hard platform limits — neither is fixable by this app:

1. **Android Auto never shows custom app UI on the car screen.** Third‑party messaging
   apps can only use notifications + the system's built‑in voice reply; you cannot pop
   open your own Activity (with buttons / text boxes) on the car display. So a custom
   "record voice → edit text → send" screen on the car is not possible by design
   (driver‑distraction rules).
2. **WeChat exposes no inline‑reply hook.** Unlike WhatsApp/Telegram/Signal, WeChat's
   Android notifications do **not** provide a `RemoteInput` direct‑reply action, and
   personal WeChat has **no public send API**. So even though Android Auto can capture
   your spoken reply for us, there is no supported way to inject that text back into
   WeChat.

What this app *does* do about replies:
- It posts its **own** reply action, so Android Auto's built‑in Assistant still offers
  hands‑free voice reply and hands us the recognized text — **no custom UI needed**.
- It then tries to deliver that text into WeChat via WeChat's own inline‑reply action
  **if one exists**. The **status panel shows "微信内联回复: 支持/不支持"** so you can
  verify on *your* WeChat build. On current WeChat it is "不支持", and the app clearly
  marks such replies as *undeliverable* instead of pretending they were sent.
- The only remaining route to actually send would be an **AccessibilityService** that
  drives WeChat's UI (open chat, type, tap send). It's fragile, can't run on the car
  screen, is unsafe while driving, and is ToS‑questionable — so it is intentionally
  **not** implemented. Ask if you want to explore it as a phone‑only, parked‑use tool.

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
┌───────────────┐  posts   ┌──────────────────────────────────┐  re-post as     ┌──────────────┐
│    WeChat     │ ───────▶ │ MessageNotificationListenerService │ ─ MessagingStyle ▶ │ Android Auto │
│(com.tencent.mm)│  notif  │   → parse → forward                │  (reply/markread)│  read + show │
└───────────────┘          └──────────────────────────────────┘                  └──────────────┘
                                        ▲  reply text (voice, best-effort)              │
                                        └───────────────────────────────────────────────┘
```

### Components
| Class | Responsibility |
|-------|----------------|
| `SupportedApp` | Registry of relayed apps (currently WeChat) + parsing flags. Adding another app is one line. |
| `MessageNotificationListenerService` | Reads supported apps' notifications live (`onNotificationPosted`) and forwards them. |
| `MessageParser` | Prefers the notification's own `MessagingStyle`; falls back to title/text parsing with WeChat's quirks (`[n条]` counter, `sender: body` group split). Filters ongoing/summary/call notifications and detects the app's reply capability. |
| `CarNotificationForwarder` | Builds & posts the `MessagingStyle` car notification (category `MESSAGE`, `SEMANTIC_ACTION_REPLY` + `SEMANTIC_ACTION_MARK_AS_READ`). |
| `NotificationReplyReceiver` | Handles the Auto reply (proxies your text into WeChat's reply `RemoteInput` **if it exists**) and *mark as read*. Marks replies undeliverable when WeChat provides no reply hook. |
| `ConversationStore` / `Conversation` | Per‑conversation state, de‑duplication, and the on‑screen history. |
| `MainActivity` | Enables notification access, requests `POST_NOTIFICATIONS`, toggles, a **test message** button, and a live status panel (incl. detected reply capability). |

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
listener is connected, `POST_NOTIFICATIONS` is granted, forwarding is on, and whether
WeChat exposes an inline‑reply hook.

## Using it
- When a message arrives, Android Auto shows it and (for new messages) reads it
  aloud. Tap the message or use the Assistant to **hear**, **reply**, or
  **mark as read**.
- **Reply** relies on Android Auto's built‑in voice capture (no custom UI). The app
  then tries to deliver the text into WeChat's own reply action *if it exists* — on
  current WeChat it doesn't, so such replies are marked *undeliverable* (see
  "Can I reply" above). Reading and mark‑as‑read always work.
- **拆分群消息发送者 (Split group sender)**: when on, group messages formatted as
  `发送者: 内容` are shown with the sender as the speaker and the group as the
  conversation title (nicer TTS). Turn off if your contacts' messages get split
  incorrectly.

---

## Limitations & notes
- **Message content must be visible in the notification.** If WeChat hides message
  text on the lock screen (e.g. "你收到了一条消息"), only that placeholder can be
  forwarded. Enable message previews in WeChat's notification settings.
- **Replying into WeChat is not supported by WeChat.** WeChat provides no inline‑reply
  `RemoteInput` and no send API, so voice replies captured by Android Auto cannot be
  delivered; the app marks them undeliverable rather than dropping them silently. See
  the "Can I reply to WeChat from the car?" section.
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
