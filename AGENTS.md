# AGENTS.md — Offnetic
> Read this file fully before touching anything.
> This file governs all agent behaviour, architecture decisions, and technical constraints for the Offnetic codebase.
> Design rules live in DESIGN.md — read that too before any UI work.

---

## What Is Offnetic

Offnetic is a native Android encrypted communication app that works entirely offline — no internet, no servers, no phone numbers. It uses physical proximity as the network. Devices discover each other via Bluetooth and Wi-Fi Direct, establish end-to-end encrypted sessions using the Signal Protocol, and communicate directly peer-to-peer.

Core capabilities: encrypted text messaging, file transfers up to 100MB, voice notes up to 2 minutes, P2P voice and video calls, and proximity-based contact pings. Trust is established by physically scanning a QR code. There is no account creation, no cloud, no central authority of any kind.

---

## Tech Stack — Exact Versions

| Layer | Library / Tool | Version |
|---|---|---|
| Language | Kotlin | Latest stable |
| UI | Jetpack Compose + Material3 | Latest stable |
| DI | Hilt | Latest stable |
| Async | Coroutines + Flow | Latest stable |
| Connectivity | Google Nearby Connections API (NCAPI) | Latest stable |
| Crypto | org.signal:libsignal-android | **0.86.5** |
| Crypto | org.signal:libsignal-client | **0.86.5** |
| Local DB | Room + SQLCipher | Latest stable |
| Settings | Jetpack DataStore | Latest stable |
| Camera | CameraX | Latest stable |
| Barcode | Google ML Kit Barcode Scanning | Latest stable |
| Calls | WebRTC Android SDK | Latest stable |
| Media | Jetpack Media3 / ExoPlayer | Latest stable |
| Logging | Timber | Latest stable |
| Font | Syne (Google Fonts) | — |
| minSdk | **28** | — |
| targetSdk | **35** | — |
| compileSdk | **35** | — |

### libsignal Version Lock — Non-Negotiable
Both libsignal artifacts must always be on the exact same version. One variable in `libs.versions.toml` controls both:

```toml
[versions]
libsignal = "0.86.5"

[libraries]
libsignal-android = { group = "org.signal", name = "libsignal-android", version.ref = "libsignal" }
libsignal-client  = { group = "org.signal", name = "libsignal-client",  version.ref = "libsignal" }
```

Never update one without the other. A version mismatch silently breaks the JNI bridge at runtime.

### Required Build Config

```gradle
android {
    packagingOptions {
        resources {
            excludes += setOf(
                "libsignal_jni*.dylib",
                "signal_jni*.dll",
                "libsignal_jni_testing.so"
            )
        }
    }
}
```

### Required ProGuard / R8 Rules

```pro
-keep class org.signal.libsignal.** { *; }
-keepclassmembers class org.signal.libsignal.** { *; }
-dontwarn org.signal.libsignal.**
```

---

## Architecture

### Pattern: Strict MVVM

```
Composable (UI only) → ViewModel (state + events) → Repository → DataSource (Room / NCAPI / libsignal)
```

- ViewModels expose `StateFlow` or `SharedFlow`. Never `LiveData`.
- Composables observe state only. Zero business logic inside a Composable.
- Repositories are the single source of truth. No direct DB or NCAPI calls from ViewModels.
- Use `sealed class Result<T>` for all async operations — never raw exceptions surfaced to UI.

```kotlin
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val cause: Throwable? = null) : Result<Nothing>()
    object Loading : Result<Nothing>()
}
```

### Dependency Injection
Hilt everywhere. No manual DI, no service locators, no singletons outside `@Singleton` Hilt modules.

### Async
Coroutines and Flow only. No RxJava. No raw callbacks unless forced by a third-party API — wrap those in `suspendCoroutine`.

### File Structure

