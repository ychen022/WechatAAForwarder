# WeChat Auto — 微信 / Teams 消息转发到 Android Auto

WeChat (微信) and Microsoft Teams don't integrate with Android Auto, so their
messages never show up on the car screen or get read aloud. **WeChat Auto** bridges
that gap: it reads those apps' notifications on your phone in real time and
re-publishes them as Android Auto‑compatible *messaging* notifications, so incoming
messages are **displayed and read aloud** in Android Auto — and you can **reply by
voice**.

> This app does not modify WeChat/Teams and does not need root. It only relays the
> notifications those apps already post. It has **no internet permission**.

## Supported apps
| App | Package | Typical profile |
|-----|---------|-----------------|
| WeChat 微信 | `com.tencent.mm` | Personal |
| Microsoft Teams | `com.microsoft.teams` | Work |

Each source can be toggled independently in the app. Adding another messenger is a
one-line change in `SupportedApp`.

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
│ WeChat / Teams│ ───────▶ │ MessageNotificationListenerService │ ─ MessagingStyle ▶ │ Android Auto │
│ (personal/work)│  notif  │   → parse → forward                │  (reply/markread)│  read + show │
└───────────────┘          └──────────────────────────────────┘                  └──────────────┘
                                        ▲  reply text (voice/typed)                     │
                                        └───────────────────────────────────────────────┘
```

### Components
| Class | Responsibility |
|-------|----------------|
| `SupportedApp` | Registry of relayed apps (WeChat, Teams) + per-app parsing flags. |
| `MessageNotificationListenerService` | Reads supported apps' notifications live (`onNotificationPosted`) and forwards them. |
| `MessageParser` | Prefers the notification's own `MessagingStyle` (Teams and most modern messengers); falls back to title/text parsing with WeChat's quirks (`[n条]` counter, `sender: body` group split). Filters ongoing/summary/call notifications and captures the app's inline‑reply action. |
| `CarNotificationForwarder` | Builds & posts the `MessagingStyle` car notification (category `MESSAGE`, `SEMANTIC_ACTION_REPLY` + `SEMANTIC_ACTION_MARK_AS_READ`), prefixing the conversation title with the source app. |
| `NotificationReplyReceiver` | Handles the Auto reply (proxies your text back into the source app's reply `RemoteInput`) and *mark as read* (dismisses the original + relayed notifications). |
| `ConversationStore` / `Conversation` | Per‑conversation state, de‑duplication, and the on‑screen history. |
| `MainActivity` | Enables notification access, requests `POST_NOTIFICATIONS`, per-app toggles, a **test message** button, and a live status panel. |

---

## ⚠️ Work profile & multiple Android profiles (important for Teams)
Android **isolates notifications between the personal profile and the work
profile**. A third‑party `NotificationListenerService` only sees notifications from
apps **in its own profile** — a personal‑profile listener cannot read work‑profile
Teams notifications, and vice versa.

So if WeChat is personal and Teams is in your work profile:

1. **Install and enable WeChat Auto in *both* profiles.** In your work profile's app
   list, install the same APK; grant it notification access there too. (If your work
   profile is managed by Intune/MDM, you may need IT to allow installing it, or push
   it as a managed app.)
2. The **personal** instance forwards WeChat; the **work** instance forwards Teams.
   Turn off the unused source in each instance via the per‑app toggles.
3. Each instance re‑posts the message **within its own profile**, so the only
   cross‑profile hop is reading the original notification — which the per‑profile
   listener already handles.

**Android Auto note:** Android Auto reads personal‑profile messaging notifications
reliably. Whether it also surfaces the **work‑profile** instance's notifications
depends on your Android Auto version's managed‑profile support. If the work instance's
messages don't appear in the car:
- Make sure the work profile isn't paused, and its notifications are allowed to show.
- As a future enhancement, a *connected‑apps* bridge (`INTERACT_ACROSS_PROFILES`)
  could let the work instance hand messages to the personal instance so Auto always
  sees a personal‑profile notification — this requires your IT policy to permit
  "connected work & personal apps."

If both apps are in the **same** profile, a single instance handles everything.

---

## Requirements
- Android 8.0 (API 26) or newer.
- Android Auto set up on the phone + a head unit (or the "Desktop Head Unit" for testing).
- The source app (WeChat/Teams) installed, with notifications enabled and message
  content shown on the lock/notification screen.

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
3. Make sure **转发 (Forwarding)** is on, and enable the sources you want under
   **转发来源 (Sources)** — 微信 and/or Microsoft Teams.
4. Tap **发送测试消息 (Send test message)** and connect to Android Auto — you should
   see/hear the test message on the car screen. This confirms the pipeline before a
   real message arrives.
5. Keep WeChat Auto installed; the listener runs in the background automatically.
6. For Teams in a work profile, repeat these steps **inside the work profile** (see
   the *Work profile* section above) and enable only the Teams source there.

The status panel shows, at a glance, whether: notification access is granted, the
listener is connected, `POST_NOTIFICATIONS` is granted, and forwarding is on.

## Using it
- When a message arrives, Android Auto shows it and (for new messages) reads it
  aloud. Tap the message or use the Assistant to **hear**, **reply**, or
  **mark as read**. The car title is prefixed with the source app (e.g. `微信 · 张三`,
  `Teams · #general`).
- **Reply** is proxied straight into the source app's own inline‑reply action, so your
  spoken reply is sent as a normal message — best‑effort (see limitations).
- **拆分群消息发送者 (Split group sender)**: WeChat‑only; when on, group messages
  formatted as `发送者: 内容` are shown with the sender as the speaker and the group as
  the conversation title (nicer TTS). Turn off if your contacts' messages get split
  incorrectly. (Teams already provides structured sender info via MessagingStyle.)

---

## Limitations & notes
- **Message content must be visible in the notification.** If the source app hides
  message text on the lock screen (e.g. "你收到了一条消息"), only that placeholder can
  be forwarded. Enable message previews in the app's notification settings.
- **Reply back is best‑effort.** It works only while this app's process is alive and
  only if the source app's notification exposed an inline‑reply action (WeChat and
  Teams usually do). If it can't reply, marking as read still works. Text only — no
  images/voice/stickers. Replying to a *work‑profile* app from a *personal‑profile*
  instance may be blocked by profile isolation.
- **Group sender detection is heuristic** for WeChat (`发送者: 内容`); it can
  occasionally mis‑split a 1:1 message that contains a colon — toggle it off if needed.
- Rapid duplicate posts of the same latest message are de‑duplicated within ~1.5s.
- **Privacy:** everything is processed on‑device. The app has **no internet
  permission** and sends nothing anywhere; it only re‑posts local notifications.
- Some OEM skins aggressively kill background listeners — exclude WeChat Auto from
  battery optimization if forwarding stops after a while.

## Troubleshooting
- *Nothing appears in Android Auto:* verify all four status items are ✅ and the source
  is enabled, then use **Send test message** while connected. If the test works but a
  real app doesn't, check that app's own notification settings (previews enabled, not
  silenced).
- *Teams messages missing:* confirm WeChat Auto is installed **and** has notification
  access **inside the work profile**, and that the Teams source is enabled there.
- *Listener shows disconnected:* re‑toggle notification access for WeChat Auto in
  system settings; some devices require toggling it off/on after an update.
