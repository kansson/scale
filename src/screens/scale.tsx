import { useLingui } from '@lingui/react/macro'
import { useRouter } from 'expo-router'
import { useState } from 'react'
import type { Measurement } from '@/scale/state'
import {
  type HealthAccess,
  type Saving,
  useHealthAccess,
  useHealthSaving,
} from '@/scale/use-health'
import { useScaleState } from '@/scale/use-scale'
import { formatWeight, unitSymbol } from '@/scale/weight'
import { usePreferences } from '@/settings/preferences'
import { Action, Screen } from '@/ui/screen'
import { type DockProps, Weighing, type WeighingProps } from '@/ui/weighing'
import { Scale } from '../../modules/scale'

/** Figure dashes keep the placeholder as wide as a real weight. */
const placeholderAmount = '‒‒.‒'

export function ScaleScreen() {
  const { i18n, t } = useLingui()
  const router = useRouter()
  const state = useScaleState()
  const { autoSave } = usePreferences()
  const { access, request: allowHealth } = useHealthAccess()
  const { saving, saveNow } = useHealthSaving(state.measurement, access)
  // Android stops asking after a denial or two; from then on only system settings can grant it.
  const [askedBluetooth, setAskedBluetooth] = useState(false)

  const allowBluetooth = async () => {
    if (askedBluetooth) {
      Scale.openAppSettings()

      return
    }

    setAskedBluetooth(true)

    if (await Scale.requestPermissions().catch(() => false)) Scale.start()
  }

  const reading = state.live ?? state.measurement
  const settling = state.live !== null && !(state.live.stable && state.measurement !== null)

  const weighing: WeighingProps = reading
    ? {
        figure: {
          amount: formatWeight(reading.value, i18n.locale),
          unit: unitSymbol(reading.unit),
          state: settling ? 'settling' : 'settled',
        },
        dock:
          settling || state.measurement === null
            ? {
                glyph: 'scale',
                title: t({ message: 'Weighing…', comment: 'Dock title while the scale settles' }),
                message: t({
                  message: 'Stand still until the number settles.',
                  comment: 'Dock line while the scale settles',
                }),
                moving: true,
              }
            : measurementDock(state.measurement, saving, access),
      }
    : {
        // No unit until the scale reports one: it may weigh in pounds.
        figure: { amount: placeholderAmount, unit: '', state: 'waiting' },
        dock: waitingDock(),
      }

  /** Before a weighing: what stands in the way, or what to do. */
  function waitingDock(): DockProps {
    if (state.problem === 'permission') {
      return {
        glyph: 'bluetooth',
        title: t({
          message: 'Allow Nearby devices',
          comment:
            'Dock title asking for the Bluetooth permission. Nearby devices is its name in Android',
        }),
        message: t({
          message: 'The app needs it to find your scale.',
          comment: 'Under Allow Nearby devices',
        }),
        action: {
          label: askedBluetooth
            ? t({ message: 'Open settings', comment: 'Opens the app’s system settings' })
            : t({ message: 'Allow', comment: 'Asks for a permission' }),
          onPress: () => void allowBluetooth(),
        },
      }
    }

    if (state.problem === 'bluetoothOff') {
      return {
        glyph: 'bluetoothOff',
        title: t({ message: 'Bluetooth is off', comment: 'Dock title when Bluetooth is off' }),
        message: t({
          message: 'Turn it on to read your scale.',
          comment: 'Under Bluetooth is off',
        }),
        action: {
          label: t({ message: 'Turn on', comment: 'Asks Android to turn Bluetooth on' }),
          onPress: () => Scale.requestBluetooth(),
        },
      }
    }

    if (access.status === 'updateRequired') {
      return {
        glyph: 'health',
        title: t({
          message: 'Install Health Connect',
          comment:
            'Dock title when the phone needs the Health Connect app before weights can be saved',
        }),
        message: t({
          message: 'Your weights are kept there.',
          comment: 'Under Install Health Connect',
        }),
        action: {
          label: t({ message: 'Install', comment: 'Opens Health Connect in the Play Store' }),
          onPress: () => Scale.openHealthConnect(),
        },
      }
    }

    if (autoSave && access.status === 'available' && !access.permitted) {
      return {
        glyph: 'health',
        title: t({
          message: 'Allow Health Connect',
          comment: 'Dock title asking for permission to write weights to Health Connect',
        }),
        message: t({
          message: 'So each weight is saved for you.',
          comment: 'Under Allow Health Connect',
        }),
        action: {
          label: t({ message: 'Allow', comment: 'Asks for a permission' }),
          onPress: () => void allowHealth(),
        },
      }
    }

    const scaleName =
      state.paired?.name ?? t({ message: 'your scale', comment: 'A scale that is not paired yet' })

    return {
      glyph: 'scale',
      title: t({ message: 'Step on the scale', comment: 'Dock title before there is a weight' }),
      message:
        state.connection === 'connecting'
          ? t({ message: `Connecting to ${scaleName}…`, comment: 'While connecting to the scale' })
          : state.connection === 'connected'
            ? t({
                message: `Connected to ${scaleName}`,
                comment: 'Connected, waiting for the first weight',
              })
            : state.paired
              ? t({
                  message: 'It wakes and connects as you stand on it.',
                  comment: 'Under Step on the scale while looking for the paired scale',
                })
              : t({
                  message: 'The first scale that wakes nearby is paired with this phone.',
                  comment: 'Under Step on the scale before a scale is paired',
                }),
      moving: state.connection === 'connecting',
    }
  }

  /** A settled weight: what became of it in Health Connect, and the one thing to do about it. */
  function measurementDock(
    measurement: Measurement,
    current: Saving,
    health: HealthAccess,
  ): DockProps {
    const time = i18n.date(new Date(measurement.measuredAt), { timeStyle: 'short' })
    const weighedAt = t({ message: `Weighed at ${time}`, comment: 'The time of the weighing' })

    if (health.status === 'unavailable') {
      return {
        glyph: 'health',
        title: t({ message: 'Not saved', comment: 'Dock title when the weight cannot be saved' }),
        message: t({
          message: 'Health Connect isn’t available on this phone.',
          comment: 'Under Not saved on a phone without Health Connect',
        }),
      }
    }

    if (health.status === 'updateRequired') {
      return {
        glyph: 'health',
        title: t({ message: 'Not saved', comment: 'Dock title when the weight cannot be saved' }),
        message: t({
          message: 'Install Health Connect to keep your weights.',
          comment: 'Under Not saved when Health Connect must be installed first',
        }),
        action: {
          label: t({ message: 'Install', comment: 'Opens Health Connect in the Play Store' }),
          onPress: () => Scale.openHealthConnect(),
        },
      }
    }

    switch (current.phase) {
      case 'saving':
        return {
          glyph: 'health',
          title: t({ message: 'Saving…', comment: 'While a weight is saved to Health Connect' }),
          message: weighedAt,
        }
      case 'saved':
        return {
          glyph: 'saved',
          tone: 'done',
          title: t({
            message: 'Saved to Health Connect',
            comment: 'Dock title after the weight was saved',
          }),
          message: weighedAt,
        }
      case 'removed':
        return {
          glyph: 'forget',
          title: t({
            message: 'Removed from Health Connect',
            comment: 'Dock title after removing the weight',
          }),
          message: weighedAt,
        }
      case 'failed':
        return {
          glyph: 'warning',
          tone: 'error',
          title: t({ message: 'Couldn’t save', comment: 'Dock title when saving failed' }),
          message: t({
            message: 'Check that Scale may write weights in Health Connect.',
            comment: 'Under Couldn’t save: how to fix it',
          }),
          action: {
            label: t({ message: 'Try again', comment: 'Tries saving the weight again' }),
            onPress: () => void saveNow(),
          },
        }
      case 'unsaved':
        return {
          glyph: 'health',
          title: t({ message: 'Not saved yet', comment: 'Dock title for a weight not yet saved' }),
          message: weighedAt,
          action: {
            label: t({ message: 'Save to Health Connect', comment: 'Saves the weight shown' }),
            onPress: () => void saveNow(),
          },
        }
    }
  }

  return (
    <Screen
      title={t({
        message: 'Scale',
        context: 'app name',
        comment:
          'The app’s name, the main screen’s title. A name: keep it unless Scale is known by another name',
      })}
      appBar='large'
      toBottomEdge
      actions={
        <Action
          glyph='settings'
          label={t({ message: 'Settings', comment: 'App bar button opening settings' })}
          onPress={() => router.push('/settings')}
        />
      }
    >
      <Weighing {...weighing} />
    </Screen>
  )
}