```
app/
├── di/                    # Hilt modules
├── data/
│   ├── local/
│   │   ├── db/            # Room database, DAOs, entities
│   │   ├── datastore/     # DataStore preferences
│   │   └── crypto/        # SQLCipher setup, key management
│   ├── nearby/            # NCAPI wrappers, connection lifecycle
│   ├── crypto/            # libsignal sessions, ratchet, PreKey bundles
│   └── repository/        # Repository implementations
├── domain/
│   ├── model/             # Domain models (not Room entities)
│   └── usecase/           # Use cases where logic is complex enough
├── ui/
│   ├── onboarding/        # Splash, permissions, identity gen, profile
│   ├── chat/              # Chat list, single chat screen
│   ├── call/              # Voice and video call screens
│   ├── contacts/          # QR scan, contact detail, block/delete
│   ├── nearby/            # Nearby peers screen
│   ├── settings/          # All settings screens, account management
│   └── theme/             # Color, typography, shape, theme setup
├── service/               # Foreground services (NCAPI listener)
└── util/                  # Extensions, helpers
```

---

## Security Rules — Never Violate

1. **Never log message content.** Timber logs are for system events only. Message payloads, contact names in message context, and key material must never appear in logs. Truncate public keys to first 8 chars max if logging for debug.

2. **Never store plaintext sensitive data.** All identity metadata, session keys, and message history go through SQLCipher. Non-sensitive UI prefs only in DataStore.

3. **Never skip the block list gate.** Every incoming NCAPI envelope — including `SIGNAL_PRE_KEY_BUNDLE` resets — must be checked against `BlockedPeersTable` before any crypto processing. Drop silently if matched.

4. **Never hardcode keys, secrets, or salts.** Android Keystore for all key material. Nothing in `BuildConfig`, `strings.xml`, or source files.

5. **Never store location data.** Location permission is used by NCAPI for Wi-Fi Direct only. No coordinates are ever written to disk, logged, or transmitted.

6. **Screenshot protection must be on.** `WindowManager.LayoutParams.FLAG_SECURE` must be set on every Activity. Never remove for any reason.

7. **Biometric lock must gate key access.** Keys are pulled from Android Keystore only after `BiometricPrompt` succeeds. No bypasses.

8. **Block during active call — tear down first.** If a block is triggered mid-call, the Wi-Fi Direct link is torn down before the block is written to DB. Never reverse this order.

---

## Edge-to-Edge & Insets — Mandatory

All screens must handle Android edge-to-edge correctly. This is a known failure point — not handling insets causes UI to overlap the status bar, gesture area, and battery/clock icons.

### Setup in MainActivity
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    // ...
}
```

### In every Scaffold
```kotlin
Scaffold(
    modifier = Modifier.fillMaxSize()
) { innerPadding ->
    Content(modifier = Modifier.padding(innerPadding))
}
```

### Rules
- Never hardcode status bar height or navigation bar height in dp.
- Never use fixed top/bottom padding values to "fix" system bar overlap — always use `WindowInsets`.
- Use `Modifier.safeDrawingPadding()` for full-screen surfaces that draw behind system bars.
- Use `Modifier.windowInsetsPadding(WindowInsets.navigationBars)` for bottom nav and bottom-anchored UI.
- Use `Modifier.statusBarsPadding()` for top content when not inside a Scaffold.
- The bottom navigation bar must account for gesture nav insets — never hardcode its bottom padding.
- Test layouts on both gesture navigation mode and 3-button navigation mode.
- All screens must look correct on devices with notches, punch-hole cameras, and display cutouts.
- Use `Modifier.displayCutoutPadding()` where relevant (full-screen call UI, splash).

---

## Design / UI Work Rules

These rules exist because AI agents commonly break working functionality while implementing design changes. Follow them without exception.

### Separation of Concerns During UI Work
- **Design pass = only Modifiers, colors, shapes, typography, spacing.** Nothing else.
- Never touch ViewModel logic during a UI styling task.
- Never refactor state management while doing a design change.
- Never move or rename functions, lambdas, or click handlers as part of a styling task.
- If a refactor is genuinely needed, complete the design task first, verify it works, then do the refactor as a separate task.

### Before Touching Any UI File
1. Read the file fully first.
2. Identify every interactive element: text fields, buttons, click handlers, state variables.
3. Make a mental note of what each one does.
4. After making changes, verify every interactive element still compiles and behaves correctly.

### Specific Things That Must Never Break
- Text field `onValueChange` handlers — if you restyle an input, the state binding must remain identical.
- Button `onClick` lambdas — never simplify, inline, or move these during a styling pass.
- `remember` and `mutableStateOf` blocks — do not touch these during visual changes.
- Navigation calls inside click handlers — do not remove or restructure.
- ViewModel function calls from UI — never remove even if they "look unused."

### After Every UI Change
- The file must compile without errors.
- All text inputs must accept input.
- All buttons must respond to taps.
- All navigation must function.
- No `NullPointerException` or `ClassCastException` risk introduced.

---

## NCAPI Rules

- Always initialise with `Strategy.P2P_CLUSTER`. Never change the strategy.
- Control plane (text, heartbeats, signaling) runs over Bluetooth always.
- Wi-Fi Direct links are ephemeral — spun up for file transfers and calls only, torn down immediately when the task completes.
- If a Wi-Fi Direct upgrade fails, backoff is randomised between 500ms–2500ms before retry.
- Maximum **4 concurrent active peers** before performance degrades. Surface a warning in UI at 3. Soft-block new connections at 4.
- On `onEndpointFound()`, always check block list before accepting. Silent drop if blocked.

### Radio Handover RSSI Thresholds

| Radio | Threshold | Action |
|---|---|---|
| Local Wi-Fi (LAN) | RSSI > -65 dBm | HD video calls and large transfers |
| Wi-Fi Direct | -65 to -80 dBm | Fallback for calls and transfers |
| Bluetooth / BLE | < -80 dBm or Wi-Fi unavailable | Control plane only |

Hysteresis window: 5 seconds. Do not re-trigger a handover if the previous one completed less than 5 seconds ago.

---

## Proximity Ping System

Runs inside `onEndpointFound` after the block list check.

```
Block list check → pass
    ↓
