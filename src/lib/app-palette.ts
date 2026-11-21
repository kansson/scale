import { isDynamicColorAvailable } from '@expo/ui/jetpack-compose'
import { brandColor } from '@/settings/theme'

/**
 * The seed of the app's palette, or undefined to follow the wallpaper, which it always does where
 * it can. Phones before Android 12 have no wallpaper colours, so they take the app's own.
 */
export const seedColor = isDynamicColorAvailable ? undefined : brandColor
