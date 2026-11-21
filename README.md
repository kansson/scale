# Scale

An Android app for Bluetooth smart scales. Step on the scale and the app shows your weight as it
settles, then saves it to Health Connect, where your other health apps read it. Nothing else: no
account, no server, no sync.

It reads the Xiaomi/Huami scales (Mi Scale, Mi Scale 2, Mi Body Composition Scale) and other
scales that use the standard Bluetooth Weight Scale profile. [docs/PROTOCOL.md](docs/PROTOCOL.md)
describes the protocol.

## What it does

- **Finds your scale.** While the app is open it looks for a scale; the first one that wakes
  nearby is paired, and from then on only it is used, until it is forgotten in Settings. Scales
  sleep between weighings and wake when stepped on, so the app reconnects each time.
- **Shows the weight as it settles.** The figure is light while the scale still moves and turns
  heavy, with a tick in the hand, once it settles. One weighing gives one measurement.
- **Saves it to Health Connect**, as a weight from a scale: by itself with _Save automatically_
  on, otherwise with one tap. The last saved weight can be undone from Settings, such as when
  someone else stepped on the scale.
- **Settings**: the paired scale, Health Connect, the theme (system, light or dark), the app's
  language (Android 13 and later), updates, the version and the open-source licences. Colours
  follow the wallpaper on Android 12 and later.
- **Updates itself** from this repository's releases, on a Stable or Nightly channel.

English and Swedish.

## Getting started

You need [Vite+](https://viteplus.dev) (`vp`), Node.js (the version in `.node-version`), pnpm (the
version in `package.json`), JDK 17 and the Android SDK from Android Studio.

```sh
pnpm install --frozen-lockfile
pnpm run android   # builds the development app and installs it on the connected phone
pnpm run start     # Metro alone, once the development app is installed
```

Expo Go cannot run Scale: it has native modules of its own. Bluetooth needs a real phone; the
emulator has no Bluetooth radio. `android/` is generated and ignored: after changing native
modules, config plugins or native dependencies, run `pnpm run generate` and build again.

## Developing

```sh
pnpm run check          # formats, lints, type-checks and runs the tests
pnpm run export         # bundles the app's JavaScript, to confirm it builds
pnpm run test:android   # the native modules' JVM tests (after generate; needs JDK 17)
pnpm run i18n:extract   # collects new strings; translate them in src/locales/sv
pnpm run licenses:generate  # after changing dependencies
pnpm run icons:generate     # after editing assets/icon.svg
```

Formatting uses single quotes and no semicolons, and lint runs type-aware with the pinned anti-slop
rules in `tools/oxlint/anti-slop`. No dependency younger than three days is installed
(`minimumReleaseAge` in `pnpm-workspace.yaml`). [AGENTS.md](AGENTS.md) lists the conventions;
[PRODUCT.md](PRODUCT.md) and [DESIGN.md](DESIGN.md) describe the product and its design.

| Location                     | Responsibility                                                         |
| ---------------------------- | ---------------------------------------------------------------------- |
| `src/app/`                   | Expo Router routes, each forwarding to a screen in `src/screens/`      |
| `src/ui/`                    | The app's building blocks on Expo UI's Jetpack Compose components      |
| `src/scale/`                 | The scale's state and Health Connect, parsed with zod at the boundary  |
| `src/settings/`, `src/i18n/` | Preferences, theme and licences; English and Swedish                   |
| `src/updates/`               | The self-updater                                                       |
| `modules/scale`              | Bluetooth scanning, pairing, the scale protocols and Health Connect    |
| `modules/android-components` | Material 3 pieces Expo UI lacks, stack transitions and predictive back |
| `modules/app-updates`        | Downloads, verifies and installs updates                               |

## Releases

Releases are built by GitHub Actions and the app updates itself from them. Push a `v` tag to
release a commit; `main` becomes a nightly once a day when the app changed, or at once from
Actions → Release → Run workflow. [docs/releases.md](docs/releases.md) covers signing.

The app comes in three variants, chosen with `APP_VARIANT` (`config/app-identity.ts`), which
install side by side:

| Variant     | App name      | Package                   | Link scheme        |
| ----------- | ------------- | ------------------------- | ------------------ |
| development | Scale Dev     | app.scale.android.dev     | `scale-dev://`     |
| preview     | Scale Preview | app.scale.android.preview | `scale-preview://` |
| production  | Scale         | app.scale.android         | `scale://`         |

## Licence

MIT; see [LICENSE](LICENSE).
