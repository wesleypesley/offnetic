# Current Issues — offnetic

**For Claude Fable 5:**

1. **Verify** every issue listed below — confirm it exists, assess actual severity, and fix it.
2. **Find more** — this list is not exhaustive. Audit for additional bugs, race conditions, resource leaks, and edge cases not covered here. Pay special attention to state machines, Signal protocol error handling, and concurrency.
3. **Build verification** — after every significant change, run both `:app:assembleRelease` and `:app:assembleDebug`. Release must pass; debug is currently broken (see O34) and should be fixed as part of the Kotlin upgrade. Do NOT proceed with large changes without verifying the build compiles.
4. **Onboarding redesign** — the current onboarding screens (`IdentityGenerationScreen`, `ProfileSetupScreen`, `SplashScreen`, `PermissionSlide`) look AI-generated. Redesign them to be clean, creative, and follow the Material 3 design system. Fix the design system (`Theme.kt`, `Color.kt`) if needed.
5. **Call screen** — verify the call screen renders correctly across device sizes; fix cutout/inset handling if broken; move hardcoded strings to `strings.xml`.
6. **Chat screen redesign** — complete overhaul of `ChatScreen.kt` and `ChatViewModel.kt`:
   - **Redesign everything.** Make it feel smooth and polished like Telegram/WhatsApp — message grouping, date separators, smooth scroll, proper keyboard handling, typing indicators, read receipts that actually work, message actions (copy, reply, forward, delete-for-everyone), file preview before send, upload progress, voice note waveform + seek bar + long-press-to-record, empty/loading/error states, proper accessibility content descriptions.
   - **Replace all hardcoded colors with theme tokens** from `MaterialTheme.colorScheme`. No raw `Color(0xFF...)` anywhere. If the design system is lacking, fix `Color.kt` / `Theme.kt` first.
   - **Replace all hardcoded strings** with `strings.xml` resources for i18n.
   - **Replace unicode icons** (←, +) with Material Icons (`Icons.AutoMirrored.Filled.ArrowBack`, `Icons.Filled.Add`, etc.).
   - **Fix all leaks** — MediaPlayer lifecycle, bitmap recycling, ViewModel.onCleared, temp file cleanup.
   - **Add pagination** — infinite scroll for history beyond 100 messages.
   - **Freedom to redesign** — change layout, spacing, animations, component structure as needed. The only constraint: designs should stay **consistent** across the rest of the app. If you think the entire app's design language should change, do it uniformly across all screens.

---

## Critical / High Severity

### Relay Transport
| # | File | Issue |
|---|---|---|
| 1 | `OkHttpRelayConnection.kt:63-74` | **Double-reconnect storm** — `onFailure` + `onClosed` both call `scheduleReconnect()`, creating 2 live WebSockets, leaking one |
| 2 | `RelayInboxHandler.kt:56-102` | **Uncaught exceptions kill collector permanently** — one malformed event stops ALL relay event processing |
| 3 | `RelaySessionService.kt:23-56` | **Duplicate publish** — concurrent `onSessionReady` calls double-publish every queued message |
| 4 | `RelayPool.kt:20-42` | **Silent data loss** — `tryEmit` drops events/acks when buffer full (256/64), no log |

### Nearby / P2P
| # | File | Issue |
|---|---|---|
| 5 | `NcapManagerImpl.kt:99-101` | `isAdvertising`/`isDiscovering` not `@Volatile` — stale reads across threads |
| 6 | `NcapManagerImpl.kt:112-125` | `callActiveCount++` on `@Volatile` field — lost increments, deferred files never processed |
| 7 | `NcapManagerImpl.kt:178` | **Auto-accepts ALL connections** — any nearby device can force a handshake, no consent |
| 8 | `WebRtcManager.kt:67-69` | `initialized`/`eglBase`/`peerConnectionFactory` not volatile — broken init: factory null while guard passes |
| 9 | `WebRtcManager.kt:816-853` | **Singleton video source shared across peers** — camera fallback operates on wrong peer (multi-call broken) |
| 10 | `WebRtcManager.kt:606-637` | **Dual hangup disposes already-disposed objects** — native WebRTC crash |
| 11 | `WebRtcManager.kt:100` | Static `pendingIncomingOffers` never evicted — unbounded growth from malicious offers |
| 12 | `NcapForegroundService.kt:130` | `killProcess` prevents `onDestroy()` — leaks receiver, scope, relay connections |
| 13 | `IncomingCallService.kt:113-120` | **Ringtone plays forever** if call accepted before timeout — `stopRinging()` skipped |

### Signal Protocol & Crypto
| # | File | Issue |
|---|---|---|
| 14 | `SignalProtocolManager.kt:150-176` | **Critical: all decrypt error types swallowed** — `NoSessionException`, `DuplicateMessageException` (replay), `UntrustedIdentityException` (MITM) all return `null` identically |
| 15 | `SignalProtocolManager.kt:178-184` | `handleShatteredSession()` partial state — session deleted but bundle creation fails, permanent deadlock |
| 16 | `SignalProtocolManager.kt:190-193` | `hasSession()` calls `runBlocking` on caller's thread — ANR risk |
| 17 | `Nip44.kt:13-16` | **Potentially critical: hardcoded `0x02` parity prefix** — may break interop with odd-y public keys (~50% of keys). Verify `Secp256k1.pubKeyTweakMul` behavior with wrong parity |

### Data Layer
| # | File | Issue |
|---|---|---|
| 18 | `BlossomFileService.kt:27-69` | **Blocking I/O on caller's dispatcher** — file + network without `withContext(IO)`, ANR |
| 19 | `BlossomClient.kt:62-125` | Synchronous OkHttp calls on caller's dispatcher — same ANR risk |

### UI / ViewModels
| # | File | Issue |
|---|---|---|
| 20 | `CallViewModel.kt:47` | `internalScope` not lifecycle-aware — leaks coroutines if `cleanup()` never called |
| 21 | `CallViewModel.kt:49` | `MutableStateFlow` shared across ViewModel instances via `getOrPut` — corrupted state |
| 22 | `ChatViewModel.kt:116` | `recordingGuard` non-atomic — double-starts recording, corrupts recorder state |
| 23 | `ChatViewModel.kt:456` | `retryUnsentMessages` no re-entrancy guard — duplicate delivery on rapid reconnect |
| 24 | `ChatViewModel.kt:376` | Concurrent `sendEncrypted` duplicates connection requests |
| 25 | `ActiveChatTracker.kt:8` | `@Volatile` + check-then-act = TOCTOU — clears wrong chat, suppresses notifications |

