import { Appearance } from 'react-native'
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
