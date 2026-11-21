import Storage from 'expo-sqlite/kv-store'
import { useSyncExternalStore } from 'react'
import { z } from 'zod'

/** The app's own choices, kept on the phone. Values out of range fall back to their defaults. */
const preferencesSchema = z.object({
  theme: z.enum(['system', 'light', 'dark']).catch('system'),
  /** Settled weights go to Health Connect without asking. */
  autoSave: z.boolean().catch(true),
  /** Which releases the app updates to: tested ones only, or every nightly build as well. */
  updateChannel: z.enum(['stable', 'nightly']).catch('stable'),
  /** When updates were last looked for, in milliseconds; they are looked for once a day. */
  updatesCheckedAt: z.number().catch(0),
  /** The version code of an update put off with Later; it is not offered again unasked. */
  updateDeclined: z.number().catch(0),
})

export type Preferences = z.infer<typeof preferencesSchema>

export type Theme = Preferences['theme']

const key = 'scale.preferences'

/** The stored JSON; unreadable preferences fall back to the defaults rather than stop the app. */
const storedSchema = z
  .string()
  .transform((text) => {
    try {
      return JSON.parse(text)
    } catch {
      return null
    }
  })
  .pipe(z.looseObject({}).catch({}))

/** Reads stored preferences, filling in a default for anything missing or out of range. */
function readPreferences(stored: string | null): Preferences {
  return preferencesSchema.parse(storedSchema.parse(stored ?? '{}'))
}

let current = readPreferences(Storage.getItemSync(key))

const listeners = new Set<() => void>()

export function getPreferences() {
  return current
}

export function setPreference<K extends keyof Preferences>(name: K, value: Preferences[K]) {
  current = { ...current, [name]: value }
  Storage.setItemSync(key, JSON.stringify(current))

  for (const listener of listeners) listener()
}

function subscribe(listener: () => void) {
  listeners.add(listener)

  return () => {
    listeners.delete(listener)
  }
}

export function usePreferences() {
  return useSyncExternalStore(subscribe, getPreferences)
}
