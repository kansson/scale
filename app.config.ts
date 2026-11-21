import { copyFile, mkdir } from 'node:fs/promises'
import { join } from 'node:path'
import type { ConfigContext, ExpoConfig } from 'expo/config'
import {
  type ConfigPlugin,
  withAndroidManifest,
  withAppBuildGradle,
  withDangerousMod,
  withGradleProperties,
  withProjectBuildGradle,
} from 'expo/config-plugins'
import { appIdentity } from './config/app-identity.ts'

/**
 * Gives Gradle twice the template's memory, for R8 to minify release builds in. Android
 * Gradle Plugin 9 already shrinks resources the optimised way, and release builds keep the
 * template's optimised R8 rules, since it refuses the unoptimised ones.
 */
const withGradleMemory: ConfigPlugin = (config) =>
  withGradleProperties(config, (gradleConfig) => {
    const key = 'org.gradle.jvmargs'
    const value = '-Xmx4096m -XX:MaxMetaspaceSize=1024m'

    const existing = gradleConfig.modResults.find(
      (item) => item.type === 'property' && item.key === key,
    )

    if (existing?.type === 'property') {
      existing.value = value
    } else {
      gradleConfig.modResults.push({ key, type: 'property', value })
    }

    return gradleConfig
  })

const releaseSigningMarker = '// Scale: release signing'

/**
 * Signs release builds with the owner's upload key when `~/.gradle/gradle.properties` names it
 * (`SCALE_UPLOAD_STORE_FILE`, `SCALE_UPLOAD_STORE_PASSWORD`, `SCALE_UPLOAD_KEY_ALIAS`,
 * `SCALE_UPLOAD_KEY_PASSWORD`). Without it, a release of the production app is refused rather
 * than signed with the debug key, which no properly signed update could replace; development
 * and preview releases fall back to the debug key.
 */
const withReleaseSigning =
  (production: boolean): ConfigPlugin =>
  (config) =>
    withAppBuildGradle(config, (gradleConfig) => {
      let contents = gradleConfig.modResults.contents

      if (contents.includes(releaseSigningMarker)) {
        return gradleConfig
      }

      const signingConfigs = '    signingConfigs {\n'

      const releaseSigning =
        '            signingConfig signingConfigs.debug\n            def enableShrinkResources'

      if (!contents.includes(signingConfigs) || !contents.includes(releaseSigning)) {
        throw new Error('Unable to configure release signing')
      }

      contents = contents.replace(
        signingConfigs,
        `${signingConfigs}        ${releaseSigningMarker}: the upload key named in gradle.properties.
        release {
            if (findProperty('SCALE_UPLOAD_STORE_FILE')) {
                storeFile file(findProperty('SCALE_UPLOAD_STORE_FILE'))
                storePassword findProperty('SCALE_UPLOAD_STORE_PASSWORD')
                keyAlias findProperty('SCALE_UPLOAD_KEY_ALIAS')
                keyPassword findProperty('SCALE_UPLOAD_KEY_PASSWORD')
            }
        }
`,
      )

      contents = contents.replace(
        releaseSigning,
        `            signingConfig findProperty('SCALE_UPLOAD_STORE_FILE') ? signingConfigs.release : signingConfigs.debug
            def enableShrinkResources`,
      )

      if (production) {
        contents += `
${releaseSigningMarker}: a production release is never signed with the debug key.
gradle.taskGraph.whenReady { graph ->
  def releasing = graph.allTasks.any { it.name ==~ /(assemble|bundle|package)Release/ }

  if (releasing && !findProperty('SCALE_UPLOAD_STORE_FILE')) {
    throw new GradleException('Set SCALE_UPLOAD_STORE_FILE and its passwords in ~/.gradle/gradle.properties to sign a production release.')
  }
}
`
      }

      gradleConfig.modResults.contents = contents

      return gradleConfig
    })

const abiSplitsMarker = '// Scale: one release APK per CPU architecture'

/**
 * Splits a release build into one APK per CPU architecture, such as
 * `app-arm64-v8a-release.apk`, each with only that architecture's native libraries. Debug builds
 * stay whole, and `-PreactNativeArchitectures` narrows the split as it narrows what is compiled.
 * Every split keeps the same version code: they are installed by hand, not offered side by side
 * in a store.
 */
