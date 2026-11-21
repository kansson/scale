import { Icon, IconButton, Row } from '@expo/ui/jetpack-compose'
import { useLingui } from '@lingui/react/macro'
import { useIsFocused, useNavigation, usePreventRemove, useRouter } from 'expo-router'
import { type ReactNode, useEffect, useRef, useState } from 'react'
import { findNodeHandle, type HostInstance, View } from 'react-native'
import { Host } from '@/components/host'
import { useFeedback } from '@/lib/feedback'
import {
  freezeScreen,
  LargeTopAppBar,
  navigateBack,
  onNativeDismissal,
  Scaffold,
  takeNativeDismissal,
  thawScreen,
  TopAppBar,
} from '../../modules/android-components'
import { type Glyph, glyphs } from './glyphs'

export type ActionProps = {
  glyph: Glyph
  label: string
  onPress: () => void
  enabled?: boolean
}

/**
 * An icon button with its accessible label. It sets no colour: in an app bar or list item slot it
 * takes the colour that slot provides. Inside an app bar, guard the handler instead of toggling
 * `enabled`: Expo UI removes a slot's icon button when `enabled` changes.
 */
export function Action({ glyph, label, onPress, enabled = true }: ActionProps) {
  return (
    <IconButton onClick={onPress} enabled={enabled}>
      <Icon source={glyphs[glyph]} size={24} contentDescription={label} />
    </IconButton>
  )
}

function BackAction() {
  const { t } = useLingui()
  const router = useRouter()

  return (
    <Action
      glyph='back'
      label={t({ message: 'Back', comment: 'Top bar button returning to the previous screen' })}
      // Through the system dispatcher, so screen back handlers still run.
      onPress={() => navigateBack() || router.back()}
    />
  )
}

/** Long enough for any exit transition; a screen still here then was not removed after all. */
const LEAVE_LIMIT_MS = 1000

/**
 * Back (or a link) closing this screen keeps its last frame through the stack's exit
 * transition. React removes the screen's views before the transition starts and its Compose
 * content would redraw empty, so the screen holds its removal until the frame is in place, then
 * lets it go on. A screen a back swipe dismisses natively is already drawn by the swipe, so it
 * lets go at once.
 */
function useFrameOnLeave() {
  const navigation = useNavigation()
  const frame = useRef<HostInstance>(null)
  // Held while the screen shows, and let go once its frame is kept.
  const [holding, setHolding] = useState(true)
  const removal = useRef<(() => void) | null>(null)

  usePreventRemove(holding, ({ repeat }) => {
    const tag = findNodeHandle(frame.current) ?? null

    const letGo = () => {
      removal.current = repeat
      setHolding(false)
    }

    // A predictive back has already taken the screen away natively; React only follows.
    if (tag === null || !navigation.isFocused() || takeNativeDismissal()) {
      letGo()

      return
    }

    void freezeScreen(tag).then(letGo)
  })

  // A back swipe dismisses the screen natively once its animation ends; holding the removal then
  // would leave the screen in the navigation state, so it lets go as the swipe commits.
  useEffect(
    () =>
      onNativeDismissal(() => {
        if (navigation.isFocused()) setHolding(false)
      }),
    [navigation],
  )

  // The removal goes on once the screen has stopped holding it; a screen still here later takes
  // its frame away and holds again.
  useEffect(() => {
    const repeat = removal.current

    if (holding || !repeat) return

    removal.current = null
    repeat()

    const timer = setTimeout(() => {
      const tag = findNodeHandle(frame.current) ?? null

      if (tag !== null) thawScreen(tag)

      setHolding(true)
    }, LEAVE_LIMIT_MS)

    return () => clearTimeout(timer)
  }, [holding])

  return frame
}

export type ScreenProps = {
  title: string
  /** Large collapsing bars for top-level destinations, small ones for detail screens. */
  appBar?: 'large' | 'small'
  /** A back arrow on pushed screens. */
  navigation?: 'back'
  actions?: ReactNode
  /**
   * The body draws down to the screen's bottom edge, behind the system navigation bar, and keeps
   * its own content above it, such as a docked panel.
   */
  toBottomEdge?: boolean
  children?: ReactNode
}

/**
 * A whole screen in one Compose tree: the Material 3 Scaffold, its app bar and the snackbar for
 * feedback, shown while the screen is focused.
 */
export function Screen({
  title,
  appBar = 'small',
  navigation,
  actions,
  toBottomEdge = false,
  children,
}: ScreenProps) {
  const focused = useIsFocused()
  const frame = useFrameOnLeave()
  const { clearFeedback, feedback } = useFeedback()
  const presented = focused ? feedback : null
  const navigationIcon = navigation ? <BackAction /> : undefined
  // Always present: actions often appear only once the screen's data has loaded.
  const actionRow = <Row verticalAlignment='center'>{actions}</Row>

  const topBar =
    appBar === 'large' ? (
      <LargeTopAppBar title={title} navigationIcon={navigationIcon} actions={actionRow} />
    ) : (
      <TopAppBar title={title} navigationIcon={navigationIcon} actions={actionRow} />
    )

  return (
    <View ref={frame} style={{ flex: 1 }} collapsable={false}>
      <Host style={{ flex: 1 }}>
        <Scaffold
          topBar={topBar}
          scrollBehavior={appBar === 'large' ? 'exitUntilCollapsed' : 'pinned'}
          bottomInset={!toBottomEdge}
          snackbarId={presented?.id ?? 0}
          snackbarMessage={presented?.message ?? ''}
          onSnackbarDismiss={presented ? () => clearFeedback(presented.id) : undefined}
        >
          {children}
        </Scaffold>
      </Host>
    </View>
  )
}