Look up contact's lastSeenAt
    ↓
(now - lastSeenAt) < 5 min → silent, radio blip
(now - lastSeenAt) ≥ threshold → check lastPingedAt
    ↓
(now - lastPingedAt) < 15 min → suppress (cooldown)
(now - lastPingedAt) ≥ 15 min → fire notification, update lastPingedAt
    ↓
onEndpointLost() → write departure time to lastSeenAt
```

| Away Duration | Default Behaviour |
|---|---|
| < 5 min | Silent |
| 5–30 min | Optional subtle ping (user toggle) |
| > 30 min | Full ping |

Notification content: name only. No message previews, no timestamps.

DataStore keys:
```
proximity_pings_enabled: Boolean
proximity_ping_threshold_minutes: Int  // default: 30
```

---

## Cryptographic Architecture

- **Identity generation:** Once on first boot. ECDH P-256 keypair. Public key is the unique user ID. Stored in Android Keystore + SQLCipher.
- **Trust establishment:** Physical QR code scan (TOFU). No cloud verification, no phone numbers.
- **Key exchange:** PQXDH (ML-KEM-1024 + X3DH). Falls back to classic X3DH if peer doesn't support PQXDH.
- **Message encryption:** Double Ratchet (Forward Secrecy + Post-Compromise Security).
- **Metadata protection:** Sealed Sender — always-on, never a user toggle.
- **At rest:** SQLCipher, master key in Android Keystore.
- **Session reset:** 3 consecutive decryption failures → "Shattered" state → new PreKey bundle → `SIGNAL_PRE_KEY_BUNDLE` envelope to peer. No new QR scan needed.

---

## Sealed Sender
Always-on. Not a toggle. Every outbound envelope encrypts the sender's identity alongside the payload. Prevents radio-level observers from determining who sent a message.

---

## Foreground Service

```xml
android:foregroundServiceType="connectedDevice|shortService"
```

- `connectedDevice` — keeps NCAPI radio listener alive in background.
- `shortService` — covers ephemeral Wi-Fi Direct spin-up/teardown windows under Android 16's stricter enforcement.

---

## Payload Limits — Hard Enforced at Application Layer

| Type | Limit |
|---|---|
| Text message | 5,000 characters |
| Voice note | 2 minutes (~1.5 MB) |
| File / video | 100 MB |
| Avatar | 15 KB (WebP, downscaled before transmission) |

---

## Block System

Targets the peer's **Public Key identity** — not device ID or NCAPI endpoint.

```kotlin
data class BlockedPeer(
    val blockedPublicKey: ByteArray,    // PRIMARY KEY
    val blockedAt: Long,
    val displayNameSnapshot: String
)
```

Enforcement:
1. `onEndpointFound` → silent reject before handshake
2. All incoming envelopes → block check before crypto
3. Wipe libsignal session from Room
4. Delete their PreKey bundle
5. Hide/delete chat history per user preference
6. If mid-call → tear down Wi-Fi Direct first, then write block

Unblock: session NOT restored. Fresh QR scan required.

---

## Contact Deletion

**Soft delete:** Hide from UI, stop NCAPI interaction. Session + history preserved. Reversible.

**Hard delete:** Send `SIGNAL_SESSION_TERMINATED` to peer → wipe session → delete all messages and media → remove public key from contacts table. No block list entry. Peer appears as unknown on next discovery.

---

## Account Management (3-Tier)

**Erase All Content**
Wipes messages + media. Identity keypair, contacts, sessions untouched. User stays discoverable.

**Erase All Content And Log Out**
Same wipe + stops NCAPI foreground service + clears in-memory key cache + returns to BiometricPrompt lock. Everything restored on next biometric auth.

**Delete Account**
Destroys everything: messages, media, contacts, sessions, identity keypair from SQLCipher and Android Keystore. First-boot state. Confirmation dialog must say: "This cannot be undone. All existing contacts will need to re-add you."

---

## Onboarding Flow

```
Splash (logo + tagline, 2.8s)
    ↓
