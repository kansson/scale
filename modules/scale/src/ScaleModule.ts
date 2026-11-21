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
  measurement: (NativeWeight & { id: number; measuredAt: number }) | null
}

type ScaleEvents = {
  onStateChange: (state: NativeScaleState) => void
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
  /** Saves a weight in kilograms and returns its Health Connect record id. */
  saveWeight(kilograms: number, measuredAt: number): Promise<string>
  deleteWeight(id: string): Promise<void>
  /** Health Connect's settings, or its Play Store page when it needs installing. */
  openHealthConnect(): void
}

/** The scale connection and Health Connect, owned natively: JS starts, stops and saves. */
export const Scale = requireNativeModule<ScaleNativeModule>('Scale')
