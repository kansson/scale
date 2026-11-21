import { z } from 'zod'
import type { NativeScaleState } from '../../modules/scale'

export const weightUnits = ['kg', 'lb', 'catty'] as const

export type WeightUnit = (typeof weightUnits)[number]

const weightSchema = z.object({
  value: z.number(),
  unit: z.enum(weightUnits),
  kilograms: z.number(),
})

const scaleStateSchema = z.object({
  connection: z.enum(['stopped', 'searching', 'connecting', 'connected']),
  problem: z.enum(['permission', 'bluetoothOff']).nullable(),
  paired: z.object({ address: z.string(), name: z.string() }).nullable(),
  /** What the scale shows right now, while someone is on it. */
  live: weightSchema.extend({ stable: z.boolean() }).nullable(),
  /** The weighing's settled weight, which stays after stepping off until the next weighing. */
  measurement: weightSchema.extend({ id: z.number(), measuredAt: z.number() }).nullable(),
})

export type ScaleState = z.infer<typeof scaleStateSchema>

export type Measurement = NonNullable<ScaleState['measurement']>

export const stoppedScale: ScaleState = {
  connection: 'stopped',
  problem: null,
  paired: null,
  live: null,
  measurement: null,
}

/** Reads the state the native module sent; anything it cannot read is treated as stopped. */
export function parseScaleState(value: NativeScaleState): ScaleState {
  const parsed = scaleStateSchema.safeParse(value)

  return parsed.success ? parsed.data : stoppedScale
}

export const healthStatuses = ['available', 'updateRequired', 'unavailable'] as const

export type HealthStatus = (typeof healthStatuses)[number]

export function parseHealthStatus(value: string): HealthStatus {
  return z.enum(healthStatuses).catch('unavailable').parse(value)
}