---

## Medium Severity

### Relay Transport
| # | File | Issue |
|---|---|---|
| 26 | `OkHttpRelayConnection.kt:79` | `reconnectAttempts` race condition on unsynchronized Int |
| 27 | `OkHttpRelayConnection.kt:42-46` | `openSocket()` can be called while connected, overwrites old socket without close |
| 28 | `OkHttpRelayConnection.kt:77-86` | Infinite reconnect with no max attempts — battery/bandwidth drain on bad relay URLs |
| 29 | `RelayInboxHandler.kt:245-249` | Rate-limit check-then-act race on `ConcurrentHashMap` |
| 30 | `RelayInboxHandler.kt:84-102` | Duplicate message insertion under concurrency (no unique constraint guard) |
| 31 | `RelayInboxHandler.kt:62-63` | Messages without "u" tag silently dropped |
| 32 | `RelayInboxHandler.kt:57-97` | Multiple silent null-returns without diagnostic logging (GiftWrap unwrap, Base64 decode, decrypt, JSON parse) |
| 33 | `RelayOutboxProcessor.kt:36-38` | Stale RELAYED rows never transition to FAILED — table bloat |
| 34 | `RelayOutboxProcessor.kt:75-83` | Rejected events (OK accepted=false) silently ignored; relay `message` reason dropped |
| 35 | `RelayOutboxProcessor.kt:47-50` | Contact lookup failure permanently fails message — no retry |
| 36 | `RelayOutboxProcessor.kt:30` | No pagination; large outbox blocks mutex across crypto ops |
| 37 | `RelaySessionService.kt:30-32` | Message timestamps replaced with current time — ordering lost |
| 38 | `RelayPool.kt:38-43` + `RelayMessage.kt:28-42` | NOTICE/CLOSED/EOSE/Unknown relay messages silently dropped; JSON parse errors blanket-caught |
| 39 | `RelayConnection.kt:11` | Interface declares `Flow<String>` (cold) but implementation is `SharedFlow` (hot) |
| 40 | `RelayControlSender.kt:99-104` | Mutex held across `delay()` — serializes all receipts, N × 350ms |
| 41 | `AttachmentRelayResender.kt:37-39` | Missing attachment file silently skipped forever; no fallback |
| 42 | `RelayRequestManager.kt:87-92` | Outbound republish check-then-act race |
| 43 | `RelayRequestManager.kt:60-61` | `acceptRequest` bundle send failure silently ignored — asymmetric connection state |
| 44 | `RelayRequestManager.kt:54-58` | Peer bundle processing failure silently ignored — corrupted session, contact still created |

### Nearby / P2P
| # | File | Issue |
|---|---|---|
| 45 | `NcapManagerImpl.kt:409-418` | `stopAll()` doesn't cancel heartbeat jobs |
| 46 | `NcapManagerImpl.kt:151-152` | `pendingFileMetas`/`incomingFilePayloads` maps grow unbounded (no expiry/cleanup) |
| 47 | `NcapManagerImpl.kt:649-667` | `sendCallSignal` silently drops errors — caller unaware, waits full 60s timeout |
| 48 | `NcapManagerImpl.kt:149` | `identitySentPeers` never cleared — stale profile on reconnect |
| 49 | `WebRtcManager.kt:139-185` | `eglBase` leaked on `peerConnectionFactory` init failure |
| 50 | `WebRtcManager.kt:192-201` | `getCallState` resets ENDED to IDLE — losing error messages |
| 51 | `WebRtcManager.kt:800,929` | Old `DataChannel` overwritten without `close()`/`dispose()` |
| 52 | `WebRtcManager.kt:554-561` | Glare handler discards PC + creates orphaned cached offer |
| 53 | `WebRtcManager.kt:897-905` | Grace job fires cleanup on already-disposed PC |
| 54 | `WebRtcManager.kt:383-388` | `acceptCall` no cached offer — doesn't notify remote side |
| 55 | `WifiP2pHandler.kt:329-341` | BroadcastReceiver not unregistered in `teardown()` |
| 56 | `WifiP2pHandler.kt:261-268` | `connectionInfoDeferred` race between create and assign |
| 57 | `WifiP2pHandler.kt:282-308` | First `requestPeers` delayed 2s — may get stale results |
| 58 | `WifiP2pHandler.kt:378-385` | `deletePersistentGroups()` — loops 32 reflection calls, hidden API blocked on 14+ |
| 59 | `ProxyVideoSink.kt:10-13` | `VideoFrame` leaked when target is null |
| 60 | `NcapForegroundService.kt:162` | `relayPool.connect()` no timeout |
| 61 | `NcapForegroundService.kt:170-198` | Accumulating relay subscriptions every 60s |
| 62 | `NcapForegroundService.kt:55-56` | Non-volatile `isNcapActive`/`isRelayActive` |

### Signal Protocol
| # | File | Issue |
|---|---|---|
| 63 | `SignalProtocolManager.kt:42-52` | `initialize()` TOCTOU race |
| 64 | `SignalProtocolManager.kt:54-115` | Duplicate signed/Kyber pre-key generation |
| 65 | `SignalProtocolManager.kt:128-135` | TOFU identity save without session recovery on retry failure |

### Data Layer
| # | File | Issue |
|---|---|---|
| 66 | `MessageRepositoryImpl.kt:31-39` | Flow methods don't wrap exceptions |
| 67 | `ContactRepositoryImpl.kt:33-39` | Flow methods don't wrap exceptions |
| 68 | `ProfileRepositoryImpl.kt:28-35` | Flow methods don't wrap exceptions |
| 69 | `SessionRepositoryImpl.kt:40-42` | Flow methods don't wrap exceptions |
| 70 | `BlossomFileService.kt:35-48` | Orphaned uploads on relay announcement failure |
| 71 | `BlossomFileService.kt:60-61` | `expectedSize` from untrusted sender as download cap — DoS vector |
| 72 | `NetworkMonitor.kt:36` | `registerDefaultNetworkCallback` in `init` can crash app start |
| 73 | `RelaySessionService.kt:46-49` | Identical timestamps produce non-deterministic eviction |

