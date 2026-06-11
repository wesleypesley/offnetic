---
name: Offnetic
colors:
  surface: '#141313'
  surface-dim: '#141313'
  surface-bright: '#3a3939'
  surface-container-lowest: '#0e0e0e'
  surface-container-low: '#1c1b1b'
  surface-container: '#201f1f'
  surface-container-high: '#2a2a2a'
  surface-container-highest: '#353434'
  on-surface: '#e5e2e1'
  on-surface-variant: '#c4c7c8'
  inverse-surface: '#e5e2e1'
  inverse-on-surface: '#313030'
  outline: '#8e9192'
  outline-variant: '#444748'
  surface-tint: '#c6c6c7'
  primary: '#ffffff'
  on-primary: '#2f3131'
  primary-container: '#e2e2e2'
  on-primary-container: '#636565'
  inverse-primary: '#5d5f5f'
  secondary: '#c8c6c5'
  on-secondary: '#303030'
  secondary-container: '#474746'
  on-secondary-container: '#b6b5b4'
  tertiary: '#ffffff'
  on-tertiary: '#303031'
  tertiary-container: '#e3e2e2'
  on-tertiary-container: '#646464'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#e2e2e2'
  primary-fixed-dim: '#c6c6c7'
  on-primary-fixed: '#1a1c1c'
  on-primary-fixed-variant: '#454747'
  secondary-fixed: '#e4e2e1'
  secondary-fixed-dim: '#c8c6c5'
  on-secondary-fixed: '#1b1c1c'
  on-secondary-fixed-variant: '#474746'
  tertiary-fixed: '#e3e2e2'
  tertiary-fixed-dim: '#c7c6c6'
  on-tertiary-fixed: '#1b1c1c'
  on-tertiary-fixed-variant: '#464747'
  background: '#141313'
  on-background: '#e5e2e1'
  surface-variant: '#353434'
typography:
  display-lg:
    fontFamily: Syne
    fontSize: 48px
    fontWeight: '800'
    lineHeight: '1.1'
    letterSpacing: -0.04em
  headline-lg:
    fontFamily: Syne
    fontSize: 32px
    fontWeight: '700'
    lineHeight: '1.2'
    letterSpacing: -0.02em
  headline-lg-mobile:
    fontFamily: Syne
    fontSize: 28px
    fontWeight: '700'
    lineHeight: '1.2'
    letterSpacing: -0.02em
  title-md:
    fontFamily: Syne
    fontSize: 20px
    fontWeight: '600'
    lineHeight: '1.4'
    letterSpacing: -0.01em
  body-lg:
    fontFamily: Syne
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '1.6'
    letterSpacing: '0'
  body-md:
    fontFamily: Syne
    fontSize: 15px
    fontWeight: '400'
    lineHeight: '1.5'
    letterSpacing: '0'
  label-mono:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '400'
    lineHeight: '1.4'
    letterSpacing: 0.05em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  unit: 4px
  gutter: 16px
  margin-mobile: 20px
  margin-desktop: 40px
  container-max: 1200px
---

## Brand & Style
The design system is engineered for high-security, off-grid communication, prioritizing absolute clarity and a sense of impenetrable digital sovereignty. The aesthetic is **Ultra-Modern Cyber-Minimalism**, blending a pure black "void" environment with high-fidelity technical precision. 

The brand personality is authoritative, uncompromising, and technologically advanced. It avoids friendly curves or soft colors in favor of a stark, high-contrast interface that feels like a secure terminal. Key visual motifs include atmospheric noise textures to simulate physical hardware, subtle glassmorphism to denote layered security levels, and an emphasis on raw data integrity.

## Colors
The palette is rooted in a true-black `#0A0A0A` foundation to maximize OLED efficiency and minimize light emission in off-grid scenarios. 

- **Primary:** Pure White (#FFFFFF) is reserved for critical actions and core text, providing maximum contrast against the void.
- **Secondary:** A deep Charcoal (#2A2A2A) used for structural elements and secondary UI components.
- **Surface:** A slightly elevated Slate (#161616) creates subtle depth for cards and containers.
- **Accents:** Depth is achieved not through color, but through light. We use low-opacity white borders (`rgba(255, 255, 255, 0.08)`) and semi-transparent glass layers to differentiate functional zones without breaking the dark aesthetic.

## Typography
Typography is the primary driver of the "technical" feel. 
- **Headings:** Use `Syne` with bold weights and tight letter spacing. Large display titles should feel compressed and impactful, like stamped metal.
- **Body:** `Syne` is used at 15px-16px for all messaging and descriptive text. Its unique geometric construction maintains modernism while remaining highly legible.
- **Monospace:** `JetBrains Mono` (or equivalent system mono) is strictly reserved for cryptographic keys, device identities, and system logs. This clear distinction ensures users recognize "machine data" versus "human communication."

## Layout & Spacing
The layout follows a strict 4px grid system to maintain a "calculated" appearance. 

- **Grid:** A 12-column fluid grid for desktop and a 4-column grid for mobile.
- **Safety:** Content is pushed away from edges with generous margins (20px on mobile) to ensure readability on ruggedized devices.
- **Density:** While the aesthetic is minimal, information density for message threads should be high, utilizing tight vertical spacing between message bubbles to maximize the "log" feel.

## Elevation & Depth
In this design system, elevation is not conveyed through shadows, but through **Tonal Layering** and **Material Properties**:

1.  **Level 0 (Background):** Pure black `#0A0A0A`.
2.  **Level 1 (Surface):** `#161616` with a 1px border of `rgba(255,255,255,0.08)`.
3.  **Level 2 (Glass):** Semi-transparent overlays with a `20px` backdrop blur and a fine-grain noise texture (`opacity: 0.03`) to simulate high-end lens optics.
4.  **Interaction:** Elements do not "lift" (no shadows); instead, they brighten or gain a more opaque border to indicate focus.

## Shapes
The shape language balances modern softness with structural rigidity. 

- **Containers/Cards:** Use a large radius (24px-28px) to create a distinct "encapsulated" look, making each module feel like a secure vault.
- **Interactive Elements:** Buttons and Inputs use a tighter 16px radius, providing a more precise "tool-like" feel compared to the larger container cards.
- **Visual Texture:** Apply a subtle noise/film grain to all large surfaces to break up solid digital blacks and add a tactile, hardware-inspired finish.

## Components
- **Buttons:** Primary buttons are Solid White with Black text. No shadows. High-contrast and immediate. Secondary buttons use a `#2A2A2A` fill with white text.
- **Cards:** Rounded (28px), background `#161616`, with a subtle 1px top-light border.
- **Inputs:** Minimalist containers. The border should be `1px` white at low opacity, turning to pure white on focus. Labels use the Monospace font for a "data entry" feel.
- **Progress Indicators:** Avoid traditional spinners. Use "Orbital Loaders" (thin white strokes orbiting a center point) or minimalist binary-style dots.
- **Chips/Status:** Used for encryption levels (e.g., "End-to-End"). Use the Monospace font, capitalized, with a small leading dot icon.
- **Message Bubbles:** Outlined for the recipient, slightly tinted surface for the sender. Distinct 24px corner radius, except for the "tail" corner which is 4px.