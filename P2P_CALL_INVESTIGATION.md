# Wi-Fi Direct Peer-to-Peer Call — Investigation Report

## Objective
Enable WebRTC voice/video calls between two Android devices connected **only** via Wi-Fi Direct (no router, no internet, Wi-Fi enabled but not connected to any AP).

## Devices Under Test
- **Samsung SM-A226B** (Android 14, One UI)
- **OnePlus CPH2691** (Android 14, ColorOS/OxygenOS)
- Wi-Fi ON, Bluetooth ON, Location ON, no router connected

## Baseline
- **Same-Wi-Fi calls work.** SDP exchange over NCAPI/Bluetooth, ICE discovers LAN host candidates on the Wi-Fi interface (e.g. 192.168.1.x), WebRTC connects.
- **Cross-router calls work** (OnePlus on Router1, Samsung on Router2). ICE connectivity checks find a route through the routers.
- **Pure Wi-Fi Direct fails.** Both devices on Wi-Fi only, no router. ICE gathers LAN host candidates (e.g. 0.0.0.0 or loopback) that cannot reach each other. No network route exists between the devices without a shared subnet.

## The Core Problem
WebRTC needs **UDP/IP connectivity** between peers for media. When both devices have no router, the only way to get IP connectivity is Wi-Fi Direct. Wi-Fi Direct creates a P2P subnet (192.168.49.x). Both devices must be in the **same** P2P group to share this subnet.

Google Nearby Connections API (NCAPI) with `P2P_CLUSTER` strategy provides the **control plane** over BLE (message passing for SDP, ICE candidates, signaling). But NCAPI does **not** expose a raw IP route for WebRTC media. NCAPI may internally use Wi-Fi Direct for large payload transfers (files), but our WebRTC packets are not NCAPI payloads — they are raw UDP.

## Approaches Tried

---

### Attempt 1: Manual P2P Group + MAC Exchange over NCAPI
**Concept:** Caller creates an autonomous P2P group (`WifiP2pManager.createGroup()`), sends its MAC address to the callee via NCAPI, callee calls `WifiP2pManager.connect()` with that MAC to join the group.

**What happened:**
- `createGroup()` **succeeded** on both devices → each became Group Owner (GO) with IP `192.168.49.1`
- **MAC acquisition failed.** On Android 10+, `NetworkInterface.getHardwareAddress()` returns `02:00:00:00:00:00` for all interfaces (MAC randomization/privacy). `WifiP2pManager.requestGroupInfo().owner.deviceAddress` callback never fires reliably after group creation.
- Result: P2P info payload was never sent to the callee (MAC was null/zero).

**Files changed:** `WifiP2pHandler.kt` — added `getP2pInterfaceMac()`, `getDeviceAddressWithRetry()` with 6 retry attempts with 1s delays.

---

### Attempt 2: P2P Discovery to Bypass MAC Exchange
**Concept:** Instead of sending MAC over NCAPI, have the callee use `WifiP2pManager.discoverPeers()` to find the caller's P2P group autonomously. Discovery returns the MAC via the Wi-Fi Direct protocol (not Android API level), bypassing the MAC privacy restrictions.

**What happened:**
- `discoverPeers()` **succeeded** on the callee
- `requestPeers()` returned the correct MAC (e.g. `ea:6a:3d:2d:9a:ff` for OnePlus, `7e:c2:25:f0:d0:a6` for Samsung)
- Pioneer discovery was verified: "P2P peers changed — discovery complete" fired, "requestPeers returned 1 peers" confirmed
- **`WifiP2pManager.connect()` NEVER fired its callback.** Neither `onSuccess()` nor `onFailure()` was called. The `WIFI_P2P_CONNECTION_CHANGED_ACTION` broadcast with `isConnected=true` never arrived. The call timed out after 10 seconds every time.

**Root cause:**
`WifiP2pManager.connect()` is a **known broken API** on Samsung One UI and OnePlus ColorOS/OxygenOS. The call is accepted by the framework (no exception thrown) but silently fails — no callback, no broadcast, no connection. This is documented across the Android issue tracker and StackOverflow.

**Files changed:** `WifiP2pHandler.kt` — added `discoverAndConnect()`, `discoverPeers` + `requestPeers`, `stopPeerDiscovery` before connect, PEERS_CHANGED broadcast receiver.

---

### Attempt 3: Simultaneous Call Race Condition
**Observation:** Both devices were creating P2P groups simultaneously (each initiating a call). Both became GO of their own autonomous groups. Both received each other's `group_created` payloads, both tried to discover and connect. Two autonomous groups cannot connect to each other.

**Fix:** Removed `wifiP2pHandler.teardown()` from `cleanupPeerConnection()` — was killing the P2P job when a second call arrived. Only `hangup()` and `onCallHangup()` now call teardown. Stale PC cleanup in `onSdpReceived` now only removes from `peerConnections` without triggering teardown.

**Files changed:** `WebRtcManager.kt` — moved `teardown()` out of `cleanupPeerConnection()` to explicit `hangup()` / `onCallHangup()`. `CallViewModel.kt` — clear stale CallState errors.

