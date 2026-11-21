import { usePathname } from 'expo-router'
import { useLayoutEffect } from 'react'
import { setReactNativeBack } from '../../modules/android-components'

/**
 * At the root screen Android owns Back, so the system can animate back-to-home; on the screens
 * above it, the stack previews the screen below as the system does between activities.
 */
export function SystemBack() {
  const root = usePathname() === '/'

  useLayoutEffect(() => {
    let frame: number | null = null
    let attempts = 0

    const apply = () => {
      // The activity may not be attached during the first frames; a second is plenty for that.
      if (!setReactNativeBack(!root) && ++attempts < 60) frame = requestAnimationFrame(apply)
    }

    apply()

    return () => {
      if (frame !== null) cancelAnimationFrame(frame)
      setReactNativeBack(true)
    }
  }, [root])

  return null
}
