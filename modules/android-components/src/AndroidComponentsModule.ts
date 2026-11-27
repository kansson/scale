import { NativeModule, requireOptionalNativeModule } from 'expo'

declare class AndroidComponents extends NativeModule<{ onNativeDismissal: () => void }> {
  setReactNativeBack?: (enabled: boolean) => boolean
  navigateBack?: () => boolean
  freezeScreen?: (tag: number) => Promise<boolean>
  thawScreen?: (tag: number) => Promise<void>
  takeNativeDismissal?: () => boolean
  coverWindow?: () => Promise<boolean>
  uncoverWindow?: () => Promise<void>
  systemIsDark?: () => boolean
}

const native = requireOptionalNativeModule<AndroidComponents>('AndroidComponents')

/**
 * Whether React Native takes Back. At the root screen it should not, so the system owns Back and
 * animates its predictive back-to-home; false while the activity is not attached yet.
 */
export function setReactNativeBack(enabled: boolean): boolean {
  return native?.setReactNativeBack?.(enabled) ?? false
}

/**
 * Calls `listener` when a committed back swipe is about to dismiss the top stack screen natively,
 * a few hundred milliseconds before it does; returns how to stop listening.
 */
export function onNativeDismissal(listener: () => void): () => void {
  const subscription = native?.addListener('onNativeDismissal', listener)

  return () => subscription?.remove()
}

/** Back through the system dispatcher, so screen back handlers still run. */
export function navigateBack(): boolean {
  return native?.navigateBack?.() ?? false
}

/** How long a closing screen may wait for its frame before it leaves without one. */
const FREEZE_LIMIT_MS = 120

/**
 * Keeps a closing screen's last frame on screen through the stack's exit transition, because its
 * Compose content would redraw empty once React removes it. Resolves when the frame is in place,
 * or after a short limit either way.
 */
export function freezeScreen(tag: number): Promise<boolean> {
  const freeze = native?.freezeScreen

  if (!freeze) return Promise.resolve(false)

  return Promise.race([
    freeze(tag).catch(() => false),
    new Promise<boolean>((resolve) => setTimeout(() => resolve(false), FREEZE_LIMIT_MS)),
  ])
}

/**
 * Whether a committed predictive back has just dismissed a screen natively, once. React then
 * removes the screen only to follow, with nothing left on screen to keep a frame of.
 */
export function takeNativeDismissal(): boolean {
  return native?.takeNativeDismissal?.() ?? false
}

/** Removes the frame from a screen that stayed. */
export function thawScreen(tag: number) {
  void native?.thawScreen?.(tag).catch(() => undefined)
}

/** How long a change may wait for the window's picture before it happens without a crossfade. */
const COVER_LIMIT_MS = 150

/**
 * Makes `change` crossfade the whole window: a picture of it is laid over the app and `change` runs
 * underneath. The picture fades out on `revealWindow`, once the change is committed. Without the
 * picture in time, the change happens as it is.
 */
export async function crossfadeWindow(change: () => void): Promise<void> {
  const cover = native?.coverWindow

  if (cover) {
    await Promise.race([
      cover().catch(() => false),
      new Promise<boolean>((resolve) => setTimeout(() => resolve(false), COVER_LIMIT_MS)),
    ])
  }

  change()
}

/**
 * Fades out the picture `crossfadeWindow` laid over the window, after Android has drawn what React
 * just committed. Does nothing without one.
 */
export function revealWindow() {
  void native?.uncoverWindow?.().catch(() => undefined)
}

/** The phone's own appearance, whatever the app's theme overrides it with; null when unknown. */
export function systemColorScheme(): 'dark' | 'light' | null {
  const dark = native?.systemIsDark?.()

  return dark === undefined ? null : dark ? 'dark' : 'light'
}