const withAbiSplits: ConfigPlugin = (config) =>
  withAppBuildGradle(config, (gradleConfig) => {
    const contents = gradleConfig.modResults.contents

    if (contents.includes(abiSplitsMarker)) {
      return gradleConfig
    }

    const android = '\nandroid {\n'

    if (!contents.includes(android)) {
      throw new Error('Unable to configure ABI splits')
    }

    gradleConfig.modResults.contents = contents.replace(
      android,
      `${android}    ${abiSplitsMarker}, in release builds only.
    splits {
        abi {
            enable gradle.startParameter.taskNames.any { it.toLowerCase().contains('release') }
            reset()
            include(*((findProperty('reactNativeArchitectures') ?: 'armeabi-v7a,arm64-v8a,x86,x86_64').split(',')))
            universalApk false
        }
    }

`,
    )

    return gradleConfig
  })

const expoModuleCacheMarker = '// Scale: the expo module compiles this app’s module list'

/**
 * Keeps the `expo` module out of Gradle's build cache. It compiles the list of this app's native
 * modules, generated at build time, and Expo CLI builds with `--build-cache`: a cached compile
 * can come back without that list, so the app crashes at start with `ExpoModulesPackageList`
 * missing.
 */
const withFreshExpoModuleList: ConfigPlugin = (config) =>
  withProjectBuildGradle(config, (gradleConfig) => {
    if (gradleConfig.modResults.contents.includes(expoModuleCacheMarker)) {
      return gradleConfig
    }

    gradleConfig.modResults.contents += `
${expoModuleCacheMarker}, which a cached compile has restored without; it
// always compiles afresh so the app never starts without its modules.
subprojects { subproject ->
  if (subproject.name == 'expo') {
    subproject.tasks.configureEach { task ->
      task.outputs.doNotCacheIf('It compiles the generated list of this app’s modules') { true }
    }
  }
}
`

    return gradleConfig
  })

/**
 * Leaves out the settings Expo's prebuild writes for over-the-air updates, which this app does not
 * use: without `expo-updates` nothing reads them.
 */
const withoutUpdatesSettings: ConfigPlugin = (config) =>
  withAndroidManifest(config, (manifestConfig) => {
    for (const application of manifestConfig.modResults.manifest.application ?? []) {
      application['meta-data'] = application['meta-data']?.filter(
        (item) => !item.$['android:name'].startsWith('expo.modules.updates.'),
      )
    }

    return manifestConfig
  })

/**
 * The release's name, such as `1.1.0` or a nightly's `1.1.0-nightly.20261001.42+c2fa9fc`, set by
 * the release workflow; local builds are 0.0.1.
 */
const versionName = process.env.SCALE_VERSION_NAME ?? '0.0.1'

/**
 * Writes the release's whole name into `build.gradle`, where Android, and the app through
 * expo-application, read it. The config's `version` keeps only its leading numbers, since
 * expo-localization finds where its settings go by a `versionName` of numbers alone; this runs
 * after it, as plugins wrapping the config run after those it lists.
 */
const withVersionName: ConfigPlugin = (config) =>
  withAppBuildGradle(config, (gradleConfig) => {
    const versionLine = /versionName "[^"]*"/u

    if (!versionLine.test(gradleConfig.modResults.contents)) {
      throw new Error('Unable to set the version name')
    }

    gradleConfig.modResults.contents = gradleConfig.modResults.contents.replace(
      versionLine,
      `versionName "${versionName}"`,
    )

    return gradleConfig
  })

/**
 * What Health Connect needs before it lets the app write weights: the permission, a way to see
 * whether Health Connect is installed on Android 13 and earlier, and the screen that explains the
 * permission. Android 13 and earlier open it with `ACTION_SHOW_PERMISSIONS_RATIONALE`, Android 14
 * and later through the `VIEW_PERMISSION_USAGE` alias; without either, Health Connect refuses the
 * request without asking. The app's main screen explains what it writes, so both open it.
 */
