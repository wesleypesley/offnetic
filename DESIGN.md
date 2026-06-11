# DESIGN.md — Offnetic
> Read this before any UI task. Every rule here applies without being asked.
> If a design decision isn't covered here, ask before inventing something.

---

## Core Aesthetic

**Restrained. Dark. Precise.**

Offnetic is a security tool first, a messenger second. The UI should feel like it was built by engineers who care deeply about craft — not a design agency chasing trends. No decoration that doesn't serve a function. No color that doesn't carry meaning. Every pixel earns its place.

Reference: `Offnetic_Onboarding.jsx` — that mockup is the visual contract for the whole app.

---

## Color System

### Dark Mode — Design Dark First Always

| Token | Hex | Usage |
|---|---|---|
| `background` | `#0A0A0A` | All screen backgrounds |
| `surface` | `#141414` | Cards, bottom sheets, dialogs |
| `surfaceVariant` | `#1E1E1E` | Input fields, chips, secondary surfaces |
| `border` | `rgba(255,255,255,0.08)` | All borders and dividers |
| `borderStrong` | `rgba(255,255,255,0.14)` | Active / focused borders |
| `onBackground` | `#FFFFFF` | Primary text |
| `onBackgroundMuted` | `rgba(255,255,255,0.45)` | Secondary text, descriptions |
| `onBackgroundSubtle` | `rgba(255,255,255,0.25)` | Placeholders, timestamps, hints |
| `accent` | `#FFFFFF` | Primary buttons, active states |
| `onAccent` | `#0A0A0A` | Text on white buttons |
| `statusOnline` | `#4ADE80` | Online dot, nearby ping indicator |
| `destructive` | `#EF4444` | Delete, erase, block actions |
| `onDestructive` | `#FFFFFF` | Text on destructive buttons |

### Light Mode — Must Match Dark Mode Intent

| Token | Hex | Usage |
|---|---|---|
| `background` | `#F5F5F5` | All screen backgrounds |
| `surface` | `#FFFFFF` | Cards, bottom sheets, dialogs |
| `surfaceVariant` | `#EBEBEB` | Input fields, chips |
| `border` | `rgba(0,0,0,0.08)` | Borders and dividers |
| `borderStrong` | `rgba(0,0,0,0.14)` | Active / focused borders |
| `onBackground` | `#0A0A0A` | Primary text |
| `onBackgroundMuted` | `rgba(0,0,0,0.45)` | Secondary text |
| `onBackgroundSubtle` | `rgba(0,0,0,0.25)` | Placeholders, hints |
| `accent` | `#0A0A0A` | Primary buttons |
| `onAccent` | `#FFFFFF` | Text on dark buttons |
| `statusOnline` | `#16A34A` | Online indicator (darker for light bg) |
| `destructive` | `#DC2626` | Delete, erase, block |

### Color Rules
- Green is used **only** for online/nearby status. Never as a general accent or brand color.
- No purple. No gradients. No rainbow. No colorful shadows.
- No transparency effects on text.
- Noise texture overlay (Canvas, alpha `0.035`) on all major backgrounds for depth.
- Never let Material3 default colors bleed in — override `containerColor`, `contentColor`, and `indicatorColor` explicitly everywhere.

---

## Typography

**Font: Syne** — loaded via Google Fonts in Compose.

```kotlin
val SyneFamily = FontFamily(
    Font(R.font.syne_regular, FontWeight.Normal),
    Font(R.font.syne_medium, FontWeight.Medium),
    Font(R.font.syne_semibold, FontWeight.SemiBold),
    Font(R.font.syne_bold, FontWeight.Bold),
    Font(R.font.syne_extrabold, FontWeight.ExtraBold),
)
```

| Role | Weight | Size | Letter Spacing | Usage |
|---|---|---|---|---|
| Display | ExtraBold | 36–40sp | -1sp | Onboarding titles |
| Headline | Bold | 24–28sp | -0.5sp | Screen titles |
| Title | Bold | 18–20sp | -0.3sp | Section headers |
| Body | Medium | 15–16sp | 0sp | Main readable text |
| Label | SemiBold | 12–13sp | 0.3sp | Chips, tags, captions |
| Micro | SemiBold | 10–11sp | 1.5–2.5sp | ALL CAPS labels, nav labels |

Micro labels are always uppercase + letter-spaced. Never uppercase body text.

**Never use:** Inter, Roboto, system-ui, or any default fallback as the primary font.

---

## Spacing System

Base unit: **4dp**

