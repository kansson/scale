import { DarkTheme, DefaultTheme, Stack, ThemeProvider } from 'expo-router'
import { StatusBar } from 'expo-status-bar'
import * as SystemUI from 'expo-system-ui'
import { useEffect, useMemo } from 'react'
import { GestureHandlerRootView } from 'react-native-gesture-handler'
import AppError from '@/components/app-error'
import { UpdateAlert } from '@/components/update-alert'
import { LocalizationProvider } from '@/i18n'
import { useAppColorScheme } from '@/lib/app-color-scheme'
import { FeedbackProvider } from '@/lib/feedback'
import { rootBackgroundColor } from '@/lib/root-background'
import { useScaleWhileActive } from '@/scale/use-scale'
import { getPreferences } from '@/settings/preferences'
import { applyTheme } from '@/settings/theme'
import { SystemBack } from '@/ui/system-back'
import { checkForUpdatesDaily } from '@/updates/app-updates'
import { revealWindow } from '../../modules/android-components'

export const unstable_settings = { anchor: 'index' }

// Before the first frame, so a chosen theme never flashes the system's.
applyTheme(getPreferences().theme)

function AppProviders() {
  const colorScheme = useAppColorScheme()
  const baseTheme = colorScheme === 'dark' ? DarkTheme : DefaultTheme
  const background = rootBackgroundColor(colorScheme === 'dark')

  const theme = useMemo(
    () => ({ ...baseTheme, colors: { ...baseTheme.colors, background, card: background } }),
    [baseTheme, background],
  )

  useScaleWhileActive()

  useEffect(() => {
    checkForUpdatesDaily()
  }, [])

  useEffect(() => {
    void SystemUI.setBackgroundColorAsync(background)
  }, [background])

  // A theme chosen in Settings crossfades: the old screen's picture fades once the new colours
  // are committed (changeTheme in src/settings/theme.ts).
  useEffect(() => {
    revealWindow()
  }, [colorScheme])

  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <ThemeProvider value={theme}>
        <StatusBar animated />
        <FeedbackProvider>
          {/* Screens open and close with Android 17's activity transitions
              (config/android-transitions), and a back swipe previews the screen below as the
              system does between activities (StackBack.kt in modules/android-components). */}
          <Stack
            screenOptions={{
              animation: 'default',
              contentStyle: { backgroundColor: background },
              headerShown: false,
            }}
          >
            <Stack.Screen name='index' />
            <Stack.Screen name='settings' />
            <Stack.Screen name='licenses/index' />
            <Stack.Screen name='licenses/[name]' />
          </Stack>
          <SystemBack />
          <UpdateAlert />
        </FeedbackProvider>
      </ThemeProvider>
    </GestureHandlerRootView>
  )
}

export default function RootLayout() {
  return (
    <LocalizationProvider>
      <AppProviders />
    </LocalizationProvider>
  )
}

export function ErrorBoundary({ error, retry }: { error: Error; retry: () => void }) {
  return (
    <LocalizationProvider>
      <AppError message={error.message} onRetry={retry} />
    </LocalizationProvider>
  )
}
