# Nostr Relay Plan

## Goal

Add an optional internet text fallback to Offnetic while keeping the app local-first.

Success criteria:
- Nearby contacts still use the existing NCAPI/Bluetooth/Wi-Fi path first.
- When a trusted contact is not nearby, text messages can be sent through Nostr relays.
- Relays only carry encrypted Offnetic message envelopes.
- Files, voice notes, and calls remain local-only for the first version.
- No Firebase project, Nostr account, API key, or billing setup is required.

## Revised Decisions (v1)

These supersede any conflicting detail in the older sections below. They reflect what we learned from BitChat (Nostr) and Briar (Tor/Mailbox).

- **Ephemeral identity.** Keys live in SQLCipher under an Android Keystore master key, and `allowBackup="false"`. Uninstalling the app destroys both the database and the Keystore key, so identity is unrecoverable. A reinstall generates a brand-new identity (new Offnetic key, new Nostr key, new Signal keys) and the user must re-pair. No backup/recovery in v1. See Identity Lifecycle.

- **No Tor in v1.** Relay traffic uses plain `wss://`. Message content is already end-to-end encrypted (Signal envelope inside a NIP-17 gift wrap), so relays can never read it. Tor would only hide *metadata* (IP ↔ Nostr key ↔ contact graph). Leave it as an optional future "Hide my IP from relays" toggle, not a v1 requirement.

