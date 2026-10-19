---
version: 1
name: SnapLedger
description: >
  Calm, bright ledger UI. Structure adapted from the Wise design analysis in
  VoltAgent/awesome-design-md (MIT): pale canvas, white 24 dp cards, heavy numerals, one accent.
  The accent is indigo instead of green, and there is a matching dark theme.
  Implemented in ui/src/commonMain/kotlin/dev/snapledger/ui/theme/Theme.kt; keep the two in sync.

colors:
  light:
    canvas: "#eef0f6"          # page background; cards sit on it
    surface: "#ffffff"         # cards, nav bar, inputs
    surface-soft: "#e6e7fb"    # chips, bar tracks, selected nav item
    ink: "#14162b"             # headings, amounts
    body: "#3c4057"            # secondary text
    mute: "#5a5f75"            # captions, raw bank descriptions
    hairline: "#dfe1ee"        # dividers
    accent: "#2b2f8f"          # links, bars, focus, checkboxes, source badges
    cta: "#c9cbff"             # primary button fill (lavender pill, ink label)
    on-cta: "#14162b"
    hero: "#1e2170"            # the one dark card per screen
    on-hero: "#c9cbff"
    hero-mute: "#9ea1e6"
    positive: "#00876a"        # ✓ reconciled, money in
    positive-soft: "#dff5ee"
    warning: "#9a6a00"         # suspected duplicate, AI unsure
    warning-soft: "#fff3d1"
    negative: "#c62f3a"        # ⚠ mismatch, missing fields, errors
    negative-soft: "#fde6e7"
    neutral-soft: "#e9ebf1"    # skipped rows, AI notes
  dark:
    canvas: "#121420"
    surface: "#1c1f2e"
    surface-soft: "#2a2d52"
    ink: "#eceef8"
    body: "#c3c6d8"
    mute: "#8f93ab"
    hairline: "#2e3145"
    accent: "#a5a8ff"
    cta: "#c9cbff"
    on-cta: "#14162b"
    hero: "#2b2f8f"
    on-hero: "#e0e1ff"
    hero-mute: "#b4b7f5"
    positive: "#3fcf9f"
    positive-soft: "#163a31"
    warning: "#f0b429"
    warning-soft: "#3a2f12"
    negative: "#ff6b74"
    negative-soft: "#3d1a1e"
    neutral-soft: "#23263a"

typography:                     # system font (Segoe UI / Microsoft YaHei on Windows, Roboto / Noto on Android)
  hero-number: { size: 36sp, weight: 900, letterSpacing: -0.02em }
  page-title:  { size: 24sp, weight: 900 }
  stat-number: { size: 20sp, weight: 800 }
  title:       { size: 16sp, weight: 600 }
  body:        { size: 14sp, weight: 400 }
  caption:     { size: 12sp, weight: 400 }
  amount:      { size: 15sp, weight: 700, tabularNumbers: true }

rounded: { sm: 8dp, md: 12dp, lg: 16dp, xl: 24dp, pill: 50% }
spacing: { xs: 4dp, sm: 8dp, md: 12dp, lg: 16dp, xl: 24dp }
---

## Overview

SnapLedger answers one question: where did my money go. The UI should feel calm and bright, closer to a
well-made magazine than to a banking dashboard. Pages are a pale canvas with white cards on it; the
contrast between canvas and card is the only elevation. Each screen gets at most **one** dark "hero"
card for the number that matters most (this month's spending), set in the heaviest weight.

Indigo is the single brand accent. Semantic colours (positive / warning / negative) are only for state
and never for decoration. In particular, positive teal-green marks *reconciled* and *money in*; it is
not a brand colour and stays small.

## Colours

- `canvas` behind everything; `surface` for every card, the nav bar and inputs.
- `accent` for interactive or highlighted bits: links, category bars, checkboxes, the selected tab label.
- `cta` (lavender pill, ink label) for the main action of a screen: 导入流水, 确认导入, 导出备份.
  Secondary actions are outlined pills.
- `hero` / `on-hero` only on the one big summary card.
- Amounts: money out in `ink`, money in in `positive` with a leading `+`.

### Preview row states (import preview)

| State | Background | Meaning |
|---|---|---|
| New, will be imported | `surface` | normal row |
| Skipped duplicate / unticked | `neutral-soft`, text in `mute` | already in the ledger |
| Suspected duplicate, or AI unsure of a field | `warning-soft` | check before confirming |
| Missing date / amount / direction | `negative-soft` | must be fixed or unticked |

Reconciliation lines use the same palette: ✓ reconciled → `positive-soft`, ⚠ mismatch → `negative-soft`,
– cannot verify → `neutral-soft`.

## Typography

System fonts only; no bundled font files. Weight carries the hierarchy:
900 for the hero number and page titles, 800 for stat numbers, 600 for row titles, 400 for everything else.
Amounts always use tabular figures so columns line up.

## Shapes & spacing

- Cards: `xl` 24 dp. Preview rows and list groups: `lg` 16 dp. Inputs: `md` 12 dp. Chips: pill.
- Buttons are pills. No sharp corners anywhere.
- Card padding `lg`–`xl`; gaps between cards `md`.

## Components

- **Hero card** (`hero` background): label, 36 sp / 900 number, one supporting line in `hero-mute`.
- **Stat card** (`surface`): caption label in `mute`, 20 sp / 800 number.
- **Category bars**: inside one white card; track `surface-soft`, fill `accent`, 8 dp tall, pill ends.
- **Day header** in the ledger: date in 600 weight on `canvas`, balance and ✓ / ⚠ / – on the right;
  that day's transactions follow in one white `lg` card.
- **Category chip**: `surface-soft` pill with `accent` text and the source badge (👤 📘 ✨ ✋).
- **Navigation**: rail on wide screens, bottom bar on phones; `surface` background, selected item on `surface-soft`.

## Do / Don't

- Do keep the canvas / white-card contrast; don't add drop shadows or borders to cards.
- Do keep one hero card per screen; don't make every card dark.
- Don't use green as a brand or button colour.
- Don't use pure black in dark mode; the dark canvas is a blue-tinted graphite.