### UI / ViewModels
| # | File | Issue |
|---|---|---|
| 74 | `CallViewModel.kt:88` | `acceptIncomingCall` forces phase to INCOMING — may overwrite progress |
| 75 | `CallViewModel.kt:113-125,222-234` | Duplicate call history row on missed call |
| 76 | `CallViewModel.kt:176` | `cameraEnabled` non-atomic toggle |
| 77 | `CallViewModel.kt:219-236` | `launchIncomingTimeout` collectors never cancelled after timeout |
| 78 | `ChatViewModel.kt:351` | `connectingToastShown` non-atomic — duplicate toasts |
| 79 | `ChatViewModel.kt:229` | `reachability.value` snapshot read — stale between check and send |
| 80 | `ChatViewModel.kt:565-598` | `deleteMessage`/`cancelMessage` silently swallow DB errors |
| 81 | `ChatViewModel.kt:128-130` | `WhileSubscribed(5000)` resets message list on re-subscription |
| 82 | `ChatListViewModel.kt:49` | `_myPublicKey=""` triggers bogus initial DB query |
| 83 | `ChatListViewModel.kt:78` | `init` coroutine unguarded — failure suppresses unread counts |
| 84 | `MainActivity.kt:57,64` | `StateFlow` used as one-shot event channel — fragile navigation |
| 85 | `ActiveChatTracker.kt:8` | No disposal mechanism — suppressed notifications for dead ViewModels |
| 86 | `CallActivity.kt:727-751` | Old SurfaceViewRenderer sinks not removed on rotation |

---

## Hardcoded Design Decisions

### High — Should Be Configurable
| # | Item | File | Current Value | Risk |
|---|---|---|---|---|
| H1 | Keystore credentials | `app/build.gradle.kts:70-72` | `offnetic123` | **Security breach** — committed to repo |
| H2 | NDK llvm-strip path | `app/build.gradle.kts:308` | `C:/Users/Admin/.../ndk/28.2.13676358/...` | **Build broken** on CI/other devs |
| H3 | Build tools version | `app/build.gradle.kts:178` | `35.0.0` | **Build broken** on SDK update |
| H4 | Default relay URLs | `RelayPool.kt:78-82` | 4 hardcoded WSS URLs | Relay shutdown = all messaging/calls break |
| H5 | Default Blossom servers | `BlossomClient.kt:28-31` | 3 hardcoded HTTP URLs | Server down = all file transfers break |
| H6 | STUN servers | `WebRtcManager.kt:779-780` | `stun.l.google.com:19302` | Privacy leak + Google dependency |

### Medium — Should Be Configurable
| # | Item | File | Current Value | Risk |
|---|---|---|---|---|
| H7 | P2P candidate port | `WebRtcManager.kt:1006` | `58000` | Firewall/OS conflict = P2P ICE fails |
| H8 | P2P subnet filter | `WebRtcManager.kt:1000` | `192.168.49.` | Non-standard OEM P2P subnet = no host candidate |
| H9 | Max file size (3 duplicates) | `NcapManagerImpl.kt:605`, `ChatViewModel.kt:193`, `BlossomFileService.kt:74` | `100MB` | Duplicated — changing one misses others |
| H10 | Call timeout | `WebRtcManager.kt:1053`, `IncomingCallService.kt:114` | `60s` | User expectation mismatch |
| H11 | ICE gathering timeout | `WebRtcManager.kt:283,481` | `8s` | Relay calls fail on slow networks |
| H12 | Stale call offer drop | `RelayInboxHandler.kt:169` | `45s` | Missed calls on slow relay delivery |
| H13 | Blossom HTTP timeouts | `BlossomModule.kt:19-21` | `30/120/180s` | Large file transfers timeout on slow links |

### Low — Could Be Configurable
| # | Item | File | Current Value |
|---|---|---|---|
| H14 | Voice note max duration/size | `VoiceNoteRecorder.kt:18-19` | `2min / 1.5MB` |
| H15 | Audio sample rate/bitrate | `VoiceNoteRecorder.kt:45-46` | `16kHz / 32kbps` |
| H16 | EventDeduper capacity | `EventDeduper.kt:3` | `5000` |
| H17 | Relay outbox cap | `ChatViewModel.kt:51` | `50` |
| H18 | NCAPI endpoint retry iterations | `NcapManagerImpl.kt:539` | `30 × 2s = 60s` |
| H19 | Proximity silent threshold | `NcapManagerImpl.kt:72` | `5 min` |

### Call Screen Specific
| # | Item | File | Current Value | Risk |
|---|---|---|---|---|
| H20 | Hardcoded English strings | `activity_call.xml:48,153,179` | "Camera off", "Accept", "Decline" | No i18n |
| H21 | PiP marginTop | `activity_call.xml:100` | `152dp` | Breaks on tall status bars / cutouts |
| H22 | Top padding | `activity_call.xml:62` | `56dp` | Doesn't account for display cutouts |
| H23 | Bottom padding | `activity_call.xml:121` | `48dp` | Doesn't account for gesture nav bar |
| H24 | Inline color values | `CallActivity.kt:205,208-211,327-330,403,417` | `0x40FFFFFF`, etc. | Not in colors.xml |
| H25 | Display name truncation | `CallActivity.kt:215` | `take(12)` | Arbitrary cutoff instead of XML ellipsize |
| H26 | Finish delay | `CallActivity.kt:315` | `1500ms` | Hardcoded UX timing |

---

## Concurrent Users — Summary

**Relay mode** scales better (Nostr relays handle fan-out) but:
- `RelayPool` fixed buffer (256 events, 64 acks) will overflow under concurrent usage
- `EventDeduper` 5000-entry LRU is too small for busy inboxes
- `RelayOutboxProcessor` holds mutex across all pending rows — linear processing becomes bottleneck
- Duplicate message race in `RelayInboxHandler.handleMessage`

**Nearby mode** is broken for multiple peers:
- Singleton video source (`WebRtcManager:816-853`) — two concurrent P2P calls share `activeVideoPeer`/`activeSurfaceTextureHelper`
- `NcapManagerImpl` auto-accepts all connections — any nearby device can connect

**Both modes** share `SignalProtocolManager` which has no per-peer locking on session operations.

---

## Second-Pass Findings (Onboarding, Manifest, Build, Design System)