- **Relay event format = NIP-17 / NIP-59 gift wrap** (matches BitChat). The Signal-encrypted Offnetic envelope is the inner content; it is sealed (NIP-44) and gift-wrapped under a throwaway ephemeral key with a randomized timestamp. Do **not** use NIP-04 or a plaintext `offnetic1:` content scheme. Deduplicate on the **Nostr event id after unwrapping** — drop the cleartext `messageUuid` `d` tag (a gift wrap can't carry it anyway, and `d` has reserved replaceable-event semantics).

- **Delivery = persistent subscription WebSocket held by the foreground service** (matches BitChat), not poll-on-wake. While `NcapForegroundService` runs, keep relay subscriptions open (reconnect + backoff + auto-restore subs) so incoming relay events arrive in real time and fire a **local notification** via the existing `MessageNotificationManager` — no FCM required. Poll-on-wake is only the *fallback* for when the service is off. Request a battery-optimization exemption (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) like Briar. Note: unlike Briar's Tor (where the contact dials into your hidden service), **Nostr has no inbound channel — you must hold the receive subscription open to receive at all**, so the receive socket is never idle-dropped.

- **Pairing stays strictly one-way.** Only the sharer's code/link moves, one direction; the other side never scans back or pastes a second link. Nearby = auto-connect, zero taps (physical proximity is the gate). Far-away inbound requests land in a **Requests tray** (Instagram/Messenger style): the recipient does nothing to receive, and replying *is* accepting — strangers never drop into the main chat list. The adding side sees a confirmation screen with an optional safety-number/fingerprint. Rate-limit inbound PreKey-bundle processing per Nostr key to blunt spam.

- **Deep links use base64url.** `offnetic://add?data=<base64url>` (standard base64 breaks in a URL), consistent with the envelope encoding.

## Assumptions

- Nostr is used only as a transport layer, not as Offnetic identity or storage.
- Offnetic identity remains the QR-scanned public key already used by the app.
- Each device generates a separate local Nostr keypair for relay transport.
- The user's Nostr public key is included in QR pairing data so trusted contacts know where to send relay messages.
- Public relays are best-effort and may rate-limit, reject, delete, or drop events.
- Public relays store events but offer no delivery guarantees or retention SLAs.

## Transport Model

Message sending should choose transports in this order:

1. Local Offnetic transport
   - NCAPI/Bluetooth/Wi-Fi Direct.
   - Supports text, files, voice notes, and calls.

2. Nostr relay fallback
   - Internet required.
   - Text only.
   - Sends encrypted Offnetic envelopes to public Nostr relays.

3. Local saved outbox
   - If neither local transport nor relay is available, keep the message saved and retry later.

## Nostr Identity

Each app install should create and store:

```txt
nostrPrivateKey
nostrPublicKey
```

The Nostr key should be separate from the Offnetic public key. This avoids exposing the main Offnetic identity directly to public relays.

Store the Nostr keypair in the SQLCipher database alongside the Signal protocol keys. The Android Keystore is not an option for Nostr signing: it does not support secp256k1 / BIP-340 Schnorr (Nostr's signature scheme), so the private key must live where the app can use it directly. SQLCipher is already encrypted with a Keystore-backed master key, so the key is double-protected at rest.

Generation must cover upgrades, not just fresh installs: generate the Nostr keypair on first launch *if one does not already exist*. Existing users have no fresh "first boot," so on the first launch after the update they get a key, and their displayed QR / share payload is regenerated to include `nk`.

QR pairing data should include:

```json
{"pk": "<offneticPublicKey>", "dn": "<displayName>", "nk": "<nostrPublicKey>"}
```

Base64-encoded. The `nk` field is optional for backward compatibility. Existing contacts without a Nostr key should still work locally. Relay fallback should only appear when the contact has a stored Nostr public key.

## Signal Session Over Relay

The X3DH key exchange can happen over Nostr relays. This allows two users to establish a Signal session without ever being physically nearby. The PRE_KEY_BUNDLE envelope type already exists in the app.

The QR pairing flow is one-way. Bob scans Alice's QR and gets her `pk` and `nk`. Alice does not know Bob exists from the scan alone. When they are nearby, she discovers him through the inbound NCAPI connection. When they are not nearby, the PreKey bundle exchange serves the same role — Bob's bundle carries his `pk` and `nk` in cleartext so Alice can create a contact entry for him the moment she processes it.

Flow after Bob scans Alice's QR (far-away case):

```txt
Bob scans Alice's QR (gets her offneticPublicKey + nostrPublicKey)
Bob publishes his PreKey bundle to Alice's nostrPublicKey
  Bundle includes Bob's offneticPublicKey + Bob's nostrPublicKey in cleartext
Alice receives bundle → creates contact for Bob → stores Bob's nostrPublicKey
Alice processes bundle and creates Signal session
Alice publishes her PreKey bundle back to Bob's nostrPublicKey
Bob processes Alice's bundle
Both now have a Signal session — relay text works in both directions
```

The bundle exchange is unauthenticated at the Nostr layer, but that is fine because the Offnetic identity key from the QR scan authenticates it at the Signal layer. Signal X3DH already verifies the identity key matches.

If both devices are nearby when the QR scan happens, the bundle exchange still happens over NCAPI as it does today. The relay path is only used when the devices are not nearby after pairing.

### Pairing Request Lifecycle (offline, retry, decline)

A PreKey bundle published to a far-away contact is a pairing request and must survive the recipient being offline, exactly like a relay message:

- The bundle is gift-wrapped (NIP-17) to the recipient's Nostr key and stored on the relays. It is tracked as an **outbound pending request** and re-published on the same schedule as the outbox (re-publish, 72h TTL).
- The sender's side shows the contact as **Pending — waiting to connect** until acceptance arrives.
- When the recipient comes online, their app fetches and unwraps the bundle and files it as an **inbound pending request** in the Requests tray (see Pairing Consent). It is not added to the main chat list.
- Accept = process the bundle, create the Signal session, publish a bundle back, and promote the contact to active. Replying to the request is an implicit accept.
- **Decline = silent expire.** No "declined" signal is sent back; the outbound request simply lapses at the 72h TTL on the other side. This avoids revealing that the recipient saw it.
- If acceptance never arrives within 72h, the outbound request lapses and the sender can re-share the code.

Receive routing: a single subscription (kind 1059, `#p` = this device's Nostr key) carries both messages and PreKey bundles. After unwrapping, the handler switches on the inner envelope type — a text message goes to the chat, a PreKey bundle goes to the session / Requests-tray flow.

## QR Code Enhancements

### Current QR System

`QrPairingData` currently carries only `pk` and `dn`. The pairing flow is one-way: Bob scans Alice's QR, saves her as a contact, and calls `ncapManager.reconnectToContact()` to attempt a live connection. Alice only learns about Bob when he shows up as an inbound NCAPI connection.

This one-way model is an intentional UX advantage over apps like Briar, which require both users to scan each other. This advantage is preserved with relay — see Signal Session Over Relay above.

### QR Payload Update

Add the `nk` field to `QrPairingData`:

```kotlin
data class QrPairingData(
    val publicKey: String,
    val displayName: String?,
    val nostrPublicKey: String? = null
)
```

Update `toQrPayload()` to include `nk` when present and `fromQrPayload()` to parse it. The field is optional so existing scanned contacts without a Nostr key continue to work locally.

### Share and Import Options

The current scanner screen only supports live camera scanning. Four total paths are needed — one existing, three new — to cover both nearby and far-away contact adding.

**Camera scan (existing)** — Bob points his camera at Alice's QR code on her screen. Best for when they are in the same room.

**Share QR image (new)** — Alice saves her QR as a PNG and shares it via the Android share sheet to WhatsApp, Telegram, or any other app. Bob receives the image and scans it with his camera or imports it from his gallery.

**Copy link (new)** — Alice copies a deep link that encodes the same base64 payload as the QR code:

```
offnetic://add?data=<base64payload>
```

Bob taps the link on his device. Android resolves it via an intent filter in `AndroidManifest.xml` and opens Offnetic, which pipes the payload directly into `QrScannerViewModel.onQrScanned()`. No scanning required.

**Import from gallery (new)** — Bob opens the scanner screen and taps "Import from gallery." He selects Alice's QR image from his photo gallery. ML Kit's `BarcodeScanning` client is already used for the live camera path. Gallery import reuses the same client with `InputImage.fromBitmap()` instead of `InputImage.fromMediaImage()`. No new dependency is needed.

All paths land in the same `QrPairingData.fromQrPayload()` → `viewModel.onQrScanned()` pipeline. No new ViewModel logic is required beyond wiring the two new entry points.

### Deep Link Setup

Add an intent filter to `AndroidManifest.xml`:

```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="offnetic" android:host="add" />
</intent-filter>
```

Handle the incoming intent in `MainActivity`, extract the `data` query parameter, and navigate to the scanner flow passing the raw payload string. The navigator calls `QrScannerViewModel.onQrScanned()` directly with the parsed `QrPairingData`.

### UI and UX

#### MyQrScreen

The screen sits on a pure black `#0A0A0A` background with safe drawing padding applied.

A close button (`✕`) sits at the top left in white Syne Bold 20sp.

The QR code is centered inside a white rounded card (240dp × 240dp, 24dp corner radius, 16dp inner padding). The QR canvas fills the remaining space and renders in `#0A0A0A` on white using the existing `QrCodeCanvas` composable.

Below the card, vertically stacked with consistent spacing:

```
Your QR Code
← white, Syne Bold, 20sp

Have trusted contacts scan this to establish an encrypted session.
← white 45% opacity, Syne Medium, 14sp, centered, horizontal padding

ID: wes3x9k2...
← white 25% opacity, Syne Medium, 12sp, centered
```

Below the ID line, two action buttons stacked with 10dp gap:

```
┌──────────────────────────────────────────┐
│  [share icon]  Share QR image            │  ← white fill, #0A0A0A text
└──────────────────────────────────────────┘

┌──────────────────────────────────────────┐
│  [link icon]   Copy link                 │  ← ghost: transparent, white border 25% opacity
└──────────────────────────────────────────┘
```

Both buttons are full width, 52dp height, 16dp corner radius, Syne Bold 15sp. The primary button (Share QR image) matches the existing white button style used throughout the app. The ghost button uses a transparent background with `border: 1.5px solid rgba(255,255,255,0.25)` and white text. Each button has a small icon to the left of its label.

A hint line in white 20% opacity Syne Medium 12sp sits below both buttons:

```
You can change this anytime in Settings
```

#### QrScannerScreen

The camera viewfinder fills the full screen with the existing semi-transparent overlay, animated scan line, and white corner markers. These are unchanged.

The title and subtitle at the top of the screen remain:

```
Scan QR Code
← white, Syne Bold, 24sp

Point your camera at the QR code to start a secure, encrypted handshake.
← white 45% opacity, Syne Medium, 14sp, centered
```

The bottom action area grows from one button to two, stacked with 10dp gap:

```
┌──────────────────────────────────────────┐
│  [qr icon]  Show My QR Code              │  ← white fill, #0A0A0A text (existing)
└──────────────────────────────────────────┘

┌──────────────────────────────────────────┐
│  [photo icon]  Import from gallery       │  ← ghost: transparent, white border 25% opacity
└──────────────────────────────────────────┘
```

Both buttons are full width, 52dp height, 16dp corner radius, Syne Bold 15sp. The ghost button uses the same style as the "Copy link" button on `MyQrScreen` for visual consistency across the two screens.

The existing hint line and flashlight FAB remain unchanged.

#### Summary of All Four Paths

```
Camera scan          → nearby, fastest, existing
Import from gallery  → far away, Bob already has the image
Share QR image       → far away, Alice sends the image
Copy link            → far away, one tap on any device
```

### Pairing Consent (one-way + Requests tray)

Pairing stays strictly one-way across all four paths: only the sharer's code/link travels, in one direction. The other side never scans back and never pastes a second link. This is intentionally simpler than Briar (which requires mutual QR scans nearby and mutual link exchange at a distance).

What each side does:

- **Nearby (camera scan):** Bob scans Alice's QR, confirms "Add Alice?", and they auto-connect over NCAPI. Alice does nothing — physical proximity is the gate. Zero taps for Alice.
- **Far away (image / link / gallery):** Bob scans/imports/taps Alice's code, confirms "Add Alice?" (with an optional safety-number/fingerprint), and his PreKey bundle is published to Alice's Nostr key. Because the code is now shareable, Alice's side must not silently auto-add him.

Far-away inbound requests land in a **Requests tray** (Instagram/Messenger style), not the main chat list:
- Alice does nothing to *receive* the request; it appears when her app next connects (see Background Relay Subscription).
- Replying *is* accepting. She can ignore or block without an explicit step.
- Strangers who obtained a forwarded code never pollute her main conversations.
- Rate-limit inbound PreKey-bundle processing per Nostr key to blunt spam and one-time-prekey exhaustion.

Deep links use a URL-safe payload: `offnetic://add?data=<base64url>` (standard base64 breaks in a query string). The tapped link must land on the "Add Alice?" confirmation screen, never auto-complete.

## Relay List

Start with a small default relay list:

```txt
wss://relay.damus.io
wss://nos.lol
wss://relay.primal.net
wss://offchain.pub
```

Publish to all relays on send. Subscribe from all relays on receive. This maximizes the chance that at least one relay retains the event.

Later, add settings for custom relays.

Do not promise guaranteed delivery. Public relay mode should be treated as best-effort.

## Relay Storage Behavior

Nostr relays store events on disk after accepting them. When a recipient comes online later, they subscribe with a `since` timestamp filter and the relay sends everything it stored since then.

Relay retention is not guaranteed:
- Some relays keep events for days or weeks, some purge after hours.
- A relay can delete events at any time for any reason.
- If a relay restarts, stored events may or may not survive.

Offnetic should treat relay storage as best-effort and use a re-publish strategy for unacknowledged messages (see Outbox Rules).

Caution — `since` filters and NIP-17 timestamps: NIP-17 gift wraps deliberately randomize (backdate) their `created_at` to hide timing, up to ~2 days in the past. A naive `since = last-seen` subscription filter will therefore **miss** events whose randomized timestamp falls before the cutoff. Do not rely on `since` for correctness: subscribe with a **back-dated window** (e.g. `since = lastSeen − 2 days`) and treat **event-id dedup as the real guard** against reprocessing. Persist the last-seen timestamp only as a coarse fetch hint, not a delivery boundary.

## Message Format

The app should encrypt the message using the existing Offnetic crypto/session layer before it touches Nostr.

Encrypt the message once and cache the ciphertext for retries. Do not re-encrypt on each retry attempt. Re-encryption advances the Signal Double Ratchet, which can desync the session if multiple relays accept the same logical message with different ciphertext.

Exception — session reset: if the Signal session for a contact is reset/shattered (`handleShatteredSession`) while ciphertext is still queued in the outbox, that cached ciphertext was encrypted under the dead session and is now undecryptable. On session reset, invalidate (delete or re-encrypt under the new session) the outbox entries for that contact. This is the only case where cached ciphertext may change.

Nostr transport = NIP-17 gift-wrapped private messages (NIP-59), the same model BitChat uses. The Signal-encrypted Offnetic envelope is the inner content (the "rumor"); it is sealed with NIP-44 to the recipient, then gift-wrapped (kind 1059) under a fresh throwaway key with a randomized `created_at`. The only cleartext routing field is the recipient `p` tag (required so the recipient can filter for it). The sender's real Nostr key is never exposed on the wire.

```txt
inner rumor   : Signal-encrypted Offnetic envelope (base64url)
seal          : NIP-44 encrypt(rumor) to recipient pubkey
gift wrap     : kind 1059, ephemeral key, randomized timestamp, ["p", recipientNostrPubkey]
```

Do not use NIP-04, and do not put a cleartext `messageUuid` in a `d` tag — a gift wrap cannot carry app tags, and `d` has reserved replaceable-event semantics. Instead **deduplicate on the Nostr event id after unwrapping**, then on the inner `messageUuid`, before processing. This avoids wasted ratchet steps on duplicates received from multiple relays.

Envelope fields before encryption:

```txt
messageUuid
senderOffneticPublicKey
recipientOffneticPublicKey
type: "text"
content
timestamp
```

Only `type = "text"` should be allowed for relay v1.

## Internet Connectivity Monitoring

The app currently does not monitor internet connectivity. Add a NetworkMonitor singleton that wraps ConnectivityManager and exposes a `StateFlow<Boolean>` for internet availability. Use `ConnectivityManager.registerDefaultNetworkCallback()`. Inject via Hilt alongside NcapManager.

ChatReachability depends on combining three signals:

```txt
NCAPI peer connected?       → NcapManager.peers          → LOCAL
Contact has Nostr key?      → Contact.nostrPublicKey     → relay eligible
Internet available?         → NetworkMonitor.isOnline    → INTERNET_RELAY vs OFFLINE
```

## Delivery Flow

Send:

```txt
User taps send
Try local connected endpoint
If local send succeeds, mark sent locally
If local unavailable and contact has Nostr key and internet is available:
    Encrypt message once and cache ciphertext in relay outbox
    Publish cached ciphertext to all relays
    If accepted by at least one relay, mark as relayed
    If no relay accepts, keep in outbox for retry
If no local and no internet, save to outbox for retry
```

Receive:

```txt
Hold relay subscriptions open while the foreground service runs (poll-on-wake only as fallback)
Subscribe for events tagged to this device's Nostr key, since last known timestamp
For each received event:
    Skip if the Nostr event id was already seen (dedup)
    Unwrap the NIP-17 gift wrap (NIP-44 decrypt)
    Skip if the inner messageUuid is a duplicate
    Validate Offnetic envelope recipient matches local identity
    Decrypt/process Offnetic payload (Signal)
    Insert message into Room and post a local notification
    Send delivery ack through local transport if nearby, otherwise through Nostr
```

## Outbox Rules

The relay outbox is a separate table from messages:

```txt
relay_outbox table:
    messageUuid         (PRIMARY KEY)
    chatId              (contact public key)
    ciphertext          (encrypted once, reused on retry)
    retryCount          (current attempt count)
    maxRetries          (8)
    createdAt           (timestamp)
    expiresAt           (createdAt + 72 hours)
    lastAttemptAt       (timestamp of last publish attempt)
    state               (PENDING / RELAYED / ACKNOWLEDGED / FAILED)
```

Limits:

```txt
max queued text messages per contact: 100
outbox TTL: 72 hours
max initial publish retry attempts: 8
```

Outbox state transitions:

```txt
PENDING     → publish accepted by relay    → RELAYED
RELAYED     → delivery ack from recipient  → ACKNOWLEDGED
RELAYED     → no ack after 24h            → re-publish to relays (up to 3 re-publishes)
PENDING     → 8 retries exhausted         → FAILED
PENDING     → 72h TTL expired             → FAILED
RELAYED     → 72h TTL expired with no ack → show "May not have been delivered"
```

Re-publish uses the same cached ciphertext. No re-encryption.

Retry execution uses WorkManager:
- A PeriodicWorkRequest runs every 15 minutes as baseline.
- A OneTimeWorkRequest is enqueued immediately when a new relay message is saved for fast first attempt.
- WorkManager survives process death and respects Android Doze mode.
- Retry logic lives at the service level, not in ChatViewModel. Messages retry even when the user is not on the chat screen.

Messages older than TTL should stop retrying and show as failed/saved.

Housekeeping:
- **Eviction:** when a contact's queue exceeds 100, drop the oldest `PENDING` entries first (never drop `RELAYED` entries awaiting an ack).
- **Prune:** delete `ACKNOWLEDGED` rows; they exist only to track delivery.
- **Cursor:** persist a per-device `last-seen` timestamp (a small `relay_state` row) used as the coarse, back-dated `since` fetch hint described in Relay Storage Behavior.

## Background Relay Subscription

Primary strategy: while `NcapForegroundService` is running, hold persistent relay subscription WebSockets open (this is what BitChat does in `NostrRelayManager`). The foreground service keeps the process alive, so the OS will not kill the sockets, and incoming relay events arrive in real time. On each event: unwrap, decrypt, insert, and fire a local notification via the existing `MessageNotificationManager` — no FCM required. This is how Briar delivers internet-side notifications without Google: a foreground service holding a live connection.

Connection management:
- Keep one WebSocket per relay with auto-reconnect + exponential backoff, and auto-restore subscriptions on reconnect.
- Use WS ping/pong keep-alives. A Nostr `wss://` socket is far cheaper to hold open than a Tor circuit, so Briar's idle-drop battery optimization is not needed here.
- **Do not idle-drop the receive subscription.** Unlike Briar (where a contact dials into your Tor hidden service), Nostr has no inbound channel — you only receive while a subscription is open. Dropping it means missing messages until reconnect.
- Run a periodic subscription-validation timer (~30s) that checks each relay still holds the expected subscriptions and re-sends any that were silently dropped (BitChat does this). Silent subscription loss otherwise stops delivery with no error.
- Re-check incoming events against the subscription filter client-side; relays occasionally deliver events that do not match the filter.
- Request a battery-optimization exemption (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`); aggressive OEM managers (Xiaomi/Huawei/Samsung) can otherwise kill even foreground services.

Fallback strategy (foreground service off): poll-on-wake.
- When the app comes to foreground, connect to relays, subscribe with `since` = last successful timestamp, process events, then either keep open (if the service starts) or disconnect.
- Without the service, expect catch-up-on-open latency, not real-time delivery.

## UI Changes

Current chat header uses a boolean online/offline state. Replace that with a transport status:

```kotlin
enum class ChatReachability {
    LOCAL,
    INTERNET_RELAY,
    OFFLINE
}
```

Header labels:

```txt
Nearby    // local NCAPI/Bluetooth/Wi-Fi path available
Internet  // text relay available
Offline   // no local or relay path currently available
```

Avoid using "Online" for relay mode because users may think calls, files, and voice notes work over the internet.

Suggested header states:

```txt
Alice
● Nearby

Alice
◇ Internet

Alice
○ Offline
```

Composer behavior:

```txt
Nearby:
- text enabled
- file enabled
- voice note enabled
- call enabled

Internet:
- text enabled
- file dimmed/disabled
- voice note dimmed/disabled
- call dimmed/disabled

Offline:
- text can still be typed and saved
- file/voice/call disabled
```

Outgoing message status text under own messages:

```txt
Saved           // local outbox only
Nearby          // sent over local Offnetic transport
Relayed         // published to relay, awaiting delivery ack
Delivered       // delivery ack received from recipient
Read            // read receipt received from recipient
Failed          // retry limit or TTL expired
Not delivered   // relayed but no ack after 72h TTL
```

Optional one-time system line when relay becomes available:

```txt
Text fallback available over internet
```

## Data Model Changes

Current `Message` has only `isSent`. Relay needs more detail.

Add `messageUuid` and `deliveryState` to the Message entity in Phase 2:

```kotlin
enum class MessageDeliveryState {
    SAVED,
    SENT_LOCAL,
    SENT_RELAY,
    DELIVERED,
    READ,
    FAILED
}
```

New and modified Message fields:

```txt
messageUuid     (String, UUID generated client-side)
deliveryState   (MessageDeliveryState, replaces isSent boolean)
```

The relay outbox is a separate table (see Outbox Rules). This keeps relay retry concerns out of the main messages table.

Pairing requests are also their own table, separate from contacts and messages:

```txt
pending_request table:
    requestId           (PRIMARY KEY)
    direction           (INBOUND / OUTBOUND)
    peerOffneticKey     (from the bundle / QR)
    peerNostrKey
    displayName
    state               (PENDING / ACCEPTED / EXPIRED)
    createdAt
    expiresAt           (createdAt + 72 hours)
```

OUTBOUND rows back the sender's "Pending — waiting to connect" state; INBOUND rows back the recipient's Requests tray. A small `relay_state` row persists the last-seen `since` cursor (Outbox Rules → Housekeeping).

## Account Cleanup

Follow the existing 3-tier destruction model:

```txt
Erase All Content:
- Clear relay outbox queue
- Clear all messages
- Nostr keypair is preserved

Erase All Content And Log Out:
- Clear relay outbox queue
- Disconnect from relays
- Clear in-memory Nostr keys
- Clear all messages
- Nostr keypair remains encrypted on disk

Delete Account:
- Destroy Nostr keypair alongside Offnetic identity
- Clear relay outbox queue
- Disconnect from relays
- Full nuclear wipe as existing behavior
```

## Identity Lifecycle (Uninstall / Reinstall)

Decision: identity is ephemeral in v1. There is no backup or recovery.

Storage today: the Offnetic identity key, Signal keys, and the new Nostr keypair all live inside the SQLCipher database (`offnetic.db`), encrypted with a passphrase held in the Android Keystore (`SQLCipherKeyProviderImpl`, `IdentityKeyManagerImpl`). The manifest sets `android:allowBackup="false"`.

What happens on uninstall:
- App-internal storage is wiped, so the SQLCipher database is deleted.
- The app's Keystore entries are destroyed, so the key that decrypts the database is gone.
- `allowBackup="false"` means there is no cloud copy (and Keystore keys never leave the device anyway, so a restored DB would be undecryptable).

What happens on reinstall:
- A brand-new identity is generated on first boot: new Offnetic public key, new Nostr key, new Signal keys, new QR code.
- The old QR/link is dead; pending relay messages addressed to the old Nostr key are orphaned and expire at TTL.
- Existing contacts still hold the old identity key. When the new identity contacts them, libsignal reports `IdentityChange.REPLACED_EXISTING` (the safety-number-changed event); they must re-pair (scan the new QR) to communicate again.
- Local history and contacts are lost for the reinstalling user (the other side keeps its copy, now stale).

This matches Briar/BitChat ("your identity is your device — lose it, start over"), which is intentional for a no-accounts privacy app. If "reinstall keeps the same identity" is ever wanted, it requires an explicit user-controlled recovery phrase or encrypted key export — out of scope for v1.

## Implementation Phases

### Phase 1: UI State + Network Monitor

Foundational UI and reachability state. No crypto, no relay traffic. Everything ships behind the relay feature flag (default off). The "Internet" state only becomes reachable once Phase 2 adds `nk` and Phase 3 wires transport; until then it is stubbed/unreachable.

**Part 1 — NetworkMonitor**
- ConnectivityManager wrapper exposing `StateFlow<Boolean>` via `registerDefaultNetworkCallback()`; Hilt singleton alongside NcapManager.
- Lifecycle correctness: pair every register with an unregister; no leaked callback or collector.
- Verify: flow flips on connect/disconnect; callback unregistered on teardown (leak check).

**Part 2 — ChatReachability**
- `ChatReachability` enum (LOCAL / INTERNET_RELAY / OFFLINE) combining three signals: NCAPI peer connected, contact has a Nostr key (stubbed false until Phase 2), internet available.
- Verify: each signal combination resolves to the correct state.

**Part 3 — Header + list indicators**
- Replace the boolean online/offline header with Nearby / Internet / Offline (● / ◇ / ○); update chat-list indicators.
- Verify: "Internet" never shown for a contact without a Nostr key.

**Part 4 — Composer gating**
- Disable file, voice, and call actions when only relay is available; text stays enabled; Offline allows typing + save.
- Verify: non-text actions dimmed in Internet/Offline; enabled in Nearby.

**Part 5 — Message status labels**
- Render the outgoing status scaffold (Saved / Nearby / Relayed / Delivered / Read / Failed / Not delivered). Relay states are wired in Phases 3–4.
- Verify: labels render for each state.

**Part 6 — System hint (optional)**
- One-time "Text fallback available over internet" line when a contact first becomes relay-eligible.
- Verify: appears once, not repeated.

### Phase 2: Nostr Identity + Data Model + QR Enhancements

Foundational data, identity, and pairing UI. Still no live relay traffic. Each migration ships and is verified on its own with migration tests.

**Part 1 — Message migration**
- Add `messageUuid` (client-generated UUID); replace `isSent: Boolean` with `deliveryState: MessageDeliveryState` (SAVED / SENT_LOCAL / SENT_RELAY / DELIVERED / READ / FAILED). Migrate all existing `isSent` reads/writes (MessageDao, ChatViewModel, bubble rendering).
- Verify: migration test (old DB → new, data preserved); existing nearby flows unaffected.

**Part 2 — Contact migration**
- Add nullable `nostrPublicKey` to Contact (Room migration).
- Verify: existing contacts load with null `nk` and still work locally.

**Part 3 — Relay tables**
- Create `relay_outbox`, `pending_request`, and `relay_state` schemas + DAOs (see Outbox Rules, Data Model Changes), including cap/eviction and prune-after-ACK logic, even though nothing writes to them until Phase 3.
- Verify: migration tests; DAO unit tests for state transitions, eviction, prune.

**Part 4 — Nostr identity**
- Generate a Nostr keypair on first launch *if missing* (covers fresh installs and upgrades); store in SQLCipher; Bech32 npub/nsec. (Keystore can't do BIP-340 Schnorr — see Nostr Identity.)
- Verify: key created once on fresh + upgraded installs; persists across restarts; never regenerated.

**Part 5 — QR payload**
- Add `nk` to `QrPairingData`; update `toQrPayload()`/`fromQrPayload()` (base64url). Regenerate the displayed QR / share payload for existing users once `nk` exists. Backward-compatible parse.
- Verify: old QR (no nk) still parses; new QR round-trips nk; existing users' code refreshes.

**Part 6 — Share / import paths**
- "Share QR image" (share-sheet PNG); "Copy link" (`offnetic://add?data=<base64url>`); "Import from gallery" (ML Kit `InputImage.fromBitmap()`). Reject non-Offnetic / no-QR / multi-QR images gracefully. All paths funnel to `fromQrPayload()`.
- Verify: each path adds the contact; bad images rejected cleanly.

**Part 7 — Deep link**
- `offnetic://add` intent filter; handle in MainActivity; route the parsed payload to the confirmation screen (never auto-complete).
- Verify: tapping a link opens the app on the confirmation screen.

**Part 8 — Add-contact confirmation + safety number**
- "Add Contact?" confirmation screen for all four paths, showing display name, key/ID, and an optional safety-number/fingerprint derived from the identity key. Store peer `nk` on confirm.
- Verify: nothing is added silently; safety number is deterministic and matches across devices.

### Phase 3: Relay Transport

The heavy lifting: crypto, relay client, send/receive, session-over-relay, and consent. Built bottom-up behind the flag; each part is independently testable before the next depends on it.

**Part 1 — Nostr crypto layer (pure, offline)**
- NIP-44 encrypt/decrypt, NIP-59 seal + gift wrap (kind 1059, ephemeral key, randomized `created_at`), BIP-340 Schnorr event signing, event-id hashing, Bech32.
- Verify: wrap↔unwrap round-trips; NIP-44/17 spec test vectors; proof that wrapping does **not** advance the Signal ratchet (it wraps already-encrypted bytes).

**Part 2 — RelayClient (network, harness-driven)**
- Persistent WebSocket per relay (OkHttp); connect/publish/subscribe; reconnect + exponential backoff; auto-restore subscriptions on reconnect; periodic subscription-validation/auto-repair timer (~30s); atomic event-id dedup; client-side filter re-check; WS ping keep-alive; lifecycle-owned coroutine scope.
- Verify (via test harness, two ephemeral keys): publish/receive on real relays; same event from N relays processed once; reconnect restores subs exactly once; `stop()` closes every socket, cancels every coroutine, clears every handler (leak gate).

**Part 3 — Outbox send engine**
- Encrypt once (Signal) → cache ciphertext → gift-wrap → publish to all relays → PENDING→RELAYED. Single-writer/transactional transitions; cap/eviction; cached-ciphertext reuse (no re-encrypt).
- Verify: transitions incl. concurrent writers (one wins); ciphertext reused; eviction drops oldest PENDING only.

**Part 4 — Retry worker**
- RelayRetryWorker (WorkManager): periodic (15 min) + immediate one-time on new message; unique-work (no duplicate workers); survives process death; Doze-aware.
- Verify: retries off the chat screen; no duplicate workers; re-publish reuses cached ciphertext.

**Part 5 — Receive path**
- Subscribe kind 1059 `#p` = this device's key with a **back-dated `since` window** (NIP-17 timestamp caution); event-id dedup as the real guard; unwrap → inner messageUuid dedup → validate recipient → **route by inner type** (text → chat, bundle → session/Requests tray) → Signal-decrypt → insert → fire local notification → persist last-seen cursor.
- Verify: messages arrive; duplicates from multiple relays collapse to one; backdated wraps are not missed; bundles route to the session path, not the chat.

**Part 6 — Transport selection (send)**
- Wire into ChatViewModel send: local-first → relay (if `nk` + internet) → save to outbox. Transport-claim guard prevents double-send when reachability flips mid-flight. Enforce `type = "text"` for relay.
- Verify: nearby uses local; far-away uses relay; contact going nearby mid-relay-send delivers exactly once; non-text never relayed.

**Part 7 — Session over relay (X3DH)**
- PreKey bundle exchange; idempotent session creation keyed by identity (a bundle arriving via both NCAPI and relay yields one session, no shatter); bundle carries Bob's pk+nk inside the encrypted payload; bundle exchange uses retry/TTL/re-publish (Pairing Request Lifecycle); session reset invalidates that contact's cached outbox ciphertext.
- Verify: never-met pairing works both directions; simultaneous bundles → single stable session; reset purges/re-encrypts stale outbox.

**Part 8 — Pairing consent**
- Far-away inbound bundle → `pending_request` (INBOUND) → Requests tray, not the main list; reply = accept; rate-limit inbound bundle processing per Nostr key; decline = silent expire; outbound side shows "Pending"; 72h TTL → lapse. One-way preserved.
- Verify: strangers land in the tray; spam bundles throttled; accept promotes to active; decline lapses silently.

**Part 9 — Cleanup integration (first cut)**
- Erase All Content clears the relay outbox and pending requests.
- Verify: outbox/requests cleared; keypair preserved.

### Phase 4: Receipts, Notifications, Service, Cleanup, Hardening

**Part 1 — Delivery acks**
- Recipient sends a gift-wrapped delivery ack; RELAYED→ACKNOWLEDGED (UI "Delivered"); ack-of-ack prevention; **throttle acks** (~0.35s interval) to avoid relay rate limits.
- Verify: "Relayed" → "Delivered" on ack; acks never trigger further acks; throttling holds under bursts.

**Part 2 — Read receipts**
- Set `MessageDeliveryState.READ`; send a throttled read receipt; recipient UI shows "Read." (Implements the otherwise-unused READ state.)
- Verify: read receipt flips the sender's status to "Read."

**Part 3 — Re-publish + TTL lifecycle**
- Unacked RELAYED → re-publish after 24h (≤3×); 72h TTL → FAILED / "Not delivered"; prune ACKNOWLEDGED rows; stop retrying past TTL; dedup incoming events by Nostr event id then inner messageUuid.
- Verify: re-publish after 24h; TTL expiry shows correct status; acknowledged rows pruned; duplicate events make no duplicate messages.

**Part 4 — Foreground-service subscription + notifications**
- NcapForegroundService holds relay subscriptions open while running → real-time receive + local notifications via MessageNotificationManager; poll-on-wake fallback when the service is off; request battery-optimization exemption (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
- Verify: notification fires with service running; catch-up-on-open when off; exemption prompt shown.

**Part 5 — Identity-change handling**
- On a contact's `REPLACED_EXISTING` (their reinstall changed the safety number), surface a re-pair prompt rather than failing silently.
- Verify: changed identity is flagged and re-pair is offered.

**Part 6 — Full 3-tier Account Cleanup**
- Erase All Content / Erase + Log Out (disconnect relays, clear in-memory Nostr keys) / Delete Account (destroy keypair). Handle cleanup during an in-flight send.
- Verify: each tier leaves the correct residue; full destruction leaves no relay state; wipe mid-send is safe.

**Part 7 — Surfacing**
- Show failed/undelivered states in chat ("Not delivered", "May not have been delivered").
- Verify: each terminal state renders the right label.

**Part 8 — Hardening pass**
- LeakCanary + StrictMode clean; audit coroutine/socket/callback lifetimes; final race review (outbox single-writer, dedup atomicity, dual-transport handshake, transport-flip).
- Verify: no leaks reported; race tests green.

## Risks

- Public relays are not guaranteed to accept or retain events.
- Relay metadata can still reveal timing, event size, and Nostr keys.
- Android background execution may make poll-on-wake intervals inconsistent.
- Some relays may reject high-frequency events or large payloads.
- Nostr relay mode should be described as best-effort, not guaranteed cloud messaging.
- Re-publishing unacknowledged messages increases relay traffic but is bounded by the 3 re-publish limit and 72h TTL.
- Without Tor (v1), a relay or network observer can link your IP to your Nostr key and infer your contact graph and timing. Content stays encrypted; only metadata leaks. Mitigation deferred to an optional Tor toggle.
- Aggressive OEM battery managers can kill the foreground service, silently stopping real-time relay delivery and notifications. Mitigate with a battery-optimization exemption; otherwise fall back to catch-up-on-open.
- Ephemeral identity means uninstall (or device loss) is unrecoverable — the user loses their identity, contacts, and history with no backup. This is an accepted v1 tradeoff.
- Public relays (damus.io, primal.net) increasingly drop events from unknown/low-reputation keys; an optional NIP-13 proof-of-work on events (as BitChat does) can improve acceptance.

## Recommendation

Build this as text-only best-effort relay first.

Do not include voice notes, files, or calls in relay v1.

Keep Offnetic's main promise:

```txt
Nearby is full Offnetic.
Internet relay is text fallback only.
Offline saves and retries.
```

## Build Progress (current — full relay + delivery-status + typography refresh)

### Relay infrastructure — done
- **Phase 1 (UI/state)** — `NetworkMonitor`, `ChatReachability`, tri-state header (● Nearby / ◇ Internet / ○ Offline), composer gating, `MessageDeliveryState`, bubble status labels.
- **Phase 2 (identity + data + QR)** — Nostr key generation (behind `NostrIdentityManager`), QR payload carries `nk` (npub), Share/Copy/Import paths, `offnetic://add` deep link with confirmation screen + safety number.
- **Phase 3 (relay transport)** — `NostrEvent`, `Nip44`, `GiftWrap`, `RelayMessage`/`RelayFilter`/`EventDeduper`, `OkHttpRelayConnection` (WS + reconnect backoff), `RelayPool` (4 relays, dedup, publish/subscribe), `RelayOutboxProcessor` (outbox state machine with 24h re-publish, 72h window, no wrongful FAILED), `RelayInboxHandler` (receive, dedup, route by type), `RelaySessionService` (session-over-relay), `RelayRequestManager` (Requests tray, Accept/ignore), `RelayControlSender` (prekey bundles + connection requests).

### Relay bug fixes (this session, all verified on-device + suite green)
| Part | Fix |
|---|---|
| **1** | Dead subscription-refresh loop (`NcapForegroundService.startRelay`) — the ~30s sub-validate/refresh was unreachable dead code behind a blocking `while(true)`. |
| **2** | **Durable connection-request** — previously fire-and-forget (lost on relay blip). Now persisted as an OUTBOUND `pending_request` (requestId = `"out:"+npub`) with bounded auto-retry (6× at 5-min intervals, self-heals via `hasSession` guard). |
| **4** | **Outbox state machine** — RELAYED rows were being re-published every cycle (~every 5 min) and eventually marked FAILED on retry exhaustion. Now: PENDING retries up to maxRetries, RELAYED only re-publishes after 24h within a 72h window (≤3 natural re-publishes), never FAILED by retry count. TTL expiry → FAILED for both. |
| **5** | **PreKey-bundle processed only on Accept (consent gate)** — `handleRequest` stores the bundle on `pending_request` (DB v8); `acceptRequest` processes it (with `runCatching`). Prevents strangers from consuming one-time-prekeys without consent. |
| **6** | **RelayPool.connect() idempotent + close() cancels collectors** — stops duplicate inbound-collector stacking on service stop/start within one process (the double-decrypt / Signal-ratchet-corruption risk). |
| **8** | **Subscription `since` no longer advances forward** — `computeSince` now back-dates the last-seen timestamp by 2 days (NIP-17 back-dating safety margin). Always safe even after the window drifts from randomized gift-wrap timestamps. |

### Delivery-status & receipts (Phase 4 — built & verified this session, both transports)
**New design — status labels & colors:**
- **Internet:** `Relayed` (gray) → `Delivered` (white) → `Seen` (green)
- **Nearby:** `Sending` (gray) → `Delivered` (white) → `Seen` (green)
- Plan originally specified `Read`; deliberately overridden to `Seen` (user decision).
- Colors: gray = `0x66FFFFFF`, white = `0xCCFFFFFF`, green = `0xFF4ADE80` (from the app's existing palette).

**Relay (internet) receipts:**
- `markDelivered` / `markRead` DAO — forward-only (no downgrade; `READ` beats late `DELIVERED`).
- Delivery ack: recipient auto-acks on receive (`RelayInboxHandler.handleMessage` → `RelayControlSender.sendDeliveryAck`).
- Read receipt: `ChatViewModel.markAsRead()` (chat-open) → `sendReadReceipt` to the contact's `npub`; also fires on `ON_RESUME` (resume after background). Live-arrivals: `RelayInboxHandler` sends a read receipt if `activeChatTracker.activeChatKey == contact` (actively viewing).
- **Throttle: 0.35s spacing** (`RelayControlSender` — global mutex-based throttle for all receipt sends, `scope.launch` fire-and-forget, off the inbox thread). Stated in the plan; implemented.
- **Lost-ack recovery:** `handleMessage` re-acks on dedup (republish → delivery/read receipts are re-sent), bounded by the 5000-LRU deduper + the 0.35s throttle.
- `markDelivered`/`markRead` purposely exclude `'SAVED'` (incoming-message state) — structurally impossible for a receipt to corrupt an inbound message.

**Nearby receipts:**
- **Shared message id:** `NcapEnvelope.Plain` got `messageUuid: String = ""` (serialized in JSON only when present, backward-compatible). Sender stamps `entity.messageUuid` on the SIGNAL_MESSAGE envelope.
- Recipient (`NcapManagerImpl.handleSignalMessage`) adopts the id via `.copy(messageUuid = resolvedUuid)`.
- **Payload types:** `DELIVERY_ACK` / `READ_RECEIPT` added to `NcapEnvelope.PayloadType`.
- Delivery ack: `handleSignalMessage` sends a `DELIVERY_ACK` back over the endpoint; sender dispatch → `markDelivered`.
- Read receipt: `ChatViewModel.markAsRead()` also calls `ncapManager.sendReadReceipt(contactPk, uuid)`; sender dispatch → `markRead`. Live-arrivals: the `_incomingMessages` collector sends read receipts if `activeChatKey == contact`. `sendReadReceipt` fire-and-forgets via `scope.launch`.
- Envelope-level receipts are unauthenticated (envelope `senderPublicKey` not verified) — acceptable, requires a paired connection. Relay receipts are well-authenticated by uuid-secrecy (the uuid lives inside the NIP-44-encrypted rumor).

**Audit hardening fixes (deep-pass findings):**
- Dropped `'SAVED'` from `markDelivered`/`markRead` WHERE clauses (incoming-message corruption vector closed).
- `sendReadReceipt` made non-blocking (fire-and-forget, matching the delivery-ack pattern) — prevents a hanging nearby send from stalling the `_incomingMessages` collector.
- `computeSince` back-dates by 2 days (Part 8 above).
- All receipt types are forward-only, race-safe (Delivered vs Read order), and idempotent (duplicates from multi-relay / dual-transport / live+resume harmless).

### UI / UX changes (this session)
- **Toast cleanup:** Removed the per-message `"… sending over the internet"` toast (redundant with ◇ header + "Relayed" status). The `"… connecting over the internet"` toast now fires **once per chat** (flag set before emit so concurrent sends can't double-fire).
- **Paste link:** Scanner screen has a **"Paste link"** ghost button (matching "Import from gallery") — reads the clipboard, validates `offnetic://add?data=…`, routes to the confirmation screen. Gallery-error display fixed: `state.error` rendering + auto-clear was added (previously errors were silently swallowed).
- **MyQrScreen back button:** `✕` → `‹` (back chevron, 20sp Syne Bold white — same size/style as the `✕` it replaced; consistent with the app's top-corner nav pattern).
- **DB:** version bumped 7 → 8 (`PendingRequestEntity.bundle: ByteArray?` for Part 5).

### Typography system (new design decision)
- **All 14 Compose screens migrated** from inline `FontFamilySyne` / `FontFamilyIBM` specs to `style = MaterialTheme.typography.*` named styles.
- **System font** (`FontFamily.Default` — Roboto on Android / SF on iOS) replaces the shipped Syne + IBM Plex Sans fonts. One non-Compose screen (`activity_call.xml`) was already on the system font — now consistent across the whole app.
- **Named-style ladder** (defined in `Typography` at `Color.kt:63-76`):
  - `displayLarge` 28 Bold — hero/splash headings
  - `titleLarge` 22 Bold — screen titles
  - `titleMedium` 18 Medium — section headers
  - `bodyMedium` 15 Normal — body / messages / explainers
  - `labelLarge` 15 SemiBold — buttons + list-row primary titles
  - `bodySmall` 12 Medium — captions / hints / timestamps / status
  - `labelSmall` (Material default 11) — overline section headers + small buttons
- **Deletions:** 5 `.ttf` files (`syne_variable`, `ibm_plex_sans_*`) removed; `FontFamilySyne`/`FontFamilyIBM` definitions deleted. No screen references remain.
- **Zero warnings** across the full build after migration.

### Conventions (updated)
- **DB:** version **8** (bumped from 7 for Part 5), `fallbackToDestructiveMigration` (`exportSchema=false`). Version bumps are destructive (no production users yet).
- **Tests:** 121 deterministic tests, 0 failures, 2 skipped (opt-in live relay tests). Suite command: `& .\gradlew.bat :app:testDebugUnitTest --console=plain`.
- **No code comments** (project style). No `w:` warnings anywhere (verified post-typography-migration).
- **Fonts:** System font (`FontFamily.Default`) via `MaterialTheme.typography.*`. No shipped font files. The call screen (`activity_call.xml`) already uses the default system font.
- **Status labels:** `Relayed` / `Sending` (gray) → `Delivered` (white) → `Seen` (green). Defined in `ChatScreen.kt:619-625`.
- **Receipt throttle:** 0.35s spacing via `RelayControlSender.receiptScope` (global mutex + delay, fire-and-forget). Stated in the plan.
- **Deep link format:** `offnetic://add?data=<base64url>`.
- **Pairing:** one-way (only sharer's code moves). Recipient's inbound request goes to the Requests tray; Accept creates the contact and sends the bundle back. Reply = accept.

### Next
- [ ] Revert temp `DebugTree` logging to `ReleaseTree` (privacy-safe — only logs ERROR).
- [ ] Final build + install + two-setup device test:
  - **Internet:** BT off, different networks/rooms → `Relayed → Delivered → Seen` over relay.
  - **Nearby:** BT on, phones together → `Sending → Delivered → Seen` over NCAPI.
  - Eyeball the system-font typography ladder on-device.
- [ ] Future: "Not delivered" TTL state (plan line 506); read-receipt uuid batching for bulk-unread; nearby delivery-ack envelope authentication.
