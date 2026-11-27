---
name: Scale
description: Plain Material 3 expressive on the dynamic palette; one weight, read from standing, and a dock that says what became of it.
colors:
  brand-seed: '#3f6b5c'
  launcher-ground: '#1d3b33'
typography:
  figure:
    fontFamily: 'Roboto (system face, variable from Android 12), sans-serif'
    fontSize: '168sp'
    fontWeight: 800
    lineHeight: '168sp'
    letterSpacing: '-2sp'
    fontFeature: 'tnum'
  figure-unit:
    fontFamily: 'Roboto (system face), sans-serif'
    fontSize: '28sp'
    fontWeight: 400
  dialog-title:
    fontFamily: 'Roboto (system face), sans-serif'
    fontSize: '24sp'
    fontWeight: 400
  dock-title:
    fontFamily: 'Roboto (system face), sans-serif'
    fontSize: '22sp'
    fontWeight: 400
    lineHeight: '28sp'
  group-title:
    fontFamily: 'Roboto (system face), sans-serif'
    fontSize: '14sp'
    fontWeight: 500
    lineHeight: '20sp'
  body:
    fontFamily: 'Roboto (system face), sans-serif'
    fontSize: '14sp'
    fontWeight: 400
    lineHeight: '20sp'
  label:
    fontFamily: 'Roboto (system face), sans-serif'
    fontSize: '14sp'
    fontWeight: 500
    lineHeight: '20sp'
rounded:
  row-join: '4dp'
  dialog-row: '16dp'
  badge: '18dp'
  card: '20dp'
  dock: '28dp'
  full: '9999dp'
spacing:
  hairline: '2dp'
  row-gap: '8dp'
  inline: '16dp'
  rhythm: '20dp'
  gutter: '24dp'
components:
  dock:
    rounded: '{rounded.dock}'
    padding: '16dp 24dp 24dp 24dp'
  dock-badge:
    rounded: '{rounded.badge}'
    size: '56dp'
  dock-badge-saved:
    size: '56dp'
  dock-button:
    rounded: '{rounded.full}'
    typography: '{typography.label}'
    height: '56dp'
    width: '100%'
  settings-group-row:
    rounded: '{rounded.card}'
    padding: '16dp'
  group-title:
    typography: '{typography.group-title}'
    padding: '0 4dp'
---

# Design System: Scale

## Overview

**Creative North Star: "The Scale's Own Display"**

Scale is plain Material 3 expressive, the same house style as the owner's other Android apps, coloured by the wallpaper. Nothing on screen is decorative: the weight fills the upper screen like a scale's readout, and everything about it (what to do, what happened, the one action) sits in a single dock at the bottom edge, under the thumb. The figure carries state in its own strokes: light while the scale moves, heavy once it settles.

Density is low on the weighing screen and standard Material on Settings. Colour does jobs only: primary marks settled and saved, error marks a failed save, and everything else is surface tones. All UI is native Jetpack Compose through Expo UI and the local bridge in `modules/android-components`; there are no React Native look-alikes.

**Key Characteristics:**

- Dynamic colour from the wallpaper, always; the brand seed only on phones without it.
- One figure, as large as the width allows, with tabular digits that change in place.
- One dock, one badge, one title, one line, at most one full-width button.
- Tonal layering, no shadows.
- Motion only while something moves.

## Colors

The palette is Material 3's, resolved at runtime: the wallpaper's on Android 12+, always (there is no setting), otherwise a scheme generated from the brand seed. No screen hard-codes a colour.

### Primary

- **Settled Primary** (`primary` role): the saved badge, the wavy indicator, filled dock buttons, Settings group titles. It means "this is done" or "do this next".
- **Field Green** (brand-seed): the seed of the app's own scheme on phones without dynamic colour. Taken from `brandColor` as `seedColor`, which feeds both `Host` and `rootBackgroundColor`.

### Secondary

- **Quiet Badge** (`secondaryContainer` / `onSecondaryContainer`): the neutral dock badge and the selected row of a single-choice group.

### Neutral

- **Ground** (`surface`): every screen, the app bar, and the root background behind stack transitions so fades never flash.
- **Dock and Cards** (`surfaceContainerHigh`): the dock and every Settings card row.
- **Wave Track** (`surfaceContainerHighest`): the indicator track, only while moving.
- **Ink** (`onSurface`): the settled figure and titles. **Muted Ink** (`onSurfaceVariant`): the moving figure, the unit, supporting lines, row glyphs. **Placeholder** (`outlineVariant`): the dashes before anyone steps on.
- **Launcher Ground** (launcher-ground): the launcher icon background only (`assets/icon.svg`); never used in-app.

### Error

- **Failed Save** (`errorContainer` / `onErrorContainer`): the dock badge when a save fails, and error notices.

### Named Rules

**The Colour Does Jobs Rule.** Primary appears only for settled, saved, or the next action; error only for a failure. A screen at rest is surface tones.

**The No Raw Roles Rule.** Screens never name a colour role or hex. Roles are chosen inside `src/ui` primitives and the Compose bridge.

## Typography

**Display Font:** the system face (Roboto; variable from Android 12, so strokes animate smoothly)
**Body Font:** the system face through Material 3's type scale

**Character:** One face throughout. The figure is the only custom style; everything else is a named Material type role, so system font scale applies everywhere.

### Hierarchy

