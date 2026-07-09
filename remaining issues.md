# Remaining Issues — offnetic

> **~146 open** out of original 227 | Last updated: after batch #8 (ea875fd)

---

## Critical / High Severity

### Nearby / P2P
| # | File | Issue |
|---|---|---|
| 9 | `WebRtcManager.kt:816-853` | **Singleton video source shared across peers** — camera fallback operates on wrong peer (multi-call broken) |

### Signal Protocol & Crypto
| # | File | Issue |
|---|---|---|
| 16 | `SignalProtocolManager.kt:190-193` | `hasSession()` calls `runBlocking` on caller's thread — ANR risk |
| 17 | `Nip44.kt:13-16` | **Hardcoded `0x02` parity prefix** — may break interop with odd-y public keys (~50% of keys). Verify `Secp256k1.pubKeyTweakMul` behavior with wrong parity |

### UI / ViewModels
| # | File | Issue |
|---|---|---|
| 20 | `CallViewModel.kt:47` | `internalScope` not lifecycle-aware — leaks coroutines if `cleanup()` never called |
| 21 | `CallViewModel.kt:49` | `MutableStateFlow` shared across ViewModel instances via `getOrPut` — corrupted state |
| 24 | `ChatViewModel.kt:376` | Concurrent `sendEncrypted` duplicates connection requests (partial: toast deduped, request still duplicate) |

---

## Medium Severity

### Relay Transport
| # | File | Issue |
|---|---|---|
| 29 | `RelayInboxHandler.kt:245-249` | Rate-limit check-then-act race on `ConcurrentHashMap` |
| 30 | `RelayInboxHandler.kt:84-102` | Duplicate message insertion under concurrency (no unique constraint guard) |
| 31 | `RelayInboxHandler.kt:62-63` | Messages without "u" tag silently dropped |
| 32 | `RelayInboxHandler.kt:57-97` | Multiple silent null-returns without diagnostic logging (GiftWrap unwrap, Base64 decode, decrypt, JSON parse) |
| 37 | `RelaySessionService.kt:30-32` | Message timestamps replaced with current time — ordering lost |
| 38 | `RelayPool.kt:38-43` + `RelayMessage.kt:28-42` | NOTICE/CLOSED/EOSE/Unknown relay messages silently dropped; JSON parse errors blanket-caught |
| 39 | `RelayConnection.kt:11` | Interface declares `Flow<String>` (cold) but implementation is `SharedFlow` (hot) |
| 41 | `AttachmentRelayResender.kt:37-39` | Missing attachment file silently skipped forever; no fallback |
| 42 | `RelayRequestManager.kt:87-92` | Outbound republish check-then-act race |

### Nearby / P2P
| # | File | Issue |
|---|---|---|
| 47 | `NcapManagerImpl.kt:649-667` | `sendCallSignal` silently drops errors — caller unaware, waits full 60s timeout |
| 49 | `WebRtcManager.kt:139-185` | `eglBase` leaked on `peerConnectionFactory` init failure |
| 52 | `WebRtcManager.kt:554-561` | Glare handler discards PC + creates orphaned cached offer |
| 53 | `WebRtcManager.kt:897-905` | Grace job fires cleanup on already-disposed PC |
| 54 | `WebRtcManager.kt:383-388` | `acceptCall` no cached offer — doesn't notify remote side |
| 56 | `WifiP2pHandler.kt` | **FILE DELETED** — all WifiP2pHandler issues (56-58) moot. Replaced by Method 7 (see P2P_CALL_INVESTIGATION.md) |
| 59 | `ProxyVideoSink.kt:10-13` | `VideoFrame` leaked when target is null |
| 60 | `NcapForegroundService.kt:162` | `relayPool.connect()` no timeout |

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
| 69 | `SessionRepositoryImpl.kt:40-42` | Flow methods don't wrap exceptions (note: SessionRepository is deleted, but issue remains for other repos) |
| 70 | `BlossomFileService.kt:35-48` | Orphaned uploads on relay announcement failure |
| 71 | `BlossomFileService.kt:60-61` | `expectedSize` from untrusted sender as download cap — DoS vector |
| 73 | `RelaySessionService.kt:46-49` | Identical timestamps produce non-deterministic eviction |

### UI / ViewModels
| # | File | Issue |
|---|---|---|
| 77 | `CallViewModel.kt:219-236` | `launchIncomingTimeout` collectors never cancelled after timeout |
| 79 | `ChatViewModel.kt:229` | `reachability.value` snapshot read — stale between check and send |
| 80 | `ChatViewModel.kt:565-598` | `deleteMessage`/`cancelMessage` silently swallow DB errors |
| 82 | `ChatListViewModel.kt:49` | `_myPublicKey=""` triggers bogus initial DB query |
| 84 | `MainActivity.kt:57,64` | `StateFlow` used as one-shot event channel — fragile navigation |
| 85 | `ActiveChatTracker.kt:8` | No disposal mechanism — suppressed notifications for dead ViewModels |
| 86 | `CallActivity.kt:727-751` | Old SurfaceViewRenderer sinks not removed on rotation |

