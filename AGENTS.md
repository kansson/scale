# Scale contributor guidance

- Android only. Keep Expo Router route files in `src/app/` and screens in `src/screens/`.
- Keep the app simple: one screen that shows the weight and saves it to Health Connect, and
  Settings. No accounts, servers or sync; Health Connect is where weights are kept.
- Build screens from `src/ui`, not from raw Expo UI. Add a named primitive when something is
  missing; screens must not name a Material Symbol or a colour role directly. New glyphs go in
  `src/ui/glyphs.ts`.
- Use Expo UI's Jetpack Compose components wherever one applies. Missing Material 3 pieces go in
  the focused Compose bridge `modules/android-components`, never as React Native look-alikes.
  The native Scaffold resolves slots once: keep its slots mounted.
  Expo UI pitfalls seen on device: toggling `enabled` on an icon button inside an app bar slot
  removes it (guard the handler instead), and Compose reads a `ListItem`'s slots once (`Group`
  keys rows by the slots they have).
- Android colour roles follow Material 3: screens on `surface`, containers on
  `surfaceContainerHigh`, text `onSurface` and `onSurfaceVariant`. Colour always comes from the
  dynamic palette; it is not a setting. Phones without one (before Android 12) take the brand seed
  in `src/settings/theme.ts`; `Host` and `rootBackgroundColor` both use `seedColor`
  (`src/lib/app-palette.ts`).
- Stack screens open and close with Android 17's activity transitions (`config/android-transitions`,
  installed over react-native-screens' `default` by `withAndroidStackTransitions`), started once
  the new screen has drawn (`StackEnter.kt`) with the system's edge extension (`StackEdges.kt`).
  A back swipe runs the system's cross-activity predictive back between screens (`StackBack.kt`),
  and at the root the system owns Back (`SystemBack`, `Back.kt`). `Back.kt` reflects on
  `ReactActivity`, pinned to React Native 0.88.1: retest predictive back before changing React
  Native or react-native-screens. App bar Back goes through `navigateBack()`, and `Screen` keeps
  its last frame through the exit transition (`ScreenFreeze.kt`), letting go of its removal when a
  back swipe commits (`onNativeDismissal`). App bars are rebuilt each time their screen's view is
  attached again (`rememberAttachCount` in `Scaffold.kt`): keep it, or a bar left collapsed stays
  stuck when its screen comes back.
- Settings → Licenses reads `src/settings/licenses.json`; run
  `pnpm run licenses:generate` after changing dependencies, and update its `nativeLibraries` when
  a module's Android dependencies change.
- The scale connection lives in `modules/scale`: `Scale.kt` owns scanning, pairing and
  reconnecting, `ScaleLink.kt` one GATT connection, `Weighing.kt` turns readings into
  measurements, `Health.kt` writes Health Connect. JS only starts and stops it
  (`useScaleWhileActive`), shows the state and saves measurements.
- `docs/PROTOCOL.md` is the protocol spec. Keep it, `Protocol.kt`, `Weighing.kt` and their tests
  in step; add frames captured from real scales to `ProtocolTest.kt` and record findings in the
  spec.
- Health Connect refuses its permission request without the rationale screen wiring in
  `withHealthConnect` (`app.config.ts`). Keep it when changing the manifest.
- The app updates itself from GitHub releases (`src/updates`, `modules/app-updates`): only the
  production package does, and `.github/workflows/release.yml` publishes one APK per ABI plus
  `update.json`. Keep the APK names (`scale-<version>-<abi>.apk`) and `update.json` in step with
  `src/updates/update-release.ts`.
- Parse every native and network response at its boundary with zod, and re-validate stored JSON
  when reading it (`src/settings/preferences.ts`).
- Keep app identity in `config/app-identity.ts`. Never reuse another project's signing or store
  identifiers.
- Add user-facing strings through Lingui with translator comments. Extract, translate Swedish and
  compile before checking; compiled catalogs stay ignored.
- Pin exact dependency versions in `package.json`. Only releases at least three days old are
  installed (`minimumReleaseAge`); a younger one goes in `minimumReleaseAgeExclude` with its exact
  version and a dated reason. Keep Expo packages on the SDK's compatible set
  (`npx expo install --check`).
- Workflows use only actions pinned to a full commit SHA with the version in a comment; the
  repository allows only GitHub's own actions, `voidzero-dev/setup-vp` and
  `gradle/actions/setup-gradle`. CI's `Check` job is the required status check on `main`.
- `docs/releases.md` covers signing, channels and the release secrets; keep it current. Commit
  `PRODUCT.md`, `DESIGN.md`, `.impeccable/design.json` and `.impeccable/surfaces/`; the rest of
  `.impeccable/` is local runtime state and ignores itself.
- No EAS, Expo cloud services or over-the-air updates. Release builds are signed with the owner's
  key from `~/.gradle/gradle.properties` (`SCALE_UPLOAD_*`) or the release workflow's secrets; a
  production release refuses the debug key. Never commit keystores or their passwords.
- Native dependency or module changes need a rebuild. Do not edit the generated `android/` folder;
  use config plugins and `modules/`.
- Run `pnpm run check` (it auto-fixes) and `pnpm run export` before considering work complete.
  After native changes, also run `pnpm run test:android` (needs `generate` and JDK 17).
  Report native build and device verification separately; static checks do not establish UI or
  Bluetooth behaviour.
