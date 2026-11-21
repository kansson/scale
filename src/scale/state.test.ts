import { describe, expect, it } from 'vitest'
import { parseHealthStatus, parseScaleState, stoppedScale } from './state'

describe('parseScaleState', () => {
  it('reads the state the native module sends', () => {
    const state = parseScaleState({
      connection: 'connected',
      problem: null,
      paired: { address: 'AA:BB', name: 'MIBFS' },
      live: { value: 72.4, unit: 'kg', kilograms: 72.4, stable: true },
      measurement: { id: 1, value: 72.4, unit: 'kg', kilograms: 72.4, measuredAt: 1000 },
    })

    expect(state.connection).toBe('connected')
    expect(state.measurement?.kilograms).toBe(72.4)
  })

  it('treats a state it cannot read as stopped', () => {
    expect(
      parseScaleState({
        connection: 'flying',
        problem: null,
        paired: null,
        live: null,
        measurement: null,
      }),
    ).toEqual(stoppedScale)
  })
})

describe('parseHealthStatus', () => {
  it('treats an unknown status as unavailable', () => {
    expect(parseHealthStatus('available')).toBe('available')
    expect(parseHealthStatus('sideways')).toBe('unavailable')
  })
})
