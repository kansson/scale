import { Appearance } from 'react-native'
import { crossfadeWindow, systemColorScheme } from '../../modules/android-components'
import type { Theme } from './preferences'

/** The app's own colour, the seed of its palette on phones without wallpaper colours. */
export const brandColor = '#3f6b5c'

/**
 * Applies the theme to the whole app: React Native's appearance, which the Compose hosts and the
 * navigation colours read through `useAppColorScheme`.
 */
export function applyTheme(theme: Theme) {
  Appearance.setColorScheme(theme === 'system' ? 'unspecified' : theme)
}

/**
 * Applies a theme chosen in the app, crossfading the whole screen from the old colours. A theme
 * that looks the same as now (Dark while the phone is dark) just applies: nothing would fade.
 */
export function changeTheme(theme: Theme) {
  const next = theme === 'system' ? systemColorScheme() : theme

  if (next === null || next === Appearance.getColorScheme()) {
    applyTheme(theme)

    return
  }

  void crossfadeWindow(() => applyTheme(theme))
}