---

### Attempt 4: Auto-Group Teardown on Incoming Payload
**Concept:** When receiving `group_created`, cancel own group to become responder-only.

**What happened:** Both devices cancelled their groups simultaneously — nobody had a group left to connect to. Both `connect()` calls targeted groups that no longer existed.

**Fix:** Removed the auto-cancel. But this led back to the dual-GO problem.

---

### Attempt 5: Replace connect() with createGroup(config) — GO Negotiation
**Concept:** Instead of `connect()` (broken API), use `WifiP2pManager.createGroup(channel, config)` — the **two-argument** version that does GO negotiation with a peer. The framework handles the entire handshake internally, bypassing the broken `connect()` implementation.

**What happened:**
- `createGroup(channel, config)` **succeeded** on the OnePlus: logged "negotiate createGroup succeeded" → "Negotiated: GO=true ip=192.168.49.1"
- **BUT** the OnePlus became **GO of a NEW group**, not a client of the Samsung's existing autonomous group.
- Autonomous groups (`createGroup()`) and negotiated groups (`createGroup(config)`) are **incompatible**. The Samsung had an autonomous group as GO. The OnePlus tried to negotiate. The autonomous GO didn't participate in the negotiation, so the framework fell back to making the OnePlus a GO of its own group.
- Two separate P2P groups, two GOs, no connection between devices.

**Key insight:** You cannot mix autonomous and negotiated P2P groups. Both sides must use the same mechanism.

**Files changed:** `WifiP2pHandler.kt` — rewrote to use `createGroup(channel, config)` via `negotiateWithPeer()`, removed `connectToGroup` from the discovery path.

---

### Attempt 6: Pure Negotiated Connection (Current)
**Concept:** No autonomous groups at all. Both sides run discovery to be discoverable. The initiator discovers the callee's MAC and calls `createGroup(channel, config)` with `groupOwnerIntent=15`. The callee, being in discovery mode, automatically responds to the GO negotiation and becomes a client. No `connect()` API used anywhere.

**What happened:**
- Discovery on the initiator found **zero peers** (`discovered peer MAC=null`)
- The callee was not in discovery mode → was not discoverable → initiator couldn't find it
- Just having Wi-Fi ON is not enough. A device must actively run `discoverPeers()` to be discoverable and visible to other P2P devices.

**Current state:** The file `WifiP2pHandler.kt` is rewritten to use pure negotiation. The callee side does not yet trigger its own `discoverPeers()`. This is the next step.

**Files changed:** `WifiP2pHandler.kt` — complete rewrite. Cleaned up all dead code (autonomous group creation, MAC exchange, payload handling). Reduced from 598 to ~250 lines.

---

## Summary of Blockers

| # | Blocker | API | Status |
|---|---------|-----|--------|
| 1 | MAC address acquisition | `NetworkInterface.getHardwareAddress()` | **Blocked by Android 10+ privacy** — always returns `02:00:00:00:00:00` |
| 2 | MAC via group info | `requestGroupInfo().owner.deviceAddress` | **Callback unreliable** — doesn't fire on these devices |
| 3 | MAC via discovery | `discoverPeers()` + `requestPeers()` | **WORKS** ✅ — returns real MAC |
| 4 | Connect to group | `WifiP2pManager.connect()` | **BROKEN** — callback never fires on Samsung/OnePlus |
| 5 | Autonomous + negotiated mix | `createGroup()` + `createGroup(config)` | **INCOMPATIBLE** — cannot mix group types |
| 6 | GO negotiation | `createGroup(channel, config)` | **WORKS** on OnePlus ✅ — creates group, fires broadcast |
| 7 | Callee discoverability | Device must run `discoverPeers()` to be found | **MISSING** — callee not triggering discovery |

## Remaining Solution Path

1. **Both sides must run `discoverPeers()`.** This makes both devices discoverable AND scanning.
2. **Only one side negotiates.** Use the deterministic initiator rule (lower public key) — that device calls `createGroup(channel, config)` with `groupOwnerIntent=15`.
3. **The callee just stays in discovery mode.** No `connect()`, no `createGroup()`. The framework handles the negotiation automatically when the initiator's `createGroup(config)` fires.
4. **Need to bridge the timing gap.** The callee should start discovery when it receives the CALL_OFFER (in `acceptIncomingCall`), not wait until the user accepts. This ensures both sides are in discovery mode simultaneously.

## Files Significantly Modified
- `WifiP2pHandler.kt` — rewritten 3 times, ended at ~250 lines (was ~600). Removed autonomous group creation, MAC exchange, all P2P payload handling. Added discovery, GO negotiation, clean retry logic.
- `WebRtcManager.kt` — removed broken `injectWifiDirectCandidate()`, changed `disableNetworkMonitor` to `false`, removed `startP2pCall` in some iterations, moved `teardown()` out of `cleanupPeerConnection()`, stale PC cleanup in `onSdpReceived`.
- `CallViewModel.kt` — stale error clearing in `startOutgoingCall`, `acceptIncomingCall`, `acceptCall`.