---

## Database Layer Audit

### Critical
| # | File | Line | Issue |
|---|---|---|---|
| DB1 | `SignalProtocolStoreImpl.kt` | 58,61,76,84,91,96,100,103,107,113,118,122,125,135,140,144,147,151,155,174,182,189,199,205,209 | **`runBlocking` on all 25 protocol store methods.** Partially mitigated: SignalProtocolManager now uses single-threaded dispatcher to prevent IO pool exhaustion, but `runBlocking` still calls. Libsignal requires the store to be a synchronous Java interface. |

### High
| # | File | Line | Issue |
|---|---|---|
| DB8 | `ContactDetailViewModel.kt` | 32,83 | **ViewModel bypasses repository layer.** `ContactDetailViewModel`, `ChatListViewModel`, `ChatViewModel` inject raw DAOs directly. Repository abstractions exist but are half-used — architectural inconsistency. |

### Moderate
| # | File | Line | Issue |
|---|---|---|
| DB11 | `RelayStateDao.kt` | `setLastSeen()` | **Raw `INSERT OR REPLACE` SQL** instead of `@Insert(onConflict = REPLACE)`. |
| DB12 | `entity/Profile.kt`, `entity/Contact.kt` | all | **Field duplication across tables.** `displayName` duplicated in both `profiles` and `contacts`. Profile updates don't propagate to Contact row. |
| DB14 | `MessageDao.kt` | multiple | **Enum names hardcoded in SQL strings.** `deliveryState = 'SAVED'` etc. — if `MessageDeliveryState` enum is renamed, queries silently break. |
| DB15 | `SignalProtocolStoreImpl.kt` | 41–51 | **Identity key pair cached in-memory, never refreshed.** If underlying identity is rotated, cached values are stale. |
| DB16 | `dao/SignalSessionDao.kt` | `getSubDeviceSessions()` | **LIKE query with concatenation.** Fragile string building in SQL. |
| DB17 | `entity/Message.kt` | 13–14 | **`messageUuid` defaults at construction.** Room may or may not preserve on `@Insert`. |

### Minor
| # | File | Line | Issue |
|---|---|---|
| DB19 | `MessageDao.kt` | `insert()` | **Returned `Long` ID discarded in 8 of 12 call sites.** Only `ChatViewModel` captures it. |
| DB21 | `RelayOutboxDao.kt` | `evictOldestPending()` | **Fragile subquery.** `NOT IN (SELECT ... LIMIT :cap)` — hard to reason about. |
| DB22 | `MessageDao.kt` | `getChatSummaries()` | **Complex subquery without covering index.** No index on `messages(chatId, id)`. |

### Still tracked (cross-reference)
| # | Existing # | Issue |
|---|---|---|
| DB25 | O22 | `exportSchema = false` — can't test migrations |

---

## Connection Requests Tab Audit

### Medium
| # | File | Line | Issue |
|---|---|---|
| CR2 | `RelayRequestManager.kt` / `RelayInboxHandler.kt` | 66–69 / 138 | **"Ignore" is not durable.** Sender republishes; request resurrects via `upsert` with `OnConflictStrategy.REPLACE`. |
| CR5 | `RequestsScreen.kt` / `RelayInboxHandler.kt` | 143–164 / 131 | **Sender-controlled display name is spoofable.** (partial: maxLines+ellipsis added, still missing full key verification and "unverified" signal) |

### Low
| # | File | Line | Issue |
|---|---|---|
| CR6 | `RequestsViewModel.kt` | 31–43 | **Accept/Ignore concurrency race.** Last-writer-wins on state. |
| CR7 | `RelayRequestManager.kt` | 82–85 | **Mutual-request: accepter's queued messages never flush.** |
| CR9 | `RelayInboxHandler.kt` | 124–126 | **Self/replayed request not rejected.** No guard for `senderNpub == own npub`. |

---

## Hardcoded Design Decisions

### High — Should Be Configurable
| # | Item | File | Current Value |
|---|---|---|---|
| H2 | NDK llvm-strip path | `app/build.gradle.kts:308` | `C:/Users/Admin/...` (CI/other devs broken) |
| H3 | Build tools version | `app/build.gradle.kts:178` | `35.0.0` |
| H4 | Default relay URLs | `RelayPool.kt:78-82` | 4 hardcoded WSS URLs |
| H5 | Default Blossom servers | `BlossomClient.kt:28-31` | 3 hardcoded HTTP URLs |
| H6 | STUN servers | `WebRtcManager.kt:779-780` | `stun.l.google.com:19302` |