| Token | Value | Usage |
|---|---|---|
| `space-1` | 4dp | Tight internal gaps |
| `space-2` | 8dp | Icon-to-label gaps, chip padding |
| `space-3` | 12dp | List item internal padding |
| `space-4` | 16dp | Standard component padding |
| `space-6` | 24dp | Screen horizontal padding |
| `space-8` | 32dp | Section gaps |
| `space-12` | 48dp | Bottom safe area padding |

Screen edge padding: always **24dp** horizontal.

---

## Shape System

| Component | Corner Radius |
|---|---|
| Primary buttons | 16dp |
| Input fields | 14dp |
| Cards / surfaces | 16dp |
| Avatar large | 26dp |
| Avatar small (48dp) | 16dp |
| Chips / tags | 20dp (fully rounded) |
| Bottom sheet | 24dp top corners |
| Dialogs | 20dp |
| Bottom navigation | 0dp (edge to edge) |

---

## Edge-to-Edge & Insets

This is a known real-world issue — not handling insets causes UI to bleed into the status bar, gesture area, and battery/time icons. Every screen must handle this correctly.

### Rules
- **Never hardcode status bar or navigation bar heights in dp.** They vary by device, OS version, and navigation mode.
- **Never use fixed top/bottom padding values to compensate for system bars.** Always use `WindowInsets`.
- Use `Modifier.safeDrawingPadding()` for full-screen surfaces that draw behind system bars (splash, call UI).
- Use `Modifier.windowInsetsPadding(WindowInsets.navigationBars)` for bottom-anchored UI.
- Use `Modifier.statusBarsPadding()` for top content outside a Scaffold.
- Bottom navigation must account for gesture nav insets — its padding must come from `WindowInsets.navigationBars`, never a fixed value.
- All screens inside a `Scaffold` must pass `innerPadding` from the Scaffold lambda to their content.
- Test every new screen on both gesture navigation and 3-button navigation.
- All screens must look correct on devices with notches, punch-hole cameras, and display cutouts.
- Use `Modifier.displayCutoutPadding()` on full-screen screens (splash, call UI).

### Standard Scaffold Pattern
```kotlin
Scaffold(
    modifier = Modifier.fillMaxSize(),
    containerColor = OffneticColors.background
) { innerPadding ->
    ScreenContent(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    )
}
```

---

## Component Rules

### Buttons
- Primary: `#FFFFFF` background, `#0A0A0A` text, 16dp radius, 17dp vertical padding, full width in forms
- Destructive: `#EF4444` background, white text, same shape
- Disabled: `rgba(255,255,255,0.08)` background, `rgba(255,255,255,0.25)` text
- Press state: scale `0.98`, alpha `0.85`, 150ms — subtle not dramatic
- No button icons unless the action would be ambiguous without one

### Input Fields
- Background: `rgba(255,255,255,0.05)`, border `rgba(255,255,255,0.10)`
- Focused border: `rgba(255,255,255,0.25)`
- Placeholder: `rgba(255,255,255,0.25)`
- No floating labels — static uppercase micro label above the field
- Character count shown right-aligned inside the field when a limit applies
- **State binding must never be touched during styling.** Only `Modifier`, `colors`, `shape`, `textStyle` are safe to change.

### Cards / List Items
- Transparent background by default, `rgba(255,255,255,0.03)` on press
- No card elevation or shadows on list items — flat
- No dividers between list items — use spacing instead
- Border only when the card needs to visually stand apart (e.g. ping banner)

### Avatars
- Show initials (up to 2 chars) when no photo is set
- Background: `rgba(255,255,255,0.08)`
- Online dot: 9dp, `#4ADE80`, glow `boxShadow: 0 0 8px #4ADE80`, 2dp background-colored border, bottom-right

### Dialogs
- Background: `#141414`, 20dp radius
- Title: Bold 20sp
- Body: 15sp muted
- Actions: text buttons only — Cancel in `#3B82F6`, destructive in `#EF4444`
- Never use filled buttons inside dialogs
- Never use system `AlertDialog` styling — fully custom

### Bottom Navigation
- Background: `rgba(10,10,10,0.9)` + `blur(20dp)` backdrop
- Top border: `rgba(255,255,255,0.06)`
- Tabs: Chats, Nearby, Settings
- Active: white icon + `rgba(255,255,255,0.7)` label
- Inactive: `rgba(255,255,255,0.25)` icon + label
- No active indicator pill — color change only
- Labels: 10sp SemiBold uppercase 0.5sp letter spacing
- Bottom padding must come from `WindowInsets.navigationBars` — never hardcoded

