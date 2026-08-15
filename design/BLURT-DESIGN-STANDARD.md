# Blurt Design Standard

Blurt V2 — the design board is the visual source of truth. This document
locks in the decisions so every future screen matches.

## 1. The identity

**Blurt is a place to instantly say something, and Blurt figures out what it
means.** The interface is built around one element: the **orb**. A glowing
violet sphere with a mic, it is the product — idle on Home, live while
listening, swirling while processing, complete when organized.

The whole app follows the board's rhythm: **quiet copy, generous space,
violet accent, pure-black canvas, individual cards.** No gold, no system
blue, no generic AI-SaaS chrome.

## 2. Color

The palettes are **Apple's own iOS system colors** — pure-black dark
canvas, the `#F2F2F7` grouped gray in light, white cells, label and
separator colors straight from the HIG. The one brand deviation is the
accent: violet instead of system blue, deepened in light and lightened in
dark the way Apple shifts its accent between modes.

| Token | Dark | Light |
| --- | --- | --- |
| Background (canvas) | `#000000` | `#F2F2F7` |
| Surface (cards) | `#1C1C1E` | `#FFFFFF` |
| Surface elevated | `#2C2C2E` | `#FFFFFF` |
| Primary label | `#FFFFFF` | `#000000` |
| Secondary label | primary at 60% | primary at 60% |
| Tertiary label | primary at 30% | primary at 30% |
| Hairline | `#38383A` | `#E5E5EA` |
| Accent (violet) | `#7C6CFF` | `#5A45F2` |
| Accent bright (orb glow) | `#9B7BFF` | `#8B6CFF` |
| Error | `#FF453A` | `#FF3B30` |
| Success | `#30D158` | `#34C759` |

Rules:

- The accent is **violet only** — primary action, active tab, the orb,
  meaning/signal, the brand mark. Never gold, never system blue.
- One accent family; both themes read from the same semantic tokens.
- Semantic colors (red = destructive, green = success) never appear for
  decoration.

## 3. Typography

Apple's HIG scale (SF-style; Roboto on Android), weight + size carry
hierarchy, never color alone:

| Token | Size |
| --- | --- |
| Large Title | 34 |
| Title 1 | 28 |
| Title 2 | 22 |
| Title 3 | 20 |
| Headline | 17 semibold |
| Body | 17 |
| Callout | 16 |
| Subheadline | 15 |
| Footnote | 13 |
| Caption 1 | 12 |
| Caption 2 | 11 |

Rules: body never below 17; display text never below 11; one family, no
mixed display fonts. Copy is **quiet and conversational** — the board's
words: "Just talk.", "Tap the orb and just talk.", "Blurt listens. Blurt
organizes."

## 4. The orb

The heart of the product, one component with four states (board panel
"ORB STATES"):

1. **Idle** — calm, soft pulse. Ready when you are.
2. **Recording** — active waveform ring. Listening closely.
3. **Processing** — swirl ring + breathing scale. Analyzing and organizing.
4. **Complete** — solid glowing. Done and ready.

Rules:

- The orb is a violet radial gradient disc over a soft glow halo.
- The glow breathes in idle only; everything else answers a state change.
- Icons live inside the disc: mic (idle/recording), sparkle (processing),
  check (complete), `!` (error).
- The orb is consistent across every screen that shows it (board checklist
  item 1): Home, Listening, Processing, empty and error states.

## 5. The consistent pattern

Every capture surface follows the board's "CONSISTENT PATTERN" (panel 3):

1. **Primary voice action** — the orb.
2. `or` divider — a muted hairline with the word between two short lines.
3. **"Type instead"** — the quiet text link.

Never present typing as an equal to the orb.

## 6. Navigation — four tabs only

The board's "NEW NAV STRUCTURE": **Home, Search, Library, Settings** — four
tabs, for simplicity and focus.

- **Home** — greeting + orb + Type instead. Pure capture; recent items live
  in Library.
- **Search** — greeting, recessed field, filter pills, Top results.
- **Library** — greeting, filter pills (All / Tasks / Reminders / Notes),
  cards grouped under Today / Yesterday.
- **Settings** — the account surface: avatar, name/email, Appearance,
  settings rows, sign out.