### Critical
| # | File | Line | Issue |
|---|---|---|---|
| O1 | `IdentityGenerationScreen.kt` | 66–77 | **Fake progress animation decoupled from actual identity generation.** `viewModel.generateIdentity()` runs async; a separate coroutine runs a cosmetic 40ms-tick timer. If generation fails, UI still shows "Identity created" + checkmark. |
| O2 | `ProfileSetupScreen.kt` | 153–156 | **Save-then-navigate with no error handling.** `viewModel.saveProfile(trimmed)` fires, then `onDone()` navigates immediately — regardless of save success. User proceeds with no username on failure. |
| O3 | `PermissionSlide.kt` | 118–120 | **Permission grant/deny results completely ignored.** `{ onNext() }` fires unconditionally. User can deny all three permissions and proceed to main screen, then crash on camera/bluetooth/mic usage. |
| O4 | `OffneticDatabase.kt` / `DatabaseModule.kt` | 94 / 60 | **Destructive migration — any schema change wipes ALL user data.** 16 entities, version 9, zero `Migration` objects. `fallbackToDestructiveMigration()` will delete all contacts, messages, sessions, keys on any entity change. |
| O5 | `proguard-rules.pro` | 7–16 | **Entire app codebase kept from shrinking.** Blanket `-keep class com.offnetic.** { *; }` for every layer. Combined with `isMinifyEnabled = false`, this is moot now — but if minification is ever enabled, R8 is fully defeated. |
| O6 | `AndroidManifest.xml` | 56–59 | **`uses-feature required="true"` on BLE, Wi-Fi Direct, Camera, Microphone.** App won't install on any device missing these. Should be `required="false"` with runtime checks. |

### High
| # | File | Line | Issue |
|---|---|---|---|
| O7 | `app/build.gradle.kts` | 78–79 | **Release: `isMinifyEnabled = false`, `isShrinkResources = false`.** No code shrinking, no obfuscation, no resource optimization. APK is larger and bytecode trivially reversible. |
| O8 | `app/build.gradle.kts` | 273 | **Multidex dependency hardcoded as `"androidx.multidex:multidex:2.0.1"`** instead of using version catalog. |
| O9 | `app/build.gradle.kts` | 86–244 | **Custom libsignal metadata stripping + pre-dexing tasks (70+ lines of ASM).** Tightly coupled to AGP internals (`mergeDexRelease`), hardcoded paths. Will break on AGP update. Fix: upgrade Kotlin to 2.x so libsignal's Kotlin 2.1 metadata is compatible. |
| O10 | `Color.kt` (theme) | 62–84 | **`labelSmall` typography style is NOT defined** in custom font stack. Used 12 times across screens but falls back to Material3 default — visual inconsistency. |
| O11 | All onboarding screens | multiple | **Theme color scheme ignored.** Every onboarding screen uses `Color(0xFF0A0A0A)` instead of `MaterialTheme.colorScheme.background`. The `DarkColorScheme` is defined but unused. |
| O12 | `IdentityGenerationScreen.kt` | 57–62 | **All step descriptions hardcoded English** — "Generating ECDH P-256 keypair", "Deriving public identity", etc. |
| O13 | `ProfileSetupScreen.kt` | 68–143 | **All onboarding strings hardcoded English** — "SET UP PROFILE", "Enter a username", warning text, button labels. |
| O14 | `PermissionSlide.kt` | 47–93, 164–287 | **All permission slide strings hardcoded English** — titles, descriptions, tags, buttons. |
| O15 | `app/build.gradle.kts` | 30 | **NDK ABI filter `arm64-v8a` only.** Excludes 32-bit devices and x86 emulators. Add `x86_64` for emulator dev workflows. |

### Medium
| # | File | Line | Issue |
|---|---|---|---|
| O16 | `SplashScreen.kt` | 24–27 | **No error handling after delay.** If `onDone()` throws, splash freezes forever. No timeout/fallback. |
| O17 | `SplashScreen.kt` | 33 | **Hardcoded background `Color(0xFF141A1A)`** instead of theme color. |
| O18 | `ProfileSetupScreen.kt` | 33 | **Username regex `^[a-zA-Z0-9_]{2,24}$`** too permissive — no reserved word blocklist, no profanity filter, allows `_`-only names. |
| O19 | `ProfileSetupScreen.kt` | 104 | **Silent truncation** — pasting 30 chars silently drops everything after 24 with no feedback. |
| O20 | `PermissionSlide.kt` | 109–113 | **Slide index detected by string matching on `title` parameter** — fragile coupling to call sites. |
| O21 | `PermissionSlide.kt` | 260–262 | **Pre-Tiramisu notifications slide has no-op permissions array** — button says "Allow Notifications" but does nothing. |
| O22 | `AppDatabase.kt` | 61 | **`exportSchema = false`** — impossible to write or verify migration tests. |
| O23 | `OffneticDatabase.kt` | 87–98 | **Companion `getInstance()` is dead code** — zero callers. If invoked, would create unencrypted DB (no SQLCipher). |
| O24 | `proguard-rules.pro` | 28–55 | **Redundant keep rules for libraries** (SQLCipher, DataStore, Nearby, WebRTC, CameraX, etc.) that already ship consumer rules in AARs. |
| O25 | `gradle.properties` | 2 | **`android.enableJetifier=true`** — unnecessary for a 100% AndroidX project with minSdk 28. Slows builds. |
| O26 | `gradle.properties` | 6 | **`android.experimental.r8.globalSyntheticsConsumer=true`** — required only by the Kotlin 1.9/libsignal hack. Removeable after Kotlin upgrade. |
| O27 | `libs.versions.toml` | 3 | **Kotlin 1.9.22** — approaching EOL. The entire libsignal stripping hack exists because of 1.9 vs 2.1 metadata split. |
| O28 | `libs.versions.toml` | 9–10 | **SQLCipher version skew** — library 4.5.3 vs android-database-sqlcipher 4.5.4. Latest is 4.6.0. |
| O29 | `Color.kt` (theme) | 1–105 | **File named `Color.kt` but contains Typography, Shapes, and `OffneticColors`** — misleading. Content should be split or file renamed. |

