# Blurt Design Standard — the iOS System Look

Blurt is built to feel like it came out of Apple: the same system language an
iOS app uses, on Android. Not "inspired by" — the actual system: iOS colors,
iOS grouped-list structure, iOS materials, iOS type scale, iOS motion.

The previous custom identity (gold accent, near-black surfaces, custom
"premium" tokens) is **gone**. The reference is Apple's Human Interface
Guidelines and the system colors it ships.

---

## 1. Principles (Apple's, verbatim)

- **Clarity** — text is legible at every size; icons precise; adornments subtle.
- **Deference** — the UI helps people understand and interact with content,
  never competes with it.
- **Depth** — layers communicate hierarchy: materials blur and lift, lists
  group, sheets float above the content that presented them.

## 2. Color — the iOS system palette

The accent is **system blue** (`#007AFF` light / `#0A84FF` dark). Blue is the
tint for everything interactive, exactly as Apple uses it: active state,
primary actions, selected chips, links, checkmarks, focus rings. Red
(`#FF3B30`/`#FF453A`) is destructive-only. Green (`#34C759`/`#30D158`) is
success. There is **no gold anywhere**.

| Token | Light | Dark |
|---|---|---|
| Background (page) | `#F2F2F7` | `#000000` |
| Surface (grouped cards) | `#FFFFFF` | `#1C1C1E` |
| Elevated (sheets/menus/bars) | `#FFFFFF` | `#2C2C2E` |
| Label (primary text) | `#000000` | `#FFFFFF` |
| Secondary label | `#8E8E93` | `#AEAEB2` |
| Tertiary label | `#C7C7CC` | `#8E8E93` |
| Separator | `#C6C6C8` | `#38383A` |
| Accent (system blue) | `#007AFF` | `#0A84FF` |
| Error (system red) | `#FF3B30` | `#FF453A` |
| Success (system green) | `#34C759` | `#30D158` |

Rules:
- Blue on primary actions with white label text; blue on selected/active
  states; blue-soft (`Accent @ 10–15%`) for chips, tiles, focus softness.
- Text colors carry hierarchy: label → secondary → tertiary. Metadata is
  always secondary.
- Never use blue as a background wash for whole screens; never paint content
  blue.

## 3. Typography — SF-style scale

Android's closest system font (Roboto) at SF Pro's scale and tracking. Large
titles carry the brand; everything else defers.

- **Display 34 / Bold / −0.4** — screen titles ("Blurt", "Library", "Search").
- **Title 28 / Bold / −0.3** — hero moments.
- **Headline 22 / SemiBold / −0.25** — confirm blurt, mic hero question.
- **Body 17** — content.
- **Callout 16 / Footnote 13 / Caption 12 / +0.3** — metadata, section labels
  ("RECENT BLURTS", "SUGGESTED" in secondary, uppercase, tracked).

## 4. Shape & spacing

- **16pt inset** from screen edges (iOS grouped-list margin).
- **Grouped cards**: one rounded surface (`12pt` corners) holding a list;
  rows inside are separated by **inset hairlines aligned to the text
  column** (56dp), first row has no top separator.
- **Buttons**: rounded rects, `14pt`. Pills only for chips and the Google
  button.
- **Sheets**: `16pt` top corners, resting on the elevated surface.
- **Search field**: recessed gray fill (no border at rest), soft blue ring
  on focus.
- One spacing scale: 4 / 8 / 12 / 16 / 20 / 24 / 32.

## 5. Materials & glass

- The **tab bar** is a material bar: the content layer behind it is blurred
  with a RenderEffect and clipped to the bar region (API 31+); the bar's own
  tint is translucent. Below API 31 or when reduced transparency is
  requested, the bar falls back to a solid elevated surface.
- Glass/frost belongs only to **floating chrome**: tab bar, sheets. Never on
  list rows, never glass-on-glass.

## 6. Components

- **Blurt mark**: a solid system-blue rounded square with the white
  speech-bubble glyph — an iOS app icon.
- **Mic**: a solid system-blue circle with a white glyph (the record
  button); a barely-there blue breathing ring at rest; a level-reactive
  ring + bars while listening.
- **Rows**: muted icon tile on the grouped background, preview in
  body-medium, meta in secondary, blue ★ / 🔔 markers, overflow menu.
- **Chips/filters**: blue fill + white text when active, elevated surface +
  hairline when not.
- **Empty states**: centered, icon in a hairline circle, title in Title 2,
  body in secondary.

## 7. Motion & feedback

- Fast, subtle, behavioral: press feedback fires on press-down; sheets rise
  from the bottom; transitions 150–400ms.
- Every event pairs a haptic with a sound: mic tick on start, double-tick on
  stop, pulse when the confirm sheet lands, chime on save, buzz on error.
- Honor reduced motion / reduced transparency via the animator-scale proxy:
  solid surfaces, no blur.

## 8. Anti-patterns (explicit)

- No gold, no warm accents, no custom brand palette.
- No glass cards in lists, no glass-on-glass.
- No floating blobs, rainbow gradients, neon, excessive shadow.
- No dead buttons; every visible control does something.
- No centered titles on list screens (iOS large titles are left-aligned).
- No "AI SaaS" dashboard styling — typography carries hierarchy, not boxes.

## 9. Accessibility

- Full-contrast label colors; secondary/tertiary only for metadata.
- Content descriptions on every icon button.
- Both themes honor dynamic font scale.
- Contrast and reduced-transparency fallbacks are part of the spec, not
  an afterthought.
