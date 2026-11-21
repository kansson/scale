# Releases and updates

## Builds

- Builds are made with Gradle, locally or by `.github/workflows/release.yml`; the app uses no Expo
  cloud services and no over-the-air updates.
- Release builds are minified with R8, split into one APK per CPU architecture and signed with
  the owner's upload key: from `~/.gradle/gradle.properties` locally (`SCALE_UPLOAD_STORE_FILE`,
  `SCALE_UPLOAD_STORE_PASSWORD`, `SCALE_UPLOAD_KEY_ALIAS`, `SCALE_UPLOAD_KEY_PASSWORD`) or from
  the `release` environment's secrets in GitHub (`SCALE_UPLOAD_KEYSTORE`, the keystore in base64,
  and the same passwords and alias). A production release refuses the debug key.
- The workflow sets the version (`SCALE_VERSION_NAME`, `SCALE_VERSION_CODE`, the build time in
  minutes since 2026), so every release installs over the ones before.

Create the key once, outside the project, and keep it and its password backed up: Android installs
an update only when it is signed with the same key.

```sh
keytool -genkeypair -v -storetype PKCS12 -keystore ~/.android/scale-upload.jks \
  -alias scale -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Scale"
```

A PKCS12 keystore has one password, used for both the store and the key. For local release
builds, name it in `~/.gradle/gradle.properties`:

```properties
SCALE_UPLOAD_STORE_FILE=/Users/<you>/.android/scale-upload.jks
SCALE_UPLOAD_STORE_PASSWORD=<password>
SCALE_UPLOAD_KEY_ALIAS=scale
SCALE_UPLOAD_KEY_PASSWORD=<password>
```

Then build and install a signed release on a connected phone:

```sh
APP_VARIANT=production pnpm run generate
pnpm run android:release
```

## Channels

- A pushed `v` tag makes a stable release; `main` becomes a nightly pre-release once a day when
  the app may have changed (not for documentation, tests or tooling alone), or at once when the
  workflow is run by hand. The newest ten nightlies are kept.
- Each release holds `scale-<version>-<abi>.apk` files and `update.json`. The production app
  (`app.scale.android`) checks once a day, offers a newer build on its channel (Settings →
  Updates), verifies the download against GitHub's SHA-256 and hands it to Android's installer.
  `src/updates/update-release.ts` reads those names; change both together.

## Repository secrets

The release workflow needs the `release` environment (limited to `main` and `v*` tags) and its
secrets; without them the build step says which one is missing and stops.

| Secret                        | What it is                                         |
| ----------------------------- | -------------------------------------------------- |
| `SCALE_UPLOAD_KEYSTORE`       | The upload keystore above, in base64               |
| `SCALE_UPLOAD_STORE_PASSWORD` | The keystore's password                            |
| `SCALE_UPLOAD_KEY_ALIAS`      | The key's alias (`scale` in the command above)     |
| `SCALE_UPLOAD_KEY_PASSWORD`   | The key's password (the store's, for a PKCS12 key) |

```sh
base64 -i ~/.android/scale-upload.jks | gh secret set SCALE_UPLOAD_KEYSTORE --env release
gh secret set SCALE_UPLOAD_STORE_PASSWORD --env release
gh secret set SCALE_UPLOAD_KEY_ALIAS --env release --body scale
gh secret set SCALE_UPLOAD_KEY_PASSWORD --env release
```

The repository must be public: the app reads its releases from GitHub's API without a token.