The tab bar is a **floating Liquid Glass pill** (iOS 26 pattern): a rounded
capsule of glass inset 16 pt from the screen edges, floating 8 pt above the
gesture area, with content visibly frosting beneath it. No hairline, no
full-width chrome. The active tab's violet highlight capsule **glides**
between tabs on a spring; the pill fades in on first appearance. When the
OS requests reduced transparency the pill becomes a solid elevated
surface.

The pill floats over content, so every tab screen reserves its footprint
(the shell's `LocalTabBarInset`): lists scroll clear of the glass, and
vertically-scrolling screens (Home, Settings) get bottom padding so no text
hides underneath it.

## 7. Greeting headers

Home, Library, and Search open with a **personal greeting** instead of a
wordmark (board checklist item 4): "Good morning, Srihari" / "What's on your
mind today?" — time-based (morning/afternoon/evening) + the user's first
name, with a quiet subline under it and the avatar top-right.

## 8. Cards & lists

Library and Search items are **individual cards** (surface on canvas),
never one giant grouped card:

- **Task** — a radio circle on the left; tap to complete (green check when
  done), title, "Today, 5:00 PM"-style line.
- **Reminder** — bell marker.
- **Note** — a quiet type tile (document/quotation).

Section headers ("TODAY", "YESTERDAY", "MMM d") separate the groups.

## 9. Processing checklist

While classifying, the board's checklist drives the state (screen 07):

- ✓ Transcribing
- ✓ Understanding
- ○ Organizing (spinner)

"Thinking it through…" heads the screen; the orb sits above the transcript.

## 10. Error state

Classification failure is its own screen (board screen 12), never a raw
error dump:

- Orb with an exclamation mark
- "Couldn't organize that" + a reassuring line ("Your recording is safe…")
- **Try Again** (primary), **Save as Note** (secondary), **Type instead**
  (text link)

## 11. Onboarding

Shown once before sign-in (persisted):

1. **Just talk.** / We'll organize it.
2. **How it works** — 01 You talk · 02 We understand · 03 We organize.
3. **Your thoughts. Your privacy.** — with the three promises.
4. **Let Blurt listen** — mic permission, "We only listen when you tap the
   orb."

Continue / Get Started + page dots; no skip.

## 12. Motion

- **Screen transitions are timed curves, not springs.** iOS screen
  transitions are timed (tab crossfade ≈ 220 ms, push/pop slide ≈ 320 ms,
  `FastOutSlowIn`), and a timed curve always reaches its end value — a
  quick follow-up tap can never leave an incoming screen stuck mid-fade.
  This is what keeps tab switching reliable.
- Springs are for **direct manipulation inside a screen** (micro ≈ 0.09 s,
  standard ≈ 0.31 s, entrance ≈ 0.31 s with a gentle overshoot like
  Apple's sheets): the tab capsule glide, pill entrance, presses, toggles.
- Bounce appears only where a surface physically arrives; routine fades
  never overshoot.
- Animate transforms and opacity, never layout (the waveform ring animates
  `scaleY` per bar, the processing ring rotates a sweep gradient).
- Every spring is interruptible — Compose animates from the current
  on-screen value.
- **Navigation** — tab switches **crossfade** (a quiet fade, iOS tab
  style); pushed screens (Voice, Capture, Detail) **slide in from the
  right** like an iOS stack, and slide back out on pop. The active-tab
  capsule in the floating pill glides between positions.
- **Reduce motion** — the OS setting swaps every spring/slide for a plain
  fade; looping indicators freeze into their static composition.

## 13. Sound & haptics

Subtle and only on meaningful moments (causality + utility):

- Mic press → tick + start tone; stop → double tick + stop tone.
- Analysis lands → medium pulse; save → success + tone; error → buzz +
  error tone.
- Sounds are short (<300 ms), never looping.

## 14. Accessibility

- Dynamic text respected (font scaling flows through Material).
- AA contrast within the neutral palette + violet accent on both themes.
- Every icon has a content description; every icon-only control has a
  label.
- 44×44 pt minimum touch targets, no exceptions.
- Reduced motion and reduced transparency honored (see §12 and the tab
  bar's frost fallback).
