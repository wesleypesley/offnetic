# Remaining Issues 2 — offnetic

> **~62 open** | Last updated: after batch #11 (8d259c0)

---

## Critical

### Signal Protocol
| # | File | Issue |
|---|---|---|
| 16 | `SignalProtocolManager.kt:190-193` | `hasSession()` calls `runBlocking` on caller's thread — ANR risk |

---

## UI / ViewModels

### Bugs
| # | File | Issue |
|---|---|---|
| 77 | `CallViewModel.kt:219-236` | `launchIncomingTimeout` collectors never cancelled after timeout |
| 82 | `ChatListViewModel.kt:49` | `_myPublicKey=""` triggers bogus initial DB query |
| D21 | `ChatViewModel.kt:335-349` | `markAsRead` race — messages arriving between DAO calls marked read without receipt |
| D22 | `ChatViewModel.kt:320-327` | Recording polling loop not cancelled on ViewModel clear |
| D32 | `ChatViewModel.kt:113,125` | `markAsRead` called twice on resume |
| D33 | `ChatViewModel.kt:444` | `processPending()` duplicate processing on rapid sends |
| D34 | `ChatViewModel.kt:192,228` | Temp files not cleaned on all error paths |
| D35 | `ChatListScreen.kt:98` | `isDiscovering` hardcoded to `true` |

### Critical
| # | File | Issue |
|---|---|---|
| D1 | `ChatScreen.kt:466-473` | MediaPlayer leak — if `prepare()` throws after `setDataSource` succeeds, orphaned player |
| C6 | `ChatScreen.kt:451-470` | MediaPlayer created without cleanup on recompose |

### Missing Features
| # | File | Issue |
|---|---|---|
| D9 | `ChatScreen.kt` | No edit message |
| D10 | `ChatScreen.kt` | No delete-for-everyone / unsend |
| D11 | `ChatScreen.kt` | Reply not wired — `Message.replyToId` field exists but unused |
| D13 | `ChatScreen.kt` | No message grouping — every bubble is an island |
| D14 | `ChatScreen.kt` | Read receipts misleading — `READ`/`Seen` may never be set |
| D15 | `ChatScreen.kt` | No file preview/confirm before send |
| D16 | `ChatScreen.kt` | Voice record UX bare — tap-to-toggle, no long-press, no waveform, no slide-to-cancel |
| D18 | `ChatViewModel.kt` | `ChatUiState` defined but unused — no loading/error states |
| D20 | `ChatScreen.kt` | Minimal accessibility — only 2 content descriptions, no `semantics{}` blocks |
| D26 | `ChatScreen.kt` | No forward message, share externally, or multi-select |
| D27 | `ChatScreen.kt` | Keyboard open doesn't adjust scroll — messages hidden behind keyboard |
| D28 | `ChatScreen.kt` | No typing indicators (send or receive) |
| D29 | `ChatScreen.kt` | `InputBar` clips at 4 lines with no internal scroll |
| D30 | `ChatScreen.kt` | No emoji picker or GIF support |
| D31 | `ChatScreen.kt` | Voice playback: no seek bar, no progress/duration, no pause (stop restarts from beginning) |

### Visual / Polish
| # | File | Issue |
|---|---|---|
| D19 | `ChatScreen.kt` | Hardcoded colors everywhere — `Color(0xFF0A0A0A)`, etc. `OffneticColors` tokens exist but not applied to ChatScreen |
| D25 | `ChatScreen.kt` | `NoiseOverlay` draws 300 circles every frame — should cache to Bitmap |
| D36 | `ChatListScreen.kt` | Missing `navigationBarsPadding()` / `statusBarsPadding()` / `imePadding()` |
| D37 | `ChatScreen.kt` | ✅ Fixed in batch #11 (bounds-based decode) |
| D38 | `ChatScreen.kt` | New lambda instances per recomposition — `onDelete`/`onCancel`/`onRetry` recreated |
| D39 | `ChatScreen.kt` | `SimpleDateFormat` allocated per message — 100 messages = 100 allocations |

### Incomplete
| # | File | Issue |
|---|---|---|
| C2 | `ChatScreen.kt` | Hardcoded backgrounds `Color(0xFF0A0A0A)` and `Color(0xFF141414)` — `OffneticColors` object exists but not used here |

---

## Database Layer