### Permission Slides
- Progress dots: active animates width 6dp → 20dp with `animateDpAsState`
- Icon: 44dp stroke, `rgba(255,255,255,0.9)`
- Tag: micro uppercase "01 / 03" style
- Title: 36sp ExtraBold, -1sp letter spacing, allows line breaks
- Description: 15sp, `rgba(255,255,255,0.45)`, 1.7 line height
- Permission chips: outlined `rgba(255,255,255,0.12)` border, 12sp label
- Sub-hint: "You can change this anytime in Settings" — 12sp `rgba(255,255,255,0.2)`

### Nearby Ping Banner
- Green dot with glow + name + relative time
- Auto-dismisses after 10 seconds or on tap
- Border: `rgba(255,255,255,0.08)`
- Appears at top of chat list when a trusted contact is detected nearby

---

## Animation Rules

- Entrance: `translateY(16dp → 0) + alpha(0 → 1)`, 600ms, `FastOutSlowIn`
- Staggered lists: 60ms delay per item
- Button press: scale `0.98`, alpha `0.85`, 150ms
- Tab switch: crossfade 200ms, no slide
- Screen transitions: vertical slide forward, fade back
- Identity screen orbital rings: `rememberInfiniteTransition`, linear, 1200ms / 1800ms counter-rotation
- Progress dots: `animateDpAsState`, spring stiffness Medium

No bounce. No overshoot. No playful spring — this is a security tool, not a social app.

---

## Implementing Design Without Breaking Functionality

AI agents commonly break working UI while doing design changes. These rules prevent that.

### Before Touching Any UI File
1. Read the full file.
2. Identify every interactive element: text fields, buttons, click lambdas, state variables, navigation calls.
3. Only change: `Modifier`, `colors`, `shape`, `textStyle`, `fontSize`, padding values, and visual-only parameters.
4. Never touch: `onValueChange`, `onClick`, `remember`, `mutableStateOf`, ViewModel calls, navigation calls.

### Things That Must Never Break After a Design Pass
- Text field input — must still accept and reflect user input
- Button taps — must still trigger their original action
- Profile picture picker — must still open the gallery or trigger the correct callback
- Username field — must still bind to its state variable
- Navigation — all screen transitions must still work
- Any ViewModel function call — even if it "looks unused," leave it

### When Restyling an Existing Component
- Copy the existing state/logic structure exactly
- Only change the visual wrapper around it
- If you need to restructure layout significantly, do it in a separate step after verifying the original still works

### Rule of Thumb
If the change would make the screen look different but behave identically → safe.
If the change would affect any behaviour → stop and handle separately.

---

## Screens Reference

| Screen | Purpose |
|---|---|
| Splash | Logo + tagline, silent keypair generation begins |
| Permission × 3 | Connectivity / Camera+Mic / Notifications |
| Identity Generation | Keypair creation with visual feedback + crypto log |
| Profile Setup | Avatar picker + username input |
| Chat List | Peer list, nearby ping banner, bottom nav |
| Single Chat | Message bubbles, input bar, media actions |
| Voice / Video Call | Full-screen call UI, minimal controls |
| QR Scan | CameraX scanner, add trusted contact |
| My QR Code | Display own QR for others to scan |
| Contact Detail | Profile, block, delete options |
| Nearby | Active NCAPI peer list / radar |
| Settings | App preferences, radio settings |
| Account | Username edit, 3-tier deletion flows |
| Blocked Contacts | View and manage block list |

---

## UI Copy Tone

- Short. Direct. No filler.
- No exclamation marks.
- No "We" language — "We protect your data" → "Your data stays on device"
- Destructive confirmations state exactly what will be destroyed.
- Permission rationale: honest and specific — "Used only for local device discovery, never stored or shared"
- Errors: say what happened and what to do next, not just what went wrong.

---

## What You Must Never Do

- Use purple, blue, or any color as a primary brand accent
- Use gradients on backgrounds, buttons, or text
- Use colorful shadows
- Use Inter, Roboto, or system fonts as the primary typeface
- Let Material3 default colors appear anywhere — always override
- Add decorative illustrations or clipart
- Use emoji in UI copy
- Use bottom sheets for destructive confirmations — use dialogs
- Crowd a screen — if it feels full, remove something
- Use more than 3 levels of text hierarchy on one screen
- Use green outside of online/nearby status contexts
- Use card elevation or shadows on list items
- Hardcode status bar or navigation bar heights
- Use fixed padding values to compensate for system bar overlap
- Touch `onClick`, `onValueChange`, state, or ViewModel calls during a visual styling pass
- Deliver a design change that breaks any existing interactive functionality
