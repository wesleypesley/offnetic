# Offnetic — Technical Specification

---

## Project Overview

**Offnetic** is an off-grid, serverless, privacy-hardened communication application built natively for Android. It enables end-to-end encrypted text messaging, high-speed media transfers, and low-latency audio/video calling directly between devices. The core product philosophy treats physical proximity as the network gateway, establishing secure point-to-point tunnels completely independent of the public internet, cellular towers, or third-party cloud servers.

---

## Core Features

- **Hardened Messaging:** Instant text exchange running on a rolling cryptographic system.
- **Secure File Sharing:** Direct binary transmission pipelines for high-speed local media transfers (photos, videos, documents).
- **P2P Voice & Video Calling:** Low-latency real-time streaming over local radio interfaces.
- **Network-Agnostic Proximity Roaming:** Automatic, invisible handovers between local Wi-Fi, direct peer-to-peer radio links, and low-energy channels.
- **Serverless Identity Management:** Local decentralized cryptographic validation without any central user registry or phone number lookups.

---

## The Technical Stack (Native Android Core)

- **Language & UI:** Kotlin utilizing Jetpack Compose for a declarative, state-driven user interface.
- **Connectivity Engine:** Google Nearby Connections API (NCAPI) for automated, low-level radio switching and device discovery.
- **Media & Real-time Streaming:** WebRTC Android SDK (paired with NCAPI to handle hardware audio echo cancellation, video compression, and low-latency frame rendering).
- **Cryptographic Logic:** `org.signal:libsignal-android:0.86.5` + `org.signal:libsignal-client:0.86.5` — Signal's unified Rust-core cryptographic library with Java/Kotlin bindings. Both artifacts must always be pinned to the exact same version via a single `libs.versions.toml` entry. Implements the Double Ratchet algorithm, X3DH classic key exchange, PQXDH post-quantum key exchange (ML-KEM-1024), and Sealed Sender metadata protection.
- **Local Storage Infrastructure:** SQLCipher for Android integrated with Jetpack Room.
- **Proximity Scanning / Setup:** CameraX API coupled with Google ML Kit Barcode Scanning.
- **Application Settings:** Jetpack DataStore for fast, unencrypted reactive key-value UI/radio preferences.

### Build Configuration Notes

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

```toml
# libs.versions.toml
[versions]
libsignal = "0.86.5"

[libraries]
libsignal-android = { group = "org.signal", name = "libsignal-android", version.ref = "libsignal" }
libsignal-client  = { group = "org.signal", name = "libsignal-client",  version.ref = "libsignal" }
```

```pro
# proguard-rules.pro — libsignal JNI bridge
-keep class org.signal.libsignal.** { *; }
-keepclassmembers class org.signal.libsignal.** { *; }
-dontwarn org.signal.libsignal.**
```

---

## Architecture & Engineering Constraints

### 1. Multi-Radio Handover & Priority Hierarchy

To preserve voice/video calls and heavy media streams without dropping the active session, the app dynamically migrates data through a hardware connection hierarchy:

- **Priority 1 (Local Wi-Fi / LAN):** If both users share a high-speed router network, the API utilizes it to handle intensive data loads like HD video calls.
- **Priority 2 (Wi-Fi Direct / P2P Wi-Fi):** If users step away from the router, the API seamlessly hot-swaps the streaming data to a direct device-to-device Wi-Fi radio bridge.
- **Priority 3 (Bluetooth / BLE):** Used as a baseline for low-power background discovery, initial signaling, and a backup text communication pipe.

---

### 2. Parallel 1-on-1 Session & Channel Collision Isolation

Even when conducting strictly isolated 1-on-1 user sessions, the underlying Android Wi-Fi Direct architecture forces a hardware-level Group Owner (GO) / Client micro-network negotiation. Because standard mobile devices possess only a single Wi-Fi radio, a phone cannot act as a client on one channel and a host on another simultaneously without dropping connections.

