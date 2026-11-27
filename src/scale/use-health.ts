import { useCallback, useEffect, useRef, useState } from 'react'
import { AppState } from 'react-native'
import { z } from 'zod'
import { getPreferences } from '@/settings/preferences'
import { Scale } from '../../modules/scale'
import { useLastWeight } from './last-weight'
import { type HealthStatus, type Measurement, parseHealthStatus } from './state'

export type HealthAccess = {
  status: HealthStatus
  /** The app may write weights. */
  permitted: boolean
  /** Whether `permitted` has been read from Health Connect yet, rather than assumed false. */
  checked: boolean
}

/** Health Connect's answer to whether the app may write; anything else counts as no. */
const permittedSchema = z.boolean().catch(false)

function readPermitted(answer: Promise<boolean>) {
  return answer.then(
    (value) => permittedSchema.parse(value),
    () => false,
  )
}

/** Whether Health Connect is there and allows the app to write, read again on every return. */
export function useHealthAccess() {
  const [access, setAccess] = useState<HealthAccess>(() => ({
    status: parseHealthStatus(Scale.healthStatus()),
    permitted: false,
    checked: false,
  }))

  const refresh = useCallback(async () => {
    const status = parseHealthStatus(Scale.healthStatus())
    const permitted = status === 'available' && (await readPermitted(Scale.healthPermitted()))

    setAccess({ status, permitted, checked: true })
  }, [])

  /** Shows Health Connect's permission screen unless it is allowed, then reads the access again. */
  const request = useCallback(async () => {
    await readPermitted(Scale.requestHealthPermission())
    await refresh()
  }, [refresh])

  useEffect(() => {
    void refresh()

    const subscription = AppState.addEventListener('change', (next) => {
      if (next === 'active') void refresh()
    })

    return () => subscription.remove()
  }, [refresh])

  return { access, request }
}

/** Health Connect's id for a saved record. */
const recordIdSchema = z.string().min(1)

/** Where one measurement stands with Health Connect. */
export type Saving =
  | { phase: 'unsaved' }
  | { phase: 'saving' }
  | { phase: 'saved'; recordId: string }
  | { phase: 'removed' }
  | { phase: 'failed' }

/**
 * Saves each measurement to Health Connect once: by itself when automatic saving is on and
 * allowed, otherwise when asked. One the background weighing saved already comes with its record.
 * The last one saved can be undone from Settings, such as when someone else stepped on the scale.
 */
export function useHealthSaving(measurement: Measurement | null, access: HealthAccess) {
  const [saving, setSaving] = useState<{ id: number; saving: Saving } | null>(null)
  const { undone } = useLastWeight()
  // Measurements already handed to Health Connect, so a re-render never saves one twice.
  const handled = useRef(new Set<number>())

  const savedInBackground: Saving | null = measurement?.recordId
    ? { phase: 'saved', recordId: measurement.recordId }
    : null

  const stored =
    savedInBackground ?? (measurement && saving?.id === measurement.id ? saving.saving : null)

  const current: Saving | null =
    stored?.phase === 'saved' && undone.includes(stored.recordId) ? { phase: 'removed' } : stored

  // Measurement ids only grow, so a save that ends after the next weighing began leaves it be.
  const show = useCallback((id: number, next: Saving) => {
    setSaving((previous) => (previous && previous.id > id ? previous : { id, saving: next }))
  }, [])

  const save = useCallback(
    async (next: Measurement) => {
      handled.current.add(next.id)
      show(next.id, { phase: 'saving' })

      try {
        const recordId = recordIdSchema.parse(
          await Scale.saveWeight(next.kilograms, next.measuredAt, next.value, next.unit),
        )

        show(next.id, { phase: 'saved', recordId })
      } catch {
        show(next.id, { phase: 'failed' })
      }
    },
    [show],
  )

  useEffect(() => {
    // Wait for Health Connect's answer: until then `permitted` is only assumed false.
    if (!measurement || !access.checked || handled.current.has(measurement.id)) return

    // Saved in the background already.
    if (measurement.recordId) {
      handled.current.add(measurement.id)

      return
    }

    // Weighed while the app was away: the background weighing saves it, and says so.
    if (AppState.currentState !== 'active') {
      handled.current.add(measurement.id)
      show(measurement.id, { phase: 'unsaved' })

      return
    }

    if (getPreferences().autoSave && access.permitted) {
      void save(measurement)
    } else {
      handled.current.add(measurement.id)
      show(measurement.id, { phase: 'unsaved' })
    }
  }, [measurement, access.checked, access.permitted, save, show])

  /** Saves the measurement, asking for Health Connect's permission first when needed. */
  const saveNow = async () => {
    if (!measurement) return

    const permitted = access.permitted || (await readPermitted(Scale.requestHealthPermission()))

    if (permitted) {
      await save(measurement)
    } else {
      show(measurement.id, { phase: 'failed' })
    }
  }

  return { saving: current ?? { phase: 'unsaved' as const }, saveNow }
}
