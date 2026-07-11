# Chat Features — offnetic

> Priority-ordered plan for chat UI feature work. No bugs remain — this is pure polish.

---

## Design Polish (alongside Phase A)

Quick fixes to the existing UI — not new features, just making it look professional.

### Header status
Current: green dot + "Nearby" / blue dot + "Internet" / gray dot + "Offline". Redundant — dot and text say the same thing, green vs blue has no meaning.

**Replace with icon-only:**

| State | Icon | Color | Text |
|---|---|---|---|
| Nearby | `Icons.Filled.Bluetooth` (12dp) | `OffneticColors.accentBlue` | None |
| Internet | `Icons.Outlined.Language` (12dp) | `OffneticColors.accentBlue` | None |
| Offline | Nothing | — | — |

- Bluetooth = direct P2P, universal understanding
- Globe = routed through internet
- Same blue for both — icon is the differentiator
- Offline shows nothing — like WhatsApp, absence IS the signal
- Remove the animated dot entirely
- Priority: if both Nearby AND relay available → show Bluetooth (Nearby prevails — direct is better)

### Bubble contrast
Current: both sides are nearly identical dark gray. Theirs = `surface` (#141414), mine = `surfaceVariant` (#1E1E1E). Only alignment tells you who sent what — no color asymmetry.

**Fix:**

| Message | Token | Color | Why |
|---|---|---|---|
| Theirs | `surfaceContainerHighest` | #353535 | Neutral dark gray |
| Mine | New `OffneticColors.bubbleMine` | #1A2A3A | Subtle navy blue tint — instant recognition |

Every messaging app uses color asymmetry. Offnetic's accent blue is subtle enough to not feel like iMessage, but distinct enough to register whose message it is before reading.

**⚠️ Light theme:** `bubbleMine` (#1A2A3A) only works on dark backgrounds. When/if light theme is enabled, use a lighter blue tint or invert to `surfaceContainerHighest` for mine as well.

### Input bar separation
Input bar uses `surface` (#141414) — same as the message area background. No visual divide between input zone and message list.

**Fix:** Input bar → `surfaceVariant` (#1E1E1E). Subtle horizontal divide.

### Header
Uses `surfaceScrim` (semi-transparent overlay). Causes transparency artifacts when messages scroll behind it.

**Fix:** Header → solid `surface` (#141414). Remove `alpha` modifier.

### Timestamp
Currently `textHint` (25% alpha). Unreadable in sunlight.

**Fix:** `textHint` → `OffneticColors.textSubtle` (40% alpha). Move timestamp inside bubble (bottom-right) instead of below it — saves one row per message, works with grouping.

### Read Receipts
Currently: text labels "Saved", "Sending", "Relayed", "Delivered", "Seen" below every bubble. 6 states, 3 colors, cluttered.

**Replace with dots — unique to Offnetic, no other messaging app does this:**

| State | What user sees | Dot |
|---|---|---|
| SAVED / SENT_LOCAL / SENT_RELAY | Nothing — message sent | — |
| DELIVERED | White dot | ○ `5:02 PM` |
| READ | Green dot | ● `5:02 PM` |
| FAILED | Red exclamation | ⚠️ `5:02 PM` |

- Dot and timestamp live inside the bubble, bottom-right (not below it)
- With message grouping, only the last message in a group shows dot + timestamp
- System messages (TYPE_SYSTEM, cancelled) show no dot
- Dot color: white = `textStrong` (#CCFFFFFF), green = `accentGreen` (#4ADE80), red = `danger` (#EF4444)
- No wiring changes needed — `deliveryState` already flows from DB → ChatScreen. Just swap `Text()` labels for a colored `Box(CircleShape)`

**Edge cases:**
| Case | Behavior |
|---|---|
| Failed message | Red `Icons.Filled.ErrorOutline` 12dp instead of dot. Tap to retry |
| Very short message | "Ok" + "5:02 PM" + 8dp dot = fits in bubble. Right-aligned row |
| Pagination: old READ messages | Green dot persists — correct, they were read |
| RTL | Alignment flips (bubble + dot naturally mirror via `isMine`)

### Input bar icons
"+" is still unicode text instead of `Icons.Filled.Add`. Mic button uses a Canvas circle — fine, it's custom.

**Fix:** Replace "+" with `Icon(Icons.Filled.Add, ...)`.

---

## New Design Tokens

Add to `OffneticColors` in `Color.kt`:

```kotlin
val bubbleMine = Color(0xFF1A2A3A)   // subtle navy blue tint for my messages
```

All other design polish items use existing tokens:
- Status icons → `OffneticColors.accentBlue` (#60A5FA)
- Their bubbles → `MaterialTheme.colorScheme.surfaceContainerHighest`
- Input bar → `MaterialTheme.colorScheme.surfaceVariant`
- Header → `MaterialTheme.colorScheme.surface`
- Timestamp → `OffneticColors.textSubtle`

---

## 0. Action Bar (iMessage-style)

Current: bare `DropdownMenu` with text-only items ("Copy text", "Delete for me", "Cancel sending"). Generic Material3 list, no icons, no polish.

**Replace with iMessage-style pill bar:**

```
         ┌──────────────────────────────────────┐
         │    📋       ↩️       🗑️       ✕        │
         │   Copy    Reply   Delete   Cancel     │
         └──────────────────────────────────────┘
```

- Horizontal pill — `Surface(RoundedCornerShape(24.dp))` with `surfaceVariant.copy(alpha = 0.95f)`
- Icon + 10sp label per action — `Column` with Material Icons
- Position: above bubble if in bottom half of screen, below if in top half
- If keyboard is open when long-press triggers: dismiss keyboard first, then show bar (bar must not render behind IME)
- `AnimatedVisibility` fade-in on long-press

**Icons (Material, no custom drawables):**

| Action | Icon |
|---|---|
| Copy | `Icons.Outlined.ContentCopy` |
| Reply | `Icons.AutoMirrored.Filled.Reply` |
| Delete | `Icons.Outlined.Delete` |
| Cancel | `Icons.Filled.Close` |
| Retry | `Icons.Filled.Refresh` |

**Colors — zero hardcoded, all from design tokens:**

| Element | Token |
|---|---|
| Bar background | `MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)` |
| Bar shape | `RoundedCornerShape(24.dp)` |
| Shadow | `tonalElevation = 8.dp`, `shadowElevation = 4.dp` |
| Normal actions (Copy, Reply) | `OffneticColors.textPrimary` |
| Destructive (Delete, Cancel) | `OffneticColors.danger` |
| Success (Retry) | `OffneticColors.accentGreen` |

**Haptics on long-press:**
- `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)` when action bar appears
- `TextHandleMove` haptic on delete/cancel tap (closest Compose equivalent to iOS REJECT)
- Copy, Reply, Retry — **no haptic** (non-destructive, no feedback needed)

**Haptics edge cases:**
| Case | Result |
|---|---|
| No vibration motor (tablets) | API silently no-ops. No crash |
| User disabled vibration | API silently no-ops. No crash |
| Background thread | Doesn't happen — Compose callbacks are main thread |
| Rapid double-tap | Guarded by existing `deleteInProgress` / action state checks |
| TalkBack enabled | No conflict — haptic + audio coexist |
| Battery saver | Android disables haptics cleanly. No crash |

**Actions shown per message type:**

| Message state | Actions |
|---|---|
| My text | Copy, Reply, Delete |
| Their text | Copy, Reply |
| My unsent text | Copy, Reply, Cancel, Delete |
| My image/voice/file | Reply, Delete |
| Cancelled/failed | Reply, Retry, Delete |

**Depends on:** Nothing — replaces existing DropdownMenu

---

## Tier 1: Foundation

### 1. Message Grouping
Consecutive messages from the same sender (within 5 min) merge into a single visual group:
- 2dp gap between grouped messages (instead of 8dp islands)
- Bubble corners adapt — only first and last message get rounded tails
- One timestamp for the whole group
- Group breaks on: sender change, >5 min gap, date boundary
- Grouping function runs in `derivedStateOf` to avoid recomposition on every scroll frame

**Depends on:** Nothing

### 2. Loading / Error States
`ChatUiState` is defined in ChatViewModel but never collected by ChatScreen:
- `isLoading` → shimmer placeholder while initial messages load. Set `isLoading = true` in `init {}`, set `isLoading = false` on first non-empty message emission (use `messages.first { it.isNotEmpty() }` in a `LaunchedEffect` — Room Flow is continuous, never "completes")
- `error` → snackbar or inline banner with retry button. For v1: network/DB errors from repository. Error clears on next successful load
- ChatScreen collects `viewModel.uiState` and renders accordingly

**Depends on:** Nothing

### 3. InputBar Scroll
TextField currently clips at 4 lines with no internal scroll. User can't see what they typed beyond line 4. Add `Modifier.verticalScroll(rememberScrollState())` inside the TextField or switch to `BasicTextField` with scroll.

**Depends on:** Nothing

---

## Tier 2: Core Messaging

### 4. Reply
`Message.replyToId` field already exists on the data model but is never wired:
- Action bar → "Reply" → quote preview appears above input bar
- Preview shows: sender name + truncated message (100 chars max) + X dismiss button (not tap-to-dismiss — too easy to trigger while typing)
- If quoted message is image/video: small 36dp thumbnail next to text preview
- Send with self-contained quote in JSON payload so receiver doesn't need DB lookup
- Receiver renders quoted message above the new bubble using embedded preview

**Edge cases:**
| Case | Behavior |
|---|---|
| Replying to deleted message | "Message unavailable" placeholder |
| Replying to voice note | "🎤 Voice note" instead of raw content |
| Replying to image/file | "📎 filename" |
| Reply chain depth | Only show immediate parent, no nesting |
| Old client receives reply | `replyToId` → `optString` returns "" → renders as normal ✅ |
| Sender deleted from contacts | Quote preview uses truncated public key |

**Depends on:** Grouping (1) — quote preview looks wrong without grouped bubbles. Action bar (0)

---

## Tier 3: Rich Media

### 5. Voice Note UX
Current: tap-to-toggle recording. Bare playback — no seek bar, no progress, stop restarts from beginning.

**Recording flow (WhatsApp-style — hold to record, no pre-state):**
- Existing mic button in input bar → long-press starts recording immediately
- Slide up → lock appears, release finger keeps recording (hands-free)
- Slide left (40% width) → cancel zone highlights red, release to discard
- Release (no cancel zone, no lock) → stops recording, sends voice note
- Keyboard auto-dismisses when recording starts (`focusManager.clearFocus()`)
- On send or cancel, InputBar returns but keyboard stays dismissed (user just recorded, not typing)
- Pulsing red dot + elapsed timer + live waveform while recording
- Capped at 2 minutes (`OffneticConfig.VOICE_NOTE_MAX_DURATION_MS`)
- Visual design matches Offnetic dark theme + design tokens (WhatsApp interaction model, Offnetic skin — not green)
- While recording, send TYPING signal (same payload as #6) so peer sees typing indicator

**Playback:**

- WhatsApp-style solid waveform in bubble (not individual bars — simpler to draw)
- Waveform always visible so voice notes are distinct at a glance
- Tap to play/pause (no auto-play)
- Seek bar — drag to any position, elapsed/total time shown
- Pause resumes from current position (not restart)
- No speed control (diminishing returns)
- Playing while scrolled away — keeps playing (don't auto-stop)
- Two voice notes at once → stop previous, start new (`activePlayer` guard in ViewModel)
- Old messages (no waveform data) → show progress bar only, graceful fallback

**Waveform storage:**
- New Room column `waveformData: ByteArray?` on `Message` entity (Option A)
- Migration v12 — `ALTER TABLE messages ADD COLUMN waveformData BLOB`
- Old rows default to `NULL` → show simple progress bar instead of waveform
- 2-min recording = ~1200 samples at 100ms → ~4.8KB. Fits easily in BLOB column

**Wiring:**

```
ChatViewModel:
  recordState: StateFlow<RecordState>   // Idle, Ready, Recording, Locked  
  waveformPoints: MutableList<Float>     // collected during recording
  
  VoiceNoteRecorder.startRecording()     // long-press on mic
  VoiceNoteRecorder.getMaxAmplitude()    // every 100ms while recording
  VoiceNoteRecorder.stopRecording()      // release → saves file + waveform
  VoiceNoteRecorder.cancelRecording()    // slide left → delete temp file

ChatScreen:
  Mic button in InputBar → long-press gesture (detectTapGestures + pointerInput)
  Record overlay replaces InputBar area → cancel/lock zones, timer, live waveform
  VoiceNoteBubble in LazyColumn → waveform, play button, seek bar, elapsed/total
```

**Gesture handling (one composable, three gestures):**

```kotlin
// Playback bubble
Modifier.pointerInput(Unit) {
    awaitPointerEventScope {
        // Tap → play/pause
        // Long-press → show action bar (from feature #0)
        // Horizontal drag → seek bar
        // Must use manual dispatch — detectTapGestures + detectHorizontalDragGestures conflict
    }
}

// Record mic button  
Modifier.pointerInput(Unit) {
    detectTapGestures(
        onLongPress = { startRecording() }  // cancels keyboard first
    )
    // During recording: horizontalDrag for cancel, verticalDrag for lock
}
```

**Edge cases:**

| Case | Behavior |
|---|---|
| Phone call during recording | `AudioManager.AudioFocusChangeListener` → cancel recording |
| App backgrounded (ON_PAUSE) | Cancel recording, delete temp file (Telegram does this) |
| Phone rotation | ViewModel survives. MediaRecorder uses ApplicationContext → survives. Waveform loop in viewModelScope → survives |
| No waveform data (old message) | Show progress bar instead of waveform. Fallback text: "Audio" |
| Rapid playback taps | `isPlaying` guard → stop + release previous player first |
| Scroll away during playback | Keep playing. `DisposableEffect` does NOT release on dispose (only on message removal) |
| Keyboard during recording | Dismissed on record start. Stays dismissed after send/cancel |
| Max duration reached | Auto-send when cap hit. Toast: "Voice note sent (2 min limit)" |
| Empty recording (tap, no audio) | `file.length() == 0` → discard, don't send, toast nothing |
| Recording TYPING signal | During recording, throttle sends TYPING (same as text typing, feature #6). Peer sees typing bubble. Stops when recording ends |
| Waveform render perf | `List<Float>`.size ≈ 1200 max. Canvas `Path` — single draw call. No perf issue |

**Files touched:**

| File | Change | Lines |
|---|---|---|
| `VoiceNoteRecorder.kt` | Lock support, waveform collection, lifecycle cleanup | 20 |
| `ChatViewModel.kt` | `RecordState` sealed interface, gesture handlers, player guard, waveform loop | 60 |
| `ChatScreen.kt` | Record overlay, `VoiceNoteBubble` with waveform + seek bar, gesture dispatch | 130 |
| `Message.kt` (entity) | New `waveformData: ByteArray?` column | 2 |
| `MessageDao.kt` | No change — Room auto-maps columns. Need DB migration to add column | 0 |
| `OffneticDatabase.kt` | Migration v11 → v12 (ALTER TABLE) | 8 |
| `strings.xml` | Record labels ("Slide to cancel", "Locked") | 4 |

**Haptics:**
| Trigger | Feedback |
|---|---|
| Hold starts recording | `LONG_PRESS` — confirms recording began |
| Slide reaches cancel threshold | `TextHandleMove` — trash turns red, strong pulse |
| Slide up past lock threshold | `CONFIRM` — hands-free engaged |
| Release to send | `CONFIRM` — subtle bump |

**Gesture conflicts:**
- `detectTapGestures` (tap to play) + `detectHorizontalDragGestures` (seek bar) → use `pointerInput` with manual gesture detection to avoid conflict
- Record mode replaces InputBar entirely — no conflict with text input gestures
- Phone rotation mid-recording → `MediaRecorder` dies with Activity. Acceptable — rare edge

**Depends on:** Nothing

### 6. Typing Indicators
New `TYPING` payload via NCAPI + GiftWrap relay. iMessage/Instagram-style — animated dot bubble at the bottom of the message list, not in the header.

**Design:**
- Fake message bubble at the bottom of `LazyColumn` (below real messages, above input bar)
- Styled identically to their messages: `surfaceContainerHighest` (#353535), same shape, same border, same alignment (left)
- Three pulsing dots (6dp) inside the bubble instead of text
- Dots use `onSurface.copy(alpha = animated)` — same color as message text, just animating opacity
- `AnimatedVisibility` fade in on first signal, fade out after 5s silence or new message
- No date separator above the typing bubble (it's not a message)
- When real message arrives, dismiss typing bubble immediately — zero visual jump since styling is identical

**Dot animation:**
```kotlin
infiniteRepeatable(tween(400, delayMillis = index * 200))
// Dot 0: 0→400ms, Dot 1: 200→600ms, Dot 2: 400→800ms → loop
```

**Wiring:**

```
NCAPI path:
  NcapManagerImpl.handleIncomingPayload()
      when TYPING → typingSignals.emit(envelope.senderPublicKey)   ← check != myPublicKey

Relay path:
  RelayInboxHandler.handleGiftWrap()
      when TYPE_TYPING →
          val contact = contactDao.getByNostrPublicKey(senderNpub) ?: return
          if (contact.isBlocked) return                              ← never emit for blocked peers
          typingSignals.emit(contact.publicKey)

ChatViewModel:
  Inject RelayInboxHandler (new dependency)
  merge(ncapManager.typingSignals, relayInboxHandler.typingSignals)
      .collect { senderPk →
          if (senderPk == contactPublicKey) {
              isPeerTyping = true
              typingTimerJob?.cancel()
              typingTimerJob = launch { delay(5000); isPeerTyping = false }
          }
      }
```

**Double-check list (implementer must verify every item):**

| # | Check | Why |
|---|---|---|
| 1 | Relay path checks `contactDao.getByNostrPublicKey` returns non-null | Unknown sender → don't emit |
| 2 | Relay path checks `!contact.isBlocked` | Blocked peer → don't emit, don't leak activity |
| 3 | NCAPI path checks `senderPublicKey != myPublicKey` | Self-echo from relay or NCAPI race |
| 4 | ChatViewModel checks `senderPk == contactPublicKey` | Only show for the active chat |
| 5 | Timer is cancelled in `onCleared()` / `ON_PAUSE` | No stale state after ViewModel dies |
| 6 | `AnimatedVisibility` uses `fadeIn`/`fadeOut` | Not slide — bubble is in LazyColumn, slide would break layout |
| 7 | Typing bubble uses same composable as `MessageBubble` (their style) | Identical visuals to real message, no jump on arrival |
| 8 | TYPING for attachments/file sends | If `sendFile()` / `toggleVoiceRecording()` → can't type. Throttle timer already cancelled on send. No TYPING sent during non-text sends |

**Sending (ChatViewModel):**
- Send immediately on first keystroke, then throttle to every 2s
- Stop when text becomes empty or message is sent
- Cancel throttler on message send
- Do NOT send TYPING during voice recording or file upload

**Edge cases:**

| Case | Behavior |
|---|---|
| TYPING arrives after message (race) | New message from peer → dismiss typing bubble, cancel timer |
| App backgrounded / ON_PAUSE | Cancel throttle + dismiss timer, clear `isPeerTyping` |
| Network drops mid-typing | Peer dismisses after 5s. Reconnect: reset throttle on `ON_RESUME` |
| User deletes all text | Don't send STOP — let peer's timer auto-dismiss |
| Paste without typing | No TYPING sent. Normal |
| Chat switch | `senderPk != contactPublicKey` → ignored. Correct |
| Relay duplicate delivery | Multiple TYPING gift-wraps → each resets same 5s timer. No flicker |
| Both transports emit simultaneously | `merge()` gives both, timer reset each time → extends display. Graceful |
| ChatViewModel destroyed | SharedFlow drops event (no subscribers). Fresh ViewModel starts clean |
| Phone rotation | ViewModel survives. `isPeerTyping` + timer preserved |

**Files touched:**

| File | Change | Lines |
|---|---|---|
| `NcapEnvelope.kt` | `TYPING` enum value | 1 |
| `NcapManager.kt` | `val typingSignals: SharedFlow<String>` | 2 |
| `NcapManagerImpl.kt` | `MutableSharedFlow`, emit in handler with self-check | 6 |
| `RelayControl.kt` | `TYPE_TYPING` constant | 1 |
| `RelayInboxHandler.kt` | `MutableSharedFlow`, handle TYPE_TYPING with block/npub checks | 12 |
| `ChatViewModel.kt` | Inject RelayInboxHandler, merge flows, throttle, timer, lifecycle | 30 |
| `ChatScreen.kt` | `TypingBubble` + `AnimatedDots` in LazyColumn, `AnimatedVisibility` | 45 |

**Depends on:** Nothing

---

## Tier 4: Polish

### 7. Keyboard Handling
`imePadding()` exists since initial commit — makes room for the keyboard. But message list doesn't adapt scroll position.

**WhatsApp-style behavior:**
- Opening keyboard while scrolled up → **don't** auto-scroll. Show a small floating ↓ arrow button at bottom-right of message list
- Arrow: `Icons.Filled.KeyboardArrowDown` in a 36dp circle, `surfaceVariant.copy(alpha = 0.9f)`, subtle shadow
- Tapping arrow → smooth scroll to bottom
- Sending a message → **always** scroll to bottom (override current scroll position), dismiss arrow
- Arrow dismisses when: user scrolls to bottom manually, keyboard closes, or message is sent
- State: `derivedStateOf { isScrolledUp && isKeyboardOpen }` — combines `snapshotFlow { listState.firstVisibleItemIndex > 0 }` and `WindowInsets.ime.getBottom(density) > 0`

**Edge cases:**
| Case | Behavior |
|---|---|
| Keyboard opens at bottom | No arrow — already at bottom |
| Keyboard opens while scrolled up | Arrow appears. User can ignore it |
| User sends message | Force scroll to bottom, dismiss arrow |
| User scrolls to bottom manually | Arrow dismissed (check `firstVisibleItemIndex` in `snapshotFlow`) |
| Keyboard closes (back press) | Arrow dismissed |
| Keyboard opens → arrow → user types (no send) → keyboard closes | Arrow disappears. User stays where they were |
| Float keyboard / height change | Arrow visibility tied to scroll position, not IME height |

**Depends on:** Nothing

### 8. NoiseOverlay Cache
Currently draws 300 white circles at random positions (alpha ~0.018f) on a Canvas every frame — decorative noise texture behind the chat. This is purely cosmetic but runs on every recomposition.

**Fix:** Pre-render the 300 circles to a `Bitmap` once using `remember { }`, then draw the cached bitmap with `drawImage()` or `Image(bitmap)`. Zero visual difference, one allocation instead of 300 draw calls per frame.

**Depends on:** Nothing

---

## Cross-Feature Conflicts & Edge Cases

| Interaction | Conflict? | Resolution |
|---|---|---|
| Action bar + Grouping | Action bar appears per-message, not per-group. Grouping is visual-only — action bar references the tapped message | No conflict |
| Action bar + Reply | Tapping "Reply" on message B while quote from message A is active → replaces quote preview. Dismiss old quote, show new one | Standard UX |
| Action bar + Keyboard | Action bar open → user taps input bar → keyboard opens → action bar dismisses (tap outside behavior). User can't type while action bar is open anyway | Normal |
| Reply + Typing indicators | Typing bubble lives in LazyColumn (iMessage style, feature #6). Reply quote is above input bar. Different spaces — no overlap | No conflict |
| Voice note record + Reply | Recording mode replaces InputBar entirely. Reply quote is hidden during recording. Restored after recording ends or is cancelled | Minor — acceptable |
| Voice note record + Typing indicators | During recording, TYPING is sent (feature #5). Peer sees typing bubble in LazyColumn. Recording UI replaces InputBar on sender side. No conflict on receiver | No conflict |
| Message Grouping + Date separator | Group that crosses midnight: `isSameDay` returns false → group breaks. Correct | No conflict |
| Message Grouping + Pagination | Older messages prepend → `derivedStateOf` re-runs grouping on changed list. No flicker — Compose diffing handles item keys | No conflict |
| Loading state + Grouping | `isLoading = true` → shimmer. Messages arrive → shimmer hides, grouping renders. Single transition | No conflict |
| Keyboard arrow + Reply | Arrow only appears when scrolled up + keyboard open. When replying, user is typically at bottom (typing) → arrow doesn't show | No conflict |
| Typing indicator + Chat switch | Each ChatViewModel tracks only its own peer. TYPING from chat B ignored while user is on chat A | Already handled |
| Voice note long-press + Message long-press | Voice note mic button vs message bubble — different composables, different gesture handlers. No conflict | No conflict |
| Send message mid-recording | Can't happen — record mode replaces InputBar, send button is hidden | No conflict |
| Phone rotation during recording | `MediaRecorder` dies with Activity recreation. Edge case, rare | Acceptable |
| Phone rotation during any feature | ViewModel survives (Hilt). InputBar text is lost (pre-existing, not in scope) | Acceptable |

---

## Dropped

| Feature | Reason |
|---|---|
| Delete for everyone | Complex — cancel signal, race window, peer processing |
| Edit message | Complex — re-encrypt, versioning, edit history |
| Forward / Multi-select | Complex — re-encryption per target peer, reply chain issues |
| Emoji picker | System keyboard already works |

|---

## Build Order

```
Phase A — Foundation (first, parallel)
├── Design Polish           ← MUST come first: header, bubbles, timestamps, read receipts, icons, input bar
├── 0. Action Bar           ← enables reply + all long-press interactions
├── 1. Message Grouping      ← enables reply quote preview + forward layout
├── 2. Loading / Error       ← independent
├── 3. InputBar Scroll       ← independent
├── 7. Keyboard Arrow        ← independent
└── 8. NoiseOverlay Cache    ← independent

Phase B — Interactions (depends on Phase A)
└── 4. Reply                 ← needs: action bar (tap Reply), grouping (quote preview layout)

Phase C — Rich Features (depends on Phase A, independent of each other)
├── 5. Voice Note UX         ← needs: action bar (long-press delete), grouping (bubble styling)
└── 6. Typing Indicators     ← needs: grouping (typing bubble mimics their grouped style)
```

**Rationale:** Design Polish fixes the canvas. Action Bar + Grouping are the two enablers — without them, Reply and Voice Note look wrong. Voice and Typing are independent features that sit on top of Phase A but don't block each other.
