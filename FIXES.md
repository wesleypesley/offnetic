# Offnetic Bug Fixes Log

## Round 1 — Message Send/Display Pipeline

### #1: Messages silently lost on send
**File:** `ChatViewModel.kt:254`
**Cause:** `encryptMessage()` was called BEFORE `messageDao.insert()`. If encryption threw (no session), the message was never saved to DB.
**Fix:** Insert message to DB FIRST, then encrypt. If encrypt fails, message stays as pending in DB and user sees a toast.

### #2: `isSent` never updated to `true`
**File:** `ChatViewModel.kt:254,267` (also `sendFile()` and `toggleVoiceRecording()`)
**Cause:** `messageDao.insert(entity)` returns the generated ID but it was discarded. The later `update(entity.copy(isSent = true))` passed an entity with `id = 0`, so the UPDATE matched no row.
**Fix:** Capture returned ID: `val messageId = messageDao.insert(entity)`, then `update(entity.copy(id = messageId, isSent = true))`.

### #3: Sent messages displayed on wrong side
**File:** `ChatScreen.kt:153`
**Cause:** `isMine = message.isSent` — which was always `false` (see #2).
**Fix:** `isMine = message.senderPublicKey == myPublicKey` — compare against local identity.

### #4: No user feedback on send failures
**Files:** `ChatScreen.kt:165-166`, `ChatViewModel.kt:272-274`
**Cause:** Catch block only logged to Log.e, no toast. Text input cleared regardless of success/failure.
**Fix:** Emit toast via `_toastMessage.emit()` on error. Added `timber.log.Timber` for structured logging.

### #5: No error when peer not connected
**File:** `ChatViewModel.kt:262-271`
**Cause:** When `sendEncrypted` found no CONNECTED peer, it silently logged and the message stayed `isSent=false` permanently.
**Fix:** Toast: `"$contactName not connected — message saved"`.

### #6: Messages appeared at top instead of bottom
**Files:** `MessageDao.kt:22`, `ChatScreen.kt:118-122`
**Cause:** DAO queried `ORDER BY timestamp DESC` (newest first), but chat UI auto-scrolled to last index (oldest message).
**Fix:** Changed to `ORDER BY timestamp ASC` (oldest first), scroll to `messages.size - 1` (newest at bottom).

### #7: PRE_KEY_BUNDLE send failure not retried
**File:** `NcapManagerImpl.kt:148-151`
**Cause:** If PRE_KEY_BUNDLE send failed on `onConnectionResult`, no retry was attempted. This permanently broke the Signal session for that peer.
**Fix:** Buffer failed bundles in `preKeyBundleRetries` map. Retry on next `onConnectionResult` for the same peer.

### #8: Switched to Timber logging
**File:** `ChatViewModel.kt`
**Cause:** `android.util.Log` was used instead of project-standard `Timber`.
**Fix:** Replaced all `android.util.Log` with `Timber.d/w/e`.

---

## Round 2 — Connection Flow (5 Critical Bugs)

### B1: Initiator never sends PRE_KEY_BUNDLE
**File:** `NcapManagerImpl.kt:135`
**Cause:** `onConnectionResult` looked up peer public key via `endpointPublicKeys[endpointId]`, which is only populated by `acceptConnection()`. The initiator (device that called `requestConnection`) never calls `acceptConnection`, so the key was null → `return@launch` → PRE_KEY_BUNDLE never sent. Session half-broken.
**Fix:** Fall back to `endpointPeers[endpointId]?.publicKey`.

### B2: `requestConnection` sent "Offnetic" instead of public key
**File:** `NcapManagerImpl.kt:287`
**Cause:** Used `currentAdvertisingName` which could be empty string → fallback "Offnetic". Remote peer saw wrong identity.
**Fix:** Always read identity public key from DB: `identityDao.getIdentity()?.publicKey`.

### B3: "Online" shown for disconnected peers
**File:** `ChatViewModel.kt:96-98`
**Cause:** `peers.any { it.publicKey == contactPublicKey }` — checked only public key, not connection state.
**Fix:** Added `&& it.connectionState == ConnectionState.CONNECTED`.

### B4: ICE disconnect killed call instantly
**File:** `WebRtcManager.kt:507-512`
**Cause:** On DISCONNECTED/FAILED/CLOSED, call immediately set to ENDED with no grace period.
**Fix:** DISCONNECTED gets 15s grace timer. FAILED/CLOSED still immediate.

### B5: No online indicators in chat list
**Files:** `ChatListViewModel.kt`, `ChatListScreen.kt`
**Cause:** `ChatSummary` had no `isOnline` field. `ChatListViewModel` didn't include `NcapManager.peers` in its combine flow.
**Fix:** Added `isOnline: Boolean` to `ChatSummary`. Injected `NcapManager` into ViewModel, added `ncapManager.peers` to the combine, check `CONNECTED` state. Green dot rendered in `ChatListItem`.

---

## Round 3 — Navigation

### Back from MAIN walked through all onboarding screens
**File:** `OffneticNavHost.kt:89`
**Cause:** `ProfileSetupScreen(onDone = { navController.navigate(Routes.MAIN) })` — no `popUpTo`, so back stack included all permission/identity/profile screens.
**Fix:** Added `popUpTo(Routes.SPLASH) { inclusive = true }`.

---

## Round 4 — Voice Notes

### Voice note record button couldn't stop
**File:** `ChatViewModel.kt:177`
**Cause:** `recordingGuard` check was BEFORE the `isRecording` branch. On second tap (to stop), `recordingGuard` was still `true` from start → function returned immediately.
**Fix:** Moved `if (recordingGuard) return` to only the START branch (else clause).

### Voice note playback — no tap-to-play
**File:** `ChatScreen.kt:430-451`
**Cause:** Voice note bubble was just static green dot + text, no click handler.
**Fix:** Added `clickable` + `MediaPlayer` playback. Green dot turns red during play, text shows "Playing...".

### Voice note receiver crash
**File:** `NcapManagerImpl.kt:508`
**Cause:** `handleSignalMessage` parsed ALL decrypted payloads as JSON. File/voice plaintext was raw bytes (not JSON).
**Fix:** Senders now wrap file/voice content in JSON `{type: "file"|"voice_note", content: base64, fileName, duration, timestamp}`. Receiver reads `type` field and saves to disk.

---

## Round 5 — Calls

### Call screen never appeared (incoming)
**File:** `OffneticNavHost.kt`
**Cause:** `LaunchedEffect` that collects `incomingCallRouter.incomingCall` was inside `composable(Routes.MAIN)` — only active when MAIN was composed. When user was in a chat, the collector was dead.
**Fix:** Moved `IncomingCallRouter` and its `LaunchedEffect` collector to the top-level `OffneticNavHost` composable — always alive.

### Incoming call SharedFlow lost events
**File:** `IncomingCallRouter.kt:25`
**Cause:** `MutableSharedFlow(extraBufferCapacity = 4)` had no replay. When CallViewModel started collecting later, earlier CALL_OFFER was already overwritten by ICE_CANDIDATEs.
**Fix:** Added `replay = 1`.

### Call signaling SharedFlow lost CALL_OFFER
**File:** `NcapManagerImpl.kt:82`
**Cause:** `_incomingCallSignals` had `replay = 1`. ICE_CANDIDATEs arriving before CallViewModel started collecting overwrote the CALL_OFFER.
**Fix:** Changed to `replay = 10`.

### startCall/acceptCall didn't check CONNECTED state
**File:** `WebRtcManager.kt`
**Cause:** Peer lookup filtered by publicKey only, not CONNECTED state. Sent CALL_OFFER/ANSWER to disconnected endpoints.
**Fix:** Added `&& connectionState == CONNECTED` to peer lookups.

### Video call — no accept/decline UI
**File:** `VideoCallScreen.kt`
**Cause:** `VideoCallScreen` only showed phase text ("Incoming call") with no buttons.
**Fix:** Added `Column` with peer name + "Incoming video call" + GREEN accept + RED decline buttons for `INCOMING` phase.

### Video call — remote renderer blank
**File:** `WebRtcManager.kt`
**Cause:** `attachRemoteVideo` was called on CONNECTED, but remote `VideoTrack` arrived later via `onAddTrack`. Track wasn't attached because `remoteVideoTracks[peerKey]` was still empty.
**Fix:** Added `pendingRemoteRenderers` registry. `attachRemoteVideo` registers renderers. `onAddTrack` auto-attaches to all pending renderers.

### WebRTC stability
**File:** `WebRtcManager.kt`
- `disableNetworkMonitor = true` — prevents WebRTC from re-gathering candidates on NCAPI transport switches.
- ICE DISCONNECTED grace increased from 5s → 15s.

---

## Round 6 — Heartbeat & Reconnect

### NCAPI connection idle timeout
**File:** `NcapManagerImpl.kt`
**Cause:** No keepalive. NCAPI connections dropped after idle period.
**Fix:** Added 25s `HEARTBEAT` payload sent to each connected endpoint. Starts on `onConnectionResult`, stops on `onDisconnected`.

### Auto-reconnect when opening chat
**File:** `ChatViewModel.kt`
**Cause:** When user opened a chat with a disconnected peer, no reconnect was attempted.
**Fix:** `ChatViewModel.init` calls `ncapManager.reconnectToContact(contactPublicKey)`.

### `requestConnection` retry on failure
**File:** `NcapManagerImpl.kt`
**Cause:** Failed `requestConnection` never retried.
**Fix:** 3s delay retry on `STATUS_ENDPOINT_IO_ERROR` and other failures.

### `handleEndpointFound` missing `requestConnection` call (self-inflicted)
**File:** `NcapManagerImpl.kt:694`
**Cause:** During logging refactor, accidentally deleted `requestConnection(endpointId)` from `handleEndpointFound`.
**Fix:** Restored it with hard `android.util.Log.e` fallback logs throughout all connection events.

---

## Key Diagnostic Logs Added

All use `android.util.Log.e` (guaranteed output, even if Timber filtered):

| Tag | Location | Events |
|-----|----------|--------|
| `NcapConn` | `NcapManagerImpl.kt` | FOUND peer, requestConnection OK/FAILED, onConnectionInitiated, acceptConnection, onConnectionResult, PRE_KEY_BUNDLE sent/received, Session ESTABLISHED |
| `CallNav` | `OffneticNavHost.kt` | NAVIGATING to call |
| `CallVM` | `CallViewModel.kt` | handleCallSignal, acceptCall tapped |
| `WebRTC_ICE` | `WebRtcManager.kt` | ICE state change, ICE gathering, onIceCandidate, onSdpReceived |

---

## Round 7 — Video Call Architecture Refactor (Compose → XML Activity)

> **Status: BASE INFRASTRUCTURE WORKING, RENDERING AND CONTACT ISSUES REMAINING**

### #33: Compose SurfaceView Z-order fights → black screen **(NOT FULLY FIXED)**
**Files:** `VideoCallScreen.kt` (deleted), `activity_call.xml`, `CallActivity.kt`
**Symptom:** SurfaceViewRenderer inside Compose `AndroidView` never shows video because Compose's single-surface rendering pipeline doesn't cooperate with SurfaceView's hole-punch mechanism. Frames render (`FRAME rendered` log fires) but are invisible.
**Cause:** Compose draws to a `RenderNode` surface. SurfaceView at default Z-order sits below the window surface but the Compose surface is opaque (`Scaffold.containerColor = #0A0A0A`), blocking the hole-punch.
**Fix (partial):** Replaced Compose `VideoCallScreen` with XML-based `CallActivity` + `RelativeLayout` (Meshenger pattern). Deleted `VideoCallScreen.kt`, `VoiceCallScreen.kt`, `IncomingCallRouter.kt`. Created `CallActivity.kt` (302 lines), `activity_call.xml` (RelativeLayout + 2× SurfaceViewRenderer + control ImageButtons), 8 drawable vector icons.
**Current state:** XML layout renders but still shows black screen. The pipeline (ICE→tracks→sinks→renderers) is correct but EGL context sharing issue persists (see #41).

### #34: Dual EglBase — CallActivity creates separate EglBase from WebRtcManager **(NOT FULLY FIXED)**
**Files:** `CallActivity.kt:108`, `WebRtcManager.kt:87`
**Symptom:** `CallActivity.bindViews()` created its own `EglBase.create()`, separate from `WebRtcManager.initialize()`'s EglBase. Decoded video frames (OES textures) live in the factory's EGL context — renderers in a completely different context can't sample them. Both renderers show black.
**Fix:** `CallActivity.onCreate()` now calls `webRtcManager.initialize()` synchronously before `bindViews()`. `bindViews()` calls `webRtcManager.initSurface(renderer)` instead of creating a local EglBase. Both renderers share the factory's EGL context.
**Current state:** Fix applied (latest build). Awaiting test.

### #35: Camera auto-started capture in createPeerConnection
**File:** `WebRtcManager.kt:524`
**Symptom:** `createPeerConnection` called `videoCapturer.startCapture(640, 480, 30)` immediately during peer connection creation. Camera was sending frames even though UI showed camera as OFF (Meshenger-style "always negotiate video, start camera off").
**Fix:** Removed `startCapture` from `createPeerConnection`. Camera now only starts via `setCameraEnabled(true)` → `videoCapturers[key]?.startCapture(...)`.

### #36: bindVideoTracks not called when camera was OFF
**File:** `CallActivity.kt:191-199`
**Symptom:** `updateUI(CONNECTED)` only called `bindVideoTracks()` when `cameraEnabled && !pipVisible`. Since camera starts OFF by default, tracks were never bound to renderers — remote video never attached to fullscreen.
**Fix:** `bindVideoTracks()` now always called on CONNECTED. PIP visibility gated separately.

### #37: Double finish() on hangup
**File:** `CallActivity.kt:234-239`
**Symptom:** `hangup()` called both `callViewModel.hangup()` (emits `finishEvent` → collector calls `finish()`) AND `finish()` directly. Double-close caused race conditions.
**Fix:** Added `finished` flag. `hangup()` only calls `callViewModel.hangup()`. `finishEvent` collector handles the Activity finish.

### #38: Stale ICE candidate cache
**File:** `WebRtcManager.kt:148, 217`
**Symptom:** `iceCandidateCache` and `pendingSdpOffers` retained values from previous calls. New PeerConnection received old ICE candidates from a different session.
**Fix:** `iceCandidateCache.remove(key)` and `pendingSdpOffers.remove(key)` called at start of both `startCall()` and `acceptCall()`.

### #39: Incoming call timeout not cancelled on accept
**File:** `CallViewModel.kt:55, 114, 192`
**Symptom:** 60s incoming call timeout continued running after user accepted. Could fire mid-call and transition state to ENDED.
**Fix:** Stored `timeoutJob` reference. `acceptCall()` cancels it before proceeding.

### #40: Stale SharedFlow replay causing instant CallActivity close
**Files:** `NcapManagerImpl.kt:82`, `CallViewModel.kt:155-158`
**Symptom:** `_incomingCallSignals` had `replay=10`. Old `CALL_HANGUP` from previous call replayed immediately when new `CallViewModel` started collecting → `finishEvent` emitted → Activity closed instantly. Also: `_incomingCallEvents` had `replay=1` which replayed old CALL_OFFER events.
**Fix:** Added `timestamp` to `CallSignal` data class. `CallViewModel` stores `callStartTime` and filters `CALL_HANGUP` signals older than `callStartTime`. Changed `_incomingCallEvents` to `replay=0`.

### #41: NCAPI STATUS_ENDPOINT_UNKNOWN crash
**File:** `NcapManagerImpl.kt:371-382`
**Symptom:** `sendPayload()` threw `ApiException(8011: STATUS_ENDPOINT_UNKNOWN)` when peer disconnected mid-call. Exception propagated uncaught through `WebRtcManager` coroutine → killed process.
**Fix:** Wrapped `sendPayload()` body in try-catch. Errors now logged as warnings instead of crashing.

### #42: Auto-created stub contact overwrites QR-scanned contact
**Files:** `NcapManagerImpl.kt:643-653`, `ContactDao.kt:14`
**Symptom:** When second device scanned QR: `handleSignalMessage` auto-created a stub contact with device ID as name (`isVerified=false`). Race condition: if this insert happened after QR scan's insert, `OnConflictStrategy.REPLACE` overwrote the proper display name with the device ID. Chat list showed device ID instead of name, and contact appeared offline.
**Fix:** Added `insertIfNotExists()` to `ContactDao` using `OnConflictStrategy.IGNORE`. Auto-creator now silently fails if a QR-scanned contact already exists.

### #43: acceptCall doesn't check current state
**File:** `CallActivity.kt:228-232`
**Symptom:** User could tap ACCEPT after incoming timeout expired → `WebRtcManager.acceptCall()` tried to use a cached SDP offer that was never received → "No SDP offer received" error.
**Fix:** `acceptCall()` checks `callState.phase == INCOMING` before proceeding.

### #44: singleTop CallActivity receives duplicate intents
**File:** `CallActivity.kt:96-105`
**Symptom:** `launchMode="singleTop"` on CallActivity. If a new incoming call arrived while already in a call, `onNewIntent` was never handled. User saw stale call UI.
**Fix:** Added `onNewIntent()` handler. If new call is for a different peer, hangs up current call and starts new one.

### #45: Single call button replaces voice/video split
**Files:** `ChatScreen.kt:70-74, 179-296`, `Routes.kt`, `OffneticNavHost.kt`
**Change:** Removed separate voice/video call buttons. Single phone button launches `CallActivity` (audio-only start, camera toggle during call). Meshenger/Snapchat/Instagram pattern. Removed `VOICE_CALL` and `VIDEO_CALL` routes from `Routes.kt` and `OffneticNavHost.kt`.

---

## Round 8 — One-Way QR Trust + Bidirectional Messaging/Calls

> **Goal: user1 scans user2's QR only. Both see display names. Messages + calls work both ways.**

### #46: No profile exchange after one-way QR scan **(FIXED)**
**File:** `NcapEnvelope.kt:22`, `NcapManagerImpl.kt:167-189, 504-536`
**Symptom:** user1 scans user2's QR, gets user2's display name from QR data. But user2 never receives user1's display name — auto-created stub contact uses `publicKey.take(12) + "..."` as name. Chat list shows truncated public key instead of real name.
**Cause:** No mechanism transmitted the scanner's display name to the scanned party. QR is one-directional — only the scanned party's info is in the QR code.
**Fix:** Added `INITIAL_IDENTITY` payload type to `NcapEnvelope.PayloadType`. On every `onConnectionResult(SUCCESS)`, after sending PRE_KEY_BUNDLE, both peers also send `INITIAL_IDENTITY` envelope containing `{publicKey, displayName}`. Receiver processes it: inserts into `profiles` table, updates contact display name if contact exists (or creates verified contact if not).

### #47: Endpoint routing — wrong endpoint picked for sending **(FIXED)**
**Files:** `NcapManagerImpl.kt:619-626`, `ChatViewModel.kt:296-307`, `WebRtcManager.kt:159-166, 229-238, 387-393, 689-700`
**Symptom:** When two devices discover each other via NCAPI P2P_CLUSTER, TWO connections are created (one initiated by each device). `endpointPeers` can contain both endpoints for the same publicKey. `.find()` picks first match — non-deterministic which endpoint is selected. If the "incoming" endpoint (where this device called `acceptConnection`) is picked, the peer has NO callback on that endpoint → payload silently dropped.
**Fix:** Added `NcapManager.getConnectedEndpointIds(publicKey)` which returns ALL connected endpointIds for a publicKey. Updated `ChatViewModel.sendEncrypted`, `sendFile`, `toggleVoiceRecording` and `WebRtcManager.startCall`, `acceptCall`, `hangup`, `sendIceCandidateToPeer` to use `getConnectedEndpointIds().first()` instead of `.find()` on `ncapManager.peers.value`. Also added `trySendOnAnyEndpoint()` helper in NcapManagerImpl for signals that can be retried across multiple endpoints.

### #48: Session fragility — missing session counted as decrypt failure **(FIXED)**
**Files:** `SignalProtocolManager.kt:187-192`, `NcapManagerImpl.kt:583-614`
**Symptom:** When a message arrives but no session exists for that peer (e.g. PRE_KEY_BUNDLE lost due to endpoint routing issue), `decryptMessage` returns null. This was counted as a decrypt failure. After 3 failures, session was "shattered" — deleting any existing session data and requiring a new PRE_KEY_BUNDLE exchange. This destroyed the session even when the real issue was a missing (never-created) session.
**Fix:** Added `SignalProtocolManager.hasSession(peerPublicKey)` check. When decryption fails, first check if a session exists. If no session exists, call `requestPreKeyBundle()` immediately (send a fresh PRE_KEY_BUNDLE) without counting toward the shatter threshold. Only count actual ratchet failures (session exists but decrypt fails) toward the 3-strike shatter limit.

### #49: Hilt DI for ProfileDao **(FIXED)**
**Files:** `NearbyModule.kt:4, 40, 46`
**Symptom:** Compilation failure after adding `profileDao` to `NcapManagerImpl` constructor — Hilt module `provideNcapManager` had wrong parameter count/ordering.
**Fix:** Added `profileDao: ProfileDao` parameter and import to `NearbyModule.kt`.

---

## Round 9 — Chat, QR, Files, Calls, UI, Memory (Comprehensive Fix)

> **Session: Full-system audit. Chat + QR + File transfer + Call + UI + Memory leaks.**

### Chat System

#### #50: Toast messages showed `MutableStateFlow@...` instead of contact name
**File:** `ChatViewModel.kt:167,227,302`
**Symptom:** `$contactName` resolved to `StateFlow.toString()` — "MutableStateFlow(value=Alice)..." in toasts.
**Fix:** Changed to `${_contactName.value}`.

#### #51: Chat list LazyColumn used non-keyed items
**File:** `ChatListScreen.kt:142`
**Symptom:** `items(chatSummaries.size)` caused full recomposition on every state change — janky scrolling.
**Fix:** Changed to `items(chatSummaries, key = { it.contactPublicKey })`.

#### #52: Unread count always showed 0 or 1
**Files:** `ChatListViewModel.kt:60`, `MessageDao.kt`
**Symptom:** `getChatSummaries()` returned only the latest message per chat. Unread count checked only that single message — never more than 1.
**Fix:** Added `getUnreadCountsPerChat(myPublicKey)` query returning real aggregate counts. Used `flatMapLatest` to react to identity changes.

#### #53: Messages sent while peer offline never re-sent
**Files:** `ChatViewModel.kt`, `MessageDao.kt`
**Symptom:** Message saved to DB with `isSent=false` when peer disconnected. Never retried even after peer reconnected.
**Fix:** Added `retryUnsentMessages()` that fires on `false→true` online transition. Queries `getUnsentMessagesForChat` (filters by `senderPublicKey` to prevent re-sending received messages). Re-wraps text/file/voice in correct JSON format, re-encrypts, re-sends.

#### #54: Voice note MediaPlayer leaked on scroll
**File:** `ChatScreen.kt:402`
**Symptom:** `MediaPlayer` created in `remember` for voice note playback. If user scrolled away mid-playback, player never released.
**Fix:** Added `DisposableEffect` calling `mediaPlayer?.stop()?.release()` on dispose.

#### #55: `reconnectToContact` silently failed when peer not discovered
**File:** `NcapManagerImpl.kt:378-391`
**Symptom:** `reconnectToContact` searched `endpointPeers` — returned silently if peer not found. Chat opened but no connection attempted.
**Fix:** Now calls `startDiscovery()` when peer not in endpoint list, ensuring discovery is active.

### QR System

#### #56: No NCAPI discovery triggered after QR scan
**File:** `QrScannerViewModel.kt`
**Symptom:** After scanning QR code, the scanned peer was never actively looked for. Chat opened but peer not found.
**Fix:** Injected `NcapManager`, added `startDiscovery()` call after contact creation.

#### #57: QR scanner CameraX leaked on screen leave
**File:** `QrScannerScreen.kt`
**Symptom:** CameraX `ProcessCameraProvider` bound in `AndroidView` factory, never unbound when leaving screen. Camera stayed open.
**Fix:** Added `DisposableEffect` calling `cameraProvider?.unbindAll()` on dispose.

#### #58: INITIAL_IDENTITY never sent without profile
**File:** `NcapManagerImpl.kt:189-206`
**Symptom:** If `profileDao.getByPublicKey()` returned null (profile not yet set), INITIAL_IDENTITY was silently skipped. Peer never got sender's display name.
**Fix:** Always sends INITIAL_IDENTITY. Falls back to `publicKey.take(12)` as display name if no profile.

#### #59: INITIAL_IDENTITY had no retry mechanism
**File:** `NcapManagerImpl.kt:169-215`
**Symptom:** If `sendPayload` failed for INITIAL_IDENTITY, the display name was permanently lost (unlike PRE_KEY_BUNDLE which retried).
**Fix:** Added `identityRetries` buffer. Failed INITIAL_IDENTITY payloads are retried on next `onConnectionResult`.

### File Transfer System

#### #60: Files sent as base64 over Bluetooth instead of native NCAPI
**Files:** `ChatViewModel.kt`, `NcapManagerImpl.kt`
**Symptom:** `sendFile` base64-encoded entire file, encrypted, sent via `Payload.fromBytes()` over Bluetooth. 5MB photo → 6.6MB base64 → NCAPI chunking failure → never arrived. No Wi-Fi Direct upgrade triggered.
**Fix:** `ChatViewModel.sendFile()` now routes through `NcapManager.sendFile()` which uses `Payload.fromFile()`. NCAPI's P2P_CLUSTER detects the file payload and automatically upgrades to Wi-Fi Direct/Ethernet.

#### #61: FILE_TRANSFER_REQUEST metadata not used by receiver
**File:** `NcapManagerImpl.kt:590-592, 802-820`
**Symptom:** `sendFile` sent FILE_TRANSFER_REQUEST metadata envelope, but the handler was empty (just log). `handleIncomingFile` had no access to filename/MIME.
**Fix:** FILE_TRANSFER_REQUEST handler now parses metadata into `pendingFileMetas` queue (per-sender `ArrayDeque`). `handleIncomingFile` pops metadata to get correct filename and MIME type. Queue prevents filename mix-up on concurrent transfers.

#### #62: "Open with" picker showed wrong apps (Google Pay etc)
**File:** `NcapManagerImpl.kt:808-816`
**Symptom:** NCAPI saves received files as `Nearby_Transfer_456` (no extension). FileProvider URI ended in no extension. Android's intent resolver couldn't match MIME → showed all binary-handling apps including Google Pay.
**Fix:** Received file renamed to include original extension (`Nearby_Transfer_456.jpg`) before saving `attachmentPath`. FileProvider URI now carries correct extension + `setDataAndType` MIME → correct "Open with" picker.

### File Preview UI

#### #63: File attachments showed dead text, no preview
**File:** `ChatScreen.kt:442-450, 526-760`
**Symptom:** `TYPE_FILE` branch rendered plain text label. No thumbnail, no tap interaction.
**Fix:** Three new composables: `ImageThumbnail` (BitmapFactory + Image, tap→ACTION_VIEW), `VideoThumbnail` (MediaMetadataRetriever frame + ▶ overlay, tap→video player), `DocAttachment` (extension badge + filename, tap→system picker). FileProvider + `Intent.ACTION_VIEW` for opening. MIME type inferred from filename extension.

### Call System

#### #64: Incoming calls never appeared (IncomingCallRouter deleted, no replacement)
**File:** `OffneticNavHost.kt`
**Symptom:** `IncomingCallRouter` was deleted in Round 7 refactor. `_incomingCallEvents` emitted but never collected. Incoming calls dead.
**Fix:** Added Hilt `@EntryPoint` + `LaunchedEffect` in `OffneticNavHost` that collects `incomingCallEvents` and launches `CallActivity(EXTRA_IS_INCOMING=true)`.

#### #65: Video black screen — opaque layout background blocked SurfaceView
**File:** `activity_call.xml:5`
**Symptom:** `android:background="#0A0A0A"` on `RelativeLayout` covered the fullscreen SurfaceView (default Z-order = below window). PIP worked (`setZOrderMediaOverlay=true` punches through), fullscreen didn't. Cross-checked with Meshenger (no opaque background on layout).
**Fix:** Removed opaque background from layout. Window theme already provides `#0A0A0A` via `Theme.Offnetic`. SurfaceView at default Z-order can punch through the window surface but not through opaque layout children.

#### #66: EGL "no current context" errors + SurfaceSyncer failures
**File:** `CallActivity.kt:124,222-230`
**Symptom:** `libEGL: call to OpenGL ES API with no current context` and `SurfaceSyncer: Failed to find sync`. Renderers started VISIBLE before EGL context was bound to WebRTC's rendering thread. Premature GL calls crashed.
**Fix:** Both renderers start `INVISIBLE` in `bindViews()`. Only become `VISIBLE` when `tracksBound = true` inside `bindVideoTracks()` on CONNECTED. Matches Meshenger pattern.

#### #67: Call UI disappeared — old collectors survived onNewIntent
**Files:** `CallActivity.kt:143-207,234-265`
**Symptom:** `setupCall()` launched 5 collectors in `lifecycleScope`. When `onNewIntent` triggered a new call, old collectors persisted. Old `finishEvent` collector killed new call. UI vanished.
**Fix:** All collectors moved to dedicated `callJob` scope. `callJob?.cancel()` destroys old scope on re-entry. `finishEvent` collector checks `!finished` guard. `SupervisorJob` isolates failures (one crash doesn't kill all collectors).

#### #68: Stale SDP signals replayed from SharedFlow causing video unreliability
**File:** `CallViewModel.kt:159-163`
**Symptom:** `incomingCallSignals` with `replay=5` replayed old CALL_ANSWER/OFFER from previous calls. Only CALL_HANGUP was filtered by timestamp. Stale SDPs triggered `setRemoteDescription()` 6+ times — WebRTC corrupted.
**Fix:** Stale timestamp filter now applies to ALL signal types: `if (signal.timestamp < callStartTime) return`.

#### #69: callState collectors had redundant `runOnUiThread` causing lag
**File:** `CallActivity.kt:184-206`
**Symptom:** Coroutines already ran on `Dispatchers.Main`. `runOnUiThread` wrapper added unnecessary message queue hop → ~16ms delay per state change. Compounds during rapid ICE transitions.
**Fix:** Removed all 4 redundant `runOnUiThread` wrappers.

#### #70: Camera toggle used fragile flip-state pattern
**File:** `CallActivity.kt:300-321`, `CallViewModel.kt:150-153`
**Symptom:** `enableCamera()` and `disableCamera()` both called `viewModel.toggleCamera()` — depended on Activity and ViewModel `cameraEnabled` staying in sync.
**Fix:** Added `CallViewModel.setCameraEnabled(enabled: Boolean)` — explicit, not toggle-based.

#### #71: ICE failure leaked PeerConnection + lost call history
**Files:** `WebRtcManager.kt:638-648`, `CallViewModel.kt:100-117`
**Symptom:** When ICE went FAILED/CLOSED or DISCONNECTED grace expired, state set to ENDED but `cleanupPeerConnection` never called (PeerConnection + capturer leaked). Call history never saved.
**Fix:** `cleanupPeerConnection()` now called on all terminal ICE states. `CallViewModel.observeCallSignals` auto-saves `CallHistoryEntity` on CONNECTED→ENDED transition when `connectedAt > 0`.

#### #72: `postDelayed(finish, 1500)` without guard allowed double-finish
**File:** `CallActivity.kt:236-244`
**Fix:** `finished = true` set in `updateUI(ENDED)` before `postDelayed`.

#### #73: Call layout redesigned to Meshenger-style floating controls
**File:** `activity_call.xml`
**Changes:** PIP moved bottom-right 150dp. Controls slightly larger (52dp/60dp). Incoming buttons positioned above control panel. Button backgrounds: `#33FFFFFF` (dark), `#EF4444` (red), `#4ADE80` (green) — all match Offnetic DESIGN.md palette.

#### #74: `observeCallSignals` nested launch flattened
**File:** `CallViewModel.kt:98-117`
**Symptom:** Outer `launch` nesting two inner `launch` calls — redundant. Reduced to direct `viewModelScope.launch` calls.

### UI System

#### #75: Bottom nav + QR button icons invisible on high-density screens
**Files:** `MainScreen.kt:127-163`, `ChatListScreen.kt:206-264`
**Symptom:** Canvas icons used hardcoded pixel coordinates (e.g., `Offset(2f, 5f)` on 22dp canvas). On 3x density → 66px canvas → icons drawn at 2-5px → invisible.
**Fix:** All icons rewritten with relative coordinates via `size.width` / `size.height`. Tab icons: Chats/Calls/Settings. QR button: 3×3 grid pattern with `cell = cw / 7f`.

#### #76: NavTab labels missing Syne font
**File:** `MainScreen.kt:166-172`
**Symptom:** Bottom nav label `Text` had no `fontFamily` — rendered in system font while entire app uses Syne.
**Fix:** Added `fontFamily = FontFamilySyne`.

#### #77: CallsScreen had embedded Scaffold inside MainScreen's Scaffold
**File:** `CallsScreen.kt:43-77`
**Symptom:** Double Scaffold → double `innerPadding` → system insets applied twice → content pushed off-screen.
**Fix:** Removed inner Scaffold. `CallsScreen` now uses `Box` directly.

#### #78: IdentityGenerationScreen mixed dp/px in Canvas
**File:** `IdentityGenerationScreen.kt:108`
**Symptom:** `radius = 58.dp.toPx()` inside 140dp Canvas. `.toPx()` uses display metrics — wrong on some densities.
**Fix:** Changed to `size.minDimension * 0.41f` (density-safe relative sizing).

#### #79: AccountScreen green misuse on non-status text
**File:** `AccountScreen.kt:242,275`
**Symptom:** "PROFILE" section header and "Save" button used `#4ADE80` (green). DESIGN.md: green only for online/nearby status.
**Fix:** Changed to `Color(0x40FFFFFF)` (muted white) and `Color.White` respectively.

#### #80: NearbyPeersScreen non-keyed LazyColumn
**File:** `NearbyPeersScreen.kt:128`
**Fix:** Changed `items(peers.size)` to `items(peers, key = { it.endpointId })`.

#### #81: SplashScreen logo ring proportions off vs design spec
**File:** `SplashScreen.kt:106-108`
**Symptom:** Logo rings undersized: inner `0.22` vs spec `0.25`, middle `0.50` vs `0.5625`, outer `0.78` vs `0.875`.
**Fix:** Corrected all three to match `Offnetic_Onboarding.jsx` SVG spec.

### Memory Leaks

#### #82: Voice note MediaPlayer leak on scroll (see #54)
#### #83: QR CameraX leak on screen leave (see #57)
#### #84: ICE failure PeerConnection leak (see #71)
#### #85: Stale collectors on `onNewIntent` (see #67)

---

## Known Issues Resolved

| # | Issue | Status |
|---|---|---|
| Video black screen | Fixed: opaque layout background removed (#65), renderers INVISIBLE until EGL ready (#66) | ✅ |
| Chat naming + reply | Fixed: INITIAL_IDENTITY always sent + retry (#58, #59) | ✅ |
| Bidirectional calls | Fixed: incoming routing restored (#64), stale SDP filter (#68) | ✅ |
| File transfers broken | Fixed: native NCAPI file API (#60), metadata (#61), extension rename (#62) | ✅ |
| Call UI disappears | Fixed: collector scope cancellation (#67) | ✅ |
| Video call unreliable | Fixed: stale SDP filter (#68), ICE cleanup (#71), EGL init (#66) | ✅ |
| New call killed by stale ENDED state | Fixed: `getCallState` reset + `finishRunnable` tracking + `finished=false` (#86) | ✅ |
| Wi-Fi Direct P2P calls fail | Fixed: `disableNetworkMonitor=true` discovers p2p0, manual group creation required (#87) | ✅ |
| Manual `WifiP2pManager` interop | WON'T FIX — hardware incompatibility. P2P group created via system Wi-Fi settings instead. | ⚠️ |
| Calls offline (no shared Wi-Fi) | Requires manual P2P group creation before calling. No auto-P2P setup. | ⚠️ |

---

## Architecture Summary (Current)

```
ChatScreen (Compose)
  ├── Text → sendEncrypted → Signal → Payload.fromBytes → Bluetooth
  ├── Voice note → toggleVoiceRecording → Signal → Payload.fromBytes → Bluetooth
  ├── File/Image/Video → NcapManager.sendFile → Payload.fromFile → Wi-Fi Direct
  └── Phone icon → Intent → CallActivity (XML RelativeLayout, Meshenger-style)
        ├── SurfaceViewRenderer (fullscreen, INVISIBLE until tracks bound)
        ├── SurfaceViewRenderer (PIP bottom-right 150dp, setZOrderMediaOverlay)
        ├── TextView (peer name, duration, status)
        └── ImageButton controls (mic, camera, flip, hangup, speaker) floating

CallActivity
  ├── callJob scope (SupervisorJob) — all collectors cancelled on new call
  ├── finishRunnable tracking — cancels old postDelayed on state transitions, prevents double-finish
  ├── tracksBound guard — bindVideoTracks called once, renderers visible after
  └── ICE failure → cleanupPeerConnection + auto-save call history

WebRtcManager
  ├── disableNetworkMonitor = true — discovers p2p0 interface (manual P2P groups)
  ├── injectP2pCandidate() — fallback synthetic P2P host candidate injection
  ├── getCallState() resets stale ENDED → IDLE on old flow reuse
  └── IceCandidate SDP fully logged for debugging

NcapManagerImpl
  ├── INITIAL_IDENTITY always sent (with profile fallback + retry buffer)
  ├── File transfer: FILE_TRANSFER_REQUEST metadata → pendingFileMetas queue
  ├── Incoming file: renamed with original extension for correct intent
  └── reconnectToContact: starts discovery if peer not found

OffneticNavHost
  └── IncomingCallEntryPoint + LaunchedEffect → routes CALL_OFFER to CallActivity

File open: FileProvider → content URI with extension → setDataAndType → ACTION_VIEW
```

---

## Round 10 — Wi-Fi Direct Calling + QR Pairing (Major Rewrite)

> **Goal: reliable peer-to-peer calls over Wi-Fi Direct, and QR-scanned contacts connecting immediately.**

---

### Part A — Wi-Fi Direct Call Infrastructure

These fixes implement the blueprint from `implementation_plan.md`.

#### A1: Deterministic initiator/responder role selection
**File:** `WifiP2pHandler.kt:107`
**Cause:** Both devices previously tried to act as initiator (or both waited), causing a deadlock where neither side connected.
**Fix:** `PublicKeyNormalizer.normalize(myPublicKey) < PublicKeyNormalizer.normalize(peerPublicKey)` — lexical comparison on normalized keys. Lower key = initiator, higher key = responder. Both sides compute the same result independently — zero network round-trip needed.

#### A2: MAC address discovery retry loop
**File:** `WifiP2pHandler.kt:196-219`
**Cause:** Previous code called `requestPeers()` ONCE after waiting for an unreliable `WIFI_P2P_PEERS_CHANGED` broadcast. If the peer's MAC wasn't in the list yet, it failed immediately with no retry.
**Fix:** `discoverPeerWithRetry(maxAttempts = 10, intervalMs = 2000L)` — polls `requestPeers()` up to 10 times at 2s intervals. Filters out the fake MAC `02:00:00:00:00:00`. Returns first valid MAC found.

#### A3: API 28 (Android 9) fallback
**File:** `WifiP2pHandler.kt:147-160`
**Cause:** `WifiP2pManager.createGroup(WifiP2pConfig, ActionListener)` was introduced in API 29. On API 28 devices, this call would crash or be ignored.
**Fix:** `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)` → `createGroup()` with retry; `else` → `connect()` fallback (available since API 16). WalkieTalkie reference also uses `connect()` exclusively.

#### A4: Peer IP derivation for Group Owner
**File:** `WifiP2pHandler.kt:184-194`
**Cause:** When the initiator became Group Owner (GO), `info.groupOwnerAddress` returned the GO's OWN IP — not the peer's. The old code only called `callback?.onP2pReady()` when NOT the GO, so the initiator never got the peer's IP.
**Fix:** `derivePeerIp(info)` — if not GO: returns `groupOwnerAddress` (that's the peer's IP). If GO: derives peer IP from GO's own IP (typically `192.168.49.x+1`). Callback always fires regardless of GO status.

#### A5: WebRTC Ice restart on P2P link up
**File:** `WebRtcManager.kt:56-69`
**Cause:** The caller's initial SDP offer was created BEFORE the Wi-Fi Direct interface was available. ICE gathering completed without discovering the P2P path.
**Fix:** `init` block registers `WifiP2pCallback` with `WifiP2pHandler`. When P2P link establishes, callback fires → `pc.restartIce()` triggers a fresh ICE gathering round that discovers the `192.168.49.x` interface. New candidates with P2P IPs trickle to the peer.

#### A6: Field trial for Wi-Fi Direct interface visibility
**File:** `WebRtcManager.kt:149`
**Cause:** `setFieldTrials("")` — empty string. WebRTC's network monitor did not bind to the Wi-Fi Direct network interface.
**Fix:** `setFieldTrials("WebRTC-IncludeWifiDirect/Enabled/")` — tells WebRTC to scan all network interfaces including `p2p-p2p0-*`.

#### A7: Callee waits for P2P before creating answer
**File:** `WebRtcManager.kt:304-306`
**Cause:** `acceptCall()` created the `PeerConnection` and SDP answer immediately, before the Wi-Fi Direct link was established. The answer's ICE candidates lacked the P2P path.
**Fix:** `wifiP2pHandler.awaitP2pConnection(peerPublicKey, timeoutMs = 25_000L)` called BEFORE `createPeerConnection()`. When the callee creates its PC, the P2P interface is already up — ICE candidates include the Wi-Fi Direct IP.

#### A8: getMyPublicKey interface + implementation
**Files:** `NcapManager.kt:49`, `NcapManagerImpl.kt:97-99`
**Cause:** No way to retrieve the local identity public key. Required for deterministic role selection.
**Fix:** Added `suspend fun getMyPublicKey(): String` to interface and implementation (`identityDao.getIdentity()?.publicKey`).

#### A9: enterP2pDiscoveryMode param wiring
**Files:** `WebRtcManager.kt:565-571`, `CallViewModel.kt:102`
**Cause:** `enterP2pDiscoveryMode()` had no parameter — couldn't pass the peer's public key for role selection.
**Fix:** Now accepts `peerPublicKey: String`. Gets `myPublicKey` from identity, passes both to `wifiP2pHandler.startP2pCall()`. Ice disconnect retry path also updated to pass `myPublicKey`.

#### A10: Scope declaration order fix
**File:** `WebRtcManager.kt:53-54,69`
**Cause:** `init` block referenced `scope` before the `private val scope` property initializer ran (Kotlin initializers execute in declaration order).
**Fix:** Moved `scope` and `factoryScope` above the `init` block.

---

### Part B — QR Pairing: Key Normalization + Contact Discovery

These fixes address the root cause of "scan QR but peer shows offline / can't send messages."

#### B1: Public key string mismatch between QR and NCAPI
**File:** NEW `util/PublicKeyNormalizer.kt`
**Cause:** Identity public keys are generated using standard Base64 (`Base64.NO_WRAP`), which produces `+`, `/`, and `=` characters. When used as NCAPI endpoint names, these characters may be sanitized, creating a different string. A QR-scanned contact stored with the original key would never match the NCAPI-discovered key.
**Fix:** New utility class with three methods:
- `encode(bytes)` — URL-safe Base64 (`Base64.NO_WRAP or Base64.URL_SAFE`) — no `+/=` characters
- `normalize(key)` — strips trailing `=`, converts `-_` → `+/`. Canonical form for comparison.
- `matches(a, b)` — compares normalized forms.

#### B2: Identity generation uses URL-safe encoding
**File:** `IdentityKeyManagerImpl.kt:56,69`
**Cause:** Identity public key encoded with standard Base64 — produced `+/=` characters.
**Fix:** Changed to `PublicKeyNormalizer.encode(serializedPublic)` — URL-safe encoding. No special characters that NCAPI could mangle.

#### B3: Identity decode missing URL_SAFE flag
**File:** `IdentityKeyManagerImpl.kt:96`
**Cause:** Encode used `URL_SAFE` flag but decode did not. For ~75% of generated identities, `android.util.Base64.decode()` would silently skip `-` and `_` characters, producing a corrupt public key. All crypto would fail.
**Fix:** Decode now uses `Base64.NO_WRAP or Base64.URL_SAFE` — matches the encode flags.

#### B4: Key normalization at all NCAPI entry points
**File:** `NcapManagerImpl.kt` — 6 call sites
**Cause:** Incoming keys from NCAPI (advertising name, connection info, senderPublicKey, etc.) were not normalized before contact lookup. QR-scanned contacts stored with standard Base64 would never match URL-safe NCAPI keys.
**Fix:** `PublicKeyNormalizer.normalize()` applied at:
- `discoveryCallback.onEndpointFound` (line 303) — NCAPI discovered endpoint name
- `onConnectionInitiated` (line 147) — incoming connection name
- INITIAL_IDENTITY handler (lines 701,703) — both `pk` from JSON and `senderPublicKey`
- `handleSignalMessage` chatId (line 878) — incoming message sender
- Auto-contact creation (line 934) — uses normalized `chatId`
- Self-detection in `handleEndpointFound` (line 1088) — `myKey` normalized

#### B5: QR-scanned contacts use normalized key
**File:** `QrScannerViewModel.kt:43,56-57`
**Cause:** Contact and system message stored with raw key from QR data (URL-safe format). NCAPI discovery normalizes to standard format — keys never matched.
**Fix:** `val normalizedKey = PublicKeyNormalizer.normalize(data.publicKey)` before storing contact and message. All three key sources (QR, NCAPI, DB) now converge on the same canonical form.

#### B6: Non-contact peers stored in endpointPeers
**File:** `NcapManagerImpl.kt:1051-1059`
**Cause:** `handleEndpointFound` silently returned when no contact existed for the discovered public key. The endpoint was never stored anywhere — `reconnectToContact()` could never find it later when the contact was created.
**Fix:** Non-contact peers are now stored in `endpointPeers` with `isContact = false`. The proximity ping logic still only runs for known contacts, but the endpoint ID is preserved for later reconnection.

#### B7: reconnectToContact with retry loop
**File:** `NcapManagerImpl.kt:466-493`
**Cause:** `reconnectToContact` checked `endpointPeers` once — if the peer wasn't found, it called `startDiscovery()` (no-op if already running) and returned. After QR scan, the peer was never connected.
**Fix:** `findOrAwaitEndpoint(publicKey)` retry loop — polls `endpointPeers`, if not found calls `forceRestart(name)` to restart advertising + discovery, waits 2s, retries. Up to 5 iterations (10 seconds total). Combined with B6 (storing non-contacts), the QR scan path now reliably finds and connects to the peer.

#### B8: Role comparison uses normalized keys
**File:** `WifiP2pHandler.kt:108`
**Cause:** `myPublicKey < peerPublicKey` compared URL-safe (identity) with normalized (contact DB) formats. Inconsistent character ordering could cause both devices to compute different initiator/responder results.
**Fix:** `PublicKeyNormalizer.normalize(myPublicKey) < PublicKeyNormalizer.normalize(peerPublicKey)` — both sides normalize to standard format before comparing.

#### B9: Self-detection uses normalized keys
**File:** `NcapManagerImpl.kt:1088`
**Cause:** `myKey == publicKey` compared URL-safe identity key with normalized NCAPI key. Would never match for the same identity.
**Fix:** `myKey` now normalized: `PublicKeyNormalizer.normalize(myIdentity.publicKey)`.

---

### Reference: Meshenger Comparison

Meshenger (github.com/meshenger-app/meshenger-android) uses a fundamentally different approach:

| | Meshenger | Offnetic (current) |
|---|---|---|
| **Discovery** | No discovery layer — QR contains IP addresses | NCAPI (Bluetooth/BLE) discovery + Wi-Fi Direct |
| **QR content** | `{name, publicKey, addresses[]}` | `{pk, dn}` — public key only |
| **Connection** | Direct TCP to known IP:port | NCAPI connection + PRE_KEY_BUNDLE session |
| **Reconnect** | Pinger tries all known addresses immediately | `findOrAwaitEndpoint()` retry loop with forceRestart |

Meshenger avoids discovery timing issues entirely because the QR code IS the discovery. Offnetic's approach uses NCAPI for transport flexibility (Bluetooth + Wi-Fi Direct) but requires the retry loop to bridge the async gap between contact creation and peer discovery.

---

## Round 10 Post-Mortem — What Worked, What Didn't

### Resolved

#### #86: Stale CallState flow kills new calls before they start
**Files:** `CallActivity.kt:233-280`, `WebRtcManager.kt:173-183`
**Symptom:** `WebRtcManager.getCallState(peer)` uses `getOrPut` which returns the same `MutableStateFlow` from a previous call. First emission of new CallActivity's collector was `ENDED` → `finished=true` → `postDelayed(finish, 1500)`. Later when `acceptCall()` failed and set ENDED again, `finished` was already `true` from the stale ENDED's postDelayed, allowing both postDelayed callbacks to fire and kill the call.
**Fix (3-part):**
1. **`getCallState` reset** — `WebRtcManager.kt:173-183`: when the existing flow has `phase == ENDED`, resets it to `CallState()` (IDLE) before returning, so new CallViewModel starts clean.
2. **`finishRunnable` tracking** — `CallActivity.kt:296,275-283,236-248`: stores the `Runnable` posted to `fullscreenRenderer`. On every non-ENDED phase (IDLE/OUTGOING/CONNECTING/INCOMING), cancels any pending `finishRunnable` before setting `finished = false`. On ENDED, cancels old runnable before posting new one — no two postDelayed callbacks can coexist.
3. **ICE candidate logging** — `WebRtcManager.kt:782`: logs full candidate SDP string (shows IPs like `192.168.49.x`).

### Resolved — Wi-Fi Direct Calling Works (Manual Group Setup)

#### #87: WebRTC `disableNetworkMonitor = false` hides P2P interface
**File:** `WebRtcManager.kt:139`
**Symptom:** WebRTC ICE only generated `127.0.0.1` and `::1` loopback candidates. The `p2p0` interface with IPs `192.168.49.1` and `192.168.49.203` was invisible — Android's `ConnectivityManager` doesn't report manually-created P2P groups. ICE never reached CONNECTED.
**Fix:** `disableNetworkMonitor = true` + `networkIgnoreMask = 0`. WebRTC falls back to `java.net.NetworkInterface.getNetworkInterfaces()` which enumerates ALL interfaces including `p2p0`. Combined with the existing `setFieldTrials("WebRTC-IncludeWifiDirect/Enabled/")`, WebRTC now discovers and generates host candidates for the P2P IPs.

#### #88: P2P candidate injection fallback
**File:** `WebRtcManager.kt:930-962`
**Symptom:** If `disableNetworkMonitor = true` still doesn't discover p2p0 on some devices, fallback needed.
**Fix:** `injectP2pCandidate()` reads `p2p0` IP from `NetworkInterface`, extracts `ice-ufrag` from local SDP, builds a synthetic host candidate SDP string (`candidate:... 192.168.49.x typ host`), and sends it to the peer via NCAPI. Called from both `startCall` (after CALL_OFFER sent) and `acceptCall` (after CALL_ANSWER sent).

### How Wi-Fi Direct Now Works
1. **User creates P2P group manually** via Android Wi-Fi → Wi-Fi Direct settings (one-time)
2. Both devices get IPs on `192.168.49.0/24` via the `p2p0` interface (verified with `ip addr`)
3. `disableNetworkMonitor = true` makes WebRTC discover `p2p0`
4. WebRTC generates host candidates with `192.168.49.x` IPs
5. ICE negotiates over the P2P link → CONNECTED
6. Video + audio work over the P2P link with full bandwidth

No `WifiP2pManager` API calls needed. No `createGroup()`/`connect()`. The app simply reads the existing network interface. `WifiP2pHandler.kt` remains preserved but uncalled.

---

## Hard Log Tags Reference

| Tag | File | Purpose |
|-----|------|---------|
| `NcapConn` | `NcapManagerImpl.kt` | FOUND peer, requestConnection, acceptConnection, onConnectionResult, PRE_KEY_BUNDLE, INITIAL_IDENTITY |
| `CallNav` | `OffneticNavHost.kt` | Incoming call → launching CallActivity |
| `CallVM` | `CallViewModel.kt` | handleCallSignal, acceptCall, stale signal skip (all types) |
| `WebRTC_ICE` | `WebRtcManager.kt` | ICE state, gathering, onIceCandidate, onSdpReceived, onAddTrack, camera, attachVideoTracks |
| `VideoUI` | `CallActivity.kt` | renderer init (initSurface), tracks bound |
| `ChatVM` | `ChatViewModel.kt` | sendEncrypted, endpoint selection, file/voice send, retryUnsentMessages |
| `P2P_CALL` | `WifiP2pHandler.kt` | discovery retry, negotiation, P2P connected/disconnected, teardown |