- **Figure** (displayLarge, 300 settling / 800 settled, 168sp step auto-sized down to 56sp in 4sp steps, line height 168sp, letter spacing -2sp, tabular figures): the weight. Weight animates between 300 and 800; the variable face thickens smoothly.
- **Unit** (headlineMedium, `onSurfaceVariant`): beside the figure on its baseline, 8dp gap. Hidden while the placeholder shows.
- **Dialog title** (headlineSmall): choice dialogs.
- **Dock title** (titleLarge, `onSurface`): what is happening, in a few words.
- **Group title** (titleSmall, `primary`): above Settings groups.
- **Body** (bodyMedium, `onSurfaceVariant`): the dock's one supporting line, notices.
- **Label** (labelLarge): dock button labels.

### Named Rules

**The Strokes Carry State Rule.** The figure's weight is the settling signal: 300 while moving, 800 once settled. Do not add a separate "settled" badge or line to say the same thing.

**The Placeholder Has No Unit Rule.** Before a reading, the figure shows dashes (`‒‒.‒`, heavy, `outlineVariant`) and no unit; the unit is the scale's to report.

## Layout

The weighing screen is a full-bleed column under a large top app bar titled with the app's name, with Settings as its only action. The figure is centred in the room above a fixed dock reserve (the dock's tallest usual form: two-line message plus button, scaled by font scale), so it holds still as the dock grows. The dock is overlaid at the bottom edge and draws behind the system navigation bar, keeping its content above it with `NavigationBarSpacer`.

Spacing: 24dp side gutters on the figure and inside the dock; 20dp between the dock's indicator, row and button; 16dp between badge and text; 2dp between title and line; 24dp under the dock content. Settings is a grouped lazy list inset 16dp, with 20dp between groups and above the first, 8dp between a title and its group, and 2dp between rows.

## Elevation & Depth

Flat. Depth is tonal: `surface` ground, `surfaceContainerHigh` for the dock and cards. No shadows or elevation tints are added by the app.

### Named Rules

**The Tonal Step Rule.** A container is one tonal step above its ground; separation comes from tone and corner shape, never a line or shadow.

## Shapes

Material 3 expressive corners. The dock has 28dp top corners and square bottom (it meets the screen edge). The dock badge is a 56dp shape: Material's Cookie9Sided when saved, an 18dp rounded square otherwise. Settings card runs round their outer corners at 20dp and tighten joins to 4dp. Dialog option rows use 16dp. Buttons are full pills.

## Components

### Dock (signature)

- **Character:** the single place the screen speaks. Problems (permission, Bluetooth, Health Connect) take its place in the same anatomy.
- **Anatomy:** wavy linear indicator along the top edge, then a row of badge (56dp) plus title and one line, then at most one 56dp full-width button.
- **Background:** `surfaceContainerHigh`, 28dp top corners, `animateContentSize` as the button arrives or leaves.
- **Indicator:** `LinearWavyProgressIndicator`, indeterminate in `primary` on a `surfaceContainerHighest` track only while moving (settling or connecting). At rest it stays laid out with zero amplitude and a transparent track, so the dock keeps its height and shows its plain rounded edge.
- **Badge tones:** neutral `secondaryContainer` rounded square; done `primary` Cookie9Sided; error `errorContainer` rounded square. Glyph 28dp.

### Buttons

- **Shape:** full pill, 56dp tall, full width inside 24dp gutters.
- **Filled:** the one thing to do next (Allow, Turn on, Try again, Save to Health Connect); label `onPrimary`, labelLarge.

### Weight Readout (bridge: `WeightReadout.kt`)

- Figure plus unit on one baseline. Colour and weight animate with the theme's M3 motion scheme (`defaultEffectsSpec` for colour, `slowEffectsSpec` for strokes). A Confirm haptic fires on the transition from settling to settled, not when opening on a settled weight. Reads as one phrase to TalkBack; the placeholder is silent.

### Settings groups

- **Style:** connected card rows in `surfaceContainerHigh` (selected rows `secondaryContainer`), 20dp outer / 4dp join corners, 24dp `onSurfaceVariant` leading glyph, optional one-line value, trailing chevron or switch.

### Background weighing dialog (`WeighedDialog.kt`)

- Material's alert dialog with an icon, drawn by Compose in a see-through activity over whatever is on screen: 24dp padding, centred 24dp icon (`secondary` when saved, `error` when not), centred headlineSmall title, bodyMedium text, text buttons at the end. A 60% black dim; it rises 20dp and fades in, and leaves with a 10dp rise, as Android's own dialogs. Done, Back or a tap outside dismisses it; Undo (saved) or Open Scale (not saved) is the other button. Follows the system's light or dark theme on the wallpaper palette.

### Navigation

- **Top app bar:** Material Scaffold with small (pinned) or large (exit-until-collapsed) bars on `surface`. Actions are 24dp icon buttons labelled for TalkBack. The weighing screen's bar is large, titled Scale, with Settings only.

## Do's and Don'ts

### Do:

- **Do** build screens from `src/ui` primitives; add a named primitive when something is missing.
- **Do** add glyphs to `src/ui/glyphs.ts`, named for meaning (`saved`, `warning`), not appearance.
- **Do** keep the figure centred above the fixed dock reserve so it never jumps.
- **Do** keep the wavy indicator laid out at all times and show it only while something moves.
- **Do** keep Scaffold slots mounted (the actions row is always present); the native Scaffold resolves slots once.
- **Do** guard an app bar action's handler instead of toggling `enabled`.
- **Do** key list rows by the slots they have; Compose reads a `ListItem`'s slots once.

### Don't:

- **Don't** draw a settled line or divider on the dock's top edge; a straight settled indicator read as a divider on device.
- **Don't** toggle `enabled` on an icon button inside an app bar slot; Expo UI removes it.
- **Don't** name a Material Symbol or a colour role in a screen.
- **Don't** build React Native look-alikes of Material 3 pieces; missing ones go in `modules/android-components`.
- **Don't** show a unit before the scale reports one.
- **Don't** add shadows or borders to separate surfaces.
