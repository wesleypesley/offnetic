# Far-Transport Plan — Calls (WebRTC) + File Sharing (Blossom)

**The single combined plan.** Merges the two standalone plans
(`webrtc calls (relay + stun) plan.md` and `blossom file sharing plan v4.md`) into one.
Those two can be deleted once this is adopted. Code-verified against the actual source.

Two **far** (internet) features over one shared Nostr-relay signaling channel:

```
Calls:  nearby → NCAPI signaling, trickle ICE (existing) | far → relay signaling + STUN, non-trickle ICE | offline → "Not reachable"
Files:  nearby → NCAPI sendFile (existing)               | far → encrypted blob on a Blossom server       | offline → toast, stays SAVED
```

- **Calls** stay on **WebRTC** — real-time, low-latency, direct P2P media justify the complexity. Only the SDP offer/answer/hangup travel via the relay.
- **Files** use **Blossom store-and-forward** — the recipient can be offline; no `PeerConnection`, no native leaks. Encrypt → `PUT` ciphertext to public Blossom servers → DM `{hash,key,servers}` over the relay → peer `GET`s + decrypts later.

---

## 0. Architecture decisions

- **Why not WebRTC for files?** A DataChannel is a *live* connection: bytes only flow after ICE→DTLS→OPEN with **both peers online at the same instant** — useless when a messenger recipient is offline, and it carries native `PeerConnection`/chunking/leak hazards.
- **Why Blossom for files?** Reuses OkHttp + the gift-wrap relay + `NostrEventSigner` + NIP-44; nostr-key auth (no accounts); content-blind storage.
- **Why not media CDNs (nostr.build/blossom.band)?** Their free tier is **media-only and transcodes** uploads → it rejects/mangles encrypted `octet-stream`. We use **general-purpose any-type (`*`) Blossom servers** via `PUT /upload` (BUD-02 "MUST NOT modify"), never `/media`.
- **Files are encrypted by default** — Blossom blobs are public-by-hash; only the hash+key travel, inside the encrypted gift-wrap.
- **File security property:** confidentiality from client-side encryption; **integrity** from the sha256 being delivered inside a **signed, NIP-44-encrypted** gift-wrap and **re-verified on download**. A malicious/buggy storage server can't substitute or tamper with the file (wrong bytes → hash mismatch → rejected). The server is a dumb, untrusted bit-bucket.

---

## 1. Verified facts (source + specs)

**Codebase (real symbols):**
- `WebRtcManager(context, ncapManager, wifiP2pHandler)` — current ctor; `scope` is `Dispatchers.Main`. `acceptCall` reads `pendingSdpOffers`/`pendingOfferEndpoints`; `pendingIncomingOffers` (a `Pair`, companion) is written by `NcapManagerImpl:900` and drained by `CallViewModel.observeCallSignals()` (`:114`).
- `createPeerConnection` today: no STUN + `GATHER_CONTINUALLY`; observer has `onIceGatheringChange`.
- `RelayControl.typeOf(rumor)` reads the `"t"` tag; `RelayInboxHandler.handleGiftWrap` `when(type){ … else -> handleMessage }` (`:51-57`) — new types go **before** `else`. Dedup via `messageDao.getByMessageUuid`. `Rumor.createdAt` is **unix seconds**.
- `RelayControlSender` (`NostrIdentityManager + RelayPool`) + `GiftWrap.wrap(...)` + `decode(npub)` — `sendCallSignal`/`sendFileBlossom` mirror `sendConnectionRequest`.
- Relay pump: `NcapForegroundService:175` `relayPool.events.collect { handleGiftWrap }` on **`Dispatchers.IO`**, **sequential**.
- `IncomingCallService` (`ACTION_START_RINGING` + `EXTRA_PEER_PUBLIC_KEY`) → full-screen intent → `CallActivity(EXTRA_IS_INCOMING=true)`; `CallActivity.acceptCall()` already calls `stopIncomingRinging()`. `CallActivity` drives all UI off `callViewModel.callState` (= `webRtcManager.getCallState(peer)`) — `ENDED`/`CONNECTED` is enough.
- `CallViewModel`: `wifiManager` used **only** in the two gate blocks; `webRtcManager` is `@Assisted`.
- `ChatViewModel.sendFile(uri)` (`:182-236`): `copyToLocalFile`, 100 MB cap, NCAPI branch, the `else` toast we replace; `contactDao/messageDao/networkMonitor/reachability` already injected.
- `Message` (`sessionId/chatId/senderPublicKey/content/type/timestamp/deliveryState/isRead/attachmentPath/attachmentType`), `TYPE_IMAGE/VIDEO/FILE`, `MessageDeliveryState.{SAVED,SENT_RELAY,DELIVERED}`, `MessageDao.{getById,getByMessageUuid,insert,update}`, `ContactDao.{getByPublicKey,getByNostrPublicKey}`, `CallHistoryEntity.{TYPE_VIDEO,DIRECTION_MISSED}` all exist.
- `file_paths.xml` exposes `filesDir`+`cacheDir` → received files in `filesDir` open crash-free (`ChatScreen.kt:800-818`, guarded). Media parity: `NcapManagerImpl:1194-1212`.
- **DI is acyclic** (verified): `RelayPool` factory-built; `RelayControlSender` deps minimal; nothing `WebRtcManager`/`BlossomFileService` need depends on `RelayInboxHandler`. OkHttp + `Secp256k1`/`NostrEventSigner`/`NostrJson`/`Bech32` already present.