const withHealthConnect: ConfigPlugin = (config) =>
  withAndroidManifest(config, (manifestConfig) => {
    const manifest = manifestConfig.modResults.manifest
    const application = manifest.application?.[0]

    const mainActivity = application?.activity?.find(
      (activity) => activity.$['android:name'] === '.MainActivity',
    )

    if (!application || !mainActivity) {
      throw new Error('Unable to configure Health Connect')
    }

    const weightPermission = 'android.permission.health.WRITE_WEIGHT'

    if (!manifest['uses-permission']?.some((item) => item.$['android:name'] === weightPermission)) {
      manifest['uses-permission'] = [
        ...(manifest['uses-permission'] ?? []),
        { $: { 'android:name': weightPermission } },
      ]
    }

    const rationale = 'androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE'

    if (
      !mainActivity['intent-filter']?.some((filter) =>
        filter.action?.some((action) => action.$['android:name'] === rationale),
      )
    ) {
      mainActivity['intent-filter'] = [
        ...(mainActivity['intent-filter'] ?? []),
        { action: [{ $: { 'android:name': rationale } }] },
      ]
    }

    const aliasName = 'ViewPermissionUsageActivity'

    if (!application['activity-alias']?.some((alias) => alias.$?.['android:name'] === aliasName)) {
      application['activity-alias'] = [
        ...(application['activity-alias'] ?? []),
        {
          $: {
            'android:name': aliasName,
            'android:exported': 'true',
            'android:targetActivity': '.MainActivity',
            'android:permission': 'android.permission.START_VIEW_PERMISSION_USAGE',
          },
          'intent-filter': [
            {
              action: [{ $: { 'android:name': 'android.intent.action.VIEW_PERMISSION_USAGE' } }],
              category: [{ $: { 'android:name': 'android.intent.category.HEALTH_PERMISSIONS' } }],
            },
          ],
        },
      ]
    }

    const healthConnect = 'com.google.android.apps.healthdata'
    const queries = manifest.queries ?? []

    const known = queries.some((query) =>
      query.package?.some((item) => item.$['android:name'] === healthConnect),
    )

    if (!known) {
      manifest.queries = [...queries, { package: [{ $: { 'android:name': healthConnect } }] }]
    }

    return manifestConfig
  })

/**
 * Stack screens open and close with Android 17's activity transitions, the ones the system
 * Settings app uses between its pages: the app's resources replace the react-native-screens
 * `default` animation (its Android 13 values) by name. `extend` needs Android 13, so older
 * versions keep the library's own.
 */
const withAndroidStackTransitions: ConfigPlugin = (config) =>
  withDangerousMod(config, [
    'android',
    async (modConfig) => {
      const source = join(modConfig.modRequest.projectRoot, 'config', 'android-transitions')
      const folder = join(modConfig.modRequest.platformProjectRoot, 'app/src/main/res/anim-v33')
      await mkdir(folder, { recursive: true })

      for (const name of [
        'rns_default_enter_in.xml',
        'rns_default_enter_out.xml',
        'rns_default_exit_in.xml',
        'rns_default_exit_out.xml',
      ]) {
        await copyFile(join(source, name), join(folder, name))
      }

      return modConfig
    },
  ])

export default ({ config }: ConfigContext): ExpoConfig => {
  const variant = appIdentity(process.env.APP_VARIANT)

  const app = withGradleMemory({
    ...config,
    name: variant.name,
    slug: 'scale',
    // Release builds are named and numbered by the release workflow (withVersionName).
    version: /^\d+(?:\.\d+)*/u.exec(versionName)?.[0] ?? '0.0.1',
    platforms: ['android'],
    orientation: 'portrait',
    icon: './assets/images/icon.png',
    scheme: variant.scheme,
    userInterfaceStyle: 'automatic',
    android: {
      package: variant.androidPackage,
      // The build time in minutes, from the release workflow, so every release, stable or
      // nightly, installs over the builds before it.
      versionCode: Number(process.env.SCALE_VERSION_CODE ?? 1),
      allowBackup: false,
      // Pulled in by libraries for things the app does not use.
      blockedPermissions: [
        'com.google.android.gms.permission.AD_ID',
        'android.permission.READ_EXTERNAL_STORAGE',
        'android.permission.SYSTEM_ALERT_WINDOW',
        'android.permission.VIBRATE',
        'android.permission.WRITE_EXTERNAL_STORAGE',
      ],
      predictiveBackGestureEnabled: true,
      adaptiveIcon: {
        backgroundColor: '#1d3b33',
        foregroundImage: './assets/images/android-icon-foreground.png',
        monochromeImage: './assets/images/android-icon-monochrome.png',
      },
    },
    plugins: [
      [
        'expo-build-properties',
        {
          android: {
            // Health Connect's client needs Android 8.
            minSdkVersion: 26,
            enableMinifyInReleaseBuilds: true,
            enableShrinkResourcesInReleaseBuilds: true,
            usePrecompiledHeaders: true,
          },
        },
      ],
      ['expo-localization', { supportedLocales: ['en', 'sv'] }],
      'expo-router',
      'expo-sqlite',
      'expo-status-bar',
    ],
    experiments: { typedRoutes: true, reactCompiler: true },
  })

  return withVersionName(
    withAbiSplits(
      withReleaseSigning(variant.variant === 'production')(
        withoutUpdatesSettings(
          withFreshExpoModuleList(withHealthConnect(withAndroidStackTransitions(app))),
        ),
      ),
    ),
  )
}
