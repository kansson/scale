import { getMaterialColors } from '@expo/ui/jetpack-compose'
import { seedColor } from './app-palette'

/** The Material `surface` behind native-stack transitions, so fades never flash white. */
export function rootBackgroundColor(isDark: boolean) {
  return getMaterialColors({ scheme: isDark ? 'dark' : 'light', seedColor }).surface
}