### Low
| # | File | Line | Issue |
|---|---|---|---|
| O30 | `AndroidManifest.xml` | 7–8 | **Deprecated `BLUETOOTH` / `BLUETOOTH_ADMIN` permissions** — no-ops on API 31+. Harmless but cluttered. |
| O31 | `AndroidManifest.xml` | 87–92 | **Deep link `offnetic://add` with `BROWSABLE`** — any app can send this. Verify handler validates data URI. |
| O32 | `build.gradle.kts` (root) | 19–24 | **Global `-opt-in` for `ExperimentalMaterial3Api` and `ExperimentalFoundationApi`** — masks every usage of unstable Compose APIs project-wide. |
| O33 | `app/build.gradle.kts` | 179 | **Hardcoded build-tools `35.0.0`** in `preDexLibsignal` task — breaks on SDK update. |

### Kotlin 1.9 / libsignal 2.1 Build Hack (Debug APK Broken)
| # | File | Line | Issue |
|---|---|---|---|
| O34 | `app/build.gradle.kts` | 86–244 | **Debug APK cannot build.** `copyLibsignalDexDebug` targets `intermediates/dex/debug/mergeProjectDexDebug/0` — old AGP 7.x path. AGP 8.4 restructured dex merging so this path doesn't exist. Only release builds work. Root cause: Kotlin 1.9.22 can't process libsignal's Kotlin 2.1 `@Metadata` annotations, so a ~160-line hack exists — ASM bytecode stripping (`stripLibsignalMetadata`, lines 90–153), custom `d8` pre-dexing (`preDexLibsignal`, lines 159–191, hardcoded `35.0.0` build-tools and `C:/Users/Admin/...` NDK path), and manual DEX copy tasks (`copyLibsignalDexDebug`/`Release`, lines 195–244). |
| O35 | `gradle.properties` | 6 | **`android.experimental.r8.globalSyntheticsConsumer=true`** — required only by the libsignal pre-dex hack. Remove after Kotlin upgrade. |
| O36 | `gradle.properties` | 2 | **`android.enableJetifier=true`** — unnecessary for 100% AndroidX + minSdk 28. Just slows builds. |
| O37 | `app/build.gradle.kts` | 273 | **Multidex dependency hardcoded** `"androidx.multidex:multidex:2.0.1"` instead of version catalog. |

**Fix for O34–O37**: Upgrade Kotlin to 2.1.x (`libs.versions.toml:3`), switch to `kotlin("plugin.compose")` plugin, bump KSP to 2.1.x, bump Hilt to 2.51+ for KSP2 compat, then delete all three custom tasks (~160 lines), remove `globalSyntheticsConsumer` and `enableJetifier` from `gradle.properties`. Both debug and release APKs will build without hacks.

### Initial Chat Screen Findings

| C1 | `ChatScreen.kt` | 421-428 | **Links not clickable.** `Message.TYPE_TEXT` renders with plain `Text()` — pasted URLs display as flat text with no detection, no underline styling, no click handling. Should use `ClickableText` + `buildAnnotatedString` with `SpanStyle(TextDecoration.Underline)` and `Intent(ACTION_VIEW, Uri.parse(url))` to open in default browser. |
| C2 | `ChatScreen.kt` | 152,286 | **Hardcoded backgrounds** `Color(0xFF0A0A0A)` and `Color(0xFF141414)` — ignores `MaterialTheme.colorScheme`. |
| C3 | `ChatScreen.kt` | 223 | **Back button uses unicode "←"** instead of `Icons.AutoMirrored.Filled.ArrowBack` — inconsistent with Material conventions, no RTL support. |
| C4 | `ChatScreen.kt` | 400,408-409,416-417,494-495,542,549,658,665,740-744,771-777,784,790,790-792 | **Hardcoded English strings** — "Retry", "Delete", "Tap to open", "Loading...", "Could not load preview", "File not found", "No app can open this file", "Cannot open file" — not in strings.xml. |
| C5 | `ChatScreen.kt` | 197 | **File picker uses `*/*` MIME type** — allows any file, but `inferMimeType()` only maps ~25 extensions. Unknown extensions get `application/octet-stream`, which may not open on all devices. |
| C6 | `ChatScreen.kt` | 451-470 | **MediaPlayer created without cleanup** — if voice note starts playing and composable recomposes, old player not released before new one created. |
| C7 | `ChatScreen.kt` | 825-852 | **`inferMimeType()` missing common types** — no `.m4a`/`.aac` handling, audio MIME guess incomplete. |

### Chat Screen — Deep Audit (Missing Features, Leaks, UX)

#### Critical
| # | File | Line | Issue |
|---|---|---|---|
| D1 | `ChatScreen.kt` | 451-473 | **MediaPlayer leak** — if `setDataSource` succeeds but `prepare()` throws, `mediaPlayer = mp` never assigned. The catch block has no reference to the orphaned MediaPlayer. |
| D2 | `ChatScreen.kt` | 435-440 | **MediaPlayer race on dispose** — `DisposableEffect(Unit).onDispose` calls `stop()` then `release()` mid-playback. `stop()` can throw if called on wrong thread. |

#### High
| # | File | Line | Issue |
|---|---|---|---|
| D3 | `ChatScreen.kt` | 143-147 | **Auto-scroll overrides user** — `LaunchedEffect(messages.size)` unconditionally scrolls to bottom on every new message, even when user is reading history. Should only scroll if already near bottom. |
| D4 | `ChatViewModel.kt` | 128 | **No pagination — hard cap at 100 messages.** `getMessagesForChat(100, 0)` loads only first 100. `getMessagesBefore()` exists in DAO but never called. Users can't scroll to history beyond 100. |
| D5 | `ChatViewModel.kt` | N/A | **No `onCleared()` override** — `voiceNoteRecorder` never cancelled, temp files not cleaned, `clearActive()` not called when ViewModel destroyed. |
| D6 | `ChatScreen.kt` | 622-634 | **Bitmap never recycled** — `ImageThumbnail` decodes bitmaps but no `bitmap.recycle()` in `DisposableEffect.onDispose`. |
| D7 | `ChatScreen.kt` | 684-700 | **VideoThumbnail frame bitmap never recycled** — same as D6, no recycle on dispose. |