### Medium — Should Be Configurable
| # | Item | File | Current Value |
|---|---|---|---|
| H7 | P2P candidate port | `WebRtcManager.kt:1006` | `58000` |
| H8 | P2P subnet filter | `WebRtcManager.kt:1000` | `192.168.49.` |
| H9 | Max file size (3 duplicates) | 3 files | `100MB` — changing one misses others |
| H10 | Call timeout | 2 files | `60s` |
| H11 | ICE gathering timeout | `WebRtcManager.kt:283,481` | `8s` |
| H12 | Stale call offer drop | `RelayInboxHandler.kt:169` | `45s` |
| H13 | Blossom HTTP timeouts | `BlossomModule.kt:19-21` | `30/120/180s` |

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
| H21 | PiP marginTop | `activity_call.xml:100` | `152dp` | Breaks on tall status bars |
| H22 | Top padding | `activity_call.xml:62` | `56dp` | No cutout handling |
| H23 | Bottom padding | `activity_call.xml:121` | `48dp` | No gesture nav |
| H24 | Inline color values | `CallActivity.kt` | `0x40FFFFFF`, etc. | Not in colors.xml |
| H25 | Display name truncation | `CallActivity.kt:215` | `take(12)` | Arbitrary cutoff |
| H26 | Finish delay | `CallActivity.kt:315` | `1500ms` | Hardcoded UX timing |

---

## Second-Pass Findings (Onboarding, Manifest, Build, Design System)

### Critical
| # | File | Issue |
|---|---|---|
| O1 | `IdentityGenerationScreen.kt` | Fake progress animation decoupled from actual identity generation |
| O2 | `ProfileSetupScreen.kt` | Save-then-navigate with no error handling |
| O3 | `PermissionSlide.kt` | Permission results ignored (partial: logged, still proceeds on deny) |
| O5 | `proguard-rules.pro` | Blanket `-keep class com.offnetic.** { *; }` defeats R8 |

### High
| # | File | Issue |
|---|---|---|
| O7 | `app/build.gradle.kts` | `isMinifyEnabled = false`, `isShrinkResources = false` |
| O8 | `app/build.gradle.kts` | Multidex dependency hardcoded, not in version catalog |
| O9 | `app/build.gradle.kts` | Custom libsignal metadata stripping + pre-dexing (160 lines of ASM) — breaks on AGP update |
| O10 | `Color.kt` (theme) | `labelSmall` typography style not defined |
| O11 | All onboarding | Theme color scheme ignored — `Color(0xFF0A0A0A)` instead of `MaterialTheme.colorScheme.background` |
| O12 | `IdentityGenerationScreen.kt` | All step descriptions hardcoded English |
| O13 | `ProfileSetupScreen.kt` | All onboarding strings hardcoded English |
| O14 | `PermissionSlide.kt` | All permission slide strings hardcoded English |
| O15 | `app/build.gradle.kts` | ABI filter `arm64-v8a` only — no x86_64 for emulators |

### Medium
| # | File | Issue |
|---|---|---|
| O16 | `SplashScreen.kt` | No error handling after delay |
| O17 | `SplashScreen.kt` | Hardcoded background `Color(0xFF141A1A)` |
| O18 | `ProfileSetupScreen.kt` | Username regex too permissive, no profanity filter |
| O19 | `ProfileSetupScreen.kt` | Silent truncation on paste |
| O20 | `PermissionSlide.kt` | Slide index detected by string matching on `title` |
| O21 | `PermissionSlide.kt` | Pre-Tiramisu notifications slide has no-op permissions array |
| O22 | `AppDatabase.kt` | `exportSchema = false` |
| O24 | `proguard-rules.pro` | Redundant keep rules for libraries |
| O25 | `gradle.properties` | `android.enableJetifier=true` — unnecessary |
| O26 | `gradle.properties` | `android.experimental.r8.globalSyntheticsConsumer=true` — libsignal hack |
| O27 | `libs.versions.toml` | Kotlin 1.9.22 approaching EOL |
| O28 | `libs.versions.toml` | SQLCipher version skew |
| O29 | `Color.kt` (theme) | File misnamed, contains Typography/Shapes/Colors |

### Low
| # | File | Issue |
|---|---|---|
| O30 | `AndroidManifest.xml` | Deprecated `BLUETOOTH` / `BLUETOOTH_ADMIN` permissions |
| O31 | `AndroidManifest.xml` | Deep link `offnetic://add` with `BROWSABLE` — verify handler validates URI |
| O32 | `build.gradle.kts` (root) | Global `-opt-in` for unstable Compose APIs |
| O33 | `app/build.gradle.kts` | Hardcoded build-tools `35.0.0` in `preDexLibsignal` |