| # | File | Issue |
|---|---|---|
| DB1 | `SignalProtocolStoreImpl.kt` | **`runBlocking` on all 25 protocol store methods.** Partially mitigated (single-threaded dispatcher), but `runBlocking` still calls. Libsignal requires synchronous Java interface |
| DB8 | `ContactDetailViewModel.kt`, `ChatViewModel`, `ChatListViewModel` | ViewModels bypass repository layer — inject raw DAOs directly. Repositories exist but half-used |
| DB12 | `entity/Profile.kt`, `entity/Contact.kt` | Field duplication — `displayName` duplicated in both tables. Profile updates don't propagate to Contact |
| DB14 | `MessageDao.kt` | Enum names hardcoded in SQL strings — `deliveryState = 'SAVED'` etc. silently break on rename |
| DB15 | `SignalProtocolStoreImpl.kt` | Identity key pair cached in-memory, never refreshed. Stale after rotation |
| DB17 | `entity/Message.kt` | `messageUuid` defaults at construction — Room may or may not preserve on `@Insert` |
| DB19 | `MessageDao.kt` | Returned `Long` ID discarded in 8 of 12 call sites |
| DB21 | `RelayOutboxDao.kt` | Fragile `NOT IN (SELECT ... LIMIT :cap)` subquery — hard to reason about |

---

## Onboarding & Build

### Critical
| # | File | Issue |
|---|---|---|
| O2 | `ProfileSetupScreen.kt` ✅ | Fixed in batch #11 (save state machine). Minor gap: `Failed` state not surfaced to user |
| O3 | `PermissionSlide.kt` | Permission grant/deny results ignored — user can deny all and proceed, then crash |
| O6 | `AndroidManifest.xml` | `uses-feature required="true"` on BLE, Wi-Fi Direct, Camera, Microphone — won't install on devices missing these |

### High
| # | File | Issue |
|---|---|---|
| O7 | `app/build.gradle.kts` | `isMinifyEnabled = true` / `isShrinkResources = true` — needs real device test with R8 enabled |
| O8 | `app/build.gradle.kts` | Multidex dependency hardcoded `"androidx.multidex:multidex:2.0.1"` — not in version catalog |
| O9 | `app/build.gradle.kts` | ~160-line libsignal metadata stripping + pre-dexing tasks — tightly coupled to AGP internals, hardcoded paths |
| O15 | `app/build.gradle.kts` | ABI filter `arm64-v8a` only — no `x86_64` for emulators |

### Medium
| # | File | Issue |
|---|---|---|
| O16 | `SplashScreen.kt` | No error handling after delay — if `onDone()` throws, splash freezes forever |
| O18 | `ProfileSetupScreen.kt` | Username regex `^[a-zA-Z0-9_]{2,24}$` too permissive — no reserved words, no profanity filter |
| O19 | `ProfileSetupScreen.kt` | Silent truncation on paste — 30 chars silently drops to 24 with no feedback |
| O20 | `PermissionSlide.kt` | Slide index detected by string matching on `title` parameter — fragile |
| O21 | `PermissionSlide.kt` | Pre-Tiramisu notifications slide has no-op permissions array — button does nothing |
| O24 | `proguard-rules.pro` | Redundant keep rules for libraries that ship consumer rules in AARs |
| O28 | `libs.versions.toml` ✅ | Fixed in batch #11 (dead `sqlcipher = "4.5.3"` removed). Still at `4.5.4` — latest is `4.6.0` |
| O30 | `AndroidManifest.xml` ✅ | Fixed in batch #11 (`maxSdkVersion="30"` added). Harmless clutter removed |
| O32 | `build.gradle.kts` (root) | Global `-opt-in` for `ExperimentalMaterial3Api` and `ExperimentalFoundationApi` — masks unstable API usage |

### Low
| # | File | Issue |
|---|---|---|
| O31 | `AndroidManifest.xml` | Deep link `offnetic://add` with `BROWSABLE` — verify handler validates data URI |

### Build Hack
| # | File | Issue |
|---|---|---|
| O33 | `app/build.gradle.kts` | Hardcoded build-tools `35.0.0` in `preDexLibsignal` task — breaks on SDK update |

---

## Connection Requests

| # | File | Issue |
|---|---|---|
| CR5 | `RequestsScreen.kt` / `RelayInboxHandler.kt` | Sender-controlled display name is spoofable — partial fix applied (maxLines+ellipsis), still missing full key verification and "unverified" signal |
| CR6 | `RequestsViewModel.kt` | Accept/Ignore concurrency race — last-writer-wins on state |

---

## Hardcoded Design

| # | Item | File |
|---|---|---|
| H2 | NDK llvm-strip path `C:/Users/Admin/...` | `app/build.gradle.kts` — breaks on CI/other devs |
| H3 | Build tools version `35.0.0` | `app/build.gradle.kts:178` — breaks on SDK update |

---

## Notes
- `remaining issues.md` covers batches 1–8 (frozen at ~146 open). This file reflects the state after batches 9, 10, and 11.
- Batch #9: Kotlin 2.1 toolchain, Signal races, multi-call video, onboarding progress, chat pagination
- Batch #10: R8 rules, relay hardening, call fixes, DB schema v11, connection requests, repository flows
- Batch #11: UI design system, i18n, central config, chat UX (date separators, links, copy), call-screen insets, onboarding theme colors
- All tests pass: 121 green, 0 failures
- Release + debug builds compile
- ~60% of remaining issues are ChatScreen UI polish

