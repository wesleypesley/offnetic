# P2P Call Investigation: Offline Voice/Video Calls

> Tested on: Samsung SM-A226B (Android 13, API 33) ↔ OnePlus CPH2691IN (Android 16, API 36)

## Goal

Enable peer-to-peer voice/video calls over Wi-Fi Direct so devices can call each other offline (no internet, no shared Wi-Fi router). WebRTC requires IP-level connectivity between devices for ICE negotiation and media transport. NCAPI provides Bluetooth-only transport for control plane — no IP layer for media.

---

## Method 1: NCAPI `Payload.Type.STREAM` (WalkieTalkie Model)

**Status:** ❌ Not implemented

**How it works:** Google's NearbyConnectionsWalkieTalkie sample sends raw PCM audio over `Payload.Type.STREAM`. No WebRTC, no video codecs, no echo cancellation. Just raw PCM bytes over a Unix pipe → NCAPI stream.

**Why not for us:** We need video calls and WebRTC's codec/echo-cancellation stack. NCAPI Stream can't carry WebRTC's ICE/DTLS/SRTP — it's a byte pipe, not a socket. Could work for audio-only if we drop WebRTC and use Android's AudioRecord/AudioTrack directly.

**Potential future use:** Audio-only fallback when Wi-Fi Direct is unavailable (Bluetooth-only). Would need Opus encoding (~16-32kbps fits within Bluetooth bandwidth) + AcousticEchoCanceler. ~500 lines. Serval Mesh project proved this architecture works (Codec2 over MDP datagrams) but is now abandoned.

**Reference:** `NearbyConnectionsWalkieTalkie` (Google connectivity-samples)

---

## Method 2: Manual `WifiP2pManager` API

**Status:** ⚠️ `createGroup()` works, `connect()` reports ERROR but invitation IS delivered

### What we tested:

| API Call | Samsung | OnePlus | Result |
|---|---|---|---|
| `createGroup()` | SUCCESS | SUCCESS | Creates autonomous GO, device gets `192.168.49.1/24` |
| `discoverPeers()` | SUCCESS | SUCCESS | Finds peer's P2P MAC address |
| `connect(peerMac)` | FAILED (reason=0=ERROR) | FAILED (reason=0=ERROR) | Reports failure BUT invitation dialog appears |
| `setDialogListener()` | API removed SDK 35 | API removed SDK 35 | Can't auto-accept invitation |