- **Control Plane Anchoring:** All text synchronization and heartbeats run permanently over Bluetooth across multiple distinct 1-on-1 peers without channel locks.
- **Ephemeral High-Speed Link Tearing:** High-bandwidth pipelines (Wi-Fi Direct) are spun up strictly on-demand for a file transfer or video call, and forcefully disconnected the millisecond the task concludes to release the physical radio.
- **Serialized Jittered Retry Logic:** If a high-bandwidth upgrade request fails due to radio hardware locks, the application drops the sequence, executes a randomized backoff (500ms–2500ms), and retries over the control plane.

---

### 3. Serverless WebRTC Signaling

Real-time audio and video streams rely on WebRTC. Because there is no centralized internet signaling server to orchestrate the connection, the Nearby Connections API serves as the local signaling highway, exchanging WebRTC session descriptions (SDPs) directly between the two peer devices.

---

### 4. Hardened Cryptographic Lifecycle

- **Trust Establishment:** Users build an identity bond via a physical, out-of-band QR code scan (Trust-on-First-Use / TOFU).
- **Classic Key Exchange:** ECDH (Elliptic Curve Diffie-Hellman) executes the initial handshake via the native `javax.crypto` layer.
- **Post-Quantum Key Exchange (PQXDH):** PQXDH (Post-Quantum Extended Diffie-Hellman) combines ML-KEM-1024 (the FIPS-203 standardized form of Kyber) with classic X3DH, hardening the initial handshake against future quantum adversaries. The combined shared secret output is identical in structure to classic X3DH — the Double Ratchet layer above is unaffected.
- **Double Ratchet Layer:** Every message frame steps a symmetric KDF ratchet forward (Forward Secrecy). Concurrently, periodic mid-session ECDH handshakes refresh the root keys (Post-Compromise Security).
- **Sealed Sender:** Outbound message envelopes encrypt the sender's identity key alongside message content, preventing radio-level observers from determining who sent a given payload.
- **At-Rest Security:** Local message history is written to a local database encrypted by SQLCipher, with the master key locked inside the Android Keystore System.

---

### 5. Decentralized Local Identity & Profile Sync Framework

- **Zero Cloud Dependencies:** Offnetic completely eliminates cloud identity providers. Account creation is entirely local; on first boot, the app generates an asymmetric cryptographic identity keypair, where the Public Key acts as the unique User ID.
- **On-Demand P2P Profile Synchronization:** Peer profiles synchronize via a conflict-free timestamp checking mechanism (`profile_timestamp`) paired with data hashing (`avatar_hash`).
- **Aggressive Media Optimization:** Avatars are strictly downscaled into lightweight WebP format (<15KB) prior to transmission to prevent radio congestion.

---

### 6. Local Payload & Storage Thresholds

To prevent physical hardware storage exhaustion and shield local P2P radio links from saturation, the application layer strictly limits payload scales:

- **Text Messages:** Capped at 5,000 characters per message.
- **Voice Notes:** Capped at a hard 2-minute duration (~1.5 MB) to ensure reliable transmission even on the Bluetooth fallback.
- **Video & File Sharing:** Enforces a rigid 100 MB maximum transfer limit per file.

---

### 7. Network Topology Constraints (P2P_CLUSTER)

The Google Nearby Connections API engine is strictly initialized using `Strategy.P2P_CLUSTER`. This configuration enables a decentralized, many-to-many local ad-hoc network, allowing devices to simultaneously advertise and discover peers, ensuring users can maintain concurrent independent communication tunnels with multiple individuals without a single device acting as a centralized bottleneck.

---

### 8. Serverless Cryptographic Session Reset Protocol

In a serverless P2P environment, if a device experiences a local database write crash or missed ratchet sequences, the system detects three consecutive decryption failures. It then flags the session as "Shattered," generates a brand-new PreKey bundle, and transmits it via a `SIGNAL_PRE_KEY_BUNDLE` envelope to the peer, which forces a zero-knowledge cryptographic reset without requiring a new QR scan.

> **Note:** All incoming `SIGNAL_PRE_KEY_BUNDLE` envelopes are validated against the block list before any cryptographic processing begins. Envelopes from blocked keys are silently dropped.

