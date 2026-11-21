import Storage from 'expo-sqlite/kv-store'
import { useSyncExternalStore } from 'react'
import { z } from 'zod'
import { Scale } from '../../modules/scale'
import { weightUnits } from './state'

/** The last weight this app saved to Health Connect, so Settings can undo it. */
const lastWeightSchema = z.object({
  /** Health Connect's id for the record. */
  recordId: z.string(),
  value: z.number(),
  unit: z.enum(weightUnits),
  measuredAt: z.number(),
})

export type LastWeight = z.infer<typeof lastWeightSchema>

const key = 'scale.lastWeight'

/** Reads the stored weight; anything unreadable counts as none. */
export function readLastWeight(stored: string | null): LastWeight | null {
  if (stored === null) return null

  try {
    return lastWeightSchema.nullable().catch(null).parse(JSON.parse(stored))
  } catch {
    return null
  }
}

type Snapshot = {
  last: LastWeight | null
  /** Records undone this session, so the main screen can say its weight was removed. */
  undone: readonly string[]
}

let current: Snapshot = { last: readLastWeight(Storage.getItemSync(key)), undone: [] }

const listeners = new Set<() => void>()

function update(next: Snapshot) {
  current = next
  Storage.setItemSync(key, JSON.stringify(next.last))

  for (const listener of listeners) listener()
}

export function rememberLastWeight(weight: LastWeight) {
  update({ ...current, last: weight })
}

/** Removes the last saved weight from Health Connect. Throws when Health Connect refuses. */
export async function undoLastWeight() {
  const { last } = current

  if (!last) return

  await Scale.deleteWeight(last.recordId)
  // A weight saved while Health Connect was deleting is the last one now; keep it.
  update({
    last: current.last?.recordId === last.recordId ? null : current.last,
    undone: [...current.undone, last.recordId],
  })
}

function subscribe(listener: () => void) {
  listeners.add(listener)

  return () => {
    listeners.delete(listener)
  }
}

export function useLastWeight() {
  return useSyncExternalStore(subscribe, () => current)
}
