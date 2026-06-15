# P2P Call Investigation: Offline Voice/Video Calls

> Tested on: Samsung SM-A226B (Android 13, API 33) ↔ OnePlus CPH2691IN (Android 16, API 36)

## Goal

Enable peer-to-peer voice/video calls over Wi-Fi Direct so devices can call each other offline (no internet, no shared Wi-Fi router). WebRTC requires IP-level connectivity between devices for ICE negotiation and media transport. NCAPI provides Bluetooth-only transport for control plane — no IP layer for media.

---

## Method 1: NCAPI `Payload.Type.STREAM` (WalkieTalkie Model)

**Status:** ❌ Not implemented

**How it works:** Google's NearbyConnectionsWalkieTalkie sample sends raw PCM audio over `Payload.Type.STREAM`. No WebRTC, no video codecs, no echo cancellation. Just raw PCM bytes over a Unix pipe → NCAPI stream.

**Why not for us:** We need video calls and WebRTC's codec/echo-cancellation stack. NCAPI Stream can't carry WebRTC's ICE/DTLS/SRTP — it's a byte pipe, not a socket. Could work for audio-only if we drop WebRTC and use Android's AudioRecord/AudioTrack directly.

**Reference:** `NearbyConnectionsWalkieTalkie` (Google connectivity-samples)

---

## Method 2: Manual `WifiP2pManager` API

**Status:** ⚠️ `createGroup()` works, `connect()` fails

### What we tested:

| API Call | Samsung | OnePlus | Result |
|---|---|---|---|
| `createGroup()` | SUCCESS | SUCCESS | Creates autonomous GO, device gets `192.168.49.1/24` |
| `discoverPeers()` | SUCCESS | SUCCESS | Finds peer's P2P MAC address |
| `connect(peerMac)` | FAILED (reason=0=ERROR) | FAILED (reason=0=ERROR) | Never completes |
| `setDialogListener()` | API removed SDK 35 | API removed SDK 35 | Can't auto-accept invitation |

### `createGroup()` works when:
- Wi-Fi is ON (radio enabled)
- Device is NOT connected to a Wi-Fi network (single-radio MediaTek chipset can't do STA+P2P simultaneously)

### `connect()` fails because:
- Android 15 (SDK 35) removed `setDialogListener` — can't auto-accept the Wi-Fi Direct invitation
- User must manually tap "Accept" on a system dialog
- Without the dialog being accepted, `connect()` returns `reason=0=ERROR`

### The invitation DOES arrive:
The Samsung received a Wi-Fi Direct connection request dialog when the OnePlus called `connect()`. The system delivers the request — it just needs user approval.

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

| Method | Auto? | Video? | Works? |
|---|---|---|---|
| NCAPI Stream (WalkieTalkie) | Yes | No | Not tested |
| Manual `WifiP2pManager` API | No (dialog) | Yes | Dialog blocks |
| Wi-Fi Aware (NAN) | Yes | Yes | Hardware ❌ |
| Local-Only Hotspot | No (notification) | Yes | Client connection ❌ |
| Manual P2P Group (Settings) | No (1 tap) | Yes | ✅ |
| Same Wi-Fi Network | Yes | Yes | ✅ |

**The only reliable offline calling path on these devices:** manual Wi-Fi Direct group creation + `disableNetworkMonitor=true`. The auto approaches all hit Android security dialogs or hardware limitations.
