import { NativeModule, requireNativeModule } from 'expo'

type NativeWeight = {
  value: number
  unit: string
  kilograms: number
}

/** The state as `ScaleState.toMap()` sends it. Validate it with `parseScaleState` before use. */
export type NativeScaleState = {
  connection: string
  problem: string | null
  paired: { address: string; name: string } | null
  live: (NativeWeight & { stable: boolean }) | null
  measurement: (NativeWeight & { id: number; measuredAt: number; recordId: string | null }) | null
  /** Weigh in the background is on: Android watches for the paired scale. */
  background: boolean
}

/** The weights saved, as `SavedWeights.toMap()` sends them. Validate it with `parseSaved`. */
export type NativeSaved = {
  last: { recordId: string; value: number; unit: string; measuredAt: number } | null
  undone: string[]
}

type ScaleEvents = {
  onStateChange: (state: NativeScaleState) => void
  onSavedChange: (saved: NativeSaved) => void
}

declare class ScaleNativeModule extends NativeModule<ScaleEvents> {
  getState(): NativeScaleState
  hasPermissions(): boolean
  /** Asks for the Bluetooth permissions; true once they are allowed. */
  requestPermissions(): Promise<boolean>
  /** Starts looking for the scale, and connects whenever it wakes. Call it while in front. */
  start(): void
  stop(): void
  /** Forgets the paired scale; the next scale found is paired instead. */
  forget(): void
  /** Whether Android can watch for the scale for the app (Android 12 and later). */
  canWeighInBackground(): boolean
  /**
   * Asks Android to watch for the paired scale, in its own dialog, while the scale is awake:
   * `linked`, `notLinked`, `busy` or `unavailable`. Validate it with `parseLinkResult`.
   */
  weighInBackground(): Promise<string>
  stopWeighingInBackground(): void
  requestBluetooth(): void
  openAppSettings(): void
  /** Whether Android has a language setting for each app (Android 13 and later). */
  canChooseLanguage(): boolean
  openLanguageSettings(): void
  /** `available`, `updateRequired` or `unavailable`. Validate it before use. */
  healthStatus(): string
  healthPermitted(): Promise<boolean>
  /** Shows Health Connect's permission screen unless it is allowed; true once it is. */
  requestHealthPermission(): Promise<boolean>
  /**
   * Saves a weight in kilograms, with the value and unit the scale showed, and returns its Health
   * Connect record id. It becomes the last weight saved.
   */
  saveWeight(kilograms: number, measuredAt: number, value: number, unit: string): Promise<string>
  /** The last weight saved, by the app or in the background, and the ones undone. */
  getSaved(): NativeSaved
  /** Removes a weight the app saved from Health Connect. */
  undoWeight(recordId: string): Promise<void>
  /** Health Connect's settings, or its Play Store page when it needs installing. */
  openHealthConnect(): void
}

/**
 * The scale connection, the background weighing and Health Connect, owned natively: JS starts,
 * stops and saves.
 */
export const Scale = requireNativeModule<ScaleNativeModule>('Scale')
