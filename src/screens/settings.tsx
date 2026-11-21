import { useLingui } from '@lingui/react/macro'
import * as Application from 'expo-application'
import { useRouter } from 'expo-router'
import { useState } from 'react'
import { useFeedback } from '@/lib/feedback'
import { type LastWeight, undoLastWeight, useLastWeight } from '@/scale/last-weight'
import { useHealthAccess } from '@/scale/use-health'
import { useScaleState } from '@/scale/use-scale'
import { formatWeight, unitSymbol } from '@/scale/weight'
import { type Preferences, setPreference, type Theme, usePreferences } from '@/settings/preferences'
import { applyTheme } from '@/settings/theme'
import { ChoiceDialog } from '@/ui/dialogs'
import { Group, type GroupRow, List } from '@/ui/primitives'
import { Screen } from '@/ui/screen'
import {
  checkForUpdates,
  showUpdate,
  updatesSupported,
  useUpdateState,
} from '@/updates/app-updates'
import { Scale } from '../../modules/scale'

type UpdateChannel = Preferences['updateChannel']

type Dialog = 'theme' | 'updateChannel' | null

export function SettingsScreen() {
  const { i18n, t } = useLingui()
  const router = useRouter()
  const preferences = usePreferences()
  const scale = useScaleState()
  const { access, request: allowHealth } = useHealthAccess()
  const update = useUpdateState()
  const { last } = useLastWeight()
  const { showFeedback } = useFeedback()
  const [dialog, setDialog] = useState<Dialog>(null)

  const themes: Record<Theme, string> = {
    system: t({ message: 'System default', comment: 'Theme option following the phone' }),
    light: t({ message: 'Light', comment: 'Theme option' }),
    dark: t({ message: 'Dark', comment: 'Theme option' }),
  }

  const channels: Record<UpdateChannel, string> = {
    stable: t({ message: 'Stable', comment: 'Update channel: tested releases only' }),
    nightly: t({
      message: 'Nightly',
      comment: 'Update channel: a build of the newest changes each day',
    }),
  }

  const forget = () => {
    Scale.forget()
    showFeedback(
      t({
        message: 'Scale forgotten. The next scale that wakes nearby is paired.',
        comment: 'Snackbar after forgetting the paired scale',
      }),
    )
  }

  const lastWeightValue = (weight: LastWeight) => {
    const amount = `${formatWeight(weight.value, i18n.locale)} ${unitSymbol(weight.unit)}`

    const taken = i18n.date(new Date(weight.measuredAt), {
      dateStyle: 'medium',
      timeStyle: 'short',
    })

    return t({
      message: `${amount}, weighed ${taken}`,
      comment: 'Under Undo last weight: the weight, such as 72.4 kg, and its date and time',
    })
  }

  const undo = async () => {
    try {
      await undoLastWeight()
      showFeedback(
        t({
          message: 'Weight removed from Health Connect',
          comment: 'Snackbar after undoing the last saved weight',
        }),
      )
    } catch {
      showFeedback(
        t({
          message:
            'Couldn’t remove the weight. Check that Scale may write weights in Health Connect.',
          comment: 'Snackbar when undoing the last saved weight failed',
        }),
      )
    }
  }

  const scaleRows: GroupRow[] = scale.paired
    ? [
        {
          key: 'paired',
          glyph: 'scale',
          label: scale.paired.name,
          value:
            scale.connection === 'connected'
              ? t({ message: 'Connected', comment: 'The paired scale is connected now' })
              : t({
                  message: 'Paired. It connects when you step on it.',
                  comment: 'Under the paired scale’s name in settings',
                }),
        },
        {
          key: 'forget',
          glyph: 'forget',
          label: t({ message: 'Forget this scale', comment: 'Settings row unpairing the scale' }),
          value: t({
            message: 'To pair a different one',
            comment: 'Under Forget this scale: why to do it',
          }),
          onPress: forget,
        },
      ]
    : [
        {
          key: 'unpaired',
          glyph: 'scale',
          label: t({ message: 'No scale paired yet', comment: 'Settings row before pairing' }),
          value: t({
            message: 'Step on your scale with the app open to pair it',
            comment: 'Under No scale paired yet: how to pair',
          }),
        },
      ]

  const healthRows: GroupRow[] =
    access.status === 'available'
      ? [
          {
            key: 'autoSave',
            glyph: 'autoSave',
            label: t({
              message: 'Save automatically',
              comment: 'Settings switch saving every settled weight to Health Connect',
            }),
            value: preferences.autoSave
              ? t({
                  message: 'Each weight is saved as it settles',
                  comment: 'Under Save automatically while it is on',
                })
              : t({
                  message: 'Save each weight from the main screen',
                  comment: 'Under Save automatically while it is off',
                }),
            checked: preferences.autoSave,
            onPress: () => setPreference('autoSave', !preferences.autoSave),
          },
          ...(last
            ? [
                {
                  key: 'undo',
                  glyph: 'undo' as const,
                  label: t({
                    message: 'Undo last weight',
                    comment:
                      'Settings row removing the last weight saved to Health Connect, such as one taken holding something',
                  }),
                  value: lastWeightValue(last),
                  onPress: () => void undo(),
                },
              ]
            : []),
          access.permitted
            ? {
                key: 'open',
                glyph: 'health',
                label: t({
                  message: 'Open Health Connect',
                  comment: 'Settings row opening Health Connect, to see the weights or the access',
                }),
                value: t({
                  message: 'Your weights and the apps that read them',
                  comment: 'Under Open Health Connect',
                }),
                drillIn: true,
                onPress: () => Scale.openHealthConnect(),
              }
            : {
                key: 'allow',
                glyph: 'health',
                label: t({
                  message: 'Allow Health Connect',
                  comment: 'Settings row asking for permission to write weights',
                }),
                value: t({
                  message: 'Needed to save your weights',
                  comment: 'Under Allow Health Connect',
                }),
                onPress: () => void allowHealth(),
              },
        ]
      : access.status === 'updateRequired'
        ? [
            {
              key: 'install',
              glyph: 'health',
              label: t({
                message: 'Install Health Connect',
                comment: 'Settings row opening Health Connect in the Play Store',
              }),
              value: t({
                message: 'Needed to save your weights',
                comment: 'Under Install Health Connect',
              }),
              drillIn: true,
              onPress: () => Scale.openHealthConnect(),
            },
          ]
        : [
            {
              key: 'unavailable',
              glyph: 'health',
              label: t({
                message: 'Health Connect isn’t available',
                comment: 'Settings row on a phone without Health Connect',
              }),
              value: t({
                message: 'Weights are shown but not saved on this phone',
                comment: 'Under Health Connect isn’t available',
              }),
            },
          ]

  const appearanceRows: GroupRow[] = [
    {
      key: 'theme',
      glyph: 'theme',
      label: t({ message: 'Theme', comment: 'Settings row: light, dark or system' }),
      value: themes[preferences.theme],
      onPress: () => setDialog('theme'),
    },
    ...(Scale.canChooseLanguage()
      ? [
          {
            key: 'language',
            glyph: 'language' as const,
            label: t({ message: 'Language', comment: 'Settings row opening the app’s language' }),
            value: i18n.locale === 'sv' ? 'Svenska' : 'English',
            drillIn: true,
            onPress: () => Scale.openLanguageSettings(),
          },
        ]
      : []),
  ]

  const updateValue =
    update.phase === 'checking'
      ? t({ message: 'Checking…', comment: 'Settings row while looking for updates' })
      : update.phase === 'current'
        ? t({ message: 'Up to date', comment: 'Settings row when no update is available' })
        : update.phase === 'available' ||
            update.phase === 'downloading' ||
            update.phase === 'installing' ||
            (update.phase === 'failed' && update.update !== null)
          ? t({ message: 'Update available', comment: 'Settings row when an update is available' })
          : update.phase === 'failed'
            ? t({
                message: 'Couldn’t check for updates',
                comment: 'Settings row when the releases could not be read',
              })
            : t({ message: 'Check now', comment: 'Settings row checking for updates' })

  const version = `${Application.nativeApplicationVersion ?? ''} (${Application.nativeBuildVersion ?? ''})`

  return (
    <Screen
      title={t({ message: 'Settings', comment: 'Title of the settings screen' })}
      appBar='large'
      navigation='back'
    >
      <List grouped>
        <Group
          title={t({
            message: 'Scale',
            context: 'device',
            comment: 'Settings section about the paired scale',
          })}
          rows={scaleRows}
        />
        <Group
          title={t({ message: 'Health Connect', comment: 'Settings section; a product name' })}
          rows={healthRows}
        />
        <Group
          title={t({ message: 'Appearance', comment: 'Settings section' })}
          rows={appearanceRows}
        />
        {updatesSupported ? (
          <Group
            title={t({ message: 'Updates', comment: 'Settings section' })}
            rows={[
              {
                key: 'updateChannel',
                glyph: 'updateChannel',
                label: t({
                  message: 'Update channel',
                  comment: 'Settings row choosing which releases the app updates to',
                }),
                value: channels[preferences.updateChannel],
                onPress: () => setDialog('updateChannel'),
              },
              {
                key: 'checkUpdates',
                glyph: 'update',
                label: t({
                  message: 'Check for updates',
                  comment: 'Settings row looking for a newer version',
                }),
                value: updateValue,
                onPress: () =>
                  update.phase === 'idle' ||
                  update.phase === 'current' ||
                  (update.phase === 'failed' && update.update === null)
                    ? void checkForUpdates({ asked: true })
                    : showUpdate(),
              },
            ]}
          />
        ) : null}
        <Group
          title={t({ message: 'About', comment: 'Settings section about the app' })}
          rows={[
            {
              key: 'version',
              glyph: 'info',
              label: t({ message: 'Version', comment: 'Settings row showing the app version' }),
              value: version,
            },
            {
              key: 'licenses',
              glyph: 'licenses',
              label: t({
                message: 'Licenses',
                comment:
                  'Settings row opening the licenses of the open-source software the app uses',
              }),
              drillIn: true,
              onPress: () => router.push('/licenses'),
            },
          ]}
        />
      </List>
      {dialog === 'theme' ? (
        <ChoiceDialog
          title={t({ message: 'Theme', comment: 'Dialog title' })}
          options={(['system', 'light', 'dark'] as const).map((value) => ({
            value,
            label: themes[value],
          }))}
          selected={preferences.theme}
          onSelect={(theme) => {
            setPreference('theme', theme)
            applyTheme(theme)
          }}
          onDismiss={() => setDialog(null)}
        />
      ) : dialog === 'updateChannel' ? (
        <ChoiceDialog
          title={t({ message: 'Update channel', comment: 'Dialog title' })}
          options={(['stable', 'nightly'] as const).map((value) => ({
            value,
            label: channels[value],
          }))}
          selected={preferences.updateChannel}
          onSelect={(channel) => {
            setPreference('updateChannel', channel)
            void checkForUpdates({ asked: false })
          }}
          onDismiss={() => setDialog(null)}
        />
      ) : null}
    </Screen>
  )
}