#### Medium
| # | File | Line | Issue |
|---|---|---|---|
| D8 | `ChatScreen.kt` | 559-583 | **No copy-to-clipboard** — context menu only has "Delete for me" + "Cancel sending". |
| D9 | `ChatScreen.kt` | N/A | **No edit message** — no menu option, ViewModel has no `editMessage()`. |
| D10 | `ChatScreen.kt` | 562-569 | **No delete-for-everyone / unsend** — only local delete. No protocol-level cancel sent to recipient. |
| D11 | `ChatScreen.kt` | N/A | **Reply not wired** — `Message.replyToId` field exists in domain model but never used. No reply UI, no quote preview, no reply indicator. |
| D12 | `ChatScreen.kt` | 591, 870-872 | **No date separators** — every message shows `HH:mm` only. No "Yesterday", no date headers between days. `ChatListScreen` has proper relative time formatting, chat screen doesn't. |
| D13 | `ChatScreen.kt` | 173, 378 | **No message grouping** — consecutive messages from same sender are not visually merged. Every message is an island with full padding. |
| D14 | `ChatScreen.kt` | 595-610 | **Read receipts misleading** — `READ`/`Seen` state may never be set on outbound messages. No code path calls `markRead` on sender's messages. |
| D15 | `ChatScreen.kt` | 137-141 | **No file preview/confirm before send** — `sendFile(uri)` fires instantly on pick. No confirm dialog, no upload progress indicator. |
| D16 | `ChatScreen.kt` | 304-316 | **Voice record UX bare** — tap-to-toggle, no long-press-to-record, no duration timer, no waveform, no slide-to-cancel. |
| D17 | `ChatScreen.kt` | 167-184 | **No empty state** — blank screen below header when no messages. `ChatListScreen` has a proper `EmptyState()` composable. |
| D18 | `ChatViewModel.kt` | 54-59 | **`ChatUiState` defined but entirely unused** — `isLoading` and `error` fields never collected by ChatScreen. No spinner/shimmer, no error display. |
| D19 | `ChatScreen.kt` | Throughout | **Hardcoded colors everywhere** — `Color(0xFF0A0A0A)`, `Color(0xFF141414)`, `Color(0xFF1E1E1E)` — zero theme usage. No light mode support possible. |
| D20 | `ChatScreen.kt` | 266,347,652,713 | **Minimal accessibility** — content descriptions only on send/call/image/video. Back button is unicode text, voice/attach/mic buttons have no CD. No `semantics{}` blocks. |
| D21 | `ChatViewModel.kt` | 335-349 | **`markAsRead` race** — messages arriving between `getUnreadIncomingUuids()` and the `forEach` are marked read but no receipt sent. |
| D22 | `ChatViewModel.kt` | 320-327 | **Recording polling loop not cancelled on ViewModel clear** — `while(isRecording) { delay(100) }` runs indefinitely if `stopRecording()` never called. |
| D23 | `ChatViewModel.kt` | 456-518 | **`retryUnsentMessages` no rate limit** — all unsent messages resent at once on reconnect, no dedup, no cancellation token. |
| D24 | `ChatViewModel.kt` | 619-623 | **Retry maps IMAGE/VIDEO → TYPE_FILE** — retrying an image or video message loses the type distinction, receiver sees generic file. |
| D25 | `ChatScreen.kt` | 856-868 | **`NoiseOverlay` draws 300 circles every frame** — decorative canvas re-executes on every recomposition. Should cache to Bitmap. |

#### Low
| # | File | Line | Issue |
|---|---|---|---|
| D26 | `ChatScreen.kt` | N/A | No forward message, no share externally, no multi-select. |
| D27 | `ChatScreen.kt` | 153 | Keyboard open doesn't adjust scroll — messages hidden behind keyboard while typing. |
| D28 | `ChatScreen.kt` | N/A | No typing indicators (send or receive). |
| D29 | `ChatScreen.kt` | 329 | `InputBar` clips at 4 lines with no internal scroll — user can't see full text beyond 4 lines. |
| D30 | `ChatScreen.kt` | 295 | No emoji picker or GIF support. |
| D31 | `ChatScreen.kt` | 430-498 | Voice playback: no seek bar, no progress/duration, no pause (stop restarts from beginning). |
| D32 | `ChatViewModel.kt` | 113,125 | `markAsRead` called twice on resume — `ON_RESUME` + `LaunchedEffect(Unit)`. |
| D33 | `ChatViewModel.kt` | 444 | `processPending()` called on every relay send — potential duplicate processing on rapid sends. |
| D34 | `ChatViewModel.kt` | 192,228 | Temp files not cleaned on all error paths — file copied to cache but not deleted if send fails after DB insert. |
| D35 | `ChatListScreen.kt` | 98 | `isDiscovering` hardcoded to `true` — always shows "Discovering nearby" regardless of actual state. |
| D36 | `ChatListScreen.kt` | 61-64 | Missing `navigationBarsPadding()`/`statusBarsPadding()`/`imePadding()` — content obscured on gesture nav devices. |
| D37 | `ChatScreen.kt` | 627-634 | `inSampleSize = 2` hardcoded — no bounds-decoding to calculate optimal size for target view dimensions. |
| D38 | `ChatScreen.kt` | 179-181 | New lambda instances per recomposition — `onDelete`/`onCancel`/`onRetry` recreated each render. |
| D39 | `ChatScreen.kt` | 870-872 | `SimpleDateFormat` allocated per message — 100 messages = 100 allocations per recomposition. |

---

---

## Database Layer Audit

### Critical
| # | File | Line | Issue |
|---|---|---|---|
| DB1 | `SignalProtocolStoreImpl.kt` | 58,61,76,84,91,96,100,103,107,113,118,122,125,135,140,144,147,151,155,174,182,189,199,205,209 | **`runBlocking` on all 25 protocol store methods.** Libsignal calls these synchronously during encrypt/decrypt. `decryptMessage()` runs on `Dispatchers.IO` → `SessionCipher.decrypt()` → store call → `runBlocking` blocks the IO dispatcher thread. Concurrent relay inbox sync (many gift-wraps) can exhaust the thread pool. No simple fix — the store must become fully suspend-based, but `SignalProtocolStore` is a Java interface. |
| DB2 | `RelaySessionService.kt` | 27–59 | **No `@Transaction` on multi-step writes.** `onSessionReady()` re-encrypts messages, upserts to `relay_outbox`, marks messages as `SENT_RELAY` — all sequential without a transaction. Crash mid-loop = messages marked sent but not actually in outbox → silently lost. |
| DB3 | `ContactDetailViewModel.kt` | 69–87 | **`hardDelete()` touches 4 DAOs with no `@Transaction`.** Deletes messages → sessions → prekeys → contact sequentially. Crash between steps = orphaned rows, inconsistent state. |
| DB4 | `ContactDao.kt` | `getByNostrPublicKey()` | **Missing index on `nostrPublicKey` column.** Called on every incoming relay gift-wrap message. Full table scan per message. Add `@Index` on `nostrPublicKey`. |