---

### 9. Permanent Debugging & Diagnostic Ecosystem

- **Continuous Debugging Engine (Logcat & Timber):** A centralized, permanent logging infrastructure tracks every RSSI change, radio fallback, Double Ratchet sequence, and database IO write operation, ensuring absolute system visibility while explicitly truncating sensitive cryptographic keys and message content from logs.
  - *Radio Logs:* Tracks every RSSI change, connection fallback, and NCAPI radio swap.
  - *Crypto State Logs:* Logs Double Ratchet step sequences, DH public key updates, and handshake successes (with absolute truncation/redaction of actual keys and message contents).
  - *Database & Sync Logs:* Tracks SQLCipher open/close events, database migrations, and IO write operations to prevent silent corruption.

---

### 10. Peer Block System

Blocking in a serverless environment is enforced entirely at the local application layer — there is no server to propagate a block. All block enforcement targets the peer's cryptographic **Public Key identity**, not a device ID or NCAPI endpoint, ensuring the block survives device restarts and NCAPI reconnection attempts.

**Block Storage:**

```
BlockedPeersTable {
    blockedPublicKey : ByteArray  (PRIMARY KEY)
    blockedAt        : Long
    displayNameSnapshot : String
}
```

**Enforcement Layers:**

- **Inbound Discovery Drop:** On every `onEndpointFound()` NCAPI event, the connecting peer's identity payload is checked against the block list before any handshake is accepted. Blocked keys are silently rejected with no response sent.
- **Session Termination:** The existing libsignal session for the blocked peer is wiped from Room. Their PreKey bundle is deleted. No messages can be processed even if a packet slips through.
- **Envelope Gate:** All incoming envelopes — including `SIGNAL_PRE_KEY_BUNDLE` session reset packets — are checked against the block list before crypto processing. Blocked envelopes are dropped at the gate.
- **UI Erasure:** The contact moves to a "Blocked" state, the chat history is hidden or deleted per user preference, and they are removed from the active peers list.

**Active Call Block Handling:**
If a block is triggered during an active voice or video call, the ephemeral Wi-Fi Direct link is torn down first, then the block record is written to the database. Order is enforced to prevent audio/video frame injection during the write window.

**Re-discovery Behaviour:**
A blocked peer will continue to appear in NCAPI cluster scans because NCAPI broadcasts cannot be filtered per-peer. The `onEndpointFound` handler silently consumes these events with no UI notification. There is no way to hide your own existence from a specific peer in `P2P_CLUSTER` mode without enabling full Hidden/non-discoverable mode via the DataStore toggle.

**Unblock Behaviour:**
Unblocking does not restore the previous session. The user must perform a fresh QR code scan to re-establish cryptographic trust.

---

### 11. Contact Deletion & Key Revocation

Contact deletion is distinct from blocking. Where blocking is an ongoing enforcement state, deletion is a one-time destructive action with no enforcement list entry.

**Soft Delete:**
Hides the contact from the UI and stops all NCAPI interaction with their public key. Identity keypair, libsignal session, and chat history remain encrypted on disk. Reversible.

**Hard Delete:**
Wipes the libsignal session from Room, deletes all associated messages and media, and removes the peer's public key from the contacts table entirely. Before local deletion, a `SIGNAL_SESSION_TERMINATED` envelope is dispatched to the peer so their device can clean up the orphaned session. The peer will reappear as an unknown device in future NCAPI discovery — a fresh QR scan is required to reconnect.

> **Note:** Hard delete does not block the peer. If blocking is also intended, it must be applied separately.

---

### 12. Account Management

Account-level destructive actions are exposed as three distinct tiers in the Account settings screen, each requiring a confirmation dialog before execution:

**Erase All Content**
Wipes all messages and media from the SQLCipher database. The identity keypair, contacts table, and active libsignal sessions are fully preserved. The user remains discoverable to peers and their public key identity is untouched. Intended as a "clean slate" without losing cryptographic identity.