### `createGroup()` works when:
- Wi-Fi is ON (radio enabled)
- Device is NOT connected to a Wi-Fi network (single-radio MediaTek chipset can't do STA+P2P simultaneously)

### `connect()` reports ERROR but dialog appears:
- `connect()` returns `reason=0=ERROR` on both devices
- **However, the invitation dialog DOES appear** on the receiving device (confirmed on OnePlus Android 16)
- `setDialogListener` removal (SDK 35) means the system can't auto-accept, but the invitation is still sent and delivered
- The dialog is a standard Wi-Fi Direct invitation prompt — user can manually tap Accept

### Bugs found in existing code:

**Bug 1: `stopPeerDiscovery()` before `connect()` (Round 11 code)**
The original `startAsInitiator()` called `stopPeerDiscovery()` immediately before `connect()`. Per Stack Overflow and Google documentation: *"If you stop the scan, the target device is immediately dropped from the system's internal temporary peer list, causing `.connect()` to throw ERROR."* This was the primary cause of `connect()` failures. Round 12 removed this call but introduced a different issue.

**Bug 2: Premature abandonment after `connect()` ERROR**
Both Round 11 and Round 12 code treat `connect()` returning ERROR as a hard failure and give up. Per Google documentation: *"If `.connect()` returns ERROR but the user accepts the prompt, the device may actually connect in the background without your app knowing. Do not rely solely on the `.connect()` `onFailure` callback. Make sure you are actively listening to `WIFI_P2P_CONNECTION_CHANGED_ACTION` inside your BroadcastReceiver to verify when the state updates to connected."*

The fix: call `connect()` once, ignore the result, and wait for the `WIFI_P2P_CONNECTION_CHANGED_ACTION` broadcast (30-60s for user to tap Accept).

### `enterP2pDiscoveryMode()` is a no-op:
`WebRtcManager.enterP2pDiscoveryMode()` was added in Round 11 as an empty stub with the comment "NCAPI P2P_CLUSTER handles Wi-Fi Direct automatically." `WifiP2pHandler.startP2pCall()` is fully implemented but **never called** from the call flow. The Wi-Fi Direct code is dead code — it was never wired into the call path across any commit.

### Code location:
`WifiP2pHandler.kt` — fully implemented with `startP2pCall()`, `startAsGroupOwner()`, `connectToGroupOwner()`, discovery retry loop. **Preserved but uncalled** from the WebRTC call path.

---

## Method 3: Wi-Fi Aware (Neighbor Awareness Networking / NAN)

**Status:** ❌ Hardware unsupported

### What we tested:
- `WifiAwareManager` system service → **null on both devices**
- `android.hardware.wifi.aware` feature flag → **absent on both devices**

Samsung's MediaTek Dimensity 700 and OnePlus's chipset don't implement the NAN protocol in hardware. Wi-Fi Aware is unavailable on these devices.

### Code location:
`WifiAwareHandler.kt` — **deleted** (not applicable to this hardware)

---

## Method 4: Local-Only Hotspot

**Status:** ⚠️ Hotspot starts, client can't connect silently

### What we tested:

| Step | Result |
|---|---|
| `startLocalOnlyHotspot()` | SUCCESS on both devices. Hidden hotspot created, SSID + password returned. |
| `WifiNetworkSuggestion` (client) | `STATUS_NETWORK_SUGGESTIONS_SUCCESS` but **doesn't connect**. Shows notification/popup. |
| `waitForConnection()` (client) | Times out after 20s. Device never joins the hotspot network. |

### Why it fails:
Android 10+ blocks silent Wi-Fi connections as a security measure. Samsung OneUI requires explicit user approval via notification or dialog. No API exists to bypass this — even Google's own apps rely on system-level Play Services for this.

**Note:** `WifiNetworkSuggestion` was tested, NOT `WifiNetworkSpecifier`. These are different APIs — Suggestion is passive (OS decides when to connect), Specifier is active (shows immediate dialog, connects on approval). See Method 7 for the Specifier approach.

### Code location:
`LocalHotspotHandler.kt` — **deleted**

---

## Method 5: Manual Wi-Fi Direct Group (via System Settings)

**Status:** ✅ WORKS

### How it works:
1. User creates a Wi-Fi Direct group manually via Android Settings → Wi-Fi → Wi-Fi Direct
2. Both devices get IPs on `192.168.49.0/24` subnet (`ip addr show p2p0`)
3. Ping between devices works (<100ms)
4. WebRTC with `disableNetworkMonitor = true` discovers the `p2p0` interface
5. ICE generates host candidates with `192.168.49.x` addresses
6. ICE negotiation completes → CONNECTED
7. Both audio and video work over the P2P link

### Required app configuration:
- `disableNetworkMonitor = true` in `PeerConnectionFactory.Options` (enables WebRTC to scan ALL interfaces via `NetworkInterface.getNetworkInterfaces()`, not just those reported by Android's ConnectivityManager)
- `networkIgnoreMask = 0` (don't ignore any network types)
- `setFieldTrials("WebRTC-IncludeWifiDirect/Enabled/")` (WebRTC field trial for P2P interface visibility)

### Limitations:
- User must manually create the group before calling (one-time per session)
- Calls work offline once the group exists
- File transfers use NCAPI's built-in transport (no manual setup needed)

---

## Method 6: Same Wi-Fi Network

**Status:** ✅ WORKS

When both devices are connected to the same Wi-Fi router, NCAPI uses LAN transport. Devices get routable IPs. WebRTC discovers them via standard ICE host candidates. Calls work without any additional setup.

---

## Method 7: Automated Wi-Fi Direct via `WifiNetworkSpecifier` (PROPOSED)

**Status:** 🔧 Not yet implemented — all pieces individually proven

### The insight:
Wi-Fi Direct groups ARE Wi-Fi access points. The GO broadcasts an SSID like `DIRECT-xx-DeviceName` with WPA2 passphrase. Other devices can connect to it as a regular Wi-Fi network, completely bypassing the `WifiP2pManager.connect()` API and its dialog issues.

### Confirmed by research:
Google AI Overview citing Stack Overflow: *"You CAN connect to an active Wi-Fi Direct legacy group (which broadcasts an SSID starting with `DIRECT-`) using the `WifiNetworkSpecifier` API on Android 10+. Because the Wi-Fi Direct Group Owner behaves like a soft Access Point, Android treats it as a local, internet-less Wi-Fi network."*

### How it would work:

```
FIRST CALL (one dialog tap):
  Device A: createGroup()              → silent, no dialog, works
  Device A: requestGroupInfo()         → gets SSID + passphrase + BSSID
  Device A: sends credentials via NCAPI → already connected for messaging
  Device B: WifiNetworkSpecifier(SSID, passphrase)
            → standard Wi-Fi dialog    → user taps approve
            → onAvailable(network)     → bindProcessToNetwork(network)
  Both on 192.168.49.x                → WebRTC discovers p2p0 → call connects

SUBSEQUENT CALLS (zero taps):
  Same flow but Device B adds .setBssid(storedBssid)
  System remembers user intent for explicit MAC addresses → auto-connects
```

### Smart fallback with ICE timeout:

```
Call starts → WebRTC ICE gathers candidates
  → ICE connects in <10s?  → Same network → done, full quality call
  → ICE times out?         → Different networks → trigger Wi-Fi Direct:
      createGroup() → send creds via NCAPI → WifiNetworkSpecifier
      → p2p0 comes up → WebRTC retries ICE → connects
```

No manual detection needed. ICE failing IS the detection.

### Critical implementation details:
- `.removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)` on the `NetworkRequest` — tells Android this network doesn't need internet, prevents rejection
- `connectivityManager.bindProcessToNetwork(network)` — routes app traffic over the P2P link instead of cell data
- `.setBssid(MacAddress.fromString(storedBssid))` — skips dialog on subsequent connections to the same device
- Store BSSID in contact record for persistent zero-tap reconnection

### Advantages over Method 2 (`WifiP2pManager.connect()`):
- Uses standard Wi-Fi connection API, not P2P invitation API
- Familiar "Connect to Wi-Fi?" dialog instead of unfamiliar P2P invitation dialog
- BSSID remembering eliminates dialog on repeat calls
- Not affected by `setDialogListener` removal (different API path entirely)

### Advantages over Method 5 (manual group):
- First call: 1 dialog tap vs 8 manual steps across 2 phones
- Subsequent calls: 0 taps vs same 8 steps every time
- No user knowledge of Wi-Fi Direct settings required

### Known limitations:
- Single radio contention: joining the Wi-Fi Direct group disconnects from the router (MediaTek single-radio chipsets). Both devices lose regular Wi-Fi during the call. Reconnects automatically after teardown.
- First call to a new contact requires one dialog tap.
- `WifiNetworkSpecifier` for `DIRECT-` SSIDs is confirmed by documentation but untested on these specific devices.

### Estimated effort:
~200-300 lines across 4-5 files. No new audio pipeline, no C++, no JNI. Keeps the existing proven WebRTC call system.

---

## Other Approaches Investigated (Web Research)

### Custom WebRTC IceTransportFactory (C++/JNI)
WebRTC's native C++ stack has `webrtc::IceTransportInterface`, `webrtc::IceTransportFactory`, and `rtc::PacketTransportInternal`. These allow replacing WebRTC's entire network layer with a custom transport (e.g., routing through NCAPI). **Zero public implementations exist.** Requires building WebRTC from source (~30GB checkout), writing C++, JNI bindings. Weeks to months of effort. Extremely high risk.

### UDP Tunnel over NCAPI
Route WebRTC's UDP packets through NCAPI payloads. Each device opens a local loopback socket, WebRTC ICE points to `127.0.0.1`, a relay thread forwards packets via NCAPI. Complex, high latency over Bluetooth (~200-400ms), head-of-line blocking from reliable-over-unreliable transport.

### Similar Projects Research

| Project | Voice Calls? | How? | Status |
|---|---|---|---|
| Serval Mesh | YES (was) | Custom Opus/Codec2 over MDP datagrams + ad-hoc WiFi | Abandoned — Android killed ad-hoc WiFi |
| Briar | No | Bluetooth + Wi-Fi hotspot + Tor | Active — explicitly abandoned voice calling as "too unstable in mesh" |
| BitChat | No | Bluetooth mesh | Active — text only |
| Berty | No | libp2p + BLE driver | Active — text only |
| Meshenger | Yes (same network only) | TCP sockets over LAN + WebRTC | Active — Wi-Fi Direct on wishlist |
| Meshtastic | No | LoRa radio hardware | Active — text/GPS only, hardware-dependent |

**No existing project has solved seamless offline P2P voice/video calls on modern Android.** Serval Mesh was the only one that shipped it, and it died when Android removed ad-hoc WiFi support.

---

## Key Supporting Fixes

These bugs were found and fixed during P2P call investigation:

| # | Issue | Fix |
|---|---|---|
| StateFlow reuse | `getCallState()` returns stale ENDED flow from previous call → kills new call instantly | `getCallState` resets ENDED to IDLE before returning |
| Double postDelayed | Stale ENDED's `postDelayed(finish, 1500)` fires after new ENDED sets `finished=true` → finish() called twice | `finishRunnable` tracking — cancel old runnable on state transitions |
| IceCandidate logging | Candidate IPs not visible in logs → can't debug P2P path | Full SDP string logged in `onIceCandidate` |
| P2P candidate injection | Fallback for devices where `disableNetworkMonitor` still doesn't discover p2p0 | `injectP2pCandidate()` reads p2p0 IP from NetworkInterface, builds synthetic host candidate, sends to peer |

---

## Conclusion

| Method | Auto? | Video? | Works? | User Friction |
|---|---|---|---|---|
| NCAPI Stream (WalkieTalkie) | Yes | No | Untested | None (audio-only) |
| Manual `WifiP2pManager` API | No | Yes | Dialog + code bugs | P2P dialog every time |
| Wi-Fi Aware (NAN) | Yes | Yes | Hardware ❌ | N/A |
| Local-Only Hotspot | No | Yes | Client connection ❌ | N/A |
| Manual P2P Group (Settings) | No | Yes | ✅ | 8 steps, 2 phones |
| Same Wi-Fi Network | Yes | Yes | ✅ | None |
| **WifiNetworkSpecifier (proposed)** | **Semi** | **Yes** | **Pieces proven** | **1 tap first, 0 after** |

**Recommended path:** Method 7 (`WifiNetworkSpecifier`) with ICE timeout fallback. Automates the proven manual Wi-Fi Direct group approach (Method 5) with ~200-300 lines of code. Keeps the existing WebRTC call system. First call requires one familiar Wi-Fi dialog tap; subsequent calls to the same contact are fully automatic via BSSID remembering.

**Fallback path (future):** Method 1 (NCAPI Stream) for audio-only calls when Wi-Fi Direct is unavailable (Bluetooth-only scenarios). ~500 lines with Opus encoding. Lower quality but works everywhere NCAPI connects.