### High
| # | File | Line | Issue |
|---|---|---|---|
| DB5 | `SessionRepository.kt` + `Impl` + DI | all | **SessionRepository is dead code.** Defined, implemented, Hilt-registered — but **zero consumers inject it.** No ViewModel, Service, or Manager calls any method. The `sessions` table is never populated. |
| DB6 | `entity/Session.kt`, `dao/SessionDao.kt`, `domain/model/Session.kt`, `repository/SessionRepository*.kt`, `DatabaseModule.kt:68–69` | all | **`sessions` table is a zombie.** Table exists in schema, full stack (entity + DAO + domain model + repository + DI binding) wired, but table is **never written to** — `sessionDao.insert()` only called from dead repository. Only access is `sessionDao.deleteByRemotePublicKey()` in `hardDelete()` — deleting from an always-empty table. Plaintext ratchet key columns (`rootKey`, `chainKeySend`, `chainKeyRecv`) are a security risk even if unused. Remove the entire stack. |
| DB7 | `schema` | N/A | **Two separate session storage systems.** `sessions` (custom Double Ratchet with plaintext key columns, dead) + `signal_sessions` (libsignal opaque blobs, active). If anyone ever populates the dead table, two conflicting crypto states coexist. |
| DB8 | `ContactDetailViewModel.kt` | 32,83 | **ViewModel bypasses repository layer.** Injects `SessionDao` directly instead of using `SessionRepository`. Also injects `ContactDao`, `MessageDao`, `PreKeyDao` directly. `ChatListViewModel` / `ChatViewModel` also inject raw DAOs. Repository abstractions exist but are half-used — architectural inconsistency. |
| DB9 | `PendingRequestDao.kt` | `getByPeer()` | **No `ORDER BY`, no `LIMIT 1`.** Multiple requests from the same peer → SQLite returns an arbitrary row. Add `ORDER BY createdAt DESC LIMIT 1`. |
| DB10 | `SessionDao.kt` | `getByRemotePublicKey()` | **Returns arbitrary session.** Sessions keyed by `sessionId`, not `remotePublicKey`. If a contact has multiple sessions, returns a random one. No `ORDER BY`. Already moot since table is empty — but the bug is in the design. |

### Moderate
| # | File | Line | Issue |
|---|---|---|---|
| DB11 | `RelayStateDao.kt` | `setLastSeen()` | **Raw `INSERT OR REPLACE` SQL** instead of `@Insert(onConflict = REPLACE)`. Works but inconsistent with rest of codebase. |
| DB12 | `entity/Profile.kt`, `entity/Contact.kt` | all | **Field duplication across tables.** `displayName`, `avatarHash`, `avatarBlob` duplicated in both `profiles` and `contacts`. Profile updates don't propagate to Contact row → stale display info. |
| DB13 | `PreKeyDao.kt`, `CallHistoryDao.kt` | N/A | **No TTL cleanup.** `prekey_bundles` and `call_history` grow indefinitely. No pruning queries exist for either table. |
| DB14 | `MessageDao.kt` | `getUnsentMessagesForChat()`, `markDelivered()`, `markRead()`, `markSentRelay()` | **Enum names hardcoded in SQL strings.** `deliveryState = 'SAVED'`, `deliveryState IN ('SENT_LOCAL','SENT_RELAY')` — if `MessageDeliveryState` enum is renamed, these queries silently break. Room can't verify string literals in raw `@Query`. |
| DB15 | `SignalProtocolStoreImpl.kt` | 41–51 | **Identity key pair cached in-memory, never refreshed.** `cachedIdentityKeyPair` + `cachedRegistrationId` set once via `setIdentityKeyPair()`/`setRegistrationId()`. If the underlying identity is rotated, the cached values are stale with no invalidation path. |
| DB16 | `dao/SignalSessionDao.kt` | `getSubDeviceSessions()` | **LIKE query with concatenation.** `address LIKE :name \|\| ':%'` — string building in SQL. Room parameterizes but the pattern is fragile. |
| DB17 | `entity/Message.kt` | 13–14 | **`messageUuid` defaults at construction.** `UUID.randomUUID().toString()` called in data class body — Room may or may not preserve this on `@Insert`. Depends on how Room generates the INSERT statement. Explicitly set before insert to be safe. |

### Minor
| # | File | Line | Issue |
|---|---|---|---|
| DB18 | `util/ByteArrayConverter.kt` | all | **Dead code.** Not registered in `@TypeConverters` on `OffneticDatabase`. Room natively handles `ByteArray` columns. |
| DB19 | `MessageDao.kt` | `insert()` | **Returned `Long` ID discarded in 8 of 12 call sites.** `NcapManagerImpl` (4x), `RelayInboxHandler` (2x), `AddContactConfirmViewModel` (1x), `MessageRepositoryImpl.insert()` (1x) all ignore the auto-generated PK. Only `ChatViewModel` captures it for subsequent `update()`. |
| DB20 | `SessionDao.kt` | `getAll()` | **Flow never collected.** `SessionDao.getAll()` → `SessionRepositoryImpl.getAll()` → `Result.Success(...)` — but no consumer subscribes. Dead reactive pipeline. |
| DB21 | `RelayOutboxDao.kt` | `evictOldestPending()` | **Fragile subquery.** `NOT IN (SELECT ... LIMIT :cap)` — unusual SQLite pattern. If the subquery returns 0 rows (no pending entries for chat), `NOT IN (<empty>)` evaluates to TRUE for all rows — but there are no rows to match, so it's a no-op. Works but hard to reason about in code review. |
| DB22 | `MessageDao.kt` | `getChatSummaries()` | **Complex subquery without covering index.** `INNER JOIN (SELECT chatId, MAX(id) AS maxId FROM messages GROUP BY chatId)` — no index on `messages(chatId, id)`. Preexisting index on `messages(chatId, timestamp)` helps but `MAX(id)` can't use it. |
| DB23 | `OffneticDatabase.kt` | 87–98 | **Companion `getInstance()` is dead code.** Zero callers. If invoked, creates unencrypted DB (no SQLCipher `SupportFactory`) — data-at-rest protection bypassed. Hilt `DatabaseModule` is the real path. Remove or add deprecation warning. |