**Erase All Content And Log Out**
Performs the same full message and media wipe as above, then additionally stops the NCAPI foreground service, clears the in-memory key cache, and returns the app to the BiometricPrompt lock screen. The identity keypair and contacts remain encrypted on disk and are fully restored on next biometric authentication. Intended for temporarily securing the device.

**Delete Account**
Full nuclear wipe. Destroys all messages, media, contacts, libsignal sessions, and permanently deletes the identity keypair from both SQLCipher and the Android Keystore. The app returns to its first-boot state and generates a new keypair on next launch. All previous peers must perform a fresh QR scan to reconnect, as the old public key identity no longer exists.

> **Important:** Because Offnetic is fully serverless, "Delete Account" performs no remote calls. The confirmation dialog must explicitly inform the user that this action is irreversible and that all existing peer relationships will require new QR scans to restore.

---

## Supporting System Ecosystem

- **Jetpack Media3 / ExoPlayer:** Handles audio buffering and video rendering inside chat bubbles.
- **Android Foreground Services:** Declared with `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` as the primary type to keep the local P2P radio listener active in the background. `FOREGROUND_SERVICE_TYPE_SHORT_SERVICE` is declared as a secondary type to cover ephemeral Wi-Fi Direct link spin-up and tear-down windows, preventing OS kills during radio handovers under Android 16's stricter foreground service enforcement.
- **BiometricPrompt API:** Secures application access by authenticating the user via local fingerprint or face metrics before pulling keys from the Android Keystore.

---

## Application Settings Paradigm

To maximize layout performance, settings are structurally separated by retrieval constraints:

- **Jetpack DataStore (Fast-Path UI / Radio Preferences):** Holds unencrypted non-sensitive settings including: Discoverability toggle (Visible/Hidden broadcast status), background scanning service toggle, radio power restrictions (Bluetooth-only vs Wi-Fi optimization configurations), and UI theme selections.
- **SQLCipher Vault (Secure App Settings):** Securely contains identity metadata, profile timestamps, custom biometric re-authentication timeout intervals, data self-destruct threshold switches, and local data retention management controls.

---

## Inspiration & Reference Architecture

The architectural philosophy and baseline capabilities of Offnetic are inspired by the following open-source local-first networking projects:

- **Meshenger Android:** For its pioneering work in serverless voice communication and local contact isolation over standard networks.
  *Repository:* https://github.com/meshenger-app/meshenger-android

- **LocalSend:** For proving the market demand for zero-configuration, seamless cross-device local file sharing using purely local protocols.
  *Repository:* https://github.com/localsend/localsend

- **Bridgeify:** For its 3-tier account management UX pattern (Erase Content / Erase and Log Out / Delete Account), adapted for a fully serverless identity model.

---

## Resolved Technical Decisions

**RSSI Handover Thresholds**
Radio handovers are triggered based on the following RSSI values, with a 5-second hysteresis window to prevent thrashing at boundary values:

| Radio | Threshold | Action |
|---|---|---|
| Local Wi-Fi (LAN) | RSSI > -65 dBm | HD video calls and large transfers |
| Wi-Fi Direct | -65 to -80 dBm | Fallback for calls and transfers |
| Bluetooth / BLE | < -80 dBm or Wi-Fi unavailable | Control plane only — text and heartbeat |

**Maximum Concurrent Sessions**
P2P_CLUSTER supports up to **4 concurrent active peer sessions** on consumer Android hardware before connection stability and radio performance degrade meaningfully. The UI surfaces a warning at 3 active peers and soft-blocks new connections at 4. Users can still receive proximity pings beyond this limit but cannot open new active sessions until an existing one is closed.

**Sealed Sender**
Sealed Sender is **always-on**. It is not a user-configurable toggle. While the primary threat model for Offnetic is network-level (no server observer), BLE and Wi-Fi Direct frames can be intercepted by nearby radio sniffers. Sealed Sender ensures intercepted envelopes reveal neither sender identity nor recipient identity. The marginal UX cost is zero; the security benefit is non-zero. Decision is final.