**Blossom (specs):** `PUT /upload` raw body, `Authorization: Nostr <base64url(kind-24242)>`, optional `X-SHA-256`; server hashes exact bytes, returns `{url,sha256,size,type}`. Token tags: `["t","upload"]`, NIP-40 `["expiration",…]`, required `["x",<sha256>]`, optional `["server",<domain>]`. `GET` auth usually optional. `413` over cap · `402` payment · `401/403` auth · `415` type. `PUT /mirror` (BUD-04) = server-to-server copy. GET-by-hash (BUD-01): `<server>/<sha256>`.

**Verified general-purpose servers (2026-06, any-type `*`, ~2 GB, free):** `https://nostr.download` (route96), `https://cdn.hzrd149.com` (author's), `https://blossom.dreamith.to`. **Excluded:** nostr.build/blossom.band (media-only free, transcode). *Must still pass Phase 0 — some community servers whitelist.*

---

## 2. Dependency / DI changes

- **`WebRtcManager` ctor + `WebRtcModule`** (calls) gain: `contactDao`, `relayControlSender`, `networkMonitor`. Wi-Fi via `context` (`wifiEnabled()`), no `WifiManager` inject.
- **`RelayInboxHandler`** `@Inject` ctor gains: `webRtcManager`, `callHistoryDao` (calls), `blossomFileService` (files).
- **`ChatViewModel`** `@Inject` ctor gains: `blossomFileService` (files).
- **`CallViewModel`**: **remove** unused `wifiManager`.
- **New (files):** `BlossomClient`, `FileCrypto`, `BlossomFileService` + a DI `@Provides` for a dedicated `@Blossom OkHttpClient`.
- **Room migration:** UNIQUE index on `Message.messageUuid` (files dedup backstop, F11).

No Hilt cycle (verified §1).

---

## 3. Shared signaling layer

```kotlin
// RelayControl.kt
const val TYPE_CALL_OFFER  = "call_offer";  const val TYPE_CALL_ANSWER = "call_answer"
const val TYPE_ICE_CANDIDATE = "ice_candidate"; const val TYPE_CALL_HANGUP = "call_hangup"
const val TYPE_FILE_BLOSSOM = "file_blossom"

// RelayControlSender.kt — both mirror sendConnectionRequest
suspend fun sendCallSignal(npub, type, payload): Boolean  // tags=[[TAG_TYPE,type]]
suspend fun sendFileBlossom(npub, payloadJson): Boolean    // tags=[[TAG_TYPE,TYPE_FILE_BLOSSOM],["u",UUID()]]
```
```kotlin
// RelayInboxHandler — routes BEFORE the else
TYPE_CALL_OFFER -> handleCallOffer(senderNpub, rumor)                       // 45s TTL → cacheRelayCallOffer
TYPE_CALL_ANSWER, TYPE_ICE_CANDIDATE, TYPE_CALL_HANGUP ->
    contactDao.getByNostrPublicKey(senderNpub)?.let { webRtcManager.onRelayCallSignal(it.publicKey, type, rumor.content) }
TYPE_FILE_BLOSSOM -> handleFileBlossom(senderNpub, rumor)                   // dedup → offload download
```
`handleCallOffer`: contact lookup; `if (now - rumor.createdAt*1000 > 45_000)` → log missed call (`callHistoryDao`) + drop; else `webRtcManager.cacheRelayCallOffer(c.publicKey, rumor.content)`.

---

# PART A — CALLS (WebRTC + relay)

### A1 — state
```kotlin
enum class CallTransport { NCAPI, RELAY }
private val callTransports        = ConcurrentHashMap<String, CallTransport>()
private val iceGatheringDeferreds = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
private fun wifiEnabled() = (context.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
```
`pendingIncomingOffers` stays a `Pair` — relay never uses it (C16).

### A2 — createPeerConnection(peer, isVideo, transport) + onIceGatheringChange
RELAY → STUN (`stun.l.google.com:19302` ×2) + `GATHER_ONCE`; NCAPI → no STUN + `GATHER_CONTINUALLY`. Update the two existing call sites. `onIceGatheringChange`: `if (COMPLETE) iceGatheringDeferreds.remove(peer)?.complete(Unit)`; register the deferred **before** createOffer/createAnswer.

### A3 — startCall (transport selection + non-trickle relay)
```kotlin
val transport = if (ncapManager.getConnectedEndpointIds(peer).isNotEmpty()) NCAPI else RELAY
callTransports[peer] = transport
if (transport==NCAPI && !wifiEnabled()) { end("Wi-Fi required"); return }                 // C4
val contact = if (transport==RELAY) contactDao.getByPublicKey(peer) else null
if (transport==RELAY) { if (contact?.nostrPublicKey==null){end("Peer not reachable");return}
                        if (!networkMonitor.isOnline.value){end("Internet required");return} }
val pc = createPeerConnection(peer,isVideo,transport) ?: run{end("…");return}
if (transport==RELAY) iceGatheringDeferreds[peer]=CompletableDeferred()
// createOffer→setLocalDescription→onSetSuccess:
//   NCAPI: existing (ncap send + injectP2pCandidate + applyCachedAnswer)
//   RELAY: scope.launch { withTimeoutOrNull(8_000L){ iceGatheringDeferreds[peer]?.await() }            // C3
//          if (!sendCallSignal(contact.nostrPublicKey!!, TYPE_CALL_OFFER, sdpJson(pc.localDescription))) end("Couldn't reach relay") }  // C9
// AFTER the when (both): state CONNECTING; launchCallTimeout(peer)
```

### A4 — acceptCall (keep the existing pendingSdpOffers path)
```kotlin
val isVideo = state.value.isVideo                                       // C15
val transport = callTransports[peer] ?: NCAPI
val endpointId = if (transport==RELAY) "" else (pendingOfferEndpoints.remove(peer) ?: getConnectedEndpointIds(peer).firstOrNull())
if (transport==NCAPI && endpointId==null) { end("Peer disconnected"); return }   // C6 bypassed for RELAY
if (transport==NCAPI && !wifiEnabled())   { end("Wi-Fi required");   return }
val pc = createPeerConnection(peer,isVideo,transport) ?: run{end("…");return}
if (transport==RELAY) iceGatheringDeferreds[peer]=CompletableDeferred()
val offer = pendingSdpOffers.remove(peer) ?: run{end("No SDP offer");cleanupPeerConnection(peer);return}
pc.setRemoteDescription(/*onSetSuccess*/ → createAndSendAnswer(pc,peer,endpointId ?: "",isVideo,constraints,state))
```

### A5 — createAndSendAnswer / sendIceCandidateToPeer / hangup
- `createAndSendAnswer` branches on `callTransports[peer]`: NCAPI = existing; RELAY = `withTimeoutOrNull(8_000L){await}` then `sendCallSignal(npub, TYPE_CALL_ANSWER, sdpJson(pc.localDescription))` (end on `false`); then `state CONNECTING`.
- `sendIceCandidateToPeer`: `if (callTransports[peer]==RELAY) return` (non-trickle; bundled in SDP).
- `hangup`: NCAPI existing; RELAY `sendCallSignal(npub, TYPE_CALL_HANGUP, "")`. Keep DataChannel best-effort + cleanup.

### A6 — cacheRelayCallOffer (reuse the ring) + onRelayCallSignal (single intake)
```kotlin
fun cacheRelayCallOffer(peer, sdpJson) = scope.launch {                              // C8 IO→Main
    if (peerConnections.containsKey(peer) || pendingSdpOffers.containsKey(peer)) return@launch   // C7
    callTransports[peer] = RELAY
    getCallState(peer).update { it.copy(isVideo = true) }
    onSdpReceived(peer, sdpJson, "")                                                 // caches pendingSdpOffers + INCOMING
    if (!ncapManager.isCallActive) context.startForegroundService(                   // C5
        Intent(context, IncomingCallService::class.java).setAction(ACTION_START_RINGING).putExtra(EXTRA_PEER_PUBLIC_KEY, peer))
}
fun onRelayCallSignal(peer, type, payload) = scope.launch {                          // ONLY intake; no VM collector (C2)
    when (type) {
        TYPE_CALL_ANSWER   -> onSdpReceived(peer, payload, "")                       // C11 safe if PC gone
        TYPE_ICE_CANDIDATE -> onIceCandidateReceived(peer, payload)                  // defensive
        TYPE_CALL_HANGUP   -> { stopRingingIfAny(peer); onCallHangup(peer) }         // state→ENDED finishes CallActivity
    }
}
```

### A7 — CallViewModel (minimal)
Remove **only** the two Wi-Fi gate blocks (`:74-80`, `:93-99`) + the unused `wifiManager`. No relay collector, no new flows (C2/C4).

### A8 — cleanup
`cleanupPeerConnection`/`destroy`: add `callTransports.remove(peer)` + `iceGatheringDeferreds.remove(peer)`; existing code already `close()`+`dispose()` native objects on every terminal path (C14).

---

# PART B — FILE SHARING (Blossom)

### B1 — new components
```kotlin
@Singleton class BlossomClient(nostrIdentityManager, @Blossom okHttpClient /* own timeouts */) {
    suspend fun upload(ciphertext: File, sha256Hex: String): List<String>   // 1 PUT + BUD-04 mirror; returns servers holding blob
    suspend fun download(servers: List<String>, sha256Hex: String): File?   // tries <server>/<sha256>; streaming verify; partial cleanup
    private fun uploadToken(sha256Hex, domain): String                       // kind-24242, base64url
    companion object { val DEFAULT_SERVERS = listOf("https://nostr.download","https://cdn.hzrd149.com","https://blossom.dreamith.to") }
}                                                                            // PUT /upload (BUD-02) — never /media
object FileCrypto {                                                          // AES-256-GCM, streaming
    data class Sealed(val ciphertext: File, val keyB64: String, val sha256Hex: String)
    fun encryptToTemp(plain: File, cacheDir: File): Sealed                   // fresh 256-bit key + 96-bit nonce, prepended
    fun decryptToTemp(ciphertext: File, keyB64: String, outDir: File, name: String): File  // throws AEADBadTagException on bad key
}
@Singleton class BlossomFileService(blossomClient, fileCrypto, relayControlSender, contactDao, messageDao, messageNotificationManager, @ApplicationContext context) {
    suspend fun sendFile(npub, plain: File, mime): Boolean                   // encrypt → upload → DM {hash,key,servers}
    suspend fun receiveFile(servers: List<String>, sha256, keyB64, name, expectedSize): File?  // download → sanitize → decrypt
}
```
- **Token (F17 clock skew):** `created_at = now-30`, `expiration = now+300`, tags `[["t","upload"],["expiration",now+300],["x",sha256],["server",domain]]` → `NostrJson.eventToJson` → `Base64.urlEncoder.withoutPadding`. Scoped to server+hash (S5).
- **Streaming (F8):** GCM stream ↔ temp file ↔ OkHttp file `RequestBody`; download → temp → `DigestInputStream` → stream-decrypt. Never whole-file in RAM. Dedicated `@Blossom` OkHttp client with connect/read/call timeouts (F14).
- **Key/nonce (F19):** fresh random 256-bit key + 96-bit nonce per file; one GCM stream = one nonce; never reuse `(key,nonce)`.
- Payload JSON (encrypted in the gift-wrap): `{sha256, key, file_name, file_size, mime_type, servers:[...]}`.

### B2 — verified servers + rules
Any-type (`*`) servers only (verified), **`PUT /upload`** never `/media`, upload once + mirror, receiver fetches `<server>/<sha256>` and verifies. Phase 0 round-trips each.

### B3 — sender: ChatViewModel.sendFile() INTERNET_RELAY branch
```kotlin
} else if (reachability.value == ChatReachability.INTERNET_RELAY) {
    if (file.length() == 0L) { _toastMessage.emit("Empty file"); return@launch }              // 0-byte
    val npub = contactDao.getByPublicKey(contactPublicKey)?.nostrPublicKey
        ?: run { _toastMessage.emit("${_contactName.value} not reachable — file saved"); return@launch }
    if (blossomFileService.sendFile(npub, file, mimeType)) {                                   // encrypt→upload(+mirror)→DM
        val cur = messageDao.getById(messageId)
        if (cur?.type != Message.TYPE_CANCELLED) messageDao.update(entity.copy(id=messageId, deliveryState=SENT_RELAY))
    } else _toastMessage.emit("Upload failed (no server reachable, or oversize) — saved")      // stays SAVED, retryable (F18)
}
```
`sendFile` returns `true` **only if the gift-wrap published**; keep the plaintext local copy; delete the ciphertext temp.

### B4 — receiver: RelayInboxHandler.handleFileBlossom (offloaded + race-safe)
```kotlin
// owns: inFlightFiles = ConcurrentHashMap.newKeySet<String>();  fileScope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
private suspend fun handleFileBlossom(senderNpub: String, rumor: Rumor) {
    val uuid = rumor.tags.firstOrNull{ it.size>=2 && it[0]=="u" }?.get(1) ?: return
    if (messageDao.getByMessageUuid(uuid) != null) return                       // F3
    if (!inFlightFiles.add(uuid)) return                                        // F11: reserve BEFORE offloading
    val c = contactDao.getByNostrPublicKey(senderNpub) ?: run { inFlightFiles.remove(uuid); return }
    val j = runCatching{ JSONObject(rumor.content) }.getOrNull() ?: run { inFlightFiles.remove(uuid); return }
    fileScope.launch {                                                          // F10: don't block the sequential pump
        try {
            if (messageDao.getByMessageUuid(uuid) != null) return@launch
            val mime = j.getString("mime_type")
            val servers = j.getJSONArray("servers").let { a -> List(a.length()){ a.getString(it) } }
            val f = blossomFileService.receiveFile(servers, j.getString("sha256"), j.getString("key"),
                        j.getString("file_name"), j.getLong("file_size"))
                ?: run { Timber.w("Blossom fetch failed ${uuid.take(8)}"); return@launch }   // F5/F6/F13
            val type = when { mime.startsWith("image/") -> TYPE_IMAGE; mime.startsWith("video/") -> TYPE_VIDEO; else -> TYPE_FILE }  // F4
            messageDao.insert(Message(messageUuid=uuid, sessionId=c.publicKey, chatId=c.publicKey,
                senderPublicKey=c.publicKey, content="File: ${j.getString("file_name")}", type=type,
                timestamp=System.currentTimeMillis(), deliveryState=DELIVERED, isRead=false,
                attachmentPath=f.absolutePath, attachmentType=type))            // messageUuid UNIQUE-indexed (F11 backstop)
            messageNotificationManager.notifyIfNeeded(c.publicKey)
        } finally { inFlightFiles.remove(uuid) }
    }
}
```
`receiveFile`: `download(servers, sha256)` (tries each, streaming-verifies) → **sanitize name** `name.replace(Regex("[\\\\/]"),"_").take(120)` (F12) → `decryptToTemp` in try/catch (F13 `AEADBadTagException`) → plaintext in `filesDir`.

### B5 — ChatScreen.kt
`:189` `richEnabled = reachability != ChatReachability.OFFLINE` — else the attach-file button stays disabled over relay and files are unreachable. (Call-button `:263/:267` `!= OFFLINE` apply too.)

---

## 4. Edge cases & guards (complete)

### Shared — S#
| # | Case | Guard |
|---|------|-------|
| S1 | New gift-wrap types fall to `else → handleMessage`. | Add `call_*`/`file_blossom` routes **before** `else`. |
| S2 | `handleGiftWrap` runs on `Dispatchers.IO`, sequentially. | WebRTC mutations hop to Main via `scope.launch`; Blossom download offloaded (F10). |
| S3 | Hilt cycle from new injections. | None (verified §1). |
| S4 | ≥4 relays + `since` cursor re-deliver one event. | Deduped by `event.id` within a run (C12); cross-restart by C10 / F3. |
| S5 | Leaked Blossom token replayed (BUD-11). | Scope token to `server`+`x`; short generous `expiration`. |

### Calls — C#
| # | Case | Guard |
|---|------|-------|
| C1 | `observeCallSignals` drains `pendingIncomingOffers` → relay accept would see "No offer". | Relay accept uses `pendingSdpOffers`, never `pendingIncomingOffers`. |
| C2 | Relay answer processed twice (inbox + VM collector). | **Single intake** via `onRelayCallSignal`; CallViewModel has no relay code. |
| C3 | ICE-gather `await` never completes → hang. | `withTimeoutOrNull(8_000L)`; `CONNECTING`+timeout run regardless. |
| C4 | Outgoing relay call killed by VM Wi-Fi gate. | Gate moved into `WebRtcManager`, NCAPI-only. |
| C5 | Incoming relay call must ring reliably. | Reuse `IncomingCallService` (`ACTION_START_RINGING`). |
| C6 | `acceptCall` null-endpoint bail kills relay. | Bypass when `transport==RELAY` (`endpointId=""`). |
| C7 | **Call glare** (mutual simultaneous). | Idempotent guard; documented (rare; retry). |
| C8 | `cacheRelayCallOffer` on the IO pump. | `scope.launch` (Main). |
| C9 | `sendCallSignal` returns 0 → 60 s stall. | On `false`, `end("Couldn't reach relay")`. |
| C10 | Stale `call_offer` replay. | 45 s TTL → missed-call, no ring. |
| C11 | `call_answer` re-delivered after hangup. | `onSdpReceived` null-PC + ANSWER → safe `return`. |
| C12 | One event across ≥4 relays. | Deduped by `event.id`. |
| C13 | ~15% STUN failure (symmetric NAT). | ICE FAILED → `ENDED` with error; TURN deferred. |
| C14 | Native `PeerConnection`/`DataChannel` leak. | `cleanupPeerConnection` `close()`+`dispose()` on every terminal path. |
| C15 | `acceptCall` dropping `isVideo` (compile). | Keep `val isVideo = state.value.isVideo`. |
| C16 | Retyping `pendingIncomingOffers` breaks `NcapManagerImpl:900`/`CallViewModel:114`. | Leave it a `Pair`. |

### Files — F#
| # | Case | Guard |
|---|------|-------|
| F1 | Server **size cap** (`413`). | 2 GB servers ≫ 100 MB cap → unlikely; skip server, toast if all fail. `HEAD /upload` can pre-check. |
| F2 | **Type rejection / transform** (`415`/transcode) corrupts ciphertext. | Any-type (`*`) servers only, via **`PUT /upload`** never `/media`; Phase 0 proves byte-identical. |
| F3 | Gift-wrap **replay**. | `u` uuid + `getByMessageUuid` (+F11). |
| F4 | Received media shows as a generic file row. | mime→`TYPE_IMAGE/VIDEO`; optional `saveToPublicStorage` gallery parity. |
| F5 | **Blob expired/GC'd** (`404/410`). | `download` → null → "ask sender to resend"; multi-server widens window. |
| F6 | **Hash mismatch** / hostile server. | Streaming sha256 verify (authenticated via signed DM) → reject, try next, else null. |
| F7 | **Payment/auth** server (`402/401/403`). | Counts as failed server → try next. |
| F8 | **Large-file memory** (≤100 MB). | Stream end-to-end; never whole-file in RAM. |
| F9 | **Metadata** to server. | Content encrypted; only hash+key in the DM; non-concern per requirements. |
| F10 | **Relay-pump blocking** — a 50 MB download in the sequential `handleGiftWrap` stalls all inbound events. | `handleFileBlossom` returns fast; download/decrypt/insert on `fileScope` (IO). |
| F11 | **Offload ↔ dedup race** — offloading reopens the dedup window → duplicate download/row. | Reserve uuid in `inFlightFiles` **before** launching; re-check; **UNIQUE index on `messageUuid`** backstop. |
| F12 | **Filename path traversal** (`../`, `/`). | Sanitize `name.replace(Regex("[\\\\/]"),"_").take(120)` before building the path. |
| F13 | **Decrypt failure** (`AEADBadTagException`). | `receiveFile` try/catch → clean temps, null, no message, no crash. |
| F14 | **Download hang / partial temp / disk full.** | Dedicated OkHttp timeouts; delete partial temp (try/finally); check free space; `ENOSPC` → cleanup. |
| F15 | **Single URL = no real fallback.** | Payload carries `servers[]`; receiver tries `<server>/<sha256>` for each. |
| F16 | **3× upload on cellular.** | One `PUT /upload` + `PUT /mirror` (BUD-04) server-to-server copy. |
| F17 | **Token clock skew / slow upload.** | `created_at = now-30`, `expiration = now+300`. |
| F18 | **Send cancelled by lifecycle** (leaving the chat cancels `viewModelScope`). | Stays `SAVED`, retryable; mark `SENT_RELAY` only after publish. Durable outbox = recommended follow-up. |
| F19 | **GCM key/nonce reuse.** | Fresh 256-bit key + 96-bit nonce per file; never reuse `(key,nonce)`. |
| F20 | **SSRF / hostile URL.** | `https`-only origins from the signed DM; always sha256-verify. |

**Verified non-issues:** received-file open is crash-safe (`ChatScreen.kt:804/814/819`); `FileProvider` covers `filesDir`; encrypted blobs make public-by-hash exposure just ciphertext; concurrent file sends to one peer are independent HTTP (no lock); same file twice → fresh key → different hash (expected); `CallActivity` finishes purely off `callState→ENDED`.

---

## 5. Files to touch

| File | Change | For |
|---|---|---|
| `RelayControl.kt` | 5 type constants | both |
| `RelayControlSender.kt` | `sendCallSignal`, `sendFileBlossom` | both |
| `RelayInboxHandler.kt` | routes + `handleCallOffer`/`handleFileBlossom`; inject `webRtcManager`,`callHistoryDao`,`blossomFileService` | both |
| `WebRtcManager.kt` | `CallTransport`/maps, transport factory, relay paths, `cacheRelayCallOffer`/`onRelayCallSignal`, cleanup | calls |
| `WebRtcModule.kt` | provide new ctor deps | calls |
| `CallViewModel.kt` | remove 2 Wi-Fi gates + `wifiManager` | calls |
| `BlossomClient.kt` / `FileCrypto.kt` / `BlossomFileService.kt` | **new** | files |
| `ChatViewModel.kt` | INTERNET_RELAY branch; inject `blossomFileService` | files |
| `ChatScreen.kt` | `richEnabled != OFFLINE` (+ call-button) | both |
| DI module | `@Blossom OkHttpClient` + `BlossomClient` | files |
| Room migration | UNIQUE index on `Message.messageUuid` | files |

**Untouched:** `NcapManagerImpl`, `OffneticNavHost`, `CallActivity`, `IncomingCallService`.

**LOC:** calls ≈ +125 (`WebRtcManager`) +30 (signaling) −14 (`CallViewModel`); files ≈ +300–340 (3 new files + edits). ~**470 net new**, ~14 files. Risk concentrated in `WebRtcManager` (calls); files have no native objects.

---

## 6. Phases (each ends green on `:app:testDebugUnitTest`)

0. **Server verification + library scan (do first)** — (a) check whether Amethyst's `quartz` (or another Kotlin lib) already implements Blossom upload/auth before hand-rolling `BlossomClient`; (b) authed `PUT /upload` of a small encrypted test blob to each `DEFAULT_SERVERS`, `GET` back, assert **byte-identical** (sha256). Drop any server that 401/403/415s a random npub, transforms bytes, or is down.
1. **Shared signaling + DI** — `RelayControl` constants, `RelayControlSender` methods, inbox routes (stubs), ctor/provider edits, Room migration. Compiles, Hilt graph valid.
2. **Outgoing relay call** — `CallTransport`, `createPeerConnection(transport)`, `onIceGatheringChange`, `startCall`, `sendIceCandidateToPeer`, `hangup`; remove `CallViewModel` Wi-Fi gates. (C3/C4/C9)
3. **Incoming relay call** — `cacheRelayCallOffer` (+`IncomingCallService`), `onRelayCallSignal`, `acceptCall` RELAY bypass, `createAndSendAnswer` RELAY, `handleCallOffer` TTL. (C1/C5/C6/C7/C8/C10)
4. **File crypto + client** — `FileCrypto` (round-trip + sha256-stable + wrong-key→`AEADBadTagException` tests), `BlossomClient` (token format; upload/download/mirror vs mock; timeouts, partial cleanup). (F2/F6/F8/F13/F14/F19)
5. **File send/receive** — `BlossomFileService`, `ChatViewModel` branch (empty guard, server-list), `ChatScreen`, `handleFileBlossom` (offloaded, reservation, sanitize, fallback), dedup + UNIQUE index, mime→type. (F1/F3/F4/F5/F10/F11/F12/F15/F16)
6. **Device test** — two phones, different networks, BT off: call offer/answer + media P2P + hangup both sides + stale-offer TTL; image (~2 MB) + large file (~50 MB); **receiver offline at send → comes online → file arrives** (store-and-forward); kill the first server → next used (F15); corrupt blob → hash-reject, no message (F6); oversize/no-server → graceful toast.

---

## 7. Known limitations

- **~15% STUN failure** (symmetric NAT) → calls end with an error; TURN deferred. (C13)
- **Calls need both peers online + the foreground service up**; a stored `call_offer` past 45 s → missed call. (C10)
- **Relay call glare** — simultaneous mutual calls both fail; retry succeeds. (C7)
- **File servers must stay any-type (`*`), non-transforming** — media CDNs excluded; Phase 0 gates the list. (F2)
- **File retention** — community servers may GC; receiver fetches promptly on relay delivery; multi-server upload widens the window; very-long-offline + GC → "resend." (F5)
- **Durable file send (F18) is a follow-up** — v1 sends on `viewModelScope`; leaving the chat cancels it (stays `SAVED`, retryable). Wiring into the relay-outbox/foreground-service flow is a recommended v1.1.
- **Metadata** — relay sees gift-wrap timing; Blossom server sees uploader pubkey + size + time. Content E2E-encrypted throughout; a stated non-concern.

---

## 8. Research status

- **Design research: done** — call signaling (NIP-59 gift-wraps + STUN/non-trickle ICE) and Blossom BUD-01/02/04/05/06/11 (NIP-96 deprecated → NIP-B7), incl. live verification that any-type (`*`) Blossom servers accept encrypted octet-stream (the media-CDN-vs-encryption conflict was the key finding).
- **Still pending (do in Phase 0):** the real Blossom upload round-trip hasn't run; check `quartz`/Amethyst for an existing Kotlin Blossom client before hand-rolling; per-server retention unverified.
