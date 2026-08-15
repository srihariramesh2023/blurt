# Blurt Design Standard — Apple HIG, applied to Android

Blurt is built on Apple's Human Interface Guidelines, translated into native
Android idioms (Compose). It is *not* a copy of iOS widgets — it is Apple's
system language: the principles, the type scale, the color tiers, the motion
physics, the restraint — implemented with Compose components.

The previous custom identity (gold accent, warm surfaces) is **gone**. The
reference is the HIG and the system values it ships.

---

## 1. Principles (Apple's, verbatim)

- **Clarity** — text is legible at every size; icons precise; adornments
  subtle. Negative space is not wasted; focus goes to the user's content,
  never to chrome.
- **Deference** — the UI helps people understand and interact with content,
  never competes with it. No unnecessary ornamentation.
- **Depth** — layers communicate hierarchy: materials blur and lift, lists
  group, sheets float above the content that presented them.

## 2. Color — neutral-first with one accent

**Neutral palette only.** Light: near-white page (`#F2F2F7`) with white
grouped cards. Dark: pure black page (`#000000`) with `#1C1C1E` cards —
recalculated neutrals, not an inversion. No custom brand palette.

**Labels are opacity tiers of the primary label — no new grays:**

| Token | Light | Dark |
|---|---|---|
| Primary label | `#000000` | `#FFFFFF` |
| Secondary label | primary @ 60% | primary @ 60% |
| Tertiary label | primary @ 30% | primary @ 30% |

Secondary is for metadata and section headers. Tertiary is decoration only —
never body or interactive text.

**One accent: system blue** (`#007AFF` light / `#0A84FF` dark), used
sparingly and only for interactive/primary elements: active state, primary
actions, selected chips, links, checkmarks, focus rings, the brand mark.
Blue-soft (accent @ 10–15%) for quiet fills. Never a full-screen wash.

**Semantic colors are reserved for status only:** green (`#34C759`/`#30D158`)
for success, red (`#FF3B30`/`#FF453A`) for destructive. Nothing else uses
them.

## 3. Typography — the HIG scale, one family

One neutral sans family (Roboto on Android — SF Pro's closest system
equivalent) at SF Pro's exact scale and tracking. Weight and size carry
hierarchy, never color alone.

| Style | Size | Weight | Tracking |
|---|---|---|---|
| Large Title | 34 | Bold | −0.4 |
| Title 1 | 28 | Bold | −0.3 |
| Title 2 | 22 | SemiBold | −0.25 |
| Title 3 | 20 | SemiBold | −0.2 |
| Headline | 17 | SemiBold | −0.15 |
| Body | 17 | Regular | 0 |
| Callout | 16 | Regular | 0 |
| Subheadline | 15 | Regular | 0 |
| Footnote | 13 | Regular | 0 |
| Caption 1 | 12 | Medium | +0.3 |
| Caption 2 | 11 | Medium | +0.5 |

**Body content never renders below 17pt. Nothing renders below 11pt.**
Uppercase tracked section labels (RECENT BLURTS, REMINDER, SUGGESTED) are
Caption 1 semibold at +1.2, in secondary.

## 4. Layout — the 8pt grid

- All spacing and sizing in **multiples of 8**; 4pt reserved for fine
  adjustments inside components. Token scale: 4 / 8 / 16 / 24 / 32 / 40 / 48.
- Screen-edge margin: **16pt** (HIG allows 16–20).
- **Minimum touch target: 44×44pt, no exceptions** — icon buttons, chips,
  rows, fields. The icon inside stays small; the hit area is 44.
- **Whitespace separates, not dividers.** No hard-line dividers between list
  items or sections where spacing alone creates the break. Grouped cards use
  row padding for separation; the tab bar's hairline is chrome, not a list
  separator.
- Radii: 8–12pt buttons and small elements, 16–20pt cards, larger for
  full-screen sheets. Pills only for chips and the Google button.
- Shadows: soft, low-opacity, large blur — never hard-edged.

## 5. Materials & depth

- Translucency/blur for floating chrome only: the tab bar (RenderEffect
  backdrop, API 31+) and sheets. Below API 31 or on reduced transparency the
  bar falls back to a solid elevated surface. Never glass on list rows.
- Elevation implies hierarchy: sheets and menus sit on the elevated surface.

## 6. Components & interaction

- **Primary actions**: full-width filled buttons. **Secondary**: text-only,
  quieter. No dead buttons — every visible control does something.
- Every interactive element has a pressed state: scale to ~0.97 with a fast
  no-bounce spring.
- One icon set throughout (hand-drawn Material-style, consistent 24dp
  viewport), single stroke idiom.
- The mic: solid system-blue circle, white glyph — the record button. A
  static soft halo at rest; level-reactive ring and bars only while live.

## 7. Motion — spring physics, tied to cause

- **All transitions use spring physics** — natural deceleration, slight
  overshoot only where physically appropriate (a surface arriving). Never
  linear/ease curves for transitions. Springs animate from the current
  on-screen value, so every transition is interruptible and redirectable
  mid-flight.
- Apple's spring language, mapped to Compose (stiffness ↔ response,
  `response ≈ 2π/√stiffness`):
  - `micro` ≈ 0.09 s, damping 1.0 — presses, toggles (no overshoot)
  - `standard` ≈ 0.31 s, damping 1.0 — ordinary transitions (no overshoot)
  - `entrance` ≈ 0.31 s, damping 0.75 — sheets, full-screen (gentle
    overshoot, mirroring Apple's sheet/drawer damping ~0.8)
- **Animate transforms, not layout.** Live meters ride `scale`/`scaleY` on
  the transform layer (fixed-size bars) — never per-frame layout churn.
- Motion is always tied to a cause: a state change, a user action, a
  navigation. **No purely decorative looping animation.** Live indicators
  (listening waveform, level ring) loop because they communicate a live
  state, nothing else does.
- **Reduce motion**: the OS animator-scale setting is honored — every
  spring/slide has a fade-only fallback.
- Feedback is multimodal and event-tied: haptic + short sound (<300 ms) on
  the mic start/stop, save, and errors only. No persistent or looping sound.

## 8. Anti-patterns (explicit)

- No gold, no warm accents, no custom brand palette.
- No hard-line dividers between rows or sections.
- No decorative looping animation, no breathing idle states.
- No glass cards in lists, no glass-on-glass.
- No floating blobs, rainbow gradients, neon, excessive shadow.
- No touch target below 44pt.
- No centered titles on list screens (large titles are left-aligned).
- No "AI SaaS" dashboard styling — typography carries hierarchy, not boxes.

## 9. Accessibility (part of the design, not an afterthought)

- **WCAG AA contrast** even inside the neutral palette: primary on both
  backgrounds is full contrast; secondary (60%) is used for metadata only;
  tertiary never carries information.
- **Dynamic type**: both themes honor the system font scale — no fixed sizes
  outside the token scale.
- Every interactive element has an accessible label; icon-only controls have
  content descriptions.
- Reduced-motion and reduced-transparency fallbacks are part of the spec.
