# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

One person: the owner, weighing themselves on their own Bluetooth scale at home, usually in the
morning, phone in hand or set down nearby. No one else uses the app or the scale.

## Product Purpose

Scale reads a Bluetooth smart scale and saves each weighing to Health Connect, so other health
apps have the weight without the scale maker's app or account. Success is: open the app, step on
the scale, see the weight settle, and know it is in Health Connect, with no taps.

## Positioning

A replacement for the vendor app (Zepp Life and similar) that does one thing: it reads the scale
locally and hands the weight to Health Connect. No account, no cloud, no history of its own;
Health Connect is the record.

## Operating Context

- Used standing on the scale or just stepped off it, in a bathroom or bedroom, often before
  glasses or coffee.
- The scale sleeps between weighings and wakes when stepped on; the app pairs with the first scale
  it finds and reconnects each time.
- Weights are read in Health Connect, and by apps that read from it.
- Installed from the owner's GitHub releases; the app updates itself.

## Capabilities and Constraints

- Android only, Expo with Jetpack Compose through Expo UI and a local Material 3 bridge.
- Two screens: the weight, and Settings (paired scale, Health Connect, theme, language, updates,
  version, licences). Colour always follows the wallpaper; it is not a setting.
- Reads Xiaomi/Huami scales and standard Bluetooth weight scales (`docs/PROTOCOL.md`); kilograms,
  pounds and jin as the scale sends them.
- Saves automatically by default, or on demand; the last saved weight can be undone from Settings.
- English and Swedish.
- No accounts, servers, sync, analytics or over-the-air updates.

## Brand Commitments

- The name is Scale. Launcher mark: a scale on dark green `#1d3b33` (`assets/icon.svg`); dynamic
  colour always, with the brand seed `#3f6b5c` only on phones without it.
- Same house style as the owner's other Android apps (tension, vaka): plain Material 3, dynamic
  colour from the wallpaper by default.
- Voice: short, plain, second person; tells you what to do next.

## Evidence on Hand

No captured frames from a real scale yet; `ProtocolTest.kt` frames are built from the documented
layouts. No screenshots, store listing or users beyond the owner.

## Product Principles

1. Glanceable from standing: the weight reads at arm's length while still on the scale.
2. Trust that it saved: the save state is unmistakable, and a wrong weight is one tap from gone.
3. No taps on the happy path: open, step on, done.
4. Health Connect is the record: the app keeps no history and shows nothing it does not need.
5. Say what to do next: every problem state names the one action that fixes it.

## Accessibility & Inclusion

Material 3 and Android defaults: system font scale, TalkBack, sufficient contrast in both themes.
No additional standard.
