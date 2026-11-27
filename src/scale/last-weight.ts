import { useSyncExternalStore } from 'react'
import { z } from 'zod'
import { type NativeSaved, Scale } from '../../modules/scale'
import { weightUnits } from './state'

/**
 * The last weight saved to Health Connect, by the app or by the background weighing, so Settings
 * can undo it. The native side keeps it on the phone.
 */
const lastWeightSchema = z.object({
  /** Health Connect's id for the record. */
  recordId: z.string(),
  value: z.number(),
  unit: z.enum(weightUnits),
  measuredAt: z.number(),
})

export type LastWeight = z.infer<typeof lastWeightSchema>

const savedSchema = z.object({
  last: lastWeightSchema.nullable().catch(null),
  /** Records undone since the app started, so the main screen can say its weight was removed. */
  undone: z.array(z.string()).catch([]),
})

type Saved = z.infer<typeof savedSchema>

const nothingSaved: Saved = { last: null, undone: [] }

/** Reads what the native module sent; anything it cannot read counts as nothing saved. */
export function parseSaved(value: NativeSaved): Saved {
  const parsed = savedSchema.safeParse(value)

  return parsed.success ? parsed.data : nothingSaved
}

let snapshot: Saved | null = null

function readSaved() {
  snapshot ??= parseSaved(Scale.getSaved())

  return snapshot
}

function subscribe(listener: () => void) {
  // The native side sends what it has as soon as someone listens, then every change.
  const subscription = Scale.addListener('onSavedChange', (saved) => {
    snapshot = parseSaved(saved)
    listener()
  })

  return () => subscription.remove()
}

/** Removes the last saved weight from Health Connect. Throws when Health Connect refuses. */
export async function undoLastWeight() {
  const { last } = readSaved()

  if (last) await Scale.undoWeight(last.recordId)
}

export function useLastWeight() {
  return useSyncExternalStore(subscribe, readSaved, readSaved)
}
