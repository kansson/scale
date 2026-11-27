import { describe, expect, it } from 'vitest'
import { parseHealthStatus, parseLinkResult, parseScaleState, stoppedScale } from './state'

describe('parseScaleState', () => {
  it('reads the state the native module sends', () => {
    const state = parseScaleState({
      connection: 'connected',
      problem: null,
      paired: { address: 'AA:BB', name: 'MIBFS' },
      live: { value: 72.4, unit: 'kg', kilograms: 72.4, stable: true },
      measurement: {
        id: 1,
        value: 72.4,
        unit: 'kg',
        kilograms: 72.4,
        measuredAt: 1000,
        recordId: 'record-1',
      },
      background: true,
    })

    expect(state.connection).toBe('connected')
    expect(state.measurement?.kilograms).toBe(72.4)
    expect(state.measurement?.recordId).toBe('record-1')
    expect(state.background).toBe(true)
  })

  it('treats a state it cannot read as stopped', () => {
    expect(
      parseScaleState({
        connection: 'flying',
        problem: null,
        paired: null,
        live: null,
        measurement: null,
        background: false,
      }),
    ).toEqual(stoppedScale)
  })
})

describe('parseLinkResult', () => {
  it('treats an unknown answer as not linked', () => {
    expect(parseLinkResult('linked')).toBe('linked')
    expect(parseLinkResult('maybe')).toBe('notLinked')
  })
})

describe('parseHealthStatus', () => {
  it('treats an unknown status as unavailable', () => {
    expect(parseHealthStatus('available')).toBe('available')
    expect(parseHealthStatus('sideways')).toBe('unavailable')
  })
})