Permission Slide 1 — Connectivity (Bluetooth + Nearby Wi-Fi + Location)
Permission Slide 2 — Camera & Microphone
Permission Slide 3 — Notifications
    ↓
Identity Generation (keypair created with visual feedback)
    ↓
Profile Setup (avatar + username)
    ↓
Main Interface
```

- Storage permission requested in-context only — when the user first tries to send a file.
- No biometric setup screen — BiometricPrompt uses whatever the user already configured on device.

---

## Build Parts — Execution Order

Build strictly in this order. Each part must be stable before starting the next.

| Part | Scope |
|---|---|
| **1 — Foundation** | Hilt, Room + SQLCipher, identity keypair generation, BiometricPrompt, DataStore, navigation scaffold, edge-to-edge setup |
| **2 — NCAPI Core** | Advertising, discovery, connection lifecycle, foreground service, P2P_CLUSTER init, peer list UI |
| **3 — Crypto Layer** | libsignal sessions, QR pairing (CameraX + ML Kit), TOFU, PQXDH handshake, PreKey bundle storage |
| **4 — Messaging** | Double Ratchet send/receive over BLE, chat UI, message limits, session shattered detection + reset |
| **5 — File & Media** | Ephemeral Wi-Fi Direct, file transfer pipeline, voice note record + send, payload limits, teardown |
| **6 — Calls** | WebRTC integration, SDP exchange over NCAPI, echo cancellation, video rendering, call UI |
| **7 — Block & Contact Management** | Block system, soft/hard delete, SIGNAL_SESSION_TERMINATED, proximity ping, notification system |
| **8 — Account & Settings** | 3-tier account deletion, settings screens, DataStore toggles, SQLCipher vault settings |
| **9 — Hardening** | ProGuard/R8 audit, screenshot protection, logging audit, release build testing, insets testing on multiple devices |

---

## Coding Style

- Kotlin idiomatic: extension functions, data classes, `when` over `if-else` chains
- No Hungarian notation
- Public non-trivial functions have KDoc
- Compose: one screen per file
- State hoisting always — no internal `remember` state in leaf composables unless purely local UI
- No `!!` force unwrap — use `?: return`, `?: throw`, or `let`
- No `GlobalScope`
- No `LiveData`
- No RxJava

## What You Must Never Do

- Use `LiveData` — Flow only
- Use RxJava
- Use `GlobalScope`
- Place business logic in a Composable
- Write raw SQL — use Room DAOs
- Call NCAPI directly from a ViewModel
- Log message content or full key material
- Store anything sensitive in DataStore
- Hardcode status bar / nav bar heights in dp
- Use fixed padding to "fix" system bar overlap — always use WindowInsets
- Break working click handlers, text fields, or state bindings during a design pass
- Refactor state or logic during a UI styling task
- Skip the block list gate on any incoming envelope
- Allow a block write before an active call is torn down
- Add a toggle for Sealed Sender

---

## Reference Files

- Design rules: `DESIGN.md`
- Onboarding mockup: `Offnetic_Onboarding.jsx`
- Full product spec: `Offnetic_Spec.md`
