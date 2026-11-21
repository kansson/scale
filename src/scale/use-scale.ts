import { useEffect, useSyncExternalStore } from 'react'
import { AppState } from 'react-native'
import { Scale } from '../../modules/scale'
import { parseScaleState, type ScaleState } from './state'

let snapshot: ScaleState | null = null

function readState() {
  snapshot ??= parseScaleState(Scale.getState())

  return snapshot
}

function subscribe(listener: () => void) {
  // The native side sends the current state as soon as someone listens, then every change.
  const subscription = Scale.addListener('onStateChange', (state) => {
    snapshot = parseScaleState(state)
    listener()
  })

  return () => subscription.remove()
}

/** The scale's connection, what it shows now and the weighing's settled weight. */
export function useScaleState() {
  return useSyncExternalStore(subscribe, readState, readState)
}

/**
 * Looks for the scale while the app is in front, and lets go of it in the background. Coming back
 * starts it again, which also picks up a permission or Bluetooth allowed in the meantime.
 */
export function useScaleWhileActive() {
  useEffect(() => {
    if (AppState.currentState === 'active') Scale.start()

    const subscription = AppState.addEventListener('change', (next) => {
      if (next === 'active') Scale.start()
      else if (next === 'background') Scale.stop()
    })

    return () => {
      subscription.remove()
      Scale.stop()
    }
  }, [])
}