### Already tracked (cross-reference)
| # | Existing # | Issue |
|---|---|---|
| DB24 | O4 | `fallbackToDestructiveMigration()` — all data wiped on schema change |
| DB25 | O22 | `exportSchema = false` — can't test migrations |
| DB26 | O23 | Companion `getInstance()` dead code (duplicate of DB23) |

---

## Connection Requests Tab Audit

Feature path: `RequestsScreen.kt` → `RequestsViewModel.kt` → `RelayRequestManager.kt`; inbound via `RelayInboxHandler.handleRequest/handleBundle`; outbound created in `ChatViewModel.sendEncrypted`; DAO `PendingRequestDao`. The Nearby (NCAP) path does not use this table.

### High
| # | File | Line | Issue |
|---|---|---|---|
| CR1 | `RelayRequestManager.kt` | 60–63 | **Accept marks `ACCEPTED` even when the bundle never sends, and never retries → permanent, invisible half-open connection.** `sendBundle()` return value is discarded and `updateState(ACCEPTED)` runs unconditionally. If you tap Accept while offline / before relays reconnect, the peer never receives your prekey bundle, so they can't build a session to you. Sent bundles have **no retry** (unlike outbound *requests*, which `republishOutbound` retries — `sendBundle` is only ever called here, fire-and-forget). Worse, after Accept you've added them as a contact, so their retries hit `handleRequest:126` (`getByNostrPublicKey != null`) and are **silently dropped**. Channel stays broken until their 72h/6-attempt budget expires, with no UI indication and no recovery path. Elaborates/supersedes **#43**. |

### Medium
| # | File | Line | Issue |
|---|---|---|---|
| CR2 | `RelayRequestManager.kt` / `RelayInboxHandler.kt` | 66–69 / 138 | **"Ignore" is not durable — an ignored requester reappears.** `ignoreRequest` sets state `EXPIRED` but adds no contact and no suppression. The sender (no session yet) keeps republishing; the re-arriving request hits `upsert` with `OnConflictStrategy.REPLACE` keyed on `senderNpub` → row resurrects to `PENDING` (entity default). Reappears up to the sender's `MAX_REPUBLISH` (6, and that counter resets on the sender's app restart). "Ignore" effectively behaves like "snooze 5 minutes." |
| CR3 | `PendingRequestDao.kt` / `RelayRequestManager.kt` | `getInboundPending()` / 34 | **Inbound requests never time-expire.** The list query filters `direction='INBOUND' AND state='PENDING'` only — it never checks `expiresAt`. Requests carry a 30-day `expiresAt`, but nothing enforces it: `deleteExpired`/`clear` are dead code (zero callers) and no job marks time-expired rows. Untouched requests sit in the tab (and in the badge count) forever. |
| CR4 | `RequestsScreen.kt` | 168–189 | **Accept/Ignore have no disabled/in-progress state → double-taps + no feedback.** Buttons stay enabled while the action's coroutine runs a network round-trip (send bundle). On a slow link the row doesn't change for seconds, so users tap Accept again → the accept runs twice and the peer gets duplicate bundles. No spinner, no "Accepting…", no lock. Amplifies CR1's invisibility. |
| CR5 | `RequestsScreen.kt` / `RelayInboxHandler.kt` | 143–164 / 131 | **Sender-controlled display name is spoofable/blank/unbounded, and identity shown is weak.** The row renders the `name` straight from the sender's packet plus only 12 chars of the key, with no "unverified" signal and no way to view/verify the full key before accepting. `"name":""` (empty, not absent) → blank avatar + blank name (`optString` returns `""`, not the fallback), and accepting saves a contact with an empty name. The name `Text` has no `maxLines`/ellipsis, so long/multi-line names inflate the row. Enables impersonation of a known contact's name. |

### Low
| # | File | Line | Issue |
|---|---|---|---|
| CR6 | `RequestsViewModel.kt` | 31–43 | **Accept/Ignore concurrency race.** Nothing locks the row; tapping Accept then Ignore launches two coroutines that both write state (last-writer-wins). Can end `EXPIRED` after a contact was added and a bundle sent — peer thinks connected, your side shows ignored (or vice-versa). |
| CR7 | `RelayRequestManager.kt` | 82–85 | **Mutual-request: accepter's queued messages never flush.** If both sides sent requests, accepting creates a local session but never calls `onSessionReady(self)`; `republishOutbound` then marks the mirror outbound `ACCEPTED` without flushing. The accepter's `SAVED` messages to that peer are never encrypted/sent. |
| CR8 | `RequestsViewModel.kt` | 27–52 | **List is not reactive.** Loads on init + after accept/ignore only. A request arriving via relay while the screen is open won't appear until re-entry (the badge count *does* update live) — stale "0 shown vs N in badge" mismatch. |
| CR9 | `RelayInboxHandler.kt` | 124–126 | **Self/replayed request not rejected.** No guard for `senderNpub == own npub`; a replay of your own request could create a "request from yourself" row. Edge/unlikely. |

### Note
| # | Cross-ref | Issue |
|---|---|---|
| CR10 | DB9 | **DB9's fix is inert.** `getByPeer` (the method DB9 added `ORDER BY … LIMIT 1` to), plus `deleteExpired` and `clear`, all have **zero callers**. The DB9 change is harmless but has no runtime effect. |

Also relevant, already tracked: **#43** (accept bundle-send failure — expanded by CR1), **#44** (peer bundle processing failure silently ignored, contact still created), **#42** (outbound republish check-then-act race), **#24** (concurrent `sendEncrypted` duplicates connection requests).

---

## Notes
- Total issues: **86 runtime bugs** + **26 hardcoded design items** + **33 second-pass findings** + **7 initial chat issues** + **39 deep chat audit** + **26 database audit** + **10 connection-requests audit** (CR1–CR10; CR1 & CR10 cross-reference #43 & DB9) = **227 total**
- No TODO/FIXME/HACK comments found in codebase
- All cryptographic parameters (AES key/nonce/tag sizes, Kyber-1024, Bech32, Signal protocol values) are correctly hardcoded per protocol spec