### Kotlin 1.9 / libsignal Build Hack
| # | File | Issue |
|---|---|---|
| O34 | `app/build.gradle.kts` | Debug APK cannot build — root cause: Kotlin 1.9 vs libsignal 2.1 metadata split |
| O35 | `gradle.properties` | `globalSyntheticsConsumer` required only by libsignal hack |
| O36 | `gradle.properties` | `enableJetifier=true` unnecessary |
| O37 | `app/build.gradle.kts` | Multidex dependency hardcoded |

**Fix for O34-O37:** Upgrade Kotlin to 2.1.x, switch to compose compiler plugin, bump KSP + Hilt, delete the ~160-line libsignal hacks.

---

## Chat Screen — Deep Audit

### Critical
| # | File | Issue |
|---|---|---|
| D1 | `ChatScreen.kt` | MediaPlayer leak — if `prepare()` throws, orphaned player |
| C1 | `ChatScreen.kt` | Links not clickable — URLs render as plain text |
| C6 | `ChatScreen.kt` | MediaPlayer created without cleanup on recompose |

### High
| # | File | Issue |
|---|---|---|
| D4 | `ChatViewModel.kt` | No pagination — hard cap at 100 messages, `getMessagesBefore()` never called |
| C2 | `ChatScreen.kt` | Hardcoded backgrounds ignoring `MaterialTheme.colorScheme` |
| C4 | `ChatScreen.kt` | Hardcoded English strings throughout (Retry, Delete, Loading, etc.) |

### Medium
| # | File | Issue |
|---|---|---|
| D8-D10 | `ChatScreen.kt` | No copy/delete-for-everyone/edit message |
| D11 | `ChatScreen.kt` | Reply not wired — `Message.replyToId` unused |
| D12 | `ChatScreen.kt` | No date separators |
| D13 | `ChatScreen.kt` | No message grouping |
| D14 | `ChatScreen.kt` | Read receipts misleading |
| D15 | `ChatScreen.kt` | No file preview/confirm before send |
| D16 | `ChatScreen.kt` | Voice record UX bare (tap-to-toggle, no long-press) |
| D17 | `ChatScreen.kt` | No empty state |
| D18 | `ChatViewModel.kt` | `ChatUiState` defined but unused — no loading/error states |
| D19 | `ChatScreen.kt` | Hardcoded colors everywhere, no theme, no light mode |
| D20 | `ChatScreen.kt` | Minimal accessibility |
| D21 | `ChatViewModel.kt` | `markAsRead` race |
| D22 | `ChatViewModel.kt` | Recording polling loop not cancelled on ViewModel clear |
| D23 | `ChatViewModel.kt` | `retryUnsentMessages` no rate limit |
| D24 | `ChatViewModel.kt` | Retry maps IMAGE/VIDEO → TYPE_FILE |
| C3 | `ChatScreen.kt` | Back button uses unicode "←" instead of Material Icon |
| C5 | `ChatScreen.kt` | File picker uses `*/*` MIME, `inferMimeType()` missing types |
| C7 | `ChatScreen.kt` | `inferMimeType()` missing `.m4a`/`.aac` etc. |

### Low
| # | File | Issue |
|---|---|---|
| D26-D31 | `ChatScreen.kt` | No forward/share/multi-select, keyboard handling, typing indicators, emoji picker, voice seek bar |
| D33 | `ChatViewModel.kt` | `processPending()` duplicate processing on rapid sends |
| D34 | `ChatViewModel.kt` | Temp files not cleaned on all error paths |
| D36 | `ChatListScreen.kt` | Missing `navigationBarsPadding`/`statusBarsPadding`/`imePadding` |
| D37 | `ChatScreen.kt` | `inSampleSize = 2` hardcoded |
| D38 | `ChatScreen.kt` | New lambda instances per recomposition |

---

## Concurrent Users — Summary

**Relay mode:**
- `RelayPool` buffer overflow → **fixed** (tryEmit → emit)
- `EventDeduper` 5000-entry LRU still too small **(H16)**
- `RelayOutboxProcessor` mutex bottleneck — partial: LIMIT 100 added, still serial

**Nearby mode:**
- Singleton video source still broken **(#9)**
- Auto-accept connections → **fixed** (#7, rejects strangers)

**Both modes:**
- `SignalProtocolManager` no per-peer locking **(#63-65)**

---

## Notes
- `WifiP2pHandler` deleted (390 lines dead code). Method 7 (`WifiNetworkSpecifier`) is the planned replacement.
- `Sessions` table, entity, DAO, repository, domain model — fully purged (DB5/DB6/DB7/DB10/DB18/DB20)
- `enterP2pDiscoveryMode` stub removed
- Release build: zero warnings, all 122 tests pass
- `fallbackToDestructiveMigration()` re-added as safety net alongside proper migration 9→10
