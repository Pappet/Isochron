# Spectrum Design Language Specification

**Version:** 1.0  
**Core Philosophy:** "The Obsidian Terminal"  
**Brand Identity:** Technical, Precise, High-Contrast, Tactical

---

## 1. Design Vision

Spectrum is a design language built for power users, security professionals, and tech enthusiasts. It prioritizes data clarity and high-contrast legibility within a dark, "low-light" environment. It draws inspiration from radar systems, tactical terminals, and early cyberpunk aesthetics, but refined for modern, high-resolution displays.

---

## 2. Color Palette

The Spectrum palette is built on extreme contrast to ensure visibility and a focused "hacker tool" vibe.

| Layer | Color Name | Hex Code | Purpose |
| :--- | :--- | :--- | :--- |
| **Primary Base** | Obsidian Black | `#000000` | Pure background layer for maximum contrast. |
| **Surface** | Deep Charcoal | `#0E0E0E` | Secondary backgrounds, cards, and navigation bars. |
| **Accent (Main)** | Neon Sentinel | `#A6FF00` | Primary action color, highlights, and critical data points. |
| **Secondary** | Cyan Pulse | `#00E0FF` | Information, secondary status, and links. |
| **Warning** | Amber Alert | `#FFB800` | Security risks, warnings, and medium-priority alerts. |
| **Error** | Critical Red | `#FF3B30` | Failures, high-risk security threats, and stop actions. |
| **Borders** | Muted Moss | `#414A34` | Subtle separation of elements at low opacity (15-20%). |

---

## 3. Typography

Spectrum uses modern, wide-tracked sans-serif fonts to maintain a technical, engineered look.

* **Primary Font:** `Space Grotesk` (Google Fonts)
* **Monospace Font:** `Roboto Mono` or `Manrope` (for data values and code snippets)

### Type Styles

* **Headlines (H1/H2):** All Caps, tracking: `0.1em`, font-weight: `Bold`.
* **Labels:** All Caps, font-weight: `Medium`, font-size: `10px - 12px`, tracking: `0.2em`.
* **Body:** font-weight: `Regular`, line-height: `1.6`.
* **Data Values:** font-family: `Monospace`, font-weight: `Bold`.

---

## 4. Visual Elements & UI Patterns

### 4.1. The "Grid System"

Use a subtle background grid (1px lines, 20px-40px spacing) at very low opacity (3-5%) to ground the interface in a technical environment.

### 4.2. Shape & Roundness

* **Buttons/Cards:** Very tight corner radius (`4px` or `2px`). Spectrum is "sharp" and precise, not "soft" and consumer-focused.
* **Inputs:** Full-width borders or bottom-only borders for a more "terminal" look.

### 4.3. Glow & Elevation

* Use `box-shadow` with the `#A6FF00` color at high blur and low opacity to create a "glowing screen" effect for primary buttons and active indicators.
* Avoid standard drop shadows; use color-matched outer glows instead.

### 4.4. Iconography

* **Style:** Outlined, geometric icons.
* **Weight:** Consistent 1.5pt or 2pt stroke weight.
* **Theme:** Radar, signals, nodes, locks, and telemetry.

---

## 5. Interaction Principles

* **Instant Feedback:** Transitions should be fast and snappy (150ms-200ms).
* **State Changes:** Use opacity shifts and subtle scale-downs (98%) for active states.
* **The "Scan" Effect:** Whenever data is loading, use a sweeping vertical or horizontal line to simulate a radar scan rather than a generic spinner.

---

## 6. Implementation Notes

When coding for Spectrum:

1. **Dark Mode Only:** There is no light mode. Spectrum is obsidian by definition.
2. **Contrast is King:** Ensure all text passes WCAG AAA contrast ratios against the black background.
3. **Minimalist Content:** Remove any visual noise. If a line or border doesn't serve a functional data separation purpose, delete it.
