---
version: 1
slug: 'src-screens-scale-tsx'
primary_target: 'src/screens/scale.tsx'
related_targets: []
---

# Scale main screen

Scope: the weighing screen (`src/screens/scale.tsx`), the app's only working screen. Mode: Operate.

Task: open the app, step on the scale, read the weight from standing, know it reached Health
Connect, remove it if it is wrong. Shows only this weighing: no history (confirmed 2026-11-21).
Constraints: Material 3 family shared with tension and vaka, bolder allowed on this screen;
dynamic colour; Compose through Expo UI and the local bridge; no taps on the happy path.

## Direction contract

THESIS: The weight owns the upper screen and everything about it docks in one panel under the thumb. Refuses the category default of a centred number floating over a stray progress bar with a notice card above it.

OWN-WORLD: Material 3 expressive on the dynamic scheme: surface ground, a surfaceContainerHigh dock with 28dp top corners, a 56dp primary shape badge, tabular display figures. Colour does jobs only: primary for settled and saved, error for a failed save.

STORY: Before stepping on, a quiet placeholder figure and the dock saying what to do. While stepping on, the figure moves light and the dock edge waves. Settled, the figure turns heavy and the wave goes, leaving the dock's plain rounded edge (a settled straight line read as a divider on device). Saved, the dock confirms with the time; the last saved weight is undone from Settings, not from the dock.

FIRST VIEWPORT: Large top app bar titled Scale, Settings as its only action. Figure as wide as the column allows (up to 168sp, auto-sized down to 56sp), with its unit, centred above room kept for the dock at its tallest, so it holds still as the dock grows a button. The placeholder shows no unit until the scale reports one. Dock pinned to the bottom edge, drawn behind the navigation bar: a wave along its top only while something moves, badge plus title plus one supporting line, one full-width button when there is an action. Problems (permission, Bluetooth, Health Connect) take the dock's place in the same anatomy.

FORM: Docked status, 6th of 7 on the ranked structure list, seed:1125a011d0dad3f4. Signature move: the dock's top edge is the settling indicator, a wave while the reading moves that is gone once it settles, with the figure's weight following it from light to heavy.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
